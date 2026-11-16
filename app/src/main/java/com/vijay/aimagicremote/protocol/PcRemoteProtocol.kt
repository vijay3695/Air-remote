package com.vijay.aimagicremote.protocol

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/**
 * Windows PC Remote Protocol (Wi-Fi).
 *
 * The phone opens one persistent TCP connection to the companion receiver and
 * waits for the receiver's protocol-ready handshake before reporting connected.
 * Every command is newline-delimited JSON. Writes are serialized so frames can
 * never interleave. UDP is used only by discovery, never for commands.
 */
object PcRemoteProtocol {

    private const val TAG = "PcRemoteProtocol"
    const val DEFAULT_PORT = 9090
    const val HTTP_PORT = 8080
    private const val PROTOCOL_VERSION = 1

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

    @Volatile
    var lastAcknowledgedCommandId: String? = null
        private set

    private var tcpSocket: Socket? = null
    private var tcpOutputStream: OutputStream? = null
    private var tcpReader: BufferedReader? = null
    private var readerJob: Job? = null
    private var connectionCallback: ConnectCallback? = null
    private val writeMutex = Mutex()
    private val commandIds = AtomicLong(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getEffectiveIp(): String? {
        val ip = currentPcIp ?: RemoteManager.currentTargetIp
        return if (ip.isNotEmpty()) ip.removePrefix("/") else null
    }

    fun connect(ip: String, port: Int = DEFAULT_PORT, callback: ConnectCallback? = null) {
        val cleanIp = ip.trim().removePrefix("/")
        currentPcIp = cleanIp
        connectionCallback = callback

        scope.launch {
            disconnectInternal()
            try {
                Log.i(TAG, "Connecting to Windows PC receiver at $cleanIp:$port...")
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(cleanIp, port), 4000)
                socket.soTimeout = 6000

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val readyLine = reader.readLine()
                    ?: throw java.io.IOException("The PC receiver closed the connection before the handshake.")
                val ready = JSONObject(readyLine)
                if (ready.optString("cmd") != "ready" || ready.optInt("protocol", -1) != PROTOCOL_VERSION) {
                    throw java.io.IOException("The PC at $cleanIp did not return a compatible Air Remote handshake.")
                }

                tcpSocket = socket
                tcpOutputStream = socket.getOutputStream()
                tcpReader = reader
                isConnected = true
                Log.i(TAG, "Windows PC receiver handshake accepted at $cleanIp:$port")
                callback?.onConnected()

                readerJob = scope.launch {
                    try {
                        while (tcpSocket === socket && !socket.isClosed) {
                            val line = reader.readLine() ?: break
                            handleAcknowledgement(line)
                        }
                    } catch (_: Exception) {
                        // Socket closure is handled below.
                    } finally {
                        if (tcpSocket === socket) {
                            isConnected = false
                            closeTransport()
                            withContext(Dispatchers.Main) {
                                connectionCallback?.onDisconnected()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                isConnected = false
                closeTransport()
                Log.e(TAG, "Windows PC connection failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    connectionCallback?.onConnectionFailed(
                        "Could not connect to the Air Remote receiver at $cleanIp:$port. Start pc_receiver.ps1 and allow TCP port $port."
                    )
                }
            }
        }
    }

    private suspend fun handleAcknowledgement(line: String) {
        runCatching {
            val json = JSONObject(line)
            if (json.optString("cmd") == "ack") {
                json.optString("id").takeIf { it.isNotEmpty() }?.let { lastAcknowledgedCommandId = it }
            }
        }
    }

    fun disconnect() {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        readerJob?.cancel()
        readerJob = null
        closeTransport()
        isConnected = false
    }

    private fun closeTransport() {
        try { tcpOutputStream?.close() } catch (_: Exception) {}
        try { tcpReader?.close() } catch (_: Exception) {}
        try { tcpSocket?.close() } catch (_: Exception) {}
        tcpOutputStream = null
        tcpReader = null
        tcpSocket = null
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        sendCommand(PcCommand("mouse_move", id = nextId(), dx = dx, dy = dy))
    }

    fun sendMouseClick(button: String = "left") {
        sendCommand(PcCommand("click", id = nextId(), button = button))
    }

    fun sendMouseButtonDown(button: String = "left") {
        sendCommand(PcCommand("mouse_down", id = nextId(), button = button))
    }

    fun sendMouseButtonUp(button: String = "left") {
        sendCommand(PcCommand("mouse_up", id = nextId(), button = button))
    }

    fun sendMouseScroll(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        sendCommand(PcCommand("scroll", id = nextId(), dx = dx, dy = dy))
    }

    fun sendKey(key: String) {
        sendCommand(PcCommand("key", id = nextId(), key = key))
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        sendCommand(PcCommand("type", id = nextId(), text = text))
    }

    private fun nextId(): String = commandIds.incrementAndGet().toString()

    private fun sendCommand(command: PcCommand) {
        if (!isConnected) return
        scope.launch {
            val data = (command.encode() + "\n").toByteArray(Charsets.UTF_8)
            var sent = false
            writeMutex.withLock {
                val output = tcpOutputStream
                val socket = tcpSocket
                if (output != null && socket != null && !socket.isClosed) {
                    try {
                        output.write(data)
                        output.flush()
                        sent = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Windows PC command write failed: ${e.message}")
                        isConnected = false
                        closeTransport()
                    }
                }
            }
            if (!sent) Log.w(TAG, "Windows PC command was not sent because the receiver is disconnected.")
        }
    }
}
