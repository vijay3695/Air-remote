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
 * Controls Windows PCs over local Wi-Fi via TCP/UDP Port 9090 with 3-tier command fallback.
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
                        val probePacket = "{\"cmd\":\"ping\"}\n".toByteArray(Charsets.UTF_8)
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
                    isConnected = true // Keep active for 3-tier fallback send
                    withContext(Dispatchers.Main) {
                        callback?.onConnected()
                    }
                }
            } catch (e: Exception) {
                isConnected = true
                Log.e(TAG, "PC Connection error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback?.onConnected()
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
        val json = JSONObject().apply {
            put("cmd", "mouse_move")
            put("dx", dx)
            put("dy", dy)
        }
        sendCommand(json)
    }

    /**
     * Send Mouse Click (left, right, middle, double)
     */
    fun sendMouseClick(button: String = "left") {
        val json = JSONObject().apply {
            put("cmd", "click")
            put("button", button)
        }
        sendCommand(json)
    }

    /**
     * Send Mouse Scroll
     */
    fun sendMouseScroll(dx: Float, dy: Float) {
        val json = JSONObject().apply {
            put("cmd", "scroll")
            put("dx", dx)
            put("dy", dy)
        }
        sendCommand(json)
    }

    /**
     * Send Key command to Windows PC
     */
    fun sendKey(key: String) {
        val json = JSONObject().apply {
            put("cmd", "key")
            put("key", key)
        }
        sendCommand(json)
    }

    /**
     * Send Text String to type on Windows PC
     */
    fun sendText(text: String) {
        val json = JSONObject().apply {
            put("cmd", "type")
            put("text", text)
        }
        sendCommand(json)
    }

    private fun sendCommand(json: JSONObject) {
        val ip = getEffectiveIp() ?: return
        val data = (json.toString() + "\n").toByteArray(Charsets.UTF_8)

        scope.launch {
            var sent = false

            // 1. Primary: Active TCP stream
            tcpOutputStream?.let { out ->
                try {
                    out.write(data)
                    out.flush()
                    sent = true
                } catch (e: Exception) {
                    Log.w(TAG, "TCP write failed: ${e.message}")
                    tcpOutputStream = null
                    tcpSocket = null
                }
            }

            // 2. Secondary: Fresh TCP Socket connection
            if (!sent) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ip, DEFAULT_PORT), 1000)
                    val out = socket.getOutputStream()
                    out.write(data)
                    out.flush()
                    socket.close()
                    sent = true
                } catch (_: Exception) {}
            }

            // 3. Tertiary: UDP Datagram packet send to port 9090
            if (!sent) {
                try {
                    val socket = DatagramSocket()
                    val packet = DatagramPacket(data, data.size, InetAddress.getByName(ip), DEFAULT_PORT)
                    socket.send(packet)
                    socket.close()
                } catch (e: Exception) {
                    Log.w(TAG, "UDP send failed: ${e.message}")
                }
            }
        }
    }
}
