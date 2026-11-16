package com.vijay.aimagicremote.protocol

import android.content.Context
import android.util.Log
import com.vijay.aimagicremote.bluetooth.BluetoothController
import com.vijay.aimagicremote.bluetooth.BluetoothHidController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified, capability-aware remote dispatcher.
 *
 * Exactly one protocol target is active at a time. A connection is considered
 * ready only after the selected protocol completes its real handshake.
 */
object RemoteManager {

    private const val TAG = "RemoteManager"

    enum class TargetDeviceType {
        ANDROID_TV,
        SAMSUNG_TIZEN,
        LG_WEBOS,
        ROKU_TCL,
        WINDOWS_PC,
        BLUETOOTH_HID
    }

    interface ConnectCallback {
        fun onConnected()
        fun onConnectionFailed(error: String)
        fun onDisconnected()
    }

    @Volatile
    var activeTargetType: TargetDeviceType = TargetDeviceType.ANDROID_TV
        private set

    @Volatile
    var isConnected: Boolean = false

    @Volatile
    var currentTargetIp: String = ""

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionGeneration = AtomicLong(0)

    fun setTargetType(type: TargetDeviceType) {
        activeTargetType = type
        isConnected = false
        Log.i(TAG, "Target device type set to: $type")
    }

    fun supportsRemoteKeys(type: TargetDeviceType): Boolean =
        type != TargetDeviceType.BLUETOOTH_HID

    /** True only where the app has a genuine target-side pointer transport. */
    fun supportsPointer(type: TargetDeviceType): Boolean =
        type == TargetDeviceType.WINDOWS_PC ||
            type == TargetDeviceType.LG_WEBOS ||
            type == TargetDeviceType.SAMSUNG_TIZEN ||
            type == TargetDeviceType.BLUETOOTH_HID

    fun supportsText(type: TargetDeviceType): Boolean =
        type != TargetDeviceType.BLUETOOTH_HID

    fun isPointerReady(type: TargetDeviceType): Boolean = when (type) {
        TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.isConnected
        TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.isPointerReady()
        else -> false
    }

    fun connect(
        context: Context,
        targetType: TargetDeviceType,
        ip: String,
        port: Int = 0,
        bluetoothController: BluetoothController? = null,
        callback: ConnectCallback? = null
    ) {
        val cleanIp = ip.trim().removePrefix("/")
        val generation = connectionGeneration.incrementAndGet()

        // Close every previous protocol before selecting the new one. This avoids
        // stale callbacks and commands being sent to a previous target.
        disconnectAllProtocols()
        setTargetType(targetType)
        currentTargetIp = cleanIp

        fun isCurrent(): Boolean =
            generation == connectionGeneration.get() && activeTargetType == targetType

        fun connected() {
            if (!isCurrent()) return
            isConnected = true
            callback?.onConnected()
        }

        fun failed(error: String) {
            if (!isCurrent()) return
            isConnected = false
            callback?.onConnectionFailed(error)
        }

        fun disconnected() {
            if (!isCurrent()) return
            isConnected = false
            callback?.onDisconnected()
        }

        when (targetType) {
            TargetDeviceType.ANDROID_TV -> Atrpv2Protocol.connect(cleanIp, object : Atrpv2Protocol.ConnectCallback {
                override fun onConnected() = connected()
                override fun onConnectionFailed(error: String) = failed(error)
                override fun onDisconnected() = disconnected()
            })

            TargetDeviceType.SAMSUNG_TIZEN -> SamsungTizenProtocol.connect(cleanIp, object : SamsungTizenProtocol.ConnectCallback {
                override fun onConnected() = connected()
                override fun onConnectionFailed(error: String) = failed(error)
                override fun onDisconnected() = disconnected()
            })

            TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.connect(cleanIp, object : LgWebOsProtocol.ConnectCallback {
                override fun onConnected() = connected()
                override fun onConnectionFailed(error: String) = failed(error)
                override fun onDisconnected() = disconnected()
            })

            TargetDeviceType.ROKU_TCL -> RokuTclProtocol.connect(cleanIp, object : RokuTclProtocol.ConnectCallback {
                override fun onConnected() = connected()
                override fun onConnectionFailed(error: String) = failed(error)
                override fun onDisconnected() = disconnected()
            })

            TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.connect(cleanIp, if (port > 0) port else PcRemoteProtocol.DEFAULT_PORT, object : PcRemoteProtocol.ConnectCallback {
                override fun onConnected() = connected()
                override fun onConnectionFailed(error: String) = failed(error)
                override fun onDisconnected() = disconnected()
            })

            TargetDeviceType.BLUETOOTH_HID -> {
                // Phone-as-HID requires a real Bluetooth HID peripheral session.
                // Merely having Bluetooth switched on is not a connection.
                failed("Bluetooth HID is not implemented on this phone app. Use Wi-Fi control or run the receiver on a Windows PC.")
            }
        }
    }

    fun disconnect() {
        connectionGeneration.incrementAndGet()
        when (activeTargetType) {
            TargetDeviceType.ANDROID_TV -> Atrpv2Protocol.disconnect()
            TargetDeviceType.SAMSUNG_TIZEN -> SamsungTizenProtocol.disconnect()
            TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.disconnect()
            TargetDeviceType.ROKU_TCL -> RokuTclProtocol.disconnect()
            TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.disconnect()
            TargetDeviceType.BLUETOOTH_HID -> Unit
        }
        isConnected = false
    }

    private fun disconnectAllProtocols() {
        Atrpv2Protocol.disconnect()
        SamsungTizenProtocol.disconnect()
        LgWebOsProtocol.disconnect()
        RokuTclProtocol.disconnect()
        PcRemoteProtocol.disconnect()
        isConnected = false
    }

    fun sendKey(androidKeyCode: Int, pcKeyName: String) {
        if (!isConnected) return
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> Atrpv2Protocol.sendKey(androidKeyCode)
                TargetDeviceType.SAMSUNG_TIZEN -> SamsungTizenProtocol.sendKey(pcKeyName)
                TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.sendKey(pcKeyName)
                TargetDeviceType.ROKU_TCL -> RokuTclProtocol.sendKey(pcKeyName)
                TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.sendKey(pcKeyName)
                TargetDeviceType.BLUETOOTH_HID -> if (BluetoothHidController.isConnected()) {
                    BluetoothHidController.sendSpecialKey(pcKeyName)
                }
            }
        }
    }

    /** Send genuine pointer movement to the selected target. */
    fun sendPointerMove(dx: Float, dy: Float) {
        if (!isConnected) return
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> Atrpv2Protocol.sendPointerMove(dx, dy)
                TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.sendMouseMove(dx, dy)
                TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.sendPointerMove(dx, dy)
                TargetDeviceType.SAMSUNG_TIZEN -> SamsungTizenProtocol.sendPointerMove(dx, dy)
                TargetDeviceType.BLUETOOTH_HID -> if (BluetoothHidController.isConnected()) {
                    BluetoothHidController.sendMouseReport(dx.toInt(), dy.toInt(), 0, 0)
                }
                else -> Unit
            }
        }
    }

    fun sendPointerButtonDown(isRightClick: Boolean) {
        if (!isConnected) return
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.sendMouseButtonDown(if (isRightClick) "right" else "left")
                else -> Unit
            }
        }
    }

    fun sendPointerButtonUp(isRightClick: Boolean) {
        if (!isConnected) return
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.sendMouseButtonUp(if (isRightClick) "right" else "left")
                else -> Unit
            }
        }
    }

    fun sendClick(isRightClick: Boolean = false) {
        if (!isConnected) return
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.sendMouseClick(if (isRightClick) "right" else "left")
                TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.sendPointerClick(isRightClick)
                else -> Unit
            }
        }
    }

    fun sendScroll(dx: Float, dy: Float) {
        if (!isConnected) return
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> Atrpv2Protocol.sendPointerScroll(dx, dy)
                TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.sendMouseScroll(dx, dy)
                TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.sendPointerScroll(dx, dy)
                TargetDeviceType.SAMSUNG_TIZEN -> SamsungTizenProtocol.sendPointerScroll(dx, dy)
                TargetDeviceType.BLUETOOTH_HID -> if (BluetoothHidController.isConnected()) {
                    BluetoothHidController.sendMouseScroll(dx, dy)
                }
                else -> Unit
            }
        }
    }

    fun sendText(text: String) {
        if (!isConnected || !supportsText(activeTargetType) || text.isEmpty()) return
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> Atrpv2Protocol.sendText(text)
                TargetDeviceType.SAMSUNG_TIZEN -> SamsungTizenProtocol.sendText(text)
                TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.sendText(text)
                TargetDeviceType.ROKU_TCL -> RokuTclProtocol.sendText(text)
                TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.sendText(text)
                TargetDeviceType.BLUETOOTH_HID -> if (BluetoothHidController.isConnected()) {
                    BluetoothHidController.sendText(text)
                }
            }
        }
    }
}
