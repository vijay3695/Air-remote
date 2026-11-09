package com.vijay.aimagicremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * TV & PC Discovery Manager - discovers Android TVs and Windows PCs on local Wi-Fi.
 * Uses mDNS + LAN UDP broadcast discovery on port 9090.
 */
class TvDiscoveryManager(private val context: Context) {

    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as NsdManager?

    private val discoveredDevices = mutableListOf<DeviceInfo>()
    private val devicesLock = Any()

    private var discoveryListener: ((List<DeviceInfo>) -> Unit)? = null

    @Volatile
    private var isDiscovering: Boolean = false

    private val handler = Handler(Looper.getMainLooper())

    enum class DeviceType {
        ANDROID_TV,
        WINDOWS_PC
    }

    data class DeviceInfo(
        val id: String = java.util.UUID.randomUUID().toString(),
        val name: String = "Unknown Device",
        val host: String = "",
        val port: Int = 6466,
        val protocol: String = "ATRPv2",
        val deviceType: DeviceType = DeviceType.ANDROID_TV
    )

    private val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()

    fun startDiscovery(listener: ((List<DeviceInfo>) -> Unit)? = null) {
        if (isDiscovering) {
            listener?.invoke(getDiscoveredDevices())
            return
        }

        stopDiscovery()
        isDiscovering = true
        discoveryListener = listener
        Log.i("TvDiscovery", "Starting mDNS & LAN UDP discovery for Android TVs and Windows PCs...")

        val serviceTypes = listOf(
            Pair("_androidtvremote._tcp", DeviceType.ANDROID_TV),
            Pair("_androidtvremote2._tcp", DeviceType.ANDROID_TV),
            Pair("_airremote._tcp", DeviceType.WINDOWS_PC),
            Pair("_workstation._tcp", DeviceType.WINDOWS_PC)
        )

        for ((type, devType) in serviceTypes) {
            val discListener = createDiscoveryListener(type, devType)
            try {
                nsdManager?.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discListener)
                activeListeners.add(discListener)
            } catch (e: Exception) {
                Log.e("TvDiscovery", "Failed to start discovery for $type: ${e.message}")
            }
        }

        // Trigger LAN UDP Broadcast scan for Windows PCs
        sendLanUdpBroadcastPing()
    }

    private fun sendLanUdpBroadcastPing() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val udpSocket = DatagramSocket()
                udpSocket.broadcast = true
                udpSocket.soTimeout = 1500

                val pingMsg = "{\"cmd\":\"ping\"}\n".toByteArray(Charsets.UTF_8)
                val addresses = listOf("255.255.255.255", "192.168.1.255", "192.168.0.255")

                for (addr in addresses) {
                    try {
                        val packet = DatagramPacket(pingMsg, pingMsg.size, InetAddress.getByName(addr), 9090)
                        udpSocket.send(packet)
                    } catch (_: Exception) {}
                }

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 3000 && isDiscovering) {
                    try {
                        udpSocket.receive(packet)
                        val hostAddress = packet.address?.hostAddress ?: continue
                        val str = String(packet.data, 0, packet.length, Charsets.UTF_8)

                        if (str.contains("pong") || str.contains("Windows PC")) {
                            val pcName = try {
                                val json = JSONObject(str)
                                val name = json.optString("name", "")
                                if (name.isNotEmpty()) "Windows PC ($name)" else "Windows PC"
                            } catch (_: Exception) { "Windows PC" }

                            val deviceInfo = DeviceInfo(
                                id = "$hostAddress:9090",
                                name = pcName,
                                host = hostAddress,
                                port = 9090,
                                protocol = "PC Remote",
                                deviceType = DeviceType.WINDOWS_PC
                            )

                            synchronized(devicesLock) {
                                if (discoveredDevices.none { it.host == hostAddress }) {
                                    discoveredDevices.add(deviceInfo)
                                }
                            }

                            handler.post {
                                discoveryListener?.invoke(getDiscoveredDevices())
                            }
                        }
                    } catch (_: Exception) {}
                }
                udpSocket.close()
            } catch (e: Exception) {
                Log.w("TvDiscovery", "UDP broadcast discovery error: ${e.message}")
            }
        }
    }

    fun stopDiscovery() {
        isDiscovering = false
        for (l in activeListeners) {
            try { nsdManager?.stopServiceDiscovery(l) } catch (_: Exception) {}
        }
        activeListeners.clear()
    }

    private fun createDiscoveryListener(serviceTypeTag: String, deviceType: DeviceType) = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("TvDiscovery", "mDNS discovery start failed for $serviceType: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("TvDiscovery", "mDNS discovery stop failed for $serviceType: $errorCode")
        }

        override fun onDiscoveryStarted(serviceType: String) {
            Log.d("TvDiscovery", "mDNS discovery started for $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d("TvDiscovery", "mDNS discovery stopped for $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host
            if (host == null) {
                try {
                    nsdManager?.resolveService(serviceInfo, createResolveListener(deviceType))
                } catch (e: Exception) {
                    Log.e("TvDiscovery", "Resolve error: ${e.message}")
                }
                return
            }

            val hostAddress = host.hostAddress ?: host.toString().removePrefix("/")

            synchronized(devicesLock) {
                if (discoveredDevices.any { it.host == hostAddress }) return
            }

            val rawName = serviceInfo.serviceName
                .removePrefix("_androidtvremote._tcp.")
                .removePrefix("_androidtvremote2._tcp.")
                .removePrefix("_airremote._tcp.")
                .removePrefix("_workstation._tcp.")
                .trim()

            val defaultName = if (deviceType == DeviceType.WINDOWS_PC) "Windows PC" else "Android TV"
            val deviceInfo = DeviceInfo(
                id = "$hostAddress:${serviceInfo.port}",
                name = if (rawName.isEmpty()) defaultName else rawName,
                host = hostAddress,
                port = serviceInfo.port,
                protocol = serviceInfo.serviceType,
                deviceType = deviceType
            )

            synchronized(devicesLock) {
                discoveredDevices.add(deviceInfo)
            }

            handler.post {
                discoveryListener?.invoke(getDiscoveredDevices())
            }

            Log.d("TvDiscovery", "Found ${deviceType.name}: ${deviceInfo.name} at ${deviceInfo.host}:${deviceInfo.port}")
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val hostAddress = serviceInfo.host?.hostAddress ?: serviceInfo.host?.toString()?.removePrefix("/") ?: return
            synchronized(devicesLock) {
                discoveredDevices.removeAll { it.host == hostAddress }
            }
            handler.post {
                discoveryListener?.invoke(getDiscoveredDevices())
            }
            Log.d("TvDiscovery", "Device removed: $hostAddress")
        }
    }

    private fun createResolveListener(deviceType: DeviceType) = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e("TvDiscovery", "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host
            val port = serviceInfo.port
            if (host != null) {
                val hostAddress = host.hostAddress ?: host.toString().removePrefix("/")
                synchronized(devicesLock) {
                    if (discoveredDevices.any { it.host == hostAddress }) return
                }

                val rawName = serviceInfo.serviceName
                    .removePrefix("_androidtvremote._tcp.")
                    .removePrefix("_androidtvremote2._tcp.")
                    .removePrefix("_airremote._tcp.")
                    .removePrefix("_workstation._tcp.")
                    .trim()

                val defaultName = if (deviceType == DeviceType.WINDOWS_PC) "Windows PC" else "Android TV"
                val deviceInfo = DeviceInfo(
                    id = "$hostAddress:$port",
                    name = if (rawName.isEmpty()) defaultName else rawName,
                    host = hostAddress,
                    port = port,
                    protocol = serviceInfo.serviceType,
                    deviceType = deviceType
                )

                synchronized(devicesLock) {
                    discoveredDevices.add(deviceInfo)
                }

                handler.post {
                    discoveryListener?.invoke(getDiscoveredDevices())
                }

                Log.d("TvDiscovery", "Resolved ${deviceType.name}: ${deviceInfo.name} at ${deviceInfo.host}:$port")
            }
        }
    }

    fun getDiscoveredDevices(): List<DeviceInfo> {
        synchronized(devicesLock) {
            return discoveredDevices.toList()
        }
    }
}

fun Context.getTvDiscoveryManager(): TvDiscoveryManager {
    return TvDiscoveryManager(this)
}
