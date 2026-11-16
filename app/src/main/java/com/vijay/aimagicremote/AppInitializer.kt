package com.vijay.aimagicremote

import android.app.Application
import android.content.Context
import android.util.Log
import com.vijay.aimagicremote.remote.TvConnectionService
import com.vijay.aimagicremote.discovery.TvDiscoveryManager
import com.vijay.aimagicremote.protocol.Atrpv2Protocol
import com.vijay.aimagicremote.protocol.LgWebOsProtocol
import com.vijay.aimagicremote.protocol.SamsungTizenProtocol

/**
 * Application initializer - sets up global components
 * Used for initialization before activities create
 */
class AppInitializer : Application() {

    private var tvDiscovery: TvDiscoveryManager? = null
    private var tvConnection: TvConnectionService? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("AirRemote", "Application started - Air Remote initializing")

        // Initialize TV discovery manager for mDNS service discovery
        tvDiscovery = TvDiscoveryManager(getApplicationContext())

        // Protocol objects persist pairing credentials in SharedPreferences. They
        // must receive the application context before any connection is attempted.
        Atrpv2Protocol.initialize(this)
        SamsungTizenProtocol.initialize(this)
        LgWebOsProtocol.initialize(this)
    }

    /**
     * Get the TV discovery manager instance
     */
    fun getTvDiscovery(): TvDiscoveryManager? = tvDiscovery

    /**
     * Get the TV connection service reference
     */
    fun getTvConnection(): TvConnectionService? = tvConnection

    /**
     * Set the TV connection service reference
     */
    fun setTvConnection(service: TvConnectionService) {
        this.tvConnection = service
    }

    /**
     * Request foreground service to continue running
     * Called when user interacts with the app
     */
    fun requestKeepAlive() {
        Log.d("MagicRemote", "Keeping service alive - user active")
        // The TvConnectionService handles its own foreground service lifecycle
    }

    /**
     * Clean up resources when app process is destroyed
     */
    override fun onTerminate() {
        super.onTerminate()
        tvDiscovery = null
        tvConnection = null
        Log.d("MagicRemote", "Application shutting down")
    }
}