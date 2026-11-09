package com.vijay.aimagicremote.protocol

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LG webOS TV Protocol (SSAP WebSocket)
 * Controls LG Smart TVs over local Wi-Fi on port 3000 (ws://) or 3001 (wss://).
 * Sends SSAP command payloads: {"id":"turnOff","type":"request","uri":"ssap://system/turnOff"}
 */
object LgWebOsProtocol {

    private const val TAG = "LgWebOsProtocol"
    const val WS_PORT = 3000
    const val WSS_PORT = 3001

    interface ConnectCallback {
        fun onConnected()
        fun onConnectionFailed(error: String)
        fun onDisconnected()
    }

    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    var currentTvIp: String? = null
        private set

    private var activeWebSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(ip: String, callback: ConnectCallback? = null) {
        val cleanIp = ip.removePrefix("/")
        currentTvIp = cleanIp

        scope.launch {
            try {
                disconnectInternal()
                Log.i(TAG, "Connecting to LG webOS TV WebSocket at $cleanIp:$WS_PORT...")

                val wsUrl = "ws://$cleanIp:$WS_PORT/"
                val request = Request.Builder().url(wsUrl).build()

                activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        isConnected = true
                        Log.i(TAG, "Connected via WebSocket to LG webOS TV at $cleanIp")
                        scope.launch(Dispatchers.Main) {
                            callback?.onConnected()
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "LG WebSocket connection failed: ${t.message}")
                        scope.launch(Dispatchers.Main) {
                            callback?.onConnectionFailed("Could not connect to LG webOS TV at $cleanIp")
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        isConnected = false
                        scope.launch(Dispatchers.Main) {
                            callback?.onDisconnected()
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        isConnected = false
                    }
                })

            } catch (e: Exception) {
                isConnected = false
                Log.e(TAG, "LG webOS connection error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback?.onConnectionFailed("LG webOS error: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
        }
    }

    private fun disconnectInternal() {
        try {
            activeWebSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        activeWebSocket = null
        isConnected = false
    }

    fun sendKey(keyName: String) {
        val uri = when (keyName.uppercase()) {
            "POWER" -> "ssap://system/turnOff"
            "HOME" -> "ssap://system.launcher/open"
            "VOL_UP", "VOLUME_UP" -> "ssap://audio/volumeUp"
            "VOL_DOWN", "VOLUME_DOWN" -> "ssap://audio/volumeDown"
            "MUTE" -> "ssap://audio/setMute"
            "INPUT", "SOURCE" -> "ssap://tv/switchInput"
            else -> "ssap://media.controls/play"
        }

        val json = JSONObject().apply {
            put("id", System.currentTimeMillis().toString())
            put("type", "request")
            put("uri", uri)
        }

        val ws = activeWebSocket
        if (ws != null) {
            ws.send(json.toString())
            Log.d(TAG, "Sent LG webOS SSAP command: ${json.toString()}")
        }
    }
}
