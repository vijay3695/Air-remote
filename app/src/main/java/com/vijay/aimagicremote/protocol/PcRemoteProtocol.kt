package com.vijay.aimagicremote.protocol

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Windows PC Remote Protocol (Wi-Fi).
 *
 * The Windows receiver uses newline-delimited UTF-8 JSON over TCP (port 9090).
 * Sensor events can arrive several dozen times per second, so every write must
 * be serialized: concurrent writes to a TCP OutputStream can interleave JSON
 * frames and make the receiver discard subsequent mouse events.
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
    @Volatile
    private var currentPort: Int = DEFAULT_PORT

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // One lock for connection teardown and every outbound command. This keeps
    // JSON lines intact and prevents a disconnect from closing a stream while
    // another coroutine is in the middle of writing to it.
    private val transportMutex = Mutex()

    fun getEffectiveIp(): String? {
        val ip = currentPcIp ?: RemoteManager.currentTargetIp
        return if (ip.isNotEmpty()) ip.removePrefix("/") else null
    }

    fun connect(ip: String, port: Int = DEFAULT_PORT, callback: ConnectCallback? = null) {
        val cleanIp = ip.removePrefix("/")
        currentPcIp = cleanIp
        currentPort = port

        scope.launch {
            transportMutex.withLock {
                try {
                    disconnectInternal()
                    Log.i(TAG, "Connecting to Windows PC at $cleanIp:$port...")

                    try {
                        val socket = SocketFactory.getDefault().createSocket()
                        socket.connect(InetSocketAddress(cleanIp, port), 4000)
                        socket.tcpNoDelay = true
                        socket.soTimeout = 5000
                        tcpSocket = socket
                        tcpOutputStream = socket.getOutputStream()
                        udpSocket?.close()
                        udpSocket = null
                        isConnected = true
                        Log.i(TAG, "Connected via TCP to $cleanIp:$port")
                        withContext(Dispatchers.Main) { callback?.onConnected() }
                    } catch (e: Exception) {
                        isConnected = false
                        Log.e(TAG, "TCP connection failed: ${e.message}")
                        withContext(Dispatchers.Main) {
                            callback?.onConnectionFailed(
                                "Could not connect to PC at $cleanIp:$port. Check that the Air Remote receiver is running and Windows Firewall allows it."
                            )
                        }
                    }
                } catch (e: Exception) {
                    isConnected = false
                    Log.e(TAG, "PC connection error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        callback?.onConnectionFailed(e.message ?: "Connection error")
                    }
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            transportMutex.withLock {
                disconnectInternal()
            }
        }
    }

    private fun disconnectInternal() {
        try {
            tcpOutputStream?.close()
        } catch (_: Exception) {
        }
        try {
            tcpSocket?.close()
        } catch (_: Exception) {
        }
        try {
            udpSocket?.close()
        } catch (_: Exception) {
        }
        tcpOutputStream = null
        tcpSocket = null
        udpSocket = null
        isConnected = false
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        sendCommand(JSONObject().apply {
            put("cmd", "mouse_move")
            put("dx", dx)
            put("dy", dy)
        })
    }

    fun sendMouseClick(button: String = "left") {
        sendCommand(JSONObject().apply {
            put("cmd", "click")
            put("button", button)
        })
    }

    fun sendMouseScroll(dx: Float, dy: Float) {
        sendCommand(JSONObject().apply {
            put("cmd", "scroll")
            put("dx", dx)
            put("dy", dy)
        })
    }

    fun sendKey(key: String) {
        sendCommand(JSONObject().apply {
            put("cmd", "key")
            put("key", key)
        })
    }

    fun sendText(text: String) {
        sendCommand(JSONObject().apply {
            put("cmd", "type")
            put("text", text)
        })
    }

    private fun sendCommand(json: JSONObject) {
        val ip = getEffectiveIp() ?: return
        val data = (json.toString() + "\n").toByteArray(Charsets.UTF_8)

        scope.launch {
            transportMutex.withLock {
                if (!isConnected) {
                    Log.w(TAG, "Dropping PC command: no active receiver connection.")
                    return@withLock
                }

                try {
                    val out = tcpOutputStream
                    if (out != null && tcpSocket?.isConnected == true && tcpSocket?.isClosed == false) {
                        out.write(data)
                        out.flush()
                    } else {
                        isConnected = false
                        Log.w(TAG, "PC TCP connection is no longer available; command was not sent.")
                    }
                } catch (e: Exception) {
                    isConnected = false
                    Log.e(TAG, "PC command write failed: ${e.message}", e)
                    try {
                        tcpOutputStream?.close()
                    } catch (_: Exception) {
                    }
                    try {
                        tcpSocket?.close()
                    } catch (_: Exception) {
                    }
                    tcpOutputStream = null
                    tcpSocket = null
                }
            }
        }
    }
}
