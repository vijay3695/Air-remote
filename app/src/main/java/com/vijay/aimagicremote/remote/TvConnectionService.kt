package com.vijay.aimagicremote.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vijay.aimagicremote.protocol.Atrpv2Protocol
import kotlinx.coroutines.*

/**
 * Foreground service for maintaining persistent connection to TV
 * Handles ATRPv2 protocol communication in background
 * Allows remote control to work even when app is in background
 */
class TvConnectionService : Service() {

    private var remoteProtocol: Atrpv2Protocol? = null
    private var tvIp: String? = null
    private var connectionCallback: Atrpv2Protocol.ConnectCallback? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_CONNECT = "com.vijay.aimagicremote.CONNECT"
        const val ACTION_DISCONNECT = "com.vijay.aimagicremote.DISCONNECT"
        const val ACTION_SEND_KEY = "com.vijay.aimagicremote.SEND_KEY"
        const val EXTRA_TV_IP = "tv_ip"
        const val EXTRA_KEY_CODE = "key_code"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "tv_connection_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d("TvConnectionService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_CONNECT -> {
                    val ip = intent.getStringExtra(EXTRA_TV_IP)
                    ip?.let { connectToTv(it) }
                }
                ACTION_DISCONNECT -> {
                    disconnectFromTv()
                }
                ACTION_SEND_KEY -> {
                    val keyCode = intent.getIntExtra(EXTRA_KEY_CODE, -1)
                    if (keyCode != -1) {
                        sendKey(keyCode)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TV Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for TV remote connection"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Air Remote")
            .setContentText("Connected to TV - $tvIp")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        stopForeground(true)
    }

    private fun connectToTv(ip: String) {
        tvIp = ip
        isRunning = true
        startForegroundService()

        connectionCallback = object : Atrpv2Protocol.ConnectCallback {
            override fun onConnected() {
                Log.i("TvConnectionService", "Connected to TV at $ip")
                // Update notification
                val notification = NotificationCompat.Builder(this@TvConnectionService, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("Air Remote")
                    .setContentText("Connected to TV - $ip")
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notification)
            }

            override fun onConnectionFailed(error: String) {
                Log.e("TvConnectionService", "Connection failed: $error")
                stopForegroundService()
                isRunning = false
            }

            override fun onDisconnected() {
                Log.i("TvConnectionService", "Disconnected from TV")
                stopForegroundService()
                isRunning = false
            }
        }

        remoteProtocol = Atrpv2Protocol
        Atrpv2Protocol.connect(ip, connectionCallback)
    }

    private fun disconnectFromTv() {
        remoteProtocol?.disconnect()
        remoteProtocol = null
        stopForegroundService()
        isRunning = false
        Log.i("TvConnectionService", "Disconnected from TV")
    }

    private fun sendKey(keyCode: Int) {
        serviceScope.launch {
            remoteProtocol?.sendKey(keyCode) { response ->
                // Handle response if needed
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (isRunning) {
            disconnectFromTv()
        }
        Log.d("TvConnectionService", "Service destroyed")
    }
}