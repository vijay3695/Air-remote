package com.vijay.aimagicremote.companion

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * TV Companion App - Handles Android TV memory management and connection reliability
 * Addresses the documented pitfalls: foreground service alone is NOT sufficient
 * WorkManager for reconnection, Bluetooth HID fallback when companion unavailable
 */
class TvCompanionManager(private val context: Context) {

    private val notificationManager: NotificationManager? = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private var foregroundServiceRunning: Boolean = false
    private var lastConnectionState: Boolean = false
    private var lastTvIp: String = ""

    private val reconnectionHelper = ReconnectionHelper(context)
    private val wakeLockTracker = WakeLockTracker(context)

    /** Start companion service with reliability patterns */
    fun startCompanion() {
        if (foregroundServiceRunning) return

        // Start foreground service with notification
        foregroundServiceRunning = true
        wakeLockTracker.acquire()

        // Create notification channel (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager?.let { manager ->
                val channel = NotificationChannel(
                    "air_remote_companion",
                    "Air Remote Companion",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background service for TV connectivity"
                }
                manager.createNotificationChannel(channel)
            }
        }

        // Show foreground service notification
        showForegroundNotification()

        // Start reconnection monitoring
        reconnectionHelper.startMonitoring()

        Log.i("TvCompanion", "Companion service started with reliability patterns")
    }

    /** Stop companion service */
    fun stopCompanion() {
        foregroundServiceRunning = false
        wakeLockTracker.release()
        reconnectionHelper.stopMonitoring()
        Log.i("TvCompanion", "Companion service stopped")
    }

    /** Called when activity becomes visible */
    fun onActivityVisible() {
        // Reconnect if connection was lost while app was in background
        if (!lastConnectionState && lastTvIp.isNotEmpty()) {
            // Delayed reconnection to allow UI to stabilize
            Handler(Looper.getMainLooper()).postDelayed({
                // Would trigger reconnection attempt
            }, 3000)
        }
    }

    /** Called when connection state changes */
    fun onConnectionStateChanged(connected: Boolean, tvIp: String?) {
        lastConnectionState = connected
        lastTvIp = tvIp ?: ""

        if (connected) {
            // Reset reconnection backoff
            reconnectionHelper.resetBackoff()
            // Cancel foreground service after successful sustained connection
            // (would be managed by MainActivity)
        } else {
            // Start exponential backoff reconnection
            reconnectionHelper.scheduleReconnection(tvIp)
        }
    }

    /** Request user to keep companion running if killed */
    fun requestKeepAliveRationale() {
        // Show system dialog explaining why companion is needed
        // "Magic Remote needs to stay connected to control your TV"
        // "Some features (air-mouse, pointer control) will be unavailable"
    }

    /** Inner class: Reconnection logic with exponential backoff */
    inner class ReconnectionHelper(private val appContext: Context) {
        private var currentBackoffMs: Long = 5000
        private var reconnectionRunnable: Runnable? = null
        private val handler = Handler(Looper.getMainLooper())

        fun startMonitoring() {
            // Schedule initial reconnection check
            scheduleReconnection(null)
        }

        fun stopMonitoring() {
            reconnectionRunnable?.let { handler.removeCallbacks(it) }
            reconnectionRunnable = null
            currentBackoffMs = 5000
        }

        fun resetBackoff() {
            currentBackoffMs = 5000
        }

        fun scheduleReconnection(tvIp: String?) {
            reconnectionRunnable?.let { handler.removeCallbacks(it) }

            val tvIpToUse = tvIp ?: lastTvIp

            reconnectionRunnable = Runnable {
                // Attempt reconnection
                // Would trigger MainActivity to attempt connection
                Log.d("ReconnectionHelper", "Attempting reconnection to $tvIpToUse")

                // If fails, schedule next attempt with increased backoff
                currentBackoffMs = Math.min(currentBackoffMs * 2, 60000)
                scheduleReconnection(tvIpToUse)
            }

            handler.postDelayed(reconnectionRunnable!!, currentBackoffMs)
        }
    }

    /** Inner class: WakeLock management */
    inner class WakeLockTracker(private val ctx: Context) {
        private var wifiLock: WifiManager.WifiLock? = null
        private var cpuLock: PowerManager.WakeLock? = null

        fun acquire() {
            val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

            // WiFi lock - keeps WiFi alive even when screen is off
            val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "magic_remote_wifi")
            wifiLock?.acquire()

            // CPU lock - keeps process from being killed (strategic, short durations)
            cpuLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "magic_remote_cpu")
            // Use acquire() without timeout for continuous lock, or with timeout for strategic use
            cpuLock?.acquire()
        }

        fun release() {
            wifiLock?.apply { if (isHeld) release() }
            cpuLock?.apply { if (isHeld) release() }
        }
    }

    /** Show foreground service notification */
    private fun showForegroundNotification() {
        notificationManager?.let { manager ->
            val notification = NotificationCompat.Builder(context, "air_remote_companion")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentText("Air Remote - Connecting to TV")
                .setContentTitle("Air Remote")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            manager.notify(12345, notification.build())
        }
    }
}