package com.vijay.aimagicremote.protocol

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * LG webOS TV Protocol (SSAP WebSocket)
 * Controls LG Smart TVs over local Wi-Fi on port 3000 (ws://) or 3001 (wss://).
 * Sends SSAP registration payload with client-key persistence and handles TV approval responses.
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

    private var appContext: Context? = null
    @Volatile private var savedClientKey: String? = null
    @Volatile private var expectedCertificateFingerprint: String? = null
    @Volatile private var observedCertificateFingerprint: String? = null

    private var activeWebSocket: WebSocket? = null
    private var pointerWebSocket: WebSocket? = null
    private val tvTrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val certificate = chain.firstOrNull() ?: throw CertificateException("LG TV sent no certificate")
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                .joinToString("") { "%02x".format(it) }
            val pinned = expectedCertificateFingerprint
            if (pinned != null && !pinned.equals(fingerprint, ignoreCase = true)) {
                throw CertificateException("LG TV certificate changed. Forget the TV and pair it again.")
            }
            observedCertificateFingerprint = fingerprint
        }
    }
    private val tvSslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(tvTrustManager), SecureRandom())
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .sslSocketFactory(tvSslContext.socketFactory, tvTrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences("lg_webos_auth", Context.MODE_PRIVATE)
    private fun clientKeyPref(ip: String) = "client-key:$ip"
    private fun certificatePref(ip: String) = "certificate:$ip"

    private fun loadClientKey(ip: String): String? = prefs()?.getString(clientKeyPref(ip), null)
    private fun saveClientKey(ip: String, key: String) {
        prefs()?.edit()?.putString(clientKeyPref(ip), key)?.apply()
    }
    private fun saveCertificatePin(ip: String) {
        observedCertificateFingerprint?.let { prefs()?.edit()?.putString(certificatePref(ip), it)?.apply() }
    }

    fun connect(ip: String, callback: ConnectCallback? = null) {
        val cleanIp = ip.removePrefix("/")
        currentTvIp = cleanIp

        scope.launch {
            try {
                disconnectInternal()
                savedClientKey = loadClientKey(cleanIp)
                expectedCertificateFingerprint = prefs()?.getString(certificatePref(cleanIp), null)
                observedCertificateFingerprint = null
                Log.i(TAG, "Connecting to LG webOS TV WebSocket at $cleanIp:$WS_PORT...")

                val wsUrl = "ws://$cleanIp:$WS_PORT/"
                val request = Request.Builder().url(wsUrl).build()

                activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "WebSocket open to LG webOS TV at $cleanIp. Sending SSAP registration payload...")
                        sendRegistrationPayload(webSocket)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d(TAG, "Received message from LG webOS TV: $text")
                        try {
                            val json = JSONObject(text)
                            val type = json.optString("type")
                            if (type == "registered") {
                                val payload = json.optJSONObject("payload")
                                val clientKey = payload?.optString("client-key")
                                if (!clientKey.isNullOrEmpty()) {
                                    savedClientKey = clientKey
                                    saveClientKey(cleanIp, clientKey)
                                }
                                isConnected = true
                                saveCertificatePin(cleanIp)
                                scope.launch(Dispatchers.Main) { callback?.onConnected() }
                                requestPointerSocket(webSocket)
                            } else if (type == "response" && json.optString("id") == "pointer_socket") {
                                val socketPath = json.optJSONObject("payload")?.optString("socketPath")
                                if (!socketPath.isNullOrBlank()) openPointerSocket(socketPath)
                            } else if (type == "error" && json.optString("id") == "register_0") {
                                isConnected = false
                                scope.launch(Dispatchers.Main) {
                                    callback?.onConnectionFailed("LG TV rejected registration. Accept the Air Remote pairing prompt and retry.")
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        isConnected = false
                        Log.w(TAG, "LG ws:// connection failed: ${t.message}; trying TLS port 3001.")
                        tryWssFallback(cleanIp, callback)
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

    private fun sendRegistrationPayload(webSocket: WebSocket) {
        val registerJson = JSONObject().apply {
            put("type", "register")
            put("id", "register_0")
            put("payload", JSONObject().apply {
                put("forcePairing", false)
                put("pairingType", "PROMPT")
                savedClientKey?.let { key ->
                    put("client-key", key)
                }
                put("manifest", JSONObject().apply {
                    put("manifestVersion", 1)
                    put("permissions", JSONArray().apply {
                        put("LAUNCH")
                        put("LAUNCH_WEBAPP")
                        put("APP_TO_APP")
                        put("CLOSE")
                        put("CONTROL_AUDIO")
                        put("CONTROL_POWER")
                        put("CONTROL_INPUT_TEXT")
                        put("CONTROL_MOUSE_AND_KEYBOARD")
                        put("READ_INSTALLED_APPS")
                        put("READ_APP_STATUS")
                        put("READ_CURRENT_CHANNEL")
                        put("READ_NETWORK_STATE")
                        put("READ_RUNNING_APPS")
                        put("READ_TV_CHANNEL_LIST")
                        put("READ_POWER_STATE")
                    })
                })
            })
        }
        webSocket.send(registerJson.toString())
        Log.i(TAG, "Sent SSAP registration payload to LG webOS TV")
    }

    private fun tryWssFallback(ip: String, callback: ConnectCallback?) {
        try {
            val request = Request.Builder().url("wss://$ip:$WSS_PORT/").build()
            activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    sendRegistrationPayload(webSocket)
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleRegistrationMessage(ip, webSocket, text, callback)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isConnected = false
                    scope.launch(Dispatchers.Main) {
                        callback?.onConnectionFailed("LG webOS connection failed on ports 3000 and 3001: ${t.message}")
                    }
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    isConnected = false
                    scope.launch(Dispatchers.Main) { callback?.onDisconnected() }
                }
            })
        } catch (e: Exception) {
            isConnected = false
            scope.launch(Dispatchers.Main) {
                callback?.onConnectionFailed("LG webOS TLS connection error: ${e.message}")
            }
        }
    }

    private fun handleRegistrationMessage(ip: String, webSocket: WebSocket, text: String, callback: ConnectCallback?) {
        try {
            val json = JSONObject(text)
            if (json.optString("type") == "registered") {
                val key = json.optJSONObject("payload")?.optString("client-key")
                if (!key.isNullOrEmpty()) {
                    savedClientKey = key
                    saveClientKey(ip, key)
                }
                isConnected = true
                saveCertificatePin(ip)
                scope.launch(Dispatchers.Main) { callback?.onConnected() }
                requestPointerSocket(webSocket)
            } else if (json.optString("type") == "response" && json.optString("id") == "pointer_socket") {
                val socketPath = json.optJSONObject("payload")?.optString("socketPath")
                if (!socketPath.isNullOrBlank()) openPointerSocket(socketPath)
            } else if (json.optString("type") == "error" && json.optString("id") == "register_0") {
                isConnected = false
                scope.launch(Dispatchers.Main) {
                    callback?.onConnectionFailed("LG TV rejected registration. Accept the Air Remote pairing prompt and retry.")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse LG registration response: ${e.message}")
        }
    }

    private fun requestPointerSocket(webSocket: WebSocket) {
        val request = JSONObject().apply {
            put("type", "request")
            put("id", "pointer_socket")
            put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
        }
        webSocket.send(request.toString())
    }

    private fun openPointerSocket(socketPath: String) {
        try {
            pointerWebSocket?.close(1000, "Replacing pointer channel")
            pointerWebSocket = client.newWebSocket(Request.Builder().url(socketPath).build(), object : WebSocketListener() {
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "LG pointer socket unavailable: ${t.message}")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "LG pointer socket could not be opened: ${e.message}")
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
        }
    }

    private fun disconnectInternal() {
        try { pointerWebSocket?.close(1000, "User disconnected") } catch (_: Exception) {}
        try { activeWebSocket?.close(1000, "User disconnected") } catch (_: Exception) {}
        pointerWebSocket = null
        activeWebSocket = null
        isConnected = false
    }

    fun sendKey(keyName: String) {
        val key = keyName.uppercase()
        val pointerName = when (key) {
            "UP" -> "UP"
            "DOWN" -> "DOWN"
            "LEFT" -> "LEFT"
            "RIGHT" -> "RIGHT"
            "OK", "ENTER", "SELECT" -> "ENTER"
            "BACK", "ESCAPE" -> "BACK"
            "HOME" -> "HOME"
            "MENU" -> "MENU"
            "POWER" -> "POWER"
            "VOL_UP", "VOLUME_UP" -> "VOLUMEUP"
            "VOL_DOWN", "VOLUME_DOWN" -> "VOLUMEDOWN"
            "MUTE" -> "MUTE"
            else -> null
        }

        val pointer = pointerWebSocket
        if (pointerName != null && pointer != null && isConnected) {
            pointer.send("type:button\\nname:$pointerName\\n\\n")
            return
        }

        val uri = when (key) {
            "POWER" -> "ssap://system/turnOff"
            "HOME" -> "ssap://system.launcher/open"
            "VOL_UP", "VOLUME_UP" -> "ssap://audio/volumeUp"
            "VOL_DOWN", "VOLUME_DOWN" -> "ssap://audio/volumeDown"
            "MUTE" -> "ssap://audio/setMute"
            "INPUT", "SOURCE" -> "ssap://tv/switchInput"
            else -> null
        } ?: return

        val json = JSONObject().apply {
            put("id", System.currentTimeMillis().toString())
            put("type", "request")
            put("uri", uri)
        }
        activeWebSocket?.takeIf { isConnected }?.send(json.toString())
    }
}
