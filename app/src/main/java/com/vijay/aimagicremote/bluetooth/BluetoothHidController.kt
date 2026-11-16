package com.vijay.aimagicremote.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns the phone into a Bluetooth HID keyboard + mouse.
 *
 * This is additive to the Wi-Fi transport. It does not remove or replace any
 * existing Wi-Fi functionality.
 */
object BluetoothHidController {

    private const val TAG = "BluetoothHidController"

    private val mouseDescriptor = byteArrayOf(
        0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01,
        0x85.toByte(), 0x01,
        0x09, 0x01, 0xA1.toByte(), 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x95.toByte(), 0x03, 0x75, 0x01, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x05, 0x81.toByte(), 0x01,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38, 0x15.toByte(), 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08, 0x95.toByte(), 0x03, 0x81.toByte(), 0x06,
        0xC0.toByte(), 0xC0.toByte(),
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01,
        0x85.toByte(), 0x02,
        0x05, 0x07, 0x19, 0x00, 0x29.toByte(), 0xFF.toByte(), 0x15, 0x00, 0x25.toByte(), 0xFF.toByte(), 0x75, 0x08, 0x95.toByte(), 0x06, 0x81.toByte(), 0x00,
        0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x05, 0x91.toByte(), 0x02,
        0x95.toByte(), 0x03, 0x75, 0x01, 0x91.toByte(), 0x01,
        0xC0.toByte()
    )

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var appContext: Context? = null
    private var hidDevice: BluetoothHidDevice? = null
    @Volatile private var registered: Boolean = false
    @Volatile private var hostDevice: BluetoothDevice? = null

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                this@BluetoothHidController.register()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                registered = false
                hostDevice = null
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device
                Log.i(TAG, "Bluetooth HID host connected: ${device.name}")
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (hostDevice == device) hostDevice = null
            }
        }

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BluetoothHidController.registered = registered
            Log.i(TAG, "Bluetooth HID app status: registered=$registered device=$pluggedDevice")
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            hidDevice?.replyReport(device, type, id, ByteArray(0))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {}
        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {}
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {}
    }

    fun initialize(context: Context) {
        if (adapter == null) return
        appContext = context.applicationContext
        try {
            adapter.getProfileProxy(context.applicationContext, serviceListener, BluetoothProfile.HID_DEVICE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get HID profile proxy", e)
        }
    }

    private fun register() {
        val hid = hidDevice ?: return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "AirRemote",
            "Air Remote HID",
            "Vijay",
            BluetoothHidDevice.SUBCLASS1_MOUSE,
            mouseDescriptor
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED,
            800, 9, 800, 9, 800
        )
        val executor = appContext?.mainExecutor ?: return
        try {
            hid.registerApp(sdp, qos, qos, executor, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register HID app", e)
        }
    }

    fun start(context: Context) {
        initialize(context)
        // Registration happens asynchronously in onServiceConnected.
    }

    fun stop() {
        try {
            hidDevice?.unregisterApp()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister HID app", e)
        }
        registered = false
        hostDevice = null
    }

    fun isRegistered(): Boolean = registered

    fun isConnected(): Boolean = hostDevice != null

    fun sendMouseClick(button: String) {
        val btn = if (button == "right") 0x02 else 0x01
        sendMouseReport(0, 0, btn, 0)
        Thread.sleep(30)
        sendMouseReport(0, 0, 0, 0)
    }

    fun sendMouseScroll(dx: Float, dy: Float) {
        sendMouseReport(0, 0, 0, dy.toInt().coerceIn(-127, 127))
    }

    fun sendMouseReport(dx: Int, dy: Int, buttons: Int, wheel: Int) {
        val device = hostDevice ?: return
        val hid = hidDevice ?: return
        val report = byteArrayOf(
            (buttons and 0x07).toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte()
        )
        hid.sendReport(device, 1, report)
    }

    fun sendKeyReport(modifiers: Int, keyCode: Int) {
        val device = hostDevice ?: return
        val hid = hidDevice ?: return
        val report = byteArrayOf(modifiers.toByte(), 0, keyCode.toByte(), 0, 0, 0, 0, 0)
        hid.sendReport(device, 2, report)
        // Release
        hid.sendReport(device, 2, ByteArray(8))
    }

    fun sendText(text: String) {
        for (ch in text) {
            val code = charToHidCode(ch) ?: continue
            val shift = if (ch.isUpperCase() || ch in "!@#$%^&*()_+{}|:\"<>?") 0x02 else 0
            sendKeyReport(shift, code)
        }
    }

    fun sendSpecialKey(name: String) {
        val code = when (name.uppercase()) {
            "ENTER" -> 0x28
            "ESCAPE", "BACK" -> 0x29
            "TAB" -> 0x2B
            "SPACE" -> 0x2C
            "BACKSPACE" -> 0x2A
            "UP" -> 0x52
            "DOWN" -> 0x51
            "LEFT" -> 0x50
            "RIGHT" -> 0x4F
            "HOME" -> 0x4A
            "END" -> 0x4D
            "PAGEUP" -> 0x4B
            "PAGEDOWN" -> 0x4E
            else -> null
        } ?: return
        sendKeyReport(0, code)
    }

    private fun charToHidCode(ch: Char): Int? {
        return when (ch) {
            in 'a'..'z' -> 0x04 + (ch - 'a')
            in 'A'..'Z' -> 0x04 + (ch - 'A')
            in '1'..'9' -> 0x1E + (ch - '1')
            '0' -> 0x27
            ' ' -> 0x2C
            '-' -> 0x2D
            '=' -> 0x2E
            '[' -> 0x2F
            ']' -> 0x30
            '\\' -> 0x31
            ';' -> 0x33
            '\'' -> 0x34
            '`' -> 0x35
            ',' -> 0x36
            '.' -> 0x37
            '/' -> 0x38
            else -> null
        }
    }
}
