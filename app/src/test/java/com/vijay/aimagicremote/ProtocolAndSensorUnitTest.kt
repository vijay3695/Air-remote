package com.vijay.aimagicremote

import com.vijay.aimagicremote.bluetooth.OneEuroFilter
import com.vijay.aimagicremote.protocol.Atrpv2Protocol
import com.vijay.aimagicremote.protocol.AtvWire
import com.vijay.aimagicremote.protocol.LgWebOsProtocol
import com.vijay.aimagicremote.protocol.PcRemoteProtocol
import com.vijay.aimagicremote.protocol.RemoteManager
import com.vijay.aimagicremote.protocol.PcCommand
import com.vijay.aimagicremote.protocol.RokuTclProtocol
import com.vijay.aimagicremote.protocol.SamsungTizenProtocol
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Unit tests for Air Remote Multi-Brand Protocols, 1€ Filter, and Target Routing
 */
class ProtocolAndSensorUnitTest {

    @Test
    fun testAtvWireVarintRoundTripAcrossBoundaries() {
        val values = listOf(0L, 1L, 127L, 128L, 300L, 16_384L, 2_097_151L)
        for (value in values) {
            assertEquals(value, AtvWire.readVarint(ByteArrayInputStream(AtvWire.varint(value))))
        }
    }

    @Test
    fun testAtvWireProtobufFieldsRoundTrip() {
        val encoded = AtvWire.message(
            AtvWire.int(1, 300),
            AtvWire.string(2, "Air Remote")
        )
        val fields = AtvWire.parse(encoded)
        assertEquals(300, fields.first { it.number == 1 }.numberValue.toInt())
        assertEquals("Air Remote", String(fields.first { it.number == 2 }.bytes!!, Charsets.UTF_8))
    }

    @Test
    fun testAtvWireLengthDelimitedFrameRoundTrip() {
        val payload = AtvWire.message(AtvWire.int(1, 42), AtvWire.string(2, "frame"))
        val output = ByteArrayOutputStream()
        AtvWire.writeFrame(output, payload)
        assertArrayEquals(payload, AtvWire.readFrame(ByteArrayInputStream(output.toByteArray())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAtvWireRejectsOversizedFrame() {
        AtvWire.readFrame(ByteArrayInputStream(AtvWire.varint(1_048_577L)))
    }
    @Test
    fun testOneEuroFilterSmoothing() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.007f, dCutoff = 1.0f)
        var timeNs = 1_000_000_000L

        // Initial raw sample
        val sample1 = filter.filter(10.0f, timeNs)
        assertEquals(10.0f, sample1, 0.001f)

        // Jittery noisy sample shortly after (16ms)
        timeNs += 16_000_000L
        val sample2 = filter.filter(25.0f, timeNs)

        // The filtered value should smooth out the spike
        assertTrue("Filtered sample should smooth out noise spike", sample2 < 25.0f)
        assertTrue("Filtered sample should be above base sample", sample2 > 10.0f)
    }

    @Test
    fun testIpStringCleaning() {
        val rawHostWithSlash = "/192.168.1.4"
        val cleanHost = rawHostWithSlash.removePrefix("/")
        assertEquals("192.168.1.4", cleanHost)

        val rawHostWithoutSlash = "192.168.1.3"
        val cleanHost2 = rawHostWithoutSlash.removePrefix("/")
        assertEquals("192.168.1.3", cleanHost2)
    }

    @Test
    fun testAtrpv2KeyCodesMapping() {
        assertEquals(19, Atrpv2Protocol.KEY_CODES.UP)
        assertEquals(20, Atrpv2Protocol.KEY_CODES.DOWN)
        assertEquals(21, Atrpv2Protocol.KEY_CODES.LEFT)
        assertEquals(22, Atrpv2Protocol.KEY_CODES.RIGHT)
        assertEquals(23, Atrpv2Protocol.KEY_CODES.OK)
        assertEquals(4, Atrpv2Protocol.KEY_CODES.BACK)
        assertEquals(3, Atrpv2Protocol.KEY_CODES.HOME)
        assertEquals(26, Atrpv2Protocol.KEY_CODES.POWER)
        assertEquals(24, Atrpv2Protocol.KEY_CODES.VOLUME_UP)
        assertEquals(25, Atrpv2Protocol.KEY_CODES.VOLUME_DOWN)
        assertEquals(164, Atrpv2Protocol.KEY_CODES.MUTE)
    }

    @Test
    fun testMultiBrandProtocolPorts() {
        assertEquals(8060, RokuTclProtocol.ECP_PORT)
        assertEquals(8001, SamsungTizenProtocol.WS_PORT)
        assertEquals(8002, SamsungTizenProtocol.WSS_PORT)
        assertEquals(3000, LgWebOsProtocol.WS_PORT)
        assertEquals(3001, LgWebOsProtocol.WSS_PORT)
    }

    @Test
    fun testPcRemoteProtocolDefaultPort() {
        assertEquals(9090, PcRemoteProtocol.DEFAULT_PORT)
        assertEquals(8080, PcRemoteProtocol.HTTP_PORT)
        assertFalse(PcRemoteProtocol.isConnected)
    }

    @Test
    fun testPcCommandMouseMoveWireFormat() {
        val encoded = PcCommand("mouse_move", id = "42", dx = 12.5f, dy = -3.25f).encode()
        assertTrue(encoded.contains("\"cmd\":\"mouse_move\""))
        assertTrue(encoded.contains("\"id\":\"42\""))
        assertTrue(encoded.contains("\"dx\":12.5"))
        assertTrue(encoded.contains("\"dy\":-3.25"))
    }

    @Test
    fun testPcCommandClickAndTextWireFormat() {
        val click = PcCommand("click", id = "7", button = "right").encode()
        assertTrue(click.contains("\"cmd\":\"click\""))
        assertTrue(click.contains("\"button\":\"right\""))

        val typed = PcCommand("type", id = "8", text = "a\"b\\c").encode()
        assertTrue(typed.contains("\"cmd\":\"type\""))
        assertTrue(typed.contains("a\\\"b"))
    }

    @Test
    fun testRemoteManagerTruthfulPointerSupportMatrix() {
        assertTrue(RemoteManager.supportsPointer(RemoteManager.TargetDeviceType.BLUETOOTH_HID))
        assertFalse(RemoteManager.supportsPointer(RemoteManager.TargetDeviceType.ANDROID_TV))
        assertTrue(RemoteManager.supportsPointer(RemoteManager.TargetDeviceType.WINDOWS_PC))
        assertTrue(RemoteManager.supportsPointer(RemoteManager.TargetDeviceType.LG_WEBOS))
        assertTrue(RemoteManager.supportsPointer(RemoteManager.TargetDeviceType.SAMSUNG_TIZEN))
    }

    @Test
    fun testRemoteManagerTargetDeviceTypes() {
        assertEquals(RemoteManager.TargetDeviceType.ANDROID_TV, RemoteManager.TargetDeviceType.valueOf("ANDROID_TV"))
        assertEquals(RemoteManager.TargetDeviceType.SAMSUNG_TIZEN, RemoteManager.TargetDeviceType.valueOf("SAMSUNG_TIZEN"))
        assertEquals(RemoteManager.TargetDeviceType.LG_WEBOS, RemoteManager.TargetDeviceType.valueOf("LG_WEBOS"))
        assertEquals(RemoteManager.TargetDeviceType.ROKU_TCL, RemoteManager.TargetDeviceType.valueOf("ROKU_TCL"))
        assertEquals(RemoteManager.TargetDeviceType.WINDOWS_PC, RemoteManager.TargetDeviceType.valueOf("WINDOWS_PC"))
        assertEquals(RemoteManager.TargetDeviceType.BLUETOOTH_HID, RemoteManager.TargetDeviceType.valueOf("BLUETOOTH_HID"))
    }
}
