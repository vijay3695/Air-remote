package com.vijay.aimagicremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*}

/**
 * TV Discovery Manager - discovers Android TV devices on the local network
 * Using mDNS (multicast DNS) to find services with "_androidtvremote._tcp" subtype
 * Also discovers "_androidtvremote2._tcp" for protocol v2
 */
class TvDiscoveryManager(private val context: Context) {

    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as NsdManager?
    private val handler = Handler(Looper.getMainLooper())

    // Discovered TVs
    @Volatile
    private var discoveredTvs: List<TvInfo> = emptyList()

    // Callback for discovered TVs
    private var discoveryListener: ((List<TvInfo>) -> Unit)? = null

    // Current search in progress
    @Volatile
    private var isDiscovering: Boolean = false

    /**
     * TV information data class
     */
    data class TvInfo(
        val id: String = java.util.UUID.randomUUID().toString(),
        val name: String = "Unknown TV",
        val host: String = "",
        val port: Int = 6466,
        val protocol: String = "ATRPv2"
    )

    /** Start mDNS discovery for Android TV devices */
    fun startDiscovery(listener: ((List<TvInfo>) -> Unit)? = null) {
        if (isDiscovering) return

        discoveryListener = listener
        isDiscovering = true
        Log.i("TvDiscovery", "Starting mDNS discovery for Android TV")

        // Cancel any existing search
        nsdManager?.stopServiceQuery(queryListener)

        // Set up mDNS query for Android TV Remote Protocol
        val query = NsdServiceInfo.newBuilder()
            .setServiceType("_androidtvremote._tcp") // Protocol v1
            .build()

        // Also query for protocol v2
        val query2 = NsdServiceInfo.newBuilder()
            .setServiceType("_androidtvremote2._tcp") // Protocol v2
            .build()

        // Start searches
        nsdManager?.serviceQuery(query, queryListener)
        nsdManager?.serviceQuery(query2, queryListener)
    }

    /** Stop mDNS discovery */
    fun stopDiscovery() {
        isDiscovering = false
        discoveryListener = null
        nsdManager?.stopServiceQuery(queryListener)
        Log.i("TvDiscovery", "mDNS discovery stopped")
    }

    /** NSD query listener - handles discovered/removed services */
    private val queryListener = object : NsdManager.Listener {

        override fun onStartQuery(NsdManager nsdManager: NsdManager) {
            Log.d("TvDiscovery", "mDNS query started")
        }

        /** Called when a new service is discovered */
        override fun onServiceFound(nsdServiceInfo: NsdServiceInfo) {
            // Skip if already discovered this service
            if (discoveredTvs.any { it.host == nsdServiceInfo.host }) return

            // Validate it's actually an Android TV
            if (nsdServiceInfo.serviceType !in listOf("_androidtvremote._tcp", "_androidtvremote2._tcp")) {
                return
            }

            // Extract TV name from service name
            val tvName = nsdServiceInfo.serviceName
                .removePrefix("_androidtvremote._tcp.")
                .removePrefix("_androidtvremote2._tcp.")
                .trim()

            val tvInfo = TvInfo(
                id = nsdServiceInfo.host + ":" + nsdServiceInfo.port,
                name = tvName.isEmpty() ? "Android TV" : tvName,
                host = nsdServiceInfo.host,
                port = nsdServiceInfo.port,
                protocol = nsdServiceInfo.serviceType
            )

            // Add to discovered list
            val updatedList = tvInfo :: discoveredTvs
            discoveredTvs = updatedList

            // Notify listener
            handler.post {
                discoveryListener?.invoke(updatedList)
            }

            Log.d("TvDiscovery", "Found TV: ${tvInfo.name} at ${tvInfo.host}:${tvInfo.port}")
        }

        /** Called when a service is removed (e.g., TV turned off) */
        override fun onServiceLost(nsdServiceInfo: NsdServiceInfo) {
            discoveredTvs = discoveredTvs.filter { it.host != nsdServiceInfo.host }
            handler.post {
                discoveryListener?.invoke(discoveredTvs)
            }
            Log.d("TvDiscovery", "TV removed: ${nsdServiceInfo.host}")
        }

        override fun onDiscoveryFailed(nsdManager: NsdManager, errorCode: Int) {
            Log.e("TvDiscovery", "mDNS discovery failed: $errorCode")
            handler.post {
                discoveryListener?.invoke(emptyList())
            }
        }

        override fun onStopQuery(NsdManager nsdManager: NsdManager) {
            Log.d("TvDiscovery", "mDNS query stopped")
        }
    }

    /** Get current discovered TVs */
    fun getDiscoveredTvs(): List<TvInfo> = discoveredTvs

    /** Attempt to connect to a discovered TV */
    fun connectToTv(host: String, port: Int, callback: ((Boolean) -> Unit)?) {
        Log.i("TvDiscovery", "Connecting to TV at $host:$port")

        // Create connection intent or start activity
        // In full app: launch MainActivity with IP extra
        callback?.invoke(true)
    }
}

/**
 * Extension functions for TV discovery
 */
fun Context.getTvDiscoveryManager(): TvDiscoveryManager {
    return TvDiscoveryManager(this)
}