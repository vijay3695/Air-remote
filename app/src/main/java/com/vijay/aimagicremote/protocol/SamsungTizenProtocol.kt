package com.vijay.aimagicremote.protocol

import android.util.Base64
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
 * Samsung Tizen TV Protocol (v2 WebSocket Channel)
 * Controls Samsung Tizen Smart TVs over local Wi-Fi on port 8001 (ws://) or 8002 (wss://).
 * Sends JSON key control payloads: {"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"KEY_POWER","Option":"false","TypeOfRemote":"SendRemoteKey"}}
 */
object SamsungTizenProtocol {

    private const val TAG = "SamsungTizenProtocol"
    const val WS_PORT = 8001
    const val WSS_PORT = 8002

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

    /**
     * Connect to Samsung Tizen TV over WebSocket
     */
    fun connect(ip: String, callback: ConnectCallback? = null) {
        val cleanIp = ip.removePrefix("/")
        currentTvIp = cleanIp

        scope.launch {
            try {
                disconnectInternal()
                Log.i(TAG, "Connecting to Samsung Tizen TV WebSocket at $cleanIp:$WS_PORT...")

                val appNameBase64 = Base64.encodeToString("Air Remote".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val wsUrl = "ws://$cleanIp:$WS_PORT/api/v2/channels/samsung.remote.control?name=$appNameBase64"

                val request = Request.Builder()
                    .url(wsUrl)
                    .build()

                activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        isConnected = true
                        Log.i(TAG, "Successfully connected via WebSocket to Samsung Tizen TV at $cleanIp")
                        scope.launch(Dispatchers.Main) {
                            callback?.onConnected()
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "Samsung WebSocket connection failed: ${t.message}, trying WSS port 8002...")
                        // Fallback to WSS
                        tryWssFallback(cleanIp, appNameBase64, callback)
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
                Log.e(TAG, "Samsung Tizen connection error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback?.onConnectionFailed("Could not connect to Samsung TV at $cleanIp: ${e.message}")
                }
            }
        }
    }

    private fun tryWssFallback(ip: String, appNameBase64: String, callback: ConnectCallback?) {
        try {
            val wssUrl = "wss://$ip:$WSS_PORT/api/v2/channels/samsung.remote.control?name=$appNameBase64"
            val request = Request.Builder().url(wssUrl).build()

            activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnected = true
                    Log.i(TAG, "Connected via WSS to Samsung Tizen TV at $ip:$WSS_PORT")
                    scope.launch(Dispatchers.Main) {
                        callback?.onConnected()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isConnected = false
                    Log.e(TAG, "Samsung WSS connection failed: ${t.message}")
                    scope.launch(Dispatchers.Main) {
                        callback?.onConnectionFailed("Samsung TV at $ip rejected connection or prompt required on TV screen.")
                    }
                }
            })
        } catch (e: Exception) {
            isConnected = false
            scope.launch(Dispatchers.Main) {
                callback?.onConnectionFailed("Samsung TV error: ${e.message}")
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

    /**
     * Send Key command to Samsung Tizen TV
     */
    fun sendKey(keyName: String) {
        val mappedKey = when (keyName.uppercase()) {
            "POWER" -> "KEY_POWER"
            "HOME" -> "KEY_HOME"
            "OK", "ENTER", "SELECT" -> "KEY_ENTER"
            "BACK", "ESCAPE" -> "KEY_RETURN"
            "UP" -> "KEY_UP"
            "DOWN" -> "KEY_DOWN"
            "LEFT" -> "KEY_LEFT"
            "RIGHT" -> "KEY_RIGHT"
            "VOL_UP", "VOLUME_UP" -> "KEY_VOLUP"
            "VOL_DOWN", "VOLUME_DOWN" -> "KEY_VOLDOWN"
            "MUTE" -> "KEY_MUTE"
            "INPUT", "SOURCE" -> "KEY_SOURCE"
            "MENU" -> "KEY_MENU"
            else -> if (keyName.startsWith("KEY_")) keyName else "KEY_$keyName"
        }

        val json = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", mappedKey)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }

        val payload = json.toString()
        val ws = activeWebSocket

        if (ws != null) {
            ws.send(payload)
            Log.d(TAG, "Sent Samsung Tizen key payload: $payload")
        } else {
            // Fallback HTTP POST / socket trigger if WebSocket was briefly closed
            currentTvIp?.let { ip ->
                scope.launch {
                    try {
                        val url = "http://$ip:$WS_PORT/api/v2/"
                        val request = Request.Builder().url(url).get().build()
                        client.newCall(request).execute().close()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
