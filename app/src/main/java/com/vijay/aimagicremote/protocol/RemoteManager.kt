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

    @Volatile
    private var connectionGeneration: Long = 0

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setTargetType(type: TargetDeviceType) {
        val previous = activeTargetType
        if (previous != type) {
            connectionGeneration++ // Invalidate callbacks from the old protocol.
            disconnectProtocol(previous)
            isConnected = false
        }
        activeTargetType = type
        Log.i(TAG, "Target device type set to: $type")
    }

    private fun isCurrentConnection(generation: Long, type: TargetDeviceType): Boolean =
        generation == connectionGeneration && activeTargetType == type
    private fun disconnectProtocol(type: TargetDeviceType) {
        when (type) {
            TargetDeviceType.ANDROID_TV -> Atrpv2Protocol.disconnect()
            TargetDeviceType.SAMSUNG_TIZEN -> SamsungTizenProtocol.disconnect()
            TargetDeviceType.LG_WEBOS -> LgWebOsProtocol.disconnect()
            TargetDeviceType.ROKU_TCL -> RokuTclProtocol.disconnect()
            TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.disconnect()
            TargetDeviceType.BLUETOOTH_HID -> Unit
        }
    }

    fun connect(
        context: Context,
        targetType: TargetDeviceType,
        ip: String,
        bluetoothController: BluetoothController? = null,
        callback: ConnectCallback? = null
    ) {
        setTargetType(targetType)
        val generation = ++connectionGeneration
        isConnected = false
        val cleanIp = ip.removePrefix("/")
        currentTargetIp = cleanIp

        PcRemoteProtocol.currentPcIp = cleanIp
        Atrpv2Protocol.currentTvIp = cleanIp

        when (targetType) {
            TargetDeviceType.ANDROID_TV -> {
                Atrpv2Protocol.initialize(context)
                Atrpv2Protocol.connect(cleanIp, object : Atrpv2Protocol.ConnectCallback {
                    override fun onConnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = true
                        callback?.onConnected()
                    }
                    override fun onConnectionFailed(error: String) {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }
                    override fun onDisconnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.SAMSUNG_TIZEN -> {
                SamsungTizenProtocol.initialize(context)
                SamsungTizenProtocol.connect(cleanIp, object : SamsungTizenProtocol.ConnectCallback {
                    override fun onConnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = true
                        callback?.onConnected()
                    }
                    override fun onConnectionFailed(error: String) {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }
                    override fun onDisconnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.LG_WEBOS -> {
                LgWebOsProtocol.initialize(context)
                LgWebOsProtocol.connect(cleanIp, object : LgWebOsProtocol.ConnectCallback {
                    override fun onConnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = true
                        callback?.onConnected()
                    }
                    override fun onConnectionFailed(error: String) {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }
                    override fun onDisconnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.ROKU_TCL -> {
                RokuTclProtocol.connect(cleanIp, object : RokuTclProtocol.ConnectCallback {
                    override fun onConnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = true
                        callback?.onConnected()
                    }
                    override fun onConnectionFailed(error: String) {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }
                    override fun onDisconnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.WINDOWS_PC -> {
                PcRemoteProtocol.connect(cleanIp, PcRemoteProtocol.DEFAULT_PORT, object : PcRemoteProtocol.ConnectCallback {
                    override fun onConnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = true
                        callback?.onConnected()
                    }

                    override fun onConnectionFailed(error: String) {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onConnectionFailed(error)
                    }

                    override fun onDisconnected() {
                        if (!isCurrentConnection(generation, targetType)) return
                        isConnected = false
                        callback?.onDisconnected()
                    }
                })
            }

            TargetDeviceType.BLUETOOTH_HID -> {
                if (!isCurrentConnection(generation, targetType)) return
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
        connectionGeneration++ // Invalidate callbacks from the disconnected session.
        disconnectProtocol(activeTargetType)
        isConnected = false
    }

    /**
     * Send key command to active target device only
     */
    fun sendKey(androidKeyCode: Int, keyName: String) {
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
                    SamsungTizenProtocol.sendKey(keyName)
                }
                TargetDeviceType.LG_WEBOS -> {
                    LgWebOsProtocol.sendKey(keyName)
                }
                TargetDeviceType.ROKU_TCL -> {
                    RokuTclProtocol.sendKey(keyName)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendKey(keyName)
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
                    Atrpv2Protocol.sendPointerMove(dx, dy)
                }
                TargetDeviceType.SAMSUNG_TIZEN -> {
                    SamsungTizenProtocol.sendPointerMove(dx, dy)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendMouseMove(dx, dy)
                }
                TargetDeviceType.LG_WEBOS -> {
                    LgWebOsProtocol.sendPointerMove(dx, dy)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    Log.w(TAG, "Bluetooth HID pointer rejected: no HID session is connected.")
                }
                else -> {
                    Log.w(TAG, "Pointer/air-mouse input is not supported by the selected target: $activeTargetType")
                }
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
                    if (isRightClick) LgWebOsProtocol.sendKey("BACK")
                    else LgWebOsProtocol.sendPointerClick()
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
                    Atrpv2Protocol.sendPointerScroll(dx, dy)
                }
                TargetDeviceType.SAMSUNG_TIZEN -> {
                    SamsungTizenProtocol.sendPointerScroll(dx, dy)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendMouseScroll(dx, dy)
                }
                TargetDeviceType.LG_WEBOS -> {
                    LgWebOsProtocol.sendPointerScroll(dx, dy)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    Log.w(TAG, "Bluetooth HID scroll rejected: no HID session is connected.")
                }
                else -> {
                    Log.w(TAG, "Scroll input is not supported by the selected target: $activeTargetType")
                }
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
                    Atrpv2Protocol.sendText(text)
                }
                TargetDeviceType.SAMSUNG_TIZEN -> {
                    SamsungTizenProtocol.sendText(text)
                }
                TargetDeviceType.LG_WEBOS -> {
                    LgWebOsProtocol.sendText(text)
                }
                TargetDeviceType.ROKU_TCL -> {
                    RokuTclProtocol.sendText(text)
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
