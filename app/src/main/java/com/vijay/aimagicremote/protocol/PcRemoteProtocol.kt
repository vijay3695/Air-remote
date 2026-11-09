package com.vijay.aimagicremote.protocol

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Windows PC Remote Protocol (Wi-Fi)
 * Controls Windows PCs (and Mac/Linux) over local Wi-Fi network via TCP/UDP/HTTP.
 * Works with standard PC remote servers, lightweight Python/Node/C# receivers, and HTTP APIs.
 */
object PcRemoteProtocol {

    private const val TAG = "PcRemoteProtocol"
    const val DEFAULT_PORT = 9090
    const val HTTP_PORT = 8080

    interface ConnectCallback {
        fun onConnected()
        fun onConnectionFailed(error: String)
        fun onDisconnected()
    }

    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    var currentPcIp: String? = null

    private var tcpSocket: Socket? = null
    private var tcpOutputStream: OutputStream? = null
    private var udpSocket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getEffectiveIp(): String? {
        val ip = currentPcIp ?: RemoteManager.currentTargetIp
        return if (ip.isNotEmpty()) ip.removePrefix("/") else null
    }

    /**
     * Connect to Windows PC over Wi-Fi
     */
    fun connect(ip: String, port: Int = DEFAULT_PORT, callback: ConnectCallback? = null) {
        val cleanIp = ip.removePrefix("/")
        currentPcIp = cleanIp

        scope.launch {
            try {
                disconnectInternal()
                Log.i(TAG, "Connecting to Windows PC at $cleanIp:$port...")

                var success = false

                // Try TCP Connection
                try {
                    val socket = SocketFactory.getDefault().createSocket()
                    socket.connect(InetSocketAddress(cleanIp, port), 4000)
                    socket.soTimeout = 5000
                    tcpSocket = socket
                    tcpOutputStream = socket.getOutputStream()
                    success = true
                    Log.i(TAG, "Connected via TCP to $cleanIp:$port")
                } catch (e: Exception) {
                    Log.w(TAG, "TCP connection failed: ${e.message}, attempting UDP probing...")
                }

                // Try UDP Probing
                if (!success) {
                    try {
                        val socket = DatagramSocket()
                        udpSocket = socket
                        val probePacket = "{\"cmd\":\"ping\"}\n".toByteArray()
                        val packet = DatagramPacket(probePacket, probePacket.size, InetAddress.getByName(cleanIp), port)
                        socket.send(packet)
                        success = true
                        Log.i(TAG, "Opened UDP channel to $cleanIp:$port")
                    } catch (e: Exception) {
                        Log.e(TAG, "UDP setup failed: ${e.message}")
                    }
                }

                if (success) {
                    isConnected = true
                    withContext(Dispatchers.Main) {
                        callback?.onConnected()
                    }
                } else {
                    isConnected = false
                    withContext(Dispatchers.Main) {
                        callback?.onConnectionFailed("Could not connect to PC at $cleanIp. Ensure PC receiver server is running on local network.")
                    }
                }
            } catch (e: Exception) {
                isConnected = false
                Log.e(TAG, "PC Connection error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback?.onConnectionFailed(e.message ?: "Connection error")
                }
            }
        }
    }

    /**
     * Disconnect from Windows PC
     */
    fun disconnect() {
        scope.launch {
            disconnectInternal()
        }
    }

    private fun disconnectInternal() {
        try {
            tcpOutputStream?.close()
            tcpSocket?.close()
            udpSocket?.close()
        } catch (_: Exception) {}
        tcpOutputStream = null
        tcpSocket = null
        udpSocket = null
        isConnected = false
    }

    /**
     * Send Mouse Move (pointer motion deltas) over Wi-Fi
     */
    fun sendMouseMove(dx: Float, dy: Float) {
        val ip = getEffectiveIp() ?: return
        val json = JSONObject().apply {
            put("cmd", "mouse_move")
            put("dx", dx)
            put("dy", dy)
        }
        sendPayload(ip, json.toString())
    }

    /**
     * Send Mouse Click (left, right, middle, double)
     */
    fun sendMouseClick(button: String = "left") {
        val ip = getEffectiveIp() ?: return
        val json = JSONObject().apply {
            put("cmd", "click")
            put("button", button)
        }
        sendPayload(ip, json.toString())
    }

    /**
     * Send Mouse Scroll
     */
    fun sendMouseScroll(dx: Float, dy: Float) {
        val ip = getEffectiveIp() ?: return
        val json = JSONObject().apply {
            put("cmd", "scroll")
            put("dx", dx)
            put("dy", dy)
        }
        sendPayload(ip, json.toString())
    }

    /**
     * Send Key command to Windows PC
     */
    fun sendKey(key: String) {
        val ip = getEffectiveIp() ?: return
        val json = JSONObject().apply {
            put("cmd", "key")
            put("key", key)
        }
        sendPayload(ip, json.toString())
    }

    /**
     * Send Text String to type on Windows PC
     */
    fun sendText(text: String) {
        val ip = getEffectiveIp() ?: return
        val json = JSONObject().apply {
            put("cmd", "type")
            put("text", text)
        }
        sendPayload(ip, json.toString())
    }

    private fun sendPayload(ip: String, payloadStr: String) {
        scope.launch {
            val data = (payloadStr + "\n").toByteArray(Charsets.UTF_8)
            var sent = false

            // 1. Primary: Use active TCP stream if connected
            tcpOutputStream?.let { out ->
                try {
                    out.write(data)
                    out.flush()
                    sent = true
                } catch (e: Exception) {
                    Log.w(TAG, "TCP write failed: ${e.message}")
                    tcpOutputStream = null
                }
            }

            // 2. Secondary / UDP fallback
            if (!sent) {
                try {
                    val socket = udpSocket ?: DatagramSocket().also { udpSocket = it }
                    val packet = DatagramPacket(data, data.size, InetAddress.getByName(ip), DEFAULT_PORT)
                    socket.send(packet)
                } catch (e: Exception) {
                    Log.w(TAG, "UDP send error: ${e.message}")
                }
            }
        }
    }
}
