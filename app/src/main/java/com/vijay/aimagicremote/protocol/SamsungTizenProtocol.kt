package com.vijay.aimagicremote.protocol

import android.content.Context
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
import kotlin.math.abs
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.net.URLEncoder

/**
 * Samsung Tizen TV Protocol (v2 WebSocket Channel)
 * Controls Samsung Tizen Smart TVs over local Wi-Fi on port 8001 (ws://) or 8002 (wss://).
 * Persists and reuses authorization token returned on first connection.
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
    var isPointerEnabled: Boolean = false
        private set

    @Volatile
    var currentTvIp: String? = null
        private set

    private var appContext: Context? = null
    @Volatile private var expectedCertificateFingerprint: String? = null
    @Volatile private var observedCertificateFingerprint: String? = null

    private var activeWebSocket: WebSocket? = null

    @Volatile
    private var pointerX: Int = 960

    @Volatile
    private var pointerY: Int = 540
    private val tvTrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val certificate = chain.firstOrNull() ?: throw CertificateException("Samsung TV sent no certificate")
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                .joinToString("") { "%02x".format(it) }
            val pinned = expectedCertificateFingerprint
            if (pinned != null && !pinned.equals(fingerprint, ignoreCase = true)) {
                throw CertificateException("Samsung TV certificate changed. Forget the TV and pair it again.")
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
        .hostnameVerifier { _, _ -> true } // Samsung TVs commonly use self-signed certs/IP hosts.
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Initialize once from the application context; tokens and cert pins are stored per TV IP. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences("samsung_tv_auth", Context.MODE_PRIVATE)
    private fun tokenKey(ip: String) = "token:$ip"
    private fun certificateKey(ip: String) = "certificate:$ip"

    private fun loadToken(ip: String): String? = prefs()?.getString(tokenKey(ip), null)
    private fun saveToken(ip: String, token: String) {
        prefs()?.edit()?.putString(tokenKey(ip), token)?.apply()
    }
    private fun saveCertificatePin(ip: String) {
        observedCertificateFingerprint?.let { fingerprint ->
            prefs()?.edit()?.putString(certificateKey(ip), fingerprint)?.apply()
        }
    }

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
                expectedCertificateFingerprint = prefs()?.getString(certificateKey(cleanIp), null)
                observedCertificateFingerprint = null
                val savedToken = loadToken(cleanIp)
                var wsUrl = "ws://$cleanIp:$WS_PORT/api/v2/channels/samsung.remote.control?name=$appNameBase64"
                savedToken?.let { token ->
                    wsUrl += "&token=${URLEncoder.encode(token, "UTF-8")}"
                }

                val request = Request.Builder()
                    .url(wsUrl)
                    .build()

                activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        // WebSocket opening is only transport readiness. Wait for the TV's
                        // ms.channel.connect event (and approval/token response) before enabling controls.
                        isConnected = false
                        Log.i(TAG, "Samsung WebSocket transport opened; waiting for TV authorization event.")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d(TAG, "Received Samsung TV event.")
                        handleSamsungEvent(cleanIp, webSocket, text, callback)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "Samsung WebSocket connection failed: ${t.message}, trying WSS port 8002...")
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
            val savedToken = loadToken(ip)
            var wssUrl = "wss://$ip:$WSS_PORT/api/v2/channels/samsung.remote.control?name=$appNameBase64"
            savedToken?.let { token ->
                wssUrl += "&token=${URLEncoder.encode(token, "UTF-8")}"
            }
            val request = Request.Builder().url(wssUrl).build()

            activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnected = false
                    Log.i(TAG, "Samsung WSS transport opened; waiting for TV authorization event.")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleSamsungEvent(ip, webSocket, text, callback)
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

    private fun handleSamsungEvent(ip: String, webSocket: WebSocket, text: String, callback: ConnectCallback?) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event")
            val data = json.optJSONObject("data")
            val token = data?.optString("token")
            token?.takeIf { it.isNotEmpty() && it != "null" }?.let {
                saveToken(ip, it)
                Log.i(TAG, "Samsung TV authorization token saved for this device.")
            }
            if (text.contains("touchEnable", ignoreCase = true)) {
                isPointerEnabled = true
            }

            when (event) {
                "ms.channel.connect" -> {
                    isConnected = true
                    isPointerEnabled = false
                    saveCertificatePin(ip)
                    scope.launch(Dispatchers.Main) { callback?.onConnected() }
                }
                "ms.remote.touchEnable" -> {
                    isPointerEnabled = true
                    Log.i(TAG, "Samsung TV pointer/touch mode enabled.")
                }
                "ms.channel.timeOut" -> {
                    isConnected = false
                    isPointerEnabled = false
                    scope.launch(Dispatchers.Main) {
                        // No callback reference here; connection state is enough for the UI.
                    }
                }
                "ms.channel.unauthorized" -> {
                    isConnected = false
                    scope.launch(Dispatchers.Main) {
                        callback?.onConnectionFailed("Samsung TV rejected authorization. Approve Air Remote on the TV and retry.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse Samsung TV event: ${e.message}")
        }
    }

    private fun String?.isNull_or_empty(): Boolean {
        return this == null || this.isEmpty() || this == "null"
    }

    fun sendText(text: String) {
        if (!isConnected || text.isEmpty()) return
        val encoded = android.util.Base64.encodeToString(text.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", encoded)
                put("DataOfCmd", "base64")
                put("TypeOfRemote", "SendInputString")
            })
        }
        activeWebSocket?.send(payload.toString())
    }

    fun disconnect() {
        disconnectInternal()
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

        if (ws != null && isConnected) {
            ws.send(payload)
            Log.d(TAG, "Sent Samsung Tizen key payload: $payload")
        } else {
            Log.w(TAG, "Ignoring Samsung key command: TV authorization is not ready.")
        }
    }

    @Volatile
    private var lastPointerKeyTime: Long = 0
    private val pointerKeyDebounceMs: Long = 60
    private val pointerDeltaThreshold: Float = 6.0f

    /**
     * Pointer motion for Samsung Tizen.
     *
     * When the TV has enabled mouse mode (`ms.remote.touchEnable`), send an
     * absolute Move command through `ProcessMouseDevice`. Otherwise fall back to
     * directional remote keys so the feature still works on TVs that do not
     * expose pointer mode.
     */
    fun sendPointerMove(dx: Float, dy: Float) {
        if (!isConnected) return
        if (!isPointerEnabled) {
            sendPointerKeyFallback(dx, dy)
            return
        }
        pointerX = (pointerX + (dx * 2.0f).toInt()).coerceIn(0, 1920)
        pointerY = (pointerY + (dy * 2.0f).toInt()).coerceIn(0, 1080)
        scope.launch(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("method", "ms.remote.control")
                put("params", JSONObject().apply {
                    put("Cmd", "Move")
                    put("Position", JSONObject().apply {
                        put("x", pointerX)
                        put("y", pointerY)
                        put("Time", "0")
                    })
                    put("TypeOfRemote", "ProcessMouseDevice")
                })
            }
            activeWebSocket?.takeIf { isConnected }?.send(payload.toString())
        }
    }

    fun sendPointerClick(isRightClick: Boolean) {
        if (!isConnected) return
        if (!isPointerEnabled) {
            scope.launch(Dispatchers.IO) { sendKey(if (isRightClick) "BACK" else "OK") }
            return
        }
        scope.launch(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("method", "ms.remote.control")
                put("params", JSONObject().apply {
                    put("Cmd", if (isRightClick) "RightClick" else "LeftClick")
                    put("TypeOfRemote", "ProcessMouseDevice")
                })
            }
            activeWebSocket?.takeIf { isConnected }?.send(payload.toString())
        }
    }

    fun sendPointerScroll(dx: Float, dy: Float) {
        if (!isConnected) return
        // Samsung has no documented wheel command; use remote keys.
        val now = System.currentTimeMillis()
        if (now - lastPointerKeyTime < pointerKeyDebounceMs) return
        val absDy = abs(dy)
        if (absDy <= pointerDeltaThreshold) return
        lastPointerKeyTime = now
        scope.launch(Dispatchers.IO) {
            sendKey(if (dy > 0) "VOL_DOWN" else "VOL_UP")
        }
    }

    private fun sendPointerKeyFallback(dx: Float, dy: Float) {
        val now = System.currentTimeMillis()
        if (now - lastPointerKeyTime < pointerKeyDebounceMs) return
        val absDx = abs(dx)
        val absDy = abs(dy)
        if (absDx <= pointerDeltaThreshold && absDy <= pointerDeltaThreshold) return
        val key = if (absDx > absDy) {
            if (dx > 0) "RIGHT" else "LEFT"
        } else {
            if (dy > 0) "DOWN" else "UP"
        }
        lastPointerKeyTime = now
        scope.launch(Dispatchers.IO) { sendKey(key) }
    }
}
