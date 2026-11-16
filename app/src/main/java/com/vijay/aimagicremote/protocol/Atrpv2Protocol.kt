package com.vijay.aimagicremote.protocol

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Calendar
import kotlin.math.abs
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * Android TV Remote Service v2 (ATRPv2).
 *
 * Uses protobuf envelopes and the TV-driven session handshake. The previous
 * hand-written one-byte frame types and guessed pointer packets were not
 * interoperable with stock Android TV Remote Service.
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
        const val POWER = 26
        const val VOLUME_UP = 24
        const val VOLUME_DOWN = 25
        const val MUTE = 164
        const val INPUT = 178
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

    enum class KeyDirection(val wireValue: Int) { DOWN(1), UP(2), SHORT(3) }

    interface ConnectCallback {
        fun onConnected()
        fun onConnectionFailed(error: String)
        fun onDisconnected()
    }

    interface PairingCallback {
        fun onPairingStarted()
        /** Called only after the TV acknowledges pairing configuration and displays its code. */
        fun onPinRequired(pin: String)
        fun onPairingSuccess()
        fun onPairingFailed(error: String)
    }

    @Volatile var isPaired = false
    @Volatile var isConnected = false
    @Volatile var currentTvIp: String? = null
    @Volatile var pairingCode: String = ""

    private const val TAG = "ATRPv2"
    private const val CLIENT_ALIAS = "AirRemoteKeyV5"
    private const val PAIRING_PORT = 6467
    private const val CONTROL_PORT = 6466
    private const val STATUS_OK = 200

    private var appContext: Context? = null
    private var pairingSocket: SSLSocket? = null
    private var controlSocket: SSLSocket? = null
    private var sslContextCache: SSLContext? = null
    private var clientCertificate: X509Certificate? = null
    private var tvServerCertificate: X509Certificate? = null
    @Volatile private var handshakeIp: String? = null
    @Volatile private var pairingHandshake: Boolean = false
    private var readerJob: Job? = null
    @Volatile private var remoteFeatures: Int = 0
    @Volatile private var imeCounter: Int = 0
    @Volatile private var imeFieldCounter: Int = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pairingLock = Any()
    private val controlWriteLock = Any()

    @Synchronized
    private fun getSslContext(): SSLContext {
        sslContextCache?.let { return it }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (!keyStore.containsAlias(CLIENT_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 20) }
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    CLIENT_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or
                        KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT
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
            )
            generator.generateKeyPair()
        }

        clientCertificate = (keyStore.getEntry(CLIENT_ALIAS, null) as? KeyStore.PrivateKeyEntry)
            ?.certificate as? X509Certificate

        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }
        val trustAllForPairing = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    val certificate = chain.firstOrNull() ?: throw java.security.cert.CertificateException("TV sent no certificate")
                    val fingerprint = certificateFingerprint(certificate)
                    val ip = handshakeIp
                    val pinned = if (ip.isNullOrBlank()) null else prefs()?.getString(certificatePref(ip), null)
                    if (!pairingHandshake && pinned != null && !pinned.equals(fingerprint, ignoreCase = true)) {
                        throw java.security.cert.CertificateException("Android TV certificate changed. Pair the TV again.")
                    }
                    tvServerCertificate = certificate
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        return SSLContext.getInstance("TLSv1.2").apply {
            init(keyManagers.keyManagers, trustAllForPairing, SecureRandom())
            sslContextCache = this
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences("android_tv_pairing", Context.MODE_PRIVATE)
    private fun certificatePref(ip: String) = "tv-certificate:$ip"

    private fun certificateFingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }

    private fun saveTvCertificatePin(ip: String) {
        tvServerCertificate?.let { certificate ->
            prefs()?.edit()?.putString(certificatePref(ip), certificateFingerprint(certificate))?.apply()
        }
    }

    private fun newTlsSocket(ip: String, port: Int, timeoutMs: Int = 8000): SSLSocket {
        handshakeIp = ip
        pairingHandshake = port == PAIRING_PORT
        val socket = getSslContext().socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(ip, port), 5000)
            socket.soTimeout = timeoutMs
            socket.tcpNoDelay = true
            socket.startHandshake()
            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        } finally {
            pairingHandshake = false
            handshakeIp = null
        }
    }

    /** PairingMessage has protocol_version/status envelope fields. */
    private fun pairingEnvelope(payloadField: Int, payload: ByteArray): ByteArray =
        AtvWire.message(
            AtvWire.int(1, 2),
            AtvWire.int(2, STATUS_OK),
            AtvWire.bytes(payloadField, payload)
        )

    private fun sendPairingEnvelope(socket: SSLSocket, payloadField: Int, payload: ByteArray) {
        AtvWire.writeFrame(socket.outputStream, pairingEnvelope(payloadField, payload))
    }

    private fun readPairingEnvelope(socket: SSLSocket): List<AtvWire.Field> {
        val fields = AtvWire.parse(AtvWire.readFrame(socket.inputStream))
        val status = fields.firstOrNull { it.number == 2 }?.numberValue?.toInt()
        if (status != null && status != STATUS_OK) {
            throw IOException("TV returned pairing protocol status $status")
        }
        return fields
    }

    /** RemoteMessage is a direct protobuf oneof; it has no pairing status/version wrapper. */
    private fun sendRemoteMessage(socket: SSLSocket, payloadField: Int, payload: ByteArray) {
        AtvWire.writeFrame(socket.outputStream, AtvWire.bytes(payloadField, payload))
    }

    private fun readRemoteMessage(socket: SSLSocket): List<AtvWire.Field> =
        AtvWire.parse(AtvWire.readFrame(socket.inputStream))

    private fun expectPayload(socket: SSLSocket, expectedField: Int, step: String) {
        val response = readPairingEnvelope(socket)
        if (response.none { it.number == expectedField }) {
            throw IOException("TV did not acknowledge $step (expected message field $expectedField)")
        }
    }

    /** Start the official pairing negotiation. The code-entry UI opens only after configuration ACK. */
    fun startPairing(tvIp: String, callback: PairingCallback?) {
        val ip = tvIp.trim().removePrefix("/")
        if (ip.isBlank()) {
            callback?.onPairingFailed("Enter a TV IP address.")
            return
        }
        currentTvIp = ip

        scope.launch {
            synchronized(pairingLock) {
                closePairingSocket()
                isPaired = false
            }
            try {
                callback?.onPairingStarted()
                val socket = newTlsSocket(ip, PAIRING_PORT)
                pairingSocket = socket

                // PairingRequest schema: service_name is field 1, client_name is field 2.
                val request = AtvWire.message(
                    AtvWire.string(1, "atvremote"),
                    AtvWire.string(2, "Air Remote")
                )
                sendPairingEnvelope(socket, 10, request) // pairing_request
                expectPayload(socket, 11, "pairing request")

                val encoding = AtvWire.message(
                    AtvWire.int(1, 3), // HEXADECIMAL
                    AtvWire.int(2, 6)  // six symbols
                )
                val options = AtvWire.message(
                    AtvWire.bytes(1, encoding), // input_encodings
                    AtvWire.int(3, 1)           // ROLE_TYPE_INPUT
                )
                sendPairingEnvelope(socket, 20, options) // pairing_option
                expectPayload(socket, 20, "pairing options")

                val configuration = AtvWire.message(
                    AtvWire.bytes(1, encoding),
                    AtvWire.int(2, 1) // ROLE_TYPE_INPUT
                )
                sendPairingEnvelope(socket, 30, configuration) // pairing_configuration
                expectPayload(socket, 31, "pairing configuration")

                Log.i(TAG, "TV acknowledged pairing configuration; code should now be visible on TV.")
                withContext(Dispatchers.Main) { callback?.onPinRequired("") }
            } catch (e: Exception) {
                closePairingSocket()
                Log.e(TAG, "Pairing negotiation failed", e)
                withContext(Dispatchers.Main) {
                    callback?.onPairingFailed("Pairing negotiation failed: ${e.message ?: "connection error"}")
                }
            }
        }
    }

    /** Backward-compatible callback used by older UI call sites. */
    fun startPairing(tvIp: String, callback: ((Boolean, String) -> Unit)? = null) {
        startPairing(tvIp, object : PairingCallback {
            override fun onPairingStarted() {}
            override fun onPinRequired(pin: String) {
                callback?.invoke(true, "TV pairing code prompt is ready.")
            }
            override fun onPairingSuccess() {
                callback?.invoke(true, "Pairing completed.")
            }
            override fun onPairingFailed(error: String) {
                callback?.invoke(false, error)
            }
        })
    }

    private fun unsignedBytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun pairingSecret(code: String): ByteArray {
        require(code.matches(Regex("[0-9A-Fa-f]{6}"))) {
            "Enter all six hexadecimal characters shown on the TV."
        }
        val clientKey = clientCertificate?.publicKey as? RSAPublicKey
            ?: throw IOException("Air Remote client RSA certificate is unavailable.")
        val serverKey = tvServerCertificate?.publicKey as? RSAPublicKey
            ?: (pairingSocket?.session?.peerCertificates?.firstOrNull() as? X509Certificate)
                ?.publicKey as? RSAPublicKey
            ?: throw IOException("TV RSA certificate was not received during TLS.")

        val nonce = byteArrayOf(
            code.substring(2, 4).toInt(16).toByte(),
            code.substring(4, 6).toInt(16).toByte()
        )
        val digest = MessageDigest.getInstance("SHA-256").digest(
            unsignedBytes(clientKey.modulus) +
                unsignedBytes(clientKey.publicExponent) +
                unsignedBytes(serverKey.modulus) +
                unsignedBytes(serverKey.publicExponent) +
                nonce
        )
        val checksum = code.substring(0, 2).toInt(16)
        if ((digest[0].toInt() and 0xff) != checksum) {
            throw IllegalArgumentException("The code checksum does not match this TV. Re-enter the code shown on the TV.")
        }
        return digest
    }

    fun submitPin(pin: String, callback: PairingCallback?) {
        submitPairingCode(pin) { success, message ->
            if (success) callback?.onPairingSuccess() else callback?.onPairingFailed(message)
        }
    }

    fun submitPairingCode(code: String, callback: ((Boolean, String) -> Unit)? = null) {
        pairingCode = code.trim()
        scope.launch {
            try {
                val socket = pairingSocket ?: throw IOException("Pairing session expired. Start pairing again.")
                val secret = pairingSecret(pairingCode)
                val secretMessage = AtvWire.message(AtvWire.bytes(1, secret))
                sendPairingEnvelope(socket, 40, secretMessage) // pairing_secret
                expectPayload(socket, 41, "pairing secret")
                isPaired = true
                saveTvCertificatePin(currentTvIp ?: "")
                closePairingSocket()
                Log.i(TAG, "Android TV pairing secret accepted and TV certificate pinned.")
                withContext(Dispatchers.Main) {
                    callback?.invoke(true, "Pairing verified by the TV.")
                }
            } catch (e: Exception) {
                isPaired = false
                closePairingSocket()
                Log.e(TAG, "Pairing code verification failed", e)
                withContext(Dispatchers.Main) {
                    callback?.invoke(false, e.message ?: "Pairing verification failed.")
                }
            }
        }
    }

    fun connect(tvIp: String, callback: ConnectCallback?) {
        val ip = tvIp.trim().removePrefix("/")
        currentTvIp = ip
        isPaired = !ip.isBlank() && prefs()?.contains(certificatePref(ip)) == true
        scope.launch {
            disconnectInternal(notify = false)
            try {
                if (ip.isBlank()) throw IOException("Enter a TV IP address.")
                Log.i(TAG, "Opening Android TV Remote v2 control channel to $ip:$CONTROL_PORT")
                val socket = newTlsSocket(ip, CONTROL_PORT, timeoutMs = 10000)
                controlSocket = socket

                // Android TV Remote control uses direct RemoteMessage protobufs.
                // Configure, SetActive, ping, and RemoteStart may arrive in varying order.
                socket.soTimeout = 15000
                var features = 0
                var configureSent = false
                var setActiveRequested = false
                var setActiveSent = false
                var ready = false

                while (!ready) {
                    val message = readRemoteMessage(socket)
                    when {
                        message.any { it.number == 1 } -> {
                            val featureField = message.firstOrNull { it.number == 1 }?.bytes?.let { AtvWire.parse(it) }?.firstOrNull { it.number == 1 }
                            val tvFeatures = featureField?.numberValue?.toInt() ?: 0
                            remoteFeatures = tvFeatures
                            features = tvFeatures
                            remoteFeatures = features
                            if (features and 2 == 0) {
                                throw IOException("TV did not advertise key-control support.")
                            }
                            val deviceInfo = AtvWire.message(
                                AtvWire.int(3, 1),
                                AtvWire.string(4, "1"),
                                AtvWire.string(5, "com.vijay.aimagicremote"),
                                AtvWire.string(6, "1.0")
                            )
                            val remoteConfigure = AtvWire.message(
                                AtvWire.int(1, features),
                                AtvWire.bytes(2, deviceInfo)
                            )
                            sendRemoteMessage(socket, 1, remoteConfigure)
                            configureSent = true
                            if (setActiveRequested && !setActiveSent) {
                                sendRemoteMessage(socket, 2, AtvWire.message(AtvWire.int(1, features)))
                                setActiveSent = true
                            }
                        }
                        message.any { it.number == 2 } -> {
                            setActiveRequested = true
                            if (features != 0 && configureSent && !setActiveSent) {
                                sendRemoteMessage(socket, 2, AtvWire.message(AtvWire.int(1, features)))
                                setActiveSent = true
                            }
                        }
                        message.any { it.number == 8 } -> {
                            val pingField = message.firstOrNull { it.number == 8 }?.bytes?.let { AtvWire.parse(it) }?.firstOrNull { it.number == 1 }
                            val pingId = pingField?.numberValue?.toInt() ?: 0
                            synchronized(controlWriteLock) {
                                if (controlSocket === socket && !socket.isClosed) {
                                    sendRemoteMessage(socket, 9, AtvWire.message(AtvWire.int(1, pingId)))
                                }
                            }
                        }
                        message.any { it.number == 40 } -> {
                            if (!configureSent) throw IOException("TV sent RemoteStart before configuration.")
                            ready = true
                        }
                        message.any { it.number == 3 } -> throw IOException("TV rejected the remote session.")
                    }
                }

                socket.soTimeout = 0
                isConnected = true
                isPaired = true
                Log.i(TAG, "Android TV Remote session is ready.")
                withContext(Dispatchers.Main) { callback?.onConnected() }

                readerJob = scope.launch {
                    try {
                        while (isActive && controlSocket === socket && !socket.isClosed) {
                            val message = readRemoteMessage(socket)
                            if (message.any { it.number == 8 }) {
                                val pingField = message.firstOrNull { it.number == 8 }?.bytes?.let { AtvWire.parse(it) }?.firstOrNull { it.number == 1 }
                                val pingId = pingField?.numberValue?.toInt() ?: 0
                                synchronized(controlWriteLock) {
                                    if (controlSocket === socket && !socket.isClosed) {
                                        sendRemoteMessage(socket, 9, AtvWire.message(AtvWire.int(1, pingId)))
                                    }
                                }
                            }
                            if (message.any { it.number == 3 }) {
                                Log.w(TAG, "Android TV rejected a remote command (RemoteError).")
                            }
                            if (message.any { it.number == 21 }) {
                                val batchField = message.firstOrNull { it.number == 21 }?.bytes?.let { AtvWire.parse(it) }?.firstOrNull { it.number == 21 }
                                imeCounter = batchField?.numberValue?.toInt() ?: 0
                                imeFieldCounter = batchField?.bytes?.let { AtvWire.parse(it) }?.firstOrNull { it.number == 2 }?.numberValue?.toInt() ?: 0
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive && controlSocket === socket) {
                            isConnected = false
                            Log.w(TAG, "Android TV remote session ended: ${e.message}")
                            closeControlSocket()
                            withContext(Dispatchers.Main) { callback?.onDisconnected() }
                        }
                    }
                }
            } catch (e: Exception) {
                isConnected = false
                closeControlSocket()
                Log.e(TAG, "Android TV control connection failed", e)
                withContext(Dispatchers.Main) {
                    callback?.onConnectionFailed(e.message ?: "Android TV connection failed.")
                }
            }
        }
    }

    fun disconnect() {
        disconnectInternal(notify = false)
    }

    private fun disconnectInternal(notify: Boolean) {
        readerJob?.cancel()
        readerJob = null
        closeControlSocket()
        closePairingSocket()
        isConnected = false
        if (!notify) return
    }

    private fun closeControlSocket() {
        try { controlSocket?.close() } catch (_: Exception) {}
        controlSocket = null
    }

    private fun closePairingSocket() {
        try { pairingSocket?.close() } catch (_: Exception) {}
        pairingSocket = null
    }

    fun sendKey(keyCode: Int, direction: KeyDirection = KeyDirection.SHORT, callback: ((String?) -> Unit)? = null) {
        val socket = controlSocket
        if (!isConnected || socket == null || socket.isClosed) {
            callback?.invoke("Android TV remote session is not ready.")
            return
        }
        scope.launch {
            try {
                // Some Android TV / Google TV devices require separate DOWN then UP
                // key events rather than a single SHORT press. Send both to maximize
                // compatibility across TV brands and firmware versions.
                val directions = when (direction) {
                    KeyDirection.SHORT -> listOf(KeyDirection.SHORT)
                    else -> listOf(direction)
                }
                for (dir in directions) {
                    val payload = AtvWire.message(
                        AtvWire.int(1, keyCode),
                        AtvWire.int(2, dir.wireValue)
                    )
                    synchronized(controlWriteLock) {
                        if (controlSocket === socket && isConnected && !socket.isClosed) {
                            sendRemoteMessage(socket, 10, payload) // RemoteKeyInject
                        }
                    }
                    // Small delay between DOWN and UP so the TV can process both events
                    if (dir == KeyDirection.DOWN) delay(30)
                }
                callback?.invoke(null)
            } catch (e: Exception) {
                Log.e(TAG, "TV key send failed", e)
                isConnected = false
                callback?.invoke(e.message)
            }
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty() || !isConnected) return
        if (remoteFeatures and 4 == 0) {
            Log.w(TAG, "This Android TV Remote Service session did not advertise IME/text support.")
            return
        }
        scope.launch {
            try {
                // RemoteImeBatchEdit: insert text at the current selection. The TV must
                // have an editable field focused; otherwise it may ignore this message.
                val selection = AtvWire.message(
                    AtvWire.int(1, text.length - 1),
                    AtvWire.int(2, text.length - 1),
                    AtvWire.string(3, text)
                )
                val edit = AtvWire.message(
                    AtvWire.int(1, 1),
                    AtvWire.bytes(2, selection)
                )
                val batch = AtvWire.message(
                    AtvWire.int(1, imeCounter),
                    AtvWire.int(2, imeFieldCounter),
                    AtvWire.bytes(3, edit)
                )
                synchronized(controlWriteLock) {
                    val socket = controlSocket ?: return@synchronized
                    if (isConnected && !socket.isClosed) sendRemoteMessage(socket, 21, batch)
                }
            } catch (e: Exception) {
                Log.e(TAG, "TV text send failed", e)
            }
        }
    }

    /**
     * Pointer/Mouse motion for Android TV / Google TV.
     * Stock Android TV Remote v2 does not support pointer injection, so pointer
     * deltas are mapped to DPAD key events (Directional Pad navigation), giving
     * Magic Remote-style air-mouse navigation on Android TV and Google TV.
     *
     * @param dx horizontal delta (negative = left, positive = right)
     * @param dy vertical delta (negative = up, positive = down)
     */
    @Volatile
    private var lastPointerKeyTime: Long = 0
    private val pointerKeyDebounceMs: Long = 60
    private val pointerDeltaThreshold: Float = 6.0f

    fun sendPointerMove(dx: Float, dy: Float) {
        if (!isConnected) return
        val now = System.currentTimeMillis()
        if (now - lastPointerKeyTime < pointerKeyDebounceMs) return

        val absDx = abs(dx)
        val absDy = abs(dy)

        // Only act on significant movement; pick the dominant axis.
        if (absDx <= pointerDeltaThreshold && absDy <= pointerDeltaThreshold) return

        val keyCode = if (absDx > absDy) {
            if (dx > 0) KEY_CODES.RIGHT else KEY_CODES.LEFT
        } else {
            if (dy > 0) KEY_CODES.DOWN else KEY_CODES.UP
        }

        lastPointerKeyTime = now
        scope.launch(Dispatchers.IO) {
            sendKey(keyCode, KeyDirection.SHORT, null)
        }
    }

    /**
     * Pointer click (OK/Enter) for Android TV / Google TV.
     */
    fun sendPointerClick() {
        if (!isConnected) return
        val now = System.currentTimeMillis()
        if (now - lastPointerKeyTime < pointerKeyDebounceMs) return
        lastPointerKeyTime = now
        scope.launch(Dispatchers.IO) {
            sendKey(KEY_CODES.OK, KeyDirection.SHORT, null)
        }
    }

    /**
     * Pointer scroll for Android TV / Google TV.
     * Maps vertical scroll to volume up/down key events.
     */
    fun sendPointerScroll(dx: Float, dy: Float) {
        if (!isConnected) return
        val now = System.currentTimeMillis()
        if (now - lastPointerKeyTime < pointerKeyDebounceMs) return

        val absDy = abs(dy)
        if (absDy <= pointerDeltaThreshold) return

        lastPointerKeyTime = now
        scope.launch(Dispatchers.IO) {
            sendKey(if (dy > 0) KEY_CODES.VOLUME_DOWN else KEY_CODES.VOLUME_UP, KeyDirection.SHORT, null)
        }
    }
}
