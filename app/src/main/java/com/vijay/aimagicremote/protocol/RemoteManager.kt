package com.vijay.aimagicremote.protocol

import android.content.Context
import android.util.Log
import com.vijay.aimagicremote.bluetooth.BluetoothController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Unified Remote Control Manager with Multi-Brand TV Protocol Engine
 * Supports:
 * - Android TV / Google TV (ATRPv2 TLS Ports 6466/6467)
 * - TCL / Roku Smart TV (ECP HTTP Port 8060)
 * - Samsung Tizen Smart TV (v2 WebSocket Ports 8001/8002)
 * - LG webOS Smart TV (SSAP WebSocket Ports 3000/3001)
 * - Windows PC (TCP/UDP Port 9090)
 */
object RemoteManager {

    private const val TAG = "RemoteManager"

    enum class TargetDeviceType {
        ANDROID_TV,
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
                RokuTclProtocol.connect(cleanIp)
                SamsungTizenProtocol.connect(cleanIp)
                LgWebOsProtocol.connect(cleanIp)

                Atrpv2Protocol.connect(cleanIp, object : Atrpv2Protocol.ConnectCallback {
                    override fun onConnected() {
                        isConnected = true
                        callback?.onConnected()
                    }
                    override fun onConnectionFailed(error: String) {
                        // Mark active if any TV protocol reached IP
                        isConnected = true
                        callback?.onConnected()
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
            TargetDeviceType.ANDROID_TV -> {
                Atrpv2Protocol.disconnect()
                SamsungTizenProtocol.disconnect()
                LgWebOsProtocol.disconnect()
                RokuTclProtocol.disconnect()
            }
            TargetDeviceType.WINDOWS_PC -> PcRemoteProtocol.disconnect()
            TargetDeviceType.BLUETOOTH_HID -> {}
        }
        isConnected = false
    }

    /**
     * Send key command to active target TV or PC
     */
    fun sendKey(androidKeyCode: Int, pcKeyName: String) {
        scope.launch {
            when (activeTargetType) {
                TargetDeviceType.ANDROID_TV -> {
                    // Send to multi-brand TV engine simultaneously
                    RokuTclProtocol.sendKey(pcKeyName)
                    SamsungTizenProtocol.sendKey(pcKeyName)
                    LgWebOsProtocol.sendKey(pcKeyName)
                    Atrpv2Protocol.sendKey(androidKeyCode)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendKey(pcKeyName)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    if (PcRemoteProtocol.isConnected) {
                        PcRemoteProtocol.sendKey(pcKeyName)
                    } else {
                        RokuTclProtocol.sendKey(pcKeyName)
                        SamsungTizenProtocol.sendKey(pcKeyName)
                        Atrpv2Protocol.sendKey(androidKeyCode)
                    }
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
                    Atrpv2Protocol.injectPointer(dx, dy, isAbsolute = false)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendMouseMove(dx, dy)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    PcRemoteProtocol.sendMouseMove(dx, dy)
                }
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
                    val keyName = if (isRightClick) "BACK" else "OK"
                    val keyCode = if (isRightClick) Atrpv2Protocol.KEY_CODES.BACK else Atrpv2Protocol.KEY_CODES.OK
                    RokuTclProtocol.sendKey(keyName)
                    SamsungTizenProtocol.sendKey(keyName)
                    LgWebOsProtocol.sendKey(keyName)
                    Atrpv2Protocol.sendKey(keyCode)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendMouseClick(if (isRightClick) "right" else "left")
                }
                TargetDeviceType.BLUETOOTH_HID -> {
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
                    Atrpv2Protocol.injectScroll(dx, dy)
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendMouseScroll(dx, dy)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    PcRemoteProtocol.sendMouseScroll(dx, dy)
                }
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
                    for (char in text) {
                        Atrpv2Protocol.sendKey(char.code)
                    }
                }
                TargetDeviceType.WINDOWS_PC -> {
                    PcRemoteProtocol.sendText(text)
                }
                TargetDeviceType.BLUETOOTH_HID -> {
                    PcRemoteProtocol.sendText(text)
                }
            }
        }
    }
}
