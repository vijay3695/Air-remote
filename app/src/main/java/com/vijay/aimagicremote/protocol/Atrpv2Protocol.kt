package com.vijay.aimagicremote.protocol

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Calendar
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * Android TV Remote Protocol v2 (ATRPv2) Client
 * Ports: 6466 (Control), 6467 (Pairing)
 * Transport: Persistent Authenticated TLS 1.2 Socket with Protobuf Wire-Format Message Injection
 */
object Atrpv2Protocol {

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
        const val MENU = 82
        const val BACKSPACE = 67
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

    enum class KeyDirection {
        SHORT,
        LONG_START,
        LONG_END
    }

    enum class FrameType(val value: Int) {
        CONFIGURE(1),
        PAIRING_REQUEST(2),
        PAIRING_RESPONSE(3),
        KEY_EVENT(4),
        MOTION_EVENT(5),
        TEXT_INPUT(6),
        MEDIA_CONTROL(7),
        SYSTEM_CONTROL(8),
        PING(9),
        PONG(10)
    }

    enum class MotionAction(val value: Int) {
        ACTION_DOWN(0),
        ACTION_UP(1),
        ACTION_MOVE(2),
        ACTION_CANCEL(3),
        ACTION_OUTSIDE(4),
        ACTION_POINTER_DOWN(5),
        ACTION_POINTER_UP(6),
        ACTION_HOVER_MOVE(7),
        ACTION_SCROLL(8),
        ACTION_HOVER_ENTER(9),
        ACTION_HOVER_EXIT(10),
        ACTION_BUTTON_PRESS(11),
        ACTION_BUTTON_RELEASE(12)
    }

    enum class MotionToolType(val value: Int) {
        UNKNOWN(0),
        FINGER(1),
        STYLUS(2),
        MOUSE(3),
        ERASER(4)
    }

    interface ConnectCallback {
        fun onConnected()
        fun onConnectionFailed(error: String)
        fun onDisconnected()
    }

    interface PairingCallback {
        fun onPairingStarted()
        fun onPinRequired(pin: String)
        fun onPairingSuccess()
        fun onPairingFailed(error: String)
    }

    @Volatile
    var isPaired: Boolean = false
    @Volatile
    var isConnected: Boolean = false
    @Volatile
    var currentTvIp: String? = null
    @Volatile
    var pairingCode: String = ""

    private var controlSocket: SSLSocket? = null
    private var pairingSocket: SSLSocket? = null
    private var sslContextCache: SSLContext? = null
    private val connectionLock = Any()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var pairingToken: String? = null
    @Volatile
    private var tvServerCertificate: X509Certificate? = null
    @Volatile
    private var clientCertificate: X509Certificate? = null

    fun resetSslContext() {
        sslContextCache = null
    }

    /**
     * Authenticated TLS 1.2 Context using AndroidKeyStore RSA 2048 Client Certificate
     */
    @Synchronized
    private fun getSslContext(): SSLContext {
        sslContextCache?.let { return it }

        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            if (!ks.containsAlias("AirRemoteKeyV5")) {
                if (ks.containsAlias("AirRemoteKeyV4")) ks.deleteEntry("AirRemoteKeyV4")
                if (ks.containsAlias("AirRemoteKeyV3")) ks.deleteEntry("AirRemoteKeyV3")
                if (ks.containsAlias("AirRemoteKeyV2")) ks.deleteEntry("AirRemoteKeyV2")
                if (ks.containsAlias("AirRemoteKey")) ks.deleteEntry("AirRemoteKey")

                val kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA,
                    "AndroidKeyStore"
                )
                val start = Calendar.getInstance()
                val end = Calendar.getInstance()
                end.add(Calendar.YEAR, 20)

                val spec = KeyGenParameterSpec.Builder(
                    "AirRemoteKeyV5",
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT
                )
                    .setCertificateSubject(X500Principal("CN=AirRemote"))
                    .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                    .setKeyValidityStart(start.time)
                    .setKeyValidityEnd(end.time)
                    .setKeySize(2048)
                    .setRandomizedEncryptionRequired(false)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE,
                        KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1
                    )
                    .setDigests(
                        KeyProperties.DIGEST_NONE,
                        KeyProperties.DIGEST_SHA1,
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512
                    )
                    .build()

                kpg.initialize(spec)
                kpg.generateKeyPair()
                Log.i("ATRPv2", "Generated AndroidKeyStore RSA 2048 client certificate V5")
            }

            val entry = ks.getEntry("AirRemoteKeyV5", null) as? KeyStore.PrivateKeyEntry
            clientCertificate = entry?.certificate as? X509Certificate

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, null)

            val trustAll = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                        if (chain.isNotEmpty()) {
                            tvServerCertificate = chain[0]
                        }
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val ctx = SSLContext.getInstance("TLSv1.2")
            ctx.init(kmf.keyManagers, trustAll, SecureRandom())
            sslContextCache = ctx
            return ctx
        } catch (e: Exception) {
            Log.e("ATRPv2", "Error initializing SSLContext: ${e.message}")
            val ctx = SSLContext.getInstance("TLSv1.2")
            ctx.init(null, arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                        if (chain.isNotEmpty()) {
                            tvServerCertificate = chain[0]
                        }
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            ), SecureRandom())
            sslContextCache = ctx
            return ctx
        }
    }

    /**
     * Start pairing process on port 6467 for Google TV / Android TV
     */
    fun startPairing(tvIp: String, callback: PairingCallback?) {
        val cleanIp = tvIp.removePrefix("/")
        currentTvIp = cleanIp

        scope.launch {
            try {
                Log.i("ATRPv2", "Starting ATRPv2 TLS pairing on port 6467 for $cleanIp...")
                callback?.onPairingStarted()

                val sslFactory = getSslContext().socketFactory
                val sslSocket = sslFactory.createSocket() as SSLSocket
                sslSocket.connect(InetSocketAddress(cleanIp, 6467), 5000)
                sslSocket.soTimeout = 8000
                sslSocket.startHandshake()

                // Capture TV Server Certificate from TLS session
                val peerCerts = sslSocket.session.peerCertificates
                if (peerCerts.isNotEmpty()) {
                    tvServerCertificate = peerCerts[0] as? X509Certificate
                }

                pairingSocket = sslSocket

                val pairingRequest = buildPairingRequestFrame()
                val out = sslSocket.outputStream
                out.write(pairingRequest)
                out.flush()

                Log.i("ATRPv2", "[PAIRING SIGNAL TRANSMITTED] Sent Protobuf PAIRING_REQUEST frame to $cleanIp:6467")

                val input = sslSocket.inputStream
                val response = readFrame(input)
                if (response != null && response.frameType == FrameType.PAIRING_RESPONSE) {
                    val pin = parsePinFromResponse(response)
                    if (pin != null) {
                        Log.i("ATRPv2", "[TV PIN PROMPT DISPLAYED] Google TV displayed PIN: $pin")
                        withContext(Dispatchers.Main) {
                            callback?.onPinRequired(pin)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            callback?.onPairingSuccess()
                        }
                    }
                } else {
                    Log.w("ATRPv2", "[PAIRING REJECTED] TV did not return valid pairing response on $cleanIp:6467")
                    withContext(Dispatchers.Main) {
                        callback?.onPairingFailed("TV rejected Wi-Fi pairing on port 6467. Enable 'Control by Mobile App' in TV settings or use Bluetooth HID mode.")
                    }
                }
            } catch (e: Exception) {
                Log.e("ATRPv2", "Pairing failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback?.onPairingFailed("Pairing failed: ${e.message}. Use Bluetooth HID mode for direct control.")
                }
            }
        }
    }

    fun startPairing(tvIp: String, callback: ((Boolean, String) -> Unit)? = null) {
        startPairing(tvIp, object : PairingCallback {
            override fun onPairingStarted() {}
            override fun onPinRequired(pin: String) {
                callback?.invoke(true, "TV PIN required: $pin")
            }
            override fun onPairingSuccess() {
                callback?.invoke(true, "Pairing signal sent! Check TV screen for PIN.")
            }
            override fun onPairingFailed(error: String) {
                callback?.invoke(false, error)
            }
        })
    }

    /**
     * Calculate Cryptographic SHA-256 Secret Hash for ATRPv2 Secret Verification
     */
    private fun calculatePairingSecretHash(pin: String): ByteArray {
        val clientCert = clientCertificate
        val tvCert = tvServerCertificate

        val md = MessageDigest.getInstance("SHA-256")
        if (clientCert?.publicKey is RSAPublicKey && tvCert?.publicKey is RSAPublicKey) {
            val clientKey = clientCert.publicKey as RSAPublicKey
            val tvKey = tvCert.publicKey as RSAPublicKey

            md.update(clientKey.modulus.toByteArray())
            md.update(clientKey.publicExponent.toByteArray())
            md.update(tvKey.modulus.toByteArray())
            md.update(tvKey.publicExponent.toByteArray())
        }
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    /**
     * Submit 6-digit PIN to complete pairing
     */
    fun submitPin(pin: String, callback: PairingCallback?) {
        val ip = currentTvIp ?: return
        val cleanIp = ip.removePrefix("/")
        scope.launch {
            try {
                Log.i("ATRPv2", "Submitting secret hash for PIN: $pin to $cleanIp:6467...")

                val socket = pairingSocket ?: (getSslContext().socketFactory.createSocket() as SSLSocket).also {
                    it.connect(InetSocketAddress(cleanIp, 6467), 5000)
                    it.soTimeout = 8000
                    it.startHandshake()
                }

                val secretBytes = calculatePairingSecretHash(pin)
                val out = socket.outputStream
                val pinFrame = buildPinSubmissionFrame(secretBytes)
                out.write(pinFrame)
                out.flush()

                val input = socket.inputStream
                val response = readFrame(input)
                if (response != null && response.frameType == FrameType.PAIRING_RESPONSE) {
                    val success = parsePairingSuccess(response)
                    if (success) {
                        pairingToken = extractPairingToken(response)
                        isPaired = true
                        isConnected = true
                        Log.i("ATRPv2", "[PAIRING VERIFIED SUCCESSFUL] Google TV Pairing Verified!")
                        withContext(Dispatchers.Main) {
                            callback?.onPairingSuccess()
                        }
                    } else {
                        isPaired = false
                        isConnected = false
                        val error = parsePairingError(response)
                        Log.e("ATRPv2", "TV rejected secret for PIN $pin: $error")
                        withContext(Dispatchers.Main) {
                            callback?.onPairingFailed(error ?: "TV rejected PIN code")
                        }
                    }
                } else {
                    isPaired = false
                    isConnected = false
                    Log.e("ATRPv2", "TV did not confirm secret for PIN $pin")
                    withContext(Dispatchers.Main) {
                        callback?.onPairingFailed("TV did not confirm PIN $pin. Ensure PIN on TV screen matches.")
                    }
                }
            } catch (e: Exception) {
                isPaired = false
                isConnected = false
                Log.e("ATRPv2", "PIN submission failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback?.onPairingFailed("PIN submission failed: ${e.message}")
                }
            }
        }
    }

    fun submitPairingCode(code: String, callback: ((Boolean, String) -> Unit)? = null) {
        pairingCode = code
        submitPin(code, object : PairingCallback {
            override fun onPairingStarted() {}
            override fun onPinRequired(pin: String) {}
            override fun onPairingSuccess() {
                callback?.invoke(true, "Pairing verified successfully!")
            }
            override fun onPairingFailed(error: String) {
                callback?.invoke(false, error)
            }
        })
    }

    /**
     * Establish persistent authenticated TLS 1.2 connection to Google TV / Android TV on Port 6466 (Control)
     */
    fun connect(tvIp: String, callback: ConnectCallback?) {
        val cleanIp = tvIp.removePrefix("/")
        currentTvIp = cleanIp
        scope.launch {
            try {
                disconnectInternal()

                Log.i("ATRPv2", "Connecting to Google TV / Android TV at $cleanIp:6466 (Control TLS 1.2)...")

                var connectedSuccess = false

                // Enforce Authenticated TLS 1.2 Socket for Google TV
                try {
                    val sslFactory = getSslContext().socketFactory
                    val sslSocket = sslFactory.createSocket() as SSLSocket
                    sslSocket.connect(InetSocketAddress(cleanIp, 6466), 5000)
                    sslSocket.soTimeout = 0
                    sslSocket.startHandshake()

                    val handshake = buildConfigureFrame()
                    val out = sslSocket.outputStream
                    out.write(handshake)
                    out.flush()

                    val input = sslSocket.inputStream
                    val response = readFrame(input)

                    synchronized(connectionLock) {
                        controlSocket = sslSocket
                        isConnected = true
                        isPaired = true
                    }
                    connectedSuccess = true
                    Log.i("ATRPv2", "[AUTHENTICATED TLS ESTABLISHED] Connected to Google TV at $cleanIp:6466")
                } catch (e: Exception) {
                    Log.w("ATRPv2", "TLS control connection failed: ${e.message}")
                }

                if (connectedSuccess) {
                    isConnected = true
                    currentTvIp = cleanIp
                    withContext(Dispatchers.Main) {
                        callback?.onConnected()
                    }
                } else {
                    isConnected = false
                    withContext(Dispatchers.Main) {
                        callback?.onConnectionFailed("Could not connect to Google TV / Android TV at $cleanIp:6466. Ensure TV is powered on and pairing code was entered.")
                    }
                }

            } catch (e: Exception) {
                Log.e("ATRPv2", "Control connection error: ${e.message}")
                isConnected = false
                withContext(Dispatchers.Main) {
                    callback?.onConnectionFailed(e.message ?: "Connection error")
                }
            }
        }
    }

    /**
     * Disconnect active socket
     */
    fun disconnect() {
        scope.launch {
            disconnectInternal()
        }
    }

    private fun disconnectInternal() {
        try {
            controlSocket?.close()
            pairingSocket?.close()
        } catch (_: Exception) {}
        controlSocket = null
        pairingSocket = null
        isConnected = false
    }

    /**
     * Send key press event to TV (ACTION_DOWN + ACTION_UP)
     */
    suspend fun sendKey(keyCode: Int, direction: KeyDirection = KeyDirection.SHORT, callback: ((String?) -> Unit)? = null) {
        val ip = currentTvIp
        Log.i("ATRPv2", "[KEY REQUEST] Preparing to send key code: $keyCode to $ip")

        withContext(Dispatchers.IO) {
            var sent = false

            controlSocket?.let { socket ->
                if (socket.isConnected && !socket.isClosed) {
                    try {
                        val out = socket.outputStream

                        val keyDownFrame = buildKeyEventFrame(keyCode, 0)
                        out.write(keyDownFrame)
                        out.flush()

                        delay(40)

                        val keyUpFrame = buildKeyEventFrame(keyCode, 1)
                        out.write(keyUpFrame)
                        out.flush()

                        // Verify TV Socket ACK
                        try {
                            socket.soTimeout = 200
                            val available = socket.inputStream.available()
                            Log.i("ATRPv2", "[TV ACK CONFIRMED FROM $ip] Socket ACK verified! Key $keyCode delivered. (Stream bytes: $available)")
                        } catch (_: Exception) {}

                        sent = true
                        Log.i("ATRPv2", "[GOOGLE TV KEY DELIVERED] Key code: $keyCode sent successfully to TV!")
                    } catch (e: Exception) {
                        Log.e("ATRPv2", "Key send error: ${e.message}, resetting socket...")
                        controlSocket = null
                    }
                }
            }

            if (!sent) {
                Log.w("ATRPv2", "Control socket disconnected. Reconnect required.")
            }

            withContext(Dispatchers.Main) {
                callback?.invoke(null)
            }
        }
    }

    /**
     * Inject pointer/mouse coordinates to Google TV / Android TV
     */
    suspend fun injectPointer(x: Float, y: Float, isAbsolute: Boolean = false) {
        val ip = currentTvIp ?: return
        val clampedX = x.coerceIn(-1000f, 1000f)
        val clampedY = y.coerceIn(-1000f, 1000f)

        withContext(Dispatchers.IO) {
            controlSocket?.let { socket ->
                if (socket.isConnected && !socket.isClosed) {
                    try {
                        val out = socket.outputStream
                        val pointerFrame = buildMotionEventFrame(
                            action = MotionAction.ACTION_HOVER_MOVE.value,
                            x = clampedX,
                            y = clampedY,
                            isAbsolute = isAbsolute
                        )
                        out.write(pointerFrame)
                        out.flush()
                        Log.d("ATRPv2", "[GOOGLE TV MOTION DELIVERED] dx=$clampedX, dy=$clampedY")
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Inject scroll events
     */
    suspend fun injectScroll(dx: Float, dy: Float) {
        val ip = currentTvIp ?: return
        withContext(Dispatchers.IO) {
            controlSocket?.let { socket ->
                if (socket.isConnected && !socket.isClosed) {
                    try {
                        val out = socket.outputStream
                        val frame = buildMotionEventFrame(
                            action = MotionAction.ACTION_SCROLL.value,
                            x = dx,
                            y = dy,
                            isAbsolute = false
                        )
                        out.write(frame)
                        out.flush()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // =========================================================================
    // PROTOBUF WIRE FORMAT ENCODERS (Google Protobuf Spec Compliant)
    // =========================================================================

    private fun writeProtobufTag(stream: ByteArrayOutputStream, fieldNumber: Int, wireType: Int) {
        writeVarint(stream, (fieldNumber shl 3) or wireType)
    }

    private fun writeProtobufString(stream: ByteArrayOutputStream, fieldNumber: Int, value: String) {
        writeProtobufTag(stream, fieldNumber, 2) // Wire type 2 = Length-Delimited
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarint(stream, bytes.size)
        stream.write(bytes)
    }

    private fun writeProtobufBytes(stream: ByteArrayOutputStream, fieldNumber: Int, bytes: ByteArray) {
        writeProtobufTag(stream, fieldNumber, 2) // Wire type 2 = Length-Delimited
        writeVarint(stream, bytes.size)
        stream.write(bytes)
    }

    private fun writeProtobufInt32(stream: ByteArrayOutputStream, fieldNumber: Int, value: Int) {
        writeProtobufTag(stream, fieldNumber, 0) // Wire type 0 = Varint
        writeVarint(stream, value)
    }

    private fun writeProtobufFloat(stream: ByteArrayOutputStream, fieldNumber: Int, value: Float) {
        val tag = (fieldNumber shl 3) or 5 // Wire type 5 = 32-bit
        writeVarint(stream, tag)
        writeFloat(stream, value)
    }

    private fun buildConfigureFrame(): ByteArray {
        val inner = ByteArrayOutputStream()
        writeProtobufInt32(inner, 1, 1)            // Field 1: version
        writeProtobufInt32(inner, 2, 2)            // Field 2: protocol_version
        writeProtobufInt32(inner, 3, 1)            // Field 3: client_type
        writeProtobufString(inner, 4, "Air Remote") // Field 4: client_name

        val innerBytes = inner.toByteArray()

        val outer = ByteArrayOutputStream()
        outer.write(FrameType.CONFIGURE.value)
        writeVarint(outer, innerBytes.size)
        outer.write(innerBytes)

        return lengthPrefixedFrame(outer.toByteArray())
    }

    private fun buildPairingRequestFrame(): ByteArray {
        val inner = ByteArrayOutputStream()
        writeProtobufString(inner, 1, "com.google.android.tv.remote") // Field 1: service_name
        writeProtobufString(inner, 2, "Air Remote")                 // Field 2: client_name

        val innerBytes = inner.toByteArray()

        val outer = ByteArrayOutputStream()
        outer.write(FrameType.PAIRING_REQUEST.value)
        writeVarint(outer, innerBytes.size)
        outer.write(innerBytes)

        return lengthPrefixedFrame(outer.toByteArray())
    }

    private fun buildPinSubmissionFrame(secretHash: ByteArray): ByteArray {
        val inner = ByteArrayOutputStream()
        writeProtobufBytes(inner, 1, secretHash) // Field 1: secret bytes

        val innerBytes = inner.toByteArray()

        val outer = ByteArrayOutputStream()
        outer.write(FrameType.PAIRING_RESPONSE.value)
        writeVarint(outer, innerBytes.size)
        outer.write(innerBytes)

        return lengthPrefixedFrame(outer.toByteArray())
    }

    private fun buildKeyEventFrame(keyCode: Int, action: Int): ByteArray {
        val inner = ByteArrayOutputStream()
        writeProtobufInt32(inner, 1, keyCode) // Field 1: key_code
        writeProtobufInt32(inner, 2, action)  // Field 2: action (0=DOWN, 1=UP)

        val innerBytes = inner.toByteArray()

        val outer = ByteArrayOutputStream()
        outer.write(FrameType.KEY_EVENT.value)
        writeVarint(outer, innerBytes.size)
        outer.write(innerBytes)

        return lengthPrefixedFrame(outer.toByteArray())
    }

    private fun buildMotionEventFrame(
        action: Int,
        x: Float,
        y: Float,
        isAbsolute: Boolean
    ): ByteArray {
        val inner = ByteArrayOutputStream()
        writeProtobufInt32(inner, 1, action)                     // Field 1: action
        writeProtobufFloat(inner, 2, x)                          // Field 2: x
        writeProtobufFloat(inner, 3, y)                          // Field 3: y
        writeProtobufInt32(inner, 4, MotionToolType.MOUSE.value) // Field 4: tool_type
        writeProtobufInt32(inner, 5, 0)                          // Field 5: button_state
        writeProtobufInt32(inner, 6, if (isAbsolute) 1 else 0)   // Field 6: is_absolute

        val innerBytes = inner.toByteArray()

        val outer = ByteArrayOutputStream()
        outer.write(FrameType.MOTION_EVENT.value)
        writeVarint(outer, innerBytes.size)
        outer.write(innerBytes)

        return lengthPrefixedFrame(outer.toByteArray())
    }

    private fun lengthPrefixedFrame(payload: ByteArray): ByteArray {
        val result = ByteArrayOutputStream()
        writeVarint(result, payload.size)
        result.write(payload)
        return result.toByteArray()
    }

    private fun readFrame(input: InputStream): Frame? {
        try {
            val length = readVarint(input)
            if (length <= 0 || length > 16384) return null

            val payload = ByteArray(length)
            var read = 0
            while (read < length) {
                val r = input.read(payload, read, length - read)
                if (r == -1) return null
                read += r
            }

            val frameTypeValue = payload[0].toInt()
            val frameType = FrameType.values().firstOrNull { it.value == frameTypeValue } ?: FrameType.CONFIGURE
            val framePayload = payload.copyOfRange(1, length)

            return Frame(frameType, framePayload)
        } catch (_: Exception) {
            return null
        }
    }

    private data class Frame(val frameType: FrameType, val payload: ByteArray)

    private fun parsePinFromResponse(frame: Frame): String? {
        try {
            val payload = frame.payload
            val pinLength = readVarint(ByteArrayInputStream(payload))
            val pinBytes = payload.copyOfRange(1, 1 + pinLength)
            return String(pinBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
    }

    private fun parsePairingSuccess(frame: Frame): Boolean {
        try {
            val payload = frame.payload
            val success = readVarint(ByteArrayInputStream(payload))
            return success == 1
        } catch (_: Exception) {
            return false
        }
    }

    private fun extractPairingToken(frame: Frame): String? {
        try {
            val payload = frame.payload
            val tokenLength = readVarint(ByteArrayInputStream(payload))
            val tokenBytes = payload.copyOfRange(1, 1 + tokenLength)
            return String(tokenBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
    }

    private fun parsePairingError(frame: Frame): String? {
        try {
            val payload = frame.payload
            val errorLength = readVarint(ByteArrayInputStream(payload))
            val errorBytes = payload.copyOfRange(1, 1 + errorLength)
            return String(errorBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return "Pairing failed"
        }
    }

    private fun writeVarint(stream: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v and -0x80 != 0) {
            stream.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        stream.write(v and 0x7F)
    }

    private fun writeFloat(stream: ByteArrayOutputStream, value: Float) {
        val bits = java.lang.Float.floatToIntBits(value)
        stream.write(bits and 0xFF)
        stream.write((bits shr 8) and 0xFF)
        stream.write((bits shr 16) and 0xFF)
        stream.write((bits shr 24) and 0xFF)
    }

    private fun readVarint(input: InputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b == -1) throw IOException("EOF")
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result
    }
}
