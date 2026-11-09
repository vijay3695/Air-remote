package com.vijay.aimagicremote.protocol

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

    private var pairingSocket: SSLSocket? = null
    private var controlSocket: SSLSocket? = null
    private var sslContextCache: SSLContext? = null
    private var clientCertificate: X509Certificate? = null
    private var tvServerCertificate: X509Certificate? = null
    private var readerJob: Job? = null
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
                    if (chain.isNotEmpty()) tvServerCertificate = chain[0]
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        return SSLContext.getInstance("TLSv1.2").apply {
            init(keyManagers.keyManagers, trustAllForPairing, SecureRandom())
            sslContextCache = this
        }
    }

    private fun newTlsSocket(ip: String, port: Int, timeoutMs: Int = 8000): SSLSocket {
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
        }
    }

    private fun envelope(payloadField: Int, payload: ByteArray): ByteArray =
        AtvWire.message(
            AtvWire.int(1, 2), // protocol_version
            AtvWire.int(2, STATUS_OK), // status
            AtvWire.bytes(payloadField, payload)
        )

    private fun sendEnvelope(socket: SSLSocket, payloadField: Int, payload: ByteArray) {
        AtvWire.writeFrame(socket.outputStream, envelope(payloadField, payload))
    }

    private fun readEnvelope(socket: SSLSocket): List<AtvWire.Field> {
        val fields = AtvWire.parse(AtvWire.readFrame(socket.inputStream))
        val status = fields.firstOrNull { it.number == 2 }?.numberValue?.toInt()
        if (status != null && status != STATUS_OK) {
            throw IOException("TV returned protocol status $status")
        }
        return fields
    }

    private fun expectPayload(socket: SSLSocket, expectedField: Int, step: String) {
        val response = readEnvelope(socket)
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
                    AtvWire.string(1, "com.github.airremote/androidTvRemote"),
                    AtvWire.string(2, "Air Remote")
                )
                sendEnvelope(socket, 10, request) // pairing_request
                expectPayload(socket, 11, "pairing request")

                val encoding = AtvWire.message(
                    AtvWire.int(1, 3), // HEXADECIMAL
                    AtvWire.int(2, 6)  // six symbols
                )
                val options = AtvWire.message(
                    AtvWire.bytes(1, encoding), // input_encodings
                    AtvWire.int(3, 1)           // ROLE_TYPE_INPUT
                )
                sendEnvelope(socket, 20, options) // pairing_option
                expectPayload(socket, 21, "pairing options")

                val configuration = AtvWire.message(
                    AtvWire.bytes(1, encoding),
                    AtvWire.int(2, 1) // ROLE_TYPE_INPUT
                )
                sendEnvelope(socket, 30, configuration) // pairing_configuration
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
                sendEnvelope(socket, 40, secretMessage) // pairing_secret
                expectPayload(socket, 41, "pairing secret")
                isPaired = true
                closePairingSocket()
                Log.i(TAG, "Android TV pairing secret accepted.")
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
        scope.launch {
            disconnectInternal(notify = false)
            try {
                if (ip.isBlank()) throw IOException("Enter a TV IP address.")
                Log.i(TAG, "Opening Android TV Remote v2 control channel to $ip:$CONTROL_PORT")
                val socket = newTlsSocket(ip, CONTROL_PORT, timeoutMs = 10000)
                controlSocket = socket

                // The TV initiates the remote session with RemoteConfigure.
                val first = readEnvelope(socket)
                if (first.none { it.number == 1 }) {
                    throw IOException("TV did not send RemoteConfigure. Pair this app with the TV first.")
                }
                val features = first.nested(1).number(1)
                if (features == 0) throw IOException("TV did not advertise remote-control capabilities.")

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
                sendEnvelope(socket, 1, remoteConfigure)

                val second = readEnvelope(socket)
                if (second.none { it.number == 2 }) {
                    throw IOException("TV did not acknowledge RemoteConfigure.")
                }
                sendEnvelope(socket, 2, AtvWire.message(AtvWire.int(1, features))) // RemoteSetActive

                // RemoteStart is the actual ready signal. Ignore/respond to pings while waiting.
                socket.soTimeout = 15000
                var ready = false
                while (!ready) {
                    val message = readEnvelope(socket)
                    when {
                        message.any { it.number == 40 } -> ready = true // RemoteStart
                        message.any { it.number == 8 } -> {
                            val pingId = message.nested(8).number(1)
                            sendEnvelope(socket, 9, AtvWire.message(AtvWire.int(1, pingId)))
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
                            val message = readEnvelope(socket)
                            if (message.any { it.number == 8 }) {
                                val pingId = message.nested(8).number(1)
                                sendEnvelope(socket, 9, AtvWire.message(AtvWire.int(1, pingId)))
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
        scope.launch { disconnectInternal(notify = false) }
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
                val payload = AtvWire.message(
                    AtvWire.int(1, keyCode),
                    AtvWire.int(2, direction.wireValue)
                )
                synchronized(controlWriteLock) {
                    if (controlSocket === socket && isConnected && !socket.isClosed) {
                        sendEnvelope(socket, 10, payload) // RemoteKeyInject
                    }
                }
                callback?.invoke(null)
            } catch (e: Exception) {
                Log.e(TAG, "TV key send failed", e)
                isConnected = false
                callback?.invoke(e.message)
            }
        }
    }

    /** Stock Android TV Remote v2 does not provide a generally supported gyro pointer injection command. */
    suspend fun injectPointer(x: Float, y: Float, isAbsolute: Boolean = false) {
        Log.w(TAG, "Pointer injection is not part of the supported stock Android TV Remote v2 key protocol.")
    }

    suspend fun injectScroll(dx: Float, dy: Float) {
        Log.w(TAG, "Scroll injection is not supported by the stock Android TV Remote v2 protocol.")
    }

    fun sendText(text: String) {
        if (text.isEmpty() || !isConnected) return
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
                    AtvWire.int(1, 0),
                    AtvWire.int(2, 0),
                    AtvWire.bytes(3, edit)
                )
                synchronized(controlWriteLock) {
                    val socket = controlSocket ?: return@synchronized
                    if (isConnected && !socket.isClosed) sendEnvelope(socket, 21, batch)
                }
            } catch (e: Exception) {
                Log.e(TAG, "TV text send failed", e)
            }
        }
    }
}
