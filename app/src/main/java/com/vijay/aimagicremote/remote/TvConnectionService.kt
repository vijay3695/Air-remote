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
import com.vijay.aimagicremote.protocol.RemoteManager
import kotlinx.coroutines.*

/**
 * Foreground service for maintaining persistent connection to TV.
 * Supports all protocol targets (Android TV, Samsung Tizen, LG webOS, Roku, Windows PC)
 * by delegating protocol operations to RemoteManager while handling foreground lifecycle.
 * Allows remote control to work even when app is in background.
 */
class TvConnectionService : Service() {

    private var tvIp: String? = null
    private var targetType: RemoteManager.TargetDeviceType? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_CONNECT = "com.vijay.aimagicremote.CONNECT"
        const val ACTION_DISCONNECT = "com.vijay.aimagicremote.DISCONNECT"
        const val ACTION_SEND_KEY = "com.vijay.aimagicremote.SEND_KEY"
        const val ACTION_KEEP_ALIVE = "com.vijay.aimagicremote.KEEP_ALIVE"
        private const val PREFS_NAME = "tv_connection_service"
        private const val PREF_TV_IP = "last_tv_ip"
        private const val PREF_TARGET_TYPE = "last_target_type"
        const val EXTRA_TV_IP = "tv_ip"
        const val EXTRA_TARGET_TYPE = "target_type"
        const val EXTRA_KEY_CODE = "key_code"
        const val EXTRA_KEY_NAME = "key_name"
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
                    val typeStr = intent.getStringExtra(EXTRA_TARGET_TYPE)
                    val type = typeStr?.let { RemoteManager.TargetDeviceType.valueOf(it) }
                    if (ip != null && type != null) {
                        connectToTv(ip, type)
                    } else if (ip != null) {
                        connectToTv(ip, RemoteManager.TargetDeviceType.ANDROID_TV)
                    }
                }
                ACTION_KEEP_ALIVE -> {
                    val ip = intent.getStringExtra(EXTRA_TV_IP)
                    val typeStr = intent.getStringExtra(EXTRA_TARGET_TYPE)
                    if (!ip.isNullOrBlank()) {
                        tvIp = ip
                        targetType = typeStr?.let { RemoteManager.TargetDeviceType.valueOf(it) }
                        isRunning = true
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(PREF_TV_IP, ip)
                            .putString(PREF_TARGET_TYPE, typeStr ?: "")
                            .apply()
                        // RemoteManager owns the protocol connection. This FGS keeps
                        // the process alive so the connection survives in background.
                        startForegroundService()
                    }
                }
                ACTION_DISCONNECT -> {
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .remove(PREF_TV_IP)
                        .remove(PREF_TARGET_TYPE)
                        .apply()
                    disconnectFromTv()
                    stopSelf()
                }
                ACTION_SEND_KEY -> {
                    val keyCode = intent.getIntExtra(EXTRA_KEY_CODE, -1)
                    val keyName = intent.getStringExtra(EXTRA_KEY_NAME)
                    if (keyCode != -1) sendKey(keyCode, keyName)
                }
            }
        } ?: run {
            // START_STICKY may restart this service with a null intent after process
            // reclamation. Restore the last TV session instead of silently idling.
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastIp = prefs.getString(PREF_TV_IP, null)
            val lastType = prefs.getString(PREF_TARGET_TYPE, null)
            if (!lastIp.isNullOrBlank()) {
                targetType = lastType?.takeIf { it.isNotEmpty() }?.let { RemoteManager.TargetDeviceType.valueOf(it) }
                connectToTv(lastIp, targetType ?: RemoteManager.TargetDeviceType.ANDROID_TV)
            } else {
                stopForegroundService()
                isRunning = false
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

    private fun connectToTv(ip: String, type: RemoteManager.TargetDeviceType) {
        tvIp = ip
        targetType = type
        isRunning = true
        startForegroundService()

        RemoteManager.connect(
            context = this@TvConnectionService,
            targetType = type,
            ip = ip,
            callback = object : RemoteManager.ConnectCallback {
                override fun onConnected() {
                    Log.i("TvConnectionService", "Connected to $type at $ip")
                    val notification = NotificationCompat.Builder(this@TvConnectionService, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentTitle("Air Remote")
                        .setContentText("Connected to $type - $ip")
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
                    Log.i("TvConnectionService", "Disconnected from $type")
                    stopForegroundService()
                    isRunning = false
                }
            }
        )
    }

    private fun disconnectFromTv() {
        RemoteManager.disconnect()
        stopForegroundService()
        isRunning = false
        Log.i("TvConnectionService", "Disconnected from TV")
    }

    private fun sendKey(keyCode: Int, keyName: String?) {
        val resolvedName = keyName ?: keyCodeToName(keyCode)
        serviceScope.launch {
            RemoteManager.sendKey(keyCode, resolvedName)
        }
    }

    private fun keyCodeToName(keyCode: Int): String = when (keyCode) {
        Atrpv2Protocol.KEY_CODES.UP -> "UP"
        Atrpv2Protocol.KEY_CODES.DOWN -> "DOWN"
        Atrpv2Protocol.KEY_CODES.LEFT -> "LEFT"
        Atrpv2Protocol.KEY_CODES.RIGHT -> "RIGHT"
        Atrpv2Protocol.KEY_CODES.OK -> "OK"
        Atrpv2Protocol.KEY_CODES.BACK -> "BACK"
        Atrpv2Protocol.KEY_CODES.HOME -> "HOME"
        Atrpv2Protocol.KEY_CODES.POWER -> "POWER"
        Atrpv2Protocol.KEY_CODES.VOLUME_UP -> "VOL_UP"
        Atrpv2Protocol.KEY_CODES.VOLUME_DOWN -> "VOL_DOWN"
        Atrpv2Protocol.KEY_CODES.MUTE -> "MUTE"
        else -> keyCode.toString()
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
