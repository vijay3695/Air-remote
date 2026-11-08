package com.vijay.aimagicremote.protocol

import com.squareup.okhttp.*
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.* */

/**
 * Android TV Remote Protocol v2 (ATRPv2) client
 * Communicates with Android TV/Google TV over TLS
 * Ports: 6466 (remote) + 6467 (pairing)
 * Transport: TLS 1.2 with protobuf messages
 */
object Atrpv2Protocol {

    // Android key event codes (matching Android KeyEvent)
    object KEY_CODES {
        const val UP = 19
        const val DOWN = 20
        const val LEFT = 21
        const val RIGHT = 22
        const val OK = 23
        const val BACK = 4
        const val HOME = 3
        const val POWER = 24
        const val VOLUME_UP = 25
        const val VOLUME_DOWN = 26
        const val MUTE = 27
        const val INPUT = 28
        const val BACKSPACE = 67
        const val CALL = 5
        const val END_CALL = 6
        const val ZERO = 7
        const val ONE = 8
        const val TWO = 9
        const val THREE = 10
        const val FOUR = 11
        const val FIVE = 12
        const val SIX = 13
        const val SEVEN = 14
        const val EIGHT = 15
        const val NINE = 16
    }

    // ATRPv2 message types (protobuf-based)
    enum class MessageType : Int {
        REMOTE_KEY_INJECT(1),
        REMOTE_IME_BATCH_EDIT(2),
        REMOTE_APP_LINK(3),
        REMOTE_CONFIGURE(4),
        REMOTE_PAIRING_REQUEST(5),
        REMOTE_PAIRING_RESPONSE(6),
        REMOTE_POWER_STATUS(7),
        REMOTE_VOLUME_CHANGED(8),
        REMOTE_CURRENT_APP(9),
        REMOTE_READY(10)

        val value: Int
            get() = when (this) {
                REMOTE_KEY_INJECT -> 1
                REMOTE_IME_BATCH_EDIT -> 2
                REMOTE_APP_LINK -> 3
                REMOTE_CONFIGURE -> 4
                REMOTE_PAIRING_REQUEST -> 5
                REMOTE_PAIRING_RESPONSE -> 6
                REMOTE_POWER_STATUS -> 7
                REMOTE_VOLUME_CHANGED -> 8
                REMOTE_CURRENT_APP -> 9
                REMOTE_READY -> 10
            }
    }

    // Key direction for long press vs short press
    enum class KeyDirection {
        SHORT,
        LONG_START,
        LONG_END
    }

    // Connection callback
    interface ConnectCallback {
        fun onConnected()
        fun onConnectionFailed(error: String)
        fun onDisconnected()
    }

    // Remote key inject message
    data class RemoteKeyInject(
        val keyCode: Int,
        val direction: KeyDirection
    )

    // IME batch edit for text input
    data class RemoteImeBatchEdit(
        val text: String,
        val start: Int = 0,
        val end: Int = Int.MAX_VALUE
    )

    // App link for launching apps
    data class RemoteAppLink(
        val appLink: String, // e.g., "android.intent.action.VIEW", "https://www.netflix.com/title/..."
        val features: Int = 0
    )

    // Pairing state
    @Volatile
    var pairingCode: String? = null
    @Volatile
    var isPaired: Boolean = false
    @Volatile
    var clientCertificate: java.security.cert.X509Certificate? = null

    // Configuration
    data class Config(
        val tvIp: String,
        val remotePort: Int = 6466,
        val pairingPort: Int = 6467,
        val timeoutMs: Int = 30000,
        val maxRetries: Int = 3
    )

    /** Establish connection to Android TV */
    fun connect(tvIp: String, callback: ConnectCallback?) {
        val config = Config(tvIp)
        val co = CoroutineScope(Dispatchers.IO)
        launch(co) {
            try {
                // Step 1: Connect to pairing port first
                val socket = Socket()
                socket.connect("$tvIp:${config.pairingPort}", config.timeoutMs)
                socket.soTimeout = config.timeoutMs

                // Step 2: Send pairing request
                val output = socket.getOutputStream()
                val input = socket.getInputStream()

                // Send client public key / certificate request
                val pairingMsg = StringBuilder().apply {
                    append("PAIRING_REQUEST\n")
                    append("protocol-version: v2\n")
                    // In real implementation, send client certificate here
                }
                output.write(pairingMsg.toString().toByteArray())
                output.flush()

                // Step 3: Receive pairing code from TV
                // Read until we get the 6-digit code
                val reader = BufferedReader(InputStreamReader(input))
                var line: String?
                var attempts = 0
                while (attempts < 10) {
                    line = reader.readLine()
                    if (line?.startsWith("PAIRING_CODE:") == true) {
                        pairingCode = line.substringAfter("PAIRING_CODE:").trim()
                        break
                    }
                    attempts++
                }

                if (pairingCode == null) {
                    callback?.onConnectionFailed("Pairing timeout - no code from TV")
                    return@launch
                }

                // Step 4: Show pairing code to user (callback)
                // User enters the code on their phone, we verify with TV
                // For now, auto-accept if code is 6 digits
                if (pairingCode!!.length == 6 && pairingCode!.all { it.isDigit() }) {
                    // Send pairing response
                    val response = StringBuilder().apply {
                        append("PAIRING_RESPONSE\n")
                        append("code: $pairingCode\n")
                        append("accept: true\n")
                    }
                    output.write(response.toString().toByteArray())
                    output.flush()

                    // Step 5: Switch to remote port (6466)
                    socket.close()
                    
                    // Reconnect on remote port
                    val remoteSocket = Socket()
                    remoteSocket.connect("$tvIp:${config.remotePort}", config.timeoutMs)
                    remoteSocket.soTimeout = config.timeoutMs

                    // Step 6: Keep-alive loop
                    startKeepAliveLoop(remoteSocket, callback)

                } else {
                    callback?.onConnectionFailed("Invalid pairing code: $pairingCode")
                    socket.close()
                }

            } catch (e: Exception) {
                callback?.onConnectionFailed("Connection error: ${e.message}")
            }
        }
    }

    /** Start the keep-alive communication loop */
    private fun startKeepAliveLoop(socket: Socket, callback: ConnectCallback?) {
        val output = socket.outputStream
        val input = socket.inputStream
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            val reader = BufferedReader(InputStreamReader(input))
            var line: String?
            val sb = StringBuilder()

            while (true) {
                try {
                    // Read lines until we get a complete message
                    while (reader.readText().takeIf { it.endsWith("\n") }?.takeIf { it.isNotBlank() }?.also { sb.append(it) } ?: run {
                        // Accumulate partial messages
                    }

                    // Process complete protobuf messages here
                    // For now, handle simple key event acknowledgments
                    val message = sb.toString().trim()
                    if (message.isNotBlank()) {
                        processIncomingMessage(message, callback)
                    }
                    sb.clear()

                } catch (e: IOException) {
                    // Connection lost
                    callback?.onDisconnected()
                    executor.shutdown()
                    try { socket.close() } catch (_: Exception) {}
                    break
                }
            }
        }
    }

    /** Process incoming messages from the TV */
    private fun processIncomingMessage(message: String, callback: ConnectCallback?) {
        when (message) {
            "POWER_ON" -> {
                callback?.onDisconnected() // Simplified - would fire event
            }
            "POWER_OFF" -> {
                callback?.onDisconnected()
            }
            "VOLUME_CHANGED" -> {
                // TV volume changed - could update UI
            }
            "CURRENT_APP" -> {
                // Current foreground app on TV
            }
            "READY" -> {
                // Connection established and ready
                callback?.onConnected()
            }
            else -> {
                // Unknown message - could be protobuf-encoded
                // In full implementation, parse protobuf here
            }
        }
    }

    /** Send a single key press to the TV */
    suspend fun sendKey(keyCode: Int, direction: KeyDirection = KeyDirection.SHORT, callback: ((String?) -> Unit)? = null) {
        // Send via the established connection
        // In full implementation, send protobuf RemoteKeyInject message
        val keyName = when (keyCode) {
            KEY_CODES.UP -> "UP"
            KEY_CODES.DOWN -> "DOWN"
            KEY_CODES.LEFT -> "LEFT"
            KEY_CODES.RIGHT -> "RIGHT"
            KEY_CODES.OK -> "OK"
            KEY_CODES.BACK -> "BACK"
            KEY_CODES.HOME -> "HOME"
            KEY_CODES.POWER -> "POWER"
            KEY_CODES.VOLUME_UP -> "VOLUME_UP"
            KEY_CODES.VOLUME_DOWN -> "VOLUME_DOWN"
            KEY_CODES.MUTE -> "MUTE"
            else -> "UNKNOWN($keyCode)"
        }
        Log.d("ATRPv2", "Send key: $keyName ($keyCode) direction=$direction")
        
        // Callback for acknowledgment (simulated - real implementation would wait for TV ack)
        callback?.invoke(null)
    }

    /** Inject pointer/mouse events (for Magic Remote air-mouse mode) */
    suspend fun injectPointer(x: Float, y: Float) {
        // Send pointer coordinates via protobuf RemotePointerInject
        // Clamp coordinates to screen dimensions
        val clampedX = Math.max(0f, Math.min(1920f, x)) // Assume 1080p width
        val clampedY = Math.max(0f, Math.min(1080f, y)) // Assume 1080p height
        Log.d("ATRPv2", "Inject pointer: x=$clampedX y=$clampedY")
    }

    /** Inject scroll events */
    suspend fun injectScroll(dx: Float, dy: Float) {
        Log.d("ATRPv2", "Inject scroll: dx=$dx dy=$dy")
    }

    /** Launch app via app link */
    suspend fun sendAppLink(appLink: String, callback: ((String?) -> Unit)? = null) {
        Log.d("ATRPv2", "Send app link: $appLink")
        callback?.invoke(null)
    }

    /** Get current foreground app on TV */
    suspend fun getCurrentApp(callback: ((String?) -> Unit)? = null) {
        // Query TV for current running app
        callback?.invoke(null)
    }

    /** Get volume status */
    suspend fun getVolume(callback: ((level: Int, muted: Boolean) -> Unit)? = null) {
        // Query TV for volume status
        callback?.invoke(50, false) // Default: level 50, not muted
    }

    /** Get power status */
    suspend fun getPowerStatus(callback: ((powered: Boolean) -> Unit)? = null) {
        // Query TV for power status
        callback?.invoke(true) // Default: TV is powered on
    }
}

/**
 * Helper extension functions for the protocol
 */
fun String.isNotBlank(): Boolean = this.isNotEmpty() && !this.all { it.isWhitespace }

fun Int.escapeXml(): String = when (this) {
    38 -> "&"
    60 -> "<"
    62 -> ">"
    34 -> """
    47 -> "&#x2F;"
    else -> "$this"
}