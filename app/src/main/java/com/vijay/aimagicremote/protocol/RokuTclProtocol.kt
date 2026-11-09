package com.vijay.aimagicremote.protocol

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * TCL & Roku TV Protocol (External Control Protocol - ECP)
 * Controls TCL Roku TVs and Roku OS devices over local Wi-Fi on port 8060.
 * Standard HTTP POST requests to http://<tv-ip>:8060/keypress/<key_name>
 */
object RokuTclProtocol {

    private const val TAG = "RokuTclProtocol"
    const val ECP_PORT = 8060

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

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Connect/Verify TCL Roku TV on port 8060
     */
    fun connect(ip: String, callback: ConnectCallback? = null) {
        val cleanIp = ip.removePrefix("/")
        currentTvIp = cleanIp

        scope.launch {
            try {
                Log.i(TAG, "Verifying TCL / Roku TV at http://$cleanIp:$ECP_PORT/query/device-info...")
                val request = Request.Builder()
                    .url("http://$cleanIp:$ECP_PORT/query/device-info")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val isRokuEcp = response.isSuccessful &&
                        body.contains("<device-info", ignoreCase = true) &&
                        body.contains("roku", ignoreCase = true)
                    if (isRokuEcp) {
                        isConnected = true
                        Log.i(TAG, "Roku ECP device-info verified at $cleanIp:$ECP_PORT")
                        withContext(Dispatchers.Main) { callback?.onConnected() }
                    } else {
                        isConnected = false
                        withContext(Dispatchers.Main) {
                            callback?.onConnectionFailed(
                                "The endpoint at $cleanIp:$ECP_PORT did not return a valid Roku ECP device-info response (HTTP ${response.code})."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                isConnected = false
                Log.e(TAG, "TCL / Roku connection failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback?.onConnectionFailed("Could not reach TCL TV at $cleanIp:8060: ${e.message}")
                }
            }
        }
    }

    fun sendText(text: String) {
        val ip = currentTvIp ?: return
        if (!isConnected || text.isEmpty()) return
        scope.launch {
            for (character in text) {
                val encoded = java.net.URLEncoder.encode(character.toString(), "UTF-8")
                val request = Request.Builder()
                    .url("http://$ip:$ECP_PORT/keypress/Lit_$encoded")
                    .post("".toRequestBody("text/plain".toMediaType()))
                    .build()
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) Log.w(TAG, "Roku text input returned HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Roku text input failed: ${e.message}")
                    return@launch
                }
            }
        }
    }

    fun disconnect() {
        isConnected = false
        currentTvIp = null
    }

    /**
     * Send Key command to TCL / Roku TV (e.g. Power, Home, Select, Back, Up, Down, Left, Right, VolumeUp, VolumeDown, VolumeMute)
     */
    fun sendKey(keyName: String) {
        val ip = currentTvIp ?: return
        val mappedKey = when (keyName.uppercase()) {
            "POWER" -> "PowerOff"
            "HOME" -> "Home"
            "OK", "ENTER", "SELECT" -> "Select"
            "BACK", "ESCAPE" -> "Back"
            "UP" -> "Up"
            "DOWN" -> "Down"
            "LEFT" -> "Left"
            "RIGHT" -> "Right"
            "VOL_UP", "VOLUME_UP" -> "VolumeUp"
            "VOL_DOWN", "VOLUME_DOWN" -> "VolumeDown"
            "MUTE" -> "VolumeMute"
            else -> keyName
        }

        scope.launch {
            try {
                val url = "http://$ip:$ECP_PORT/keypress/$mappedKey"
                val emptyBody = "".toRequestBody("text/plain".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(emptyBody)
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "Sent TCL/Roku key $mappedKey to $ip -> HTTP ${response.code}")
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending TCL/Roku key $mappedKey to $ip: ${e.message}")
            }
        }
    }
}
