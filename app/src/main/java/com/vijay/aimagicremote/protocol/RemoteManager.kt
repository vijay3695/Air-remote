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
 * Enforces strict connection state truthfulness and dedicated protocol target routing (no protocol spraying).
 * Supports:
 * - Android TV / Google TV (ATRPv2 TLS Ports 6466/6467)
 * - Samsung Tizen Smart TV (v2 WebSocket Ports 8001/8002)
 * - LG webOS Smart TV (SSAP WebSocket Ports 3000/3001)
 * - TCL / Roku Smart TV (ECP HTTP Port 8060)
 * - Windows PC (TCP/UDP Port 9090)
 * - Bluetooth HID
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
        private set

    @Volatile
    var currentTargetIp: String = ""
        private set

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
        Atrpv2Protocol.currentTvIp = cleanIp

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
                isConnected = false
                callback?.onConnectionFailed(
                    if (bluetoothController?.isBluetoothAvailable() == true) {
                        "Bluetooth is enabled, but this build does not yet establish a Bluetooth HID connection to the TV. It cannot be marked connected until the TV accepts the phone as a mouse/keyboard."
                    } else {
                        "Bluetooth is disabled or not available on this device."
                    }
                )
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
     * Send key command to active target device only
     */
    fun sendKey(androidKeyCode: Int, pcKeyName: String) {
        scope.launch {
            if (!isConnected) {
                Log.w(TAG, "Cannot send key: Target device is not connected.")
                return@launch
            }

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
                    Log.w(TAG, "Bluetooth HID command rejected: no HID session is connected.")
                }
            }
        }
    }

    /**
     * Send pointer/mouse motion deltas
     */
    fun sendPointerMove(dx: Float, dy: Float, bluetoothController: BluetoothController? = null) {
        scope.launch {
            if (!isConnected) return@launch

            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> {
                    Atrpv2Protocol.injectPointer(dx, dy, isAbsolute = false)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendMouseMove(dx, dy)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    Log.w(TAG, "Bluetooth HID pointer rejected: no HID session is connected.")
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
            if (!isConnected) return@launch

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
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendMouseClick(if (isRightClick) "right" else "left")
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    Log.w(TAG, "Bluetooth HID click rejected: no HID session is connected.")
                }
            }
        }
    }

    /**
     * Send Scroll Event
     */
    fun sendScroll(dx: Float, dy: Float) {
        scope.launch {
            if (!isConnected) return@launch

            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> {
                    Atrpv2Protocol.injectScroll(dx, dy)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendMouseScroll(dx, dy)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    Log.w(TAG, "Bluetooth HID scroll rejected: no HID session is connected.")
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
            if (!isConnected) return@launch

            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> {
                    for (char in text) {
                        Atrpv2Protocol.sendKey(char.code)
                    }
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendText(text)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    Log.w(TAG, "Bluetooth HID text rejected: no HID session is connected.")
                }
                else -> {}
            }
        }
    }
}
