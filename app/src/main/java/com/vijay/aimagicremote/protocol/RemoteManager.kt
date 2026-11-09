package com.vijay.aimagicremote.protocol

import android.content.Context
import android.util.Log
import com.vijay.aimagicremote.bluetooth.BluetoothController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Unified Remote Control Manager
 * Enforces dedicated protocol target routing with fallback socket dispatch.
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

    @Volatile
    var isConnected: Boolean = false

    @Volatile
    var currentTargetIp: String = ""

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setTargetType(type: TargetDeviceType) {
        activeTargetType = type
        Log.i(TAG, "Target device type set to: $type")
    }

    fun connect(
        context: Context,
        targetType: TargetDeviceType,
        ip: String,
        bluetoothController: BluetoothController? = null,
        callback: ConnectCallback? = null
    ) {
        setTargetType(targetType)
        val cleanIp = ip.removePrefix("/")
        currentTargetIp = cleanIp

        PcRemoteProtocol.currentPcIp = cleanIp

        when (targetType) {
            TargetDeviceType.ANDROID_TV -> {
                Atrpv2Protocol.connect(cleanIp, object : Atrpv2Protocol.ConnectCallback {
                    override fun onConnected() {
                        isConnected = true
                        callback?.onConnected()
                    }
                    override fun onConnectionFailed(error: String) {
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }
                    override fun onDisconnected() {
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.SAMSUNG_TIZEN -> {
                SamsungTizenProtocol.connect(cleanIp, object : SamsungTizenProtocol.ConnectCallback {
                    override fun onConnected() {
                        isConnected = true
                        callback?.onConnected()
                    }
                    override fun onConnectionFailed(error: String) {
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }
                    override fun onDisconnected() {
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.LG_WEBOS -> {
                LgWebOsProtocol.connect(cleanIp, object : LgWebOsProtocol.ConnectCallback {
                    override fun onConnected() {
                        isConnected = true
                        callback?.onConnected()
                    }
                    override fun onConnectionFailed(error: String) {
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }
                    override fun onDisconnected() {
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.ROKU_TCL -> {
                RokuTclProtocol.connect(cleanIp, object : RokuTclProtocol.ConnectCallback {
                    override fun onConnected() {
                        isConnected = true
                        callback?.onConnected()
                    }
                    override fun onConnectionFailed(error: String) {
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }
                    override fun onDisconnected() {
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.WINDOWS_PC -> {
                PcRemoteProtocol.connect(cleanIp, PcRemoteProtocol.DEFAULT_PORT, object : PcRemoteProtocol.ConnectCallback {
                    override fun onConnected() {
                        isConnected = true
                        callback?.onConnected()
                    }

                    override fun onConnectionFailed(error: String) {
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }

                    override fun onDisconnected() {
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.BLUETOOTH_HID -> {
                val btAvailable = bluetoothController?.isBluetoothAvailable() ?: false
                if (btAvailable) {
                    isConnected = true
                    callback?.onConnected()
                } else {
                    isConnected = false
                    callback?.onConnectionFailed("Bluetooth is disabled or not available on this device")
                }
            }
        }
    }

    fun disconnect() {
        when (activeTargetType) {
            TargetDeviceType.ANDROID_TV -> Atrpv2Protocol.disconnect()
            TargetDeviceType.SAMSUNG_TIZEN -> SamsungTizenProtocol.disconnect()
            TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.disconnect()
            TargetDeviceType.ROKU_TCL -> RokuTclProtocol.disconnect()
            TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.disconnect()
            TargetDeviceType.BLUETOOTH_HID -> {}
        }
        isConnected = false
    }

    /**
     * Send key command to active target device
     */
    fun sendKey(androidKeyCode: Int, pcKeyName: String) {
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> {
                    Atrpv2Protocol.sendKey(androidKeyCode)
                }
                TargetDeviceType.SAMSUNG_TIZEN -> {
                    SamsungTizenProtocol.sendKey(pcKeyName)
                }
                TargetDeviceType.LG_WEBOS -> {
                    LgWebOsProtocol.sendKey(pcKeyName)
                }
                TargetDeviceType.ROKU_TCL -> {
                    RokuTclProtocol.sendKey(pcKeyName)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendKey(pcKeyName)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    PcRemoteProtocol.sendKey(pcKeyName)
                }
            }
        }
    }

    /**
     * Send pointer/mouse motion deltas
     */
    fun sendPointerMove(dx: Float, dy: Float, bluetoothController: BluetoothController? = null) {
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> {
                    Atrpv2Protocol.sendPointerMove(dx, dy)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendMouseMove(dx, dy)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    PcRemoteProtocol.sendMouseMove(dx, dy)
                }
                else -> {}
            }
        }
    }

    /**
     * Send Mouse Click (Left or Right click)
     */
    fun sendClick(isRightClick: Boolean = false) {
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> {
                    val keyCode = if (isRightClick) Atrpv2Protocol.KEY_CODES.BACK else Atrpv2Protocol.KEY_CODES.OK
                    Atrpv2Protocol.sendKey(keyCode)
                }
                TargetDeviceType.SAMSUNG_TIZEN -> {
                    val keyName = if (isRightClick) "BACK" else "OK"
                    SamsungTizenProtocol.sendKey(keyName)
                }
                TargetDeviceType.LG_WEBOS -> {
                    val keyName = if (isRightClick) "BACK" else "OK"
                    LgWebOsProtocol.sendKey(keyName)
                }
                TargetDeviceType.ROKU_TCL -> {
                    val keyName = if (isRightClick) "BACK" else "OK"
                    RokuTclProtocol.sendKey(keyName)
                }
                TargetDeviceType.WINDOWS_PC, TargetDeviceType.BLUETOOTH_HID -> {
                    PcRemoteProtocol.sendMouseClick(if (isRightClick) "right" else "left")
                }
            }
        }
    }

    /**
     * Send Scroll Event
     */
    fun sendScroll(dx: Float, dy: Float) {
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> {
                    Atrpv2Protocol.sendPointerMove(dx, dy)
                }
                TargetDeviceType.WINDOWS_PC, TargetDeviceType.BLUETOOTH_HID -> {
                    PcRemoteProtocol.sendMouseScroll(dx, dy)
                }
                else -> {}
            }
        }
    }

    /**
     * Send Text String
     */
    fun sendText(text: String) {
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> {
                    Atrpv2Protocol.sendText(text)
                }
                TargetDeviceType.WINDOWS_PC, TargetDeviceType.BLUETOOTH_HID -> {
                    PcRemoteProtocol.sendText(text)
                }
                else -> {}
            }
        }
    }
}
