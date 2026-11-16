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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * TV & PC Discovery Manager - discovers Android TVs and Windows PCs on local Wi-Fi.
 * Uses NetworkInterface IPv4 subnet detection + mDNS + LAN UDP broadcast discovery + fast port scanning.
 */
class TvDiscoveryManager(private val context: Context) {

    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as NsdManager?

    private val discoveredDevices = mutableListOf<DeviceInfo>()
    private val devicesLock = Any()

    private var discoveryListener: ((List<DeviceInfo>) -> Unit)? = null

    @Volatile
    private var isDiscovering: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class DeviceType {
        ANDROID_TV,
        WINDOWS_PC,
        SAMSUNG_TIZEN,
        LG_WEBOS,
        ROKU_TCL
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
        stopDiscovery()
        synchronized(devicesLock) { discoveredDevices.clear() }
        isDiscovering = true
        discoveryListener = listener
        Log.i("TvDiscovery", "Starting mDNS, UDP broadcast & active network interface subnet scan...")

        val serviceTypes = listOf(
            Pair("_androidtvremote._tcp.", DeviceType.ANDROID_TV),
            Pair("_androidtvremote2._tcp.", DeviceType.ANDROID_TV),
            Pair("_airremote._tcp.", DeviceType.WINDOWS_PC),
            Pair("_workstation._tcp.", DeviceType.WINDOWS_PC)
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

        // Trigger UDP Broadcast scan for Windows PCs
        sendLanUdpBroadcastPing()

        // Fast parallel subnet scanner across active local network subnet
        scanSubnetFast()
    }

    private fun scanSubnetFast() {
        scope.launch {
            val baseSubnet = getLocalSubnetPrefix()
            Log.i("TvDiscovery", "Fast subnet scanner probing active subnet: $baseSubnet(1..254)")
            val jobs = mutableListOf<Job>()

            val ports = listOf(
                9090 to DeviceType.WINDOWS_PC,
                6466 to DeviceType.ANDROID_TV,
                6467 to DeviceType.ANDROID_TV,
                8001 to DeviceType.SAMSUNG_TIZEN,
                8002 to DeviceType.SAMSUNG_TIZEN,
                3000 to DeviceType.LG_WEBOS,
                3001 to DeviceType.LG_WEBOS,
                8060 to DeviceType.ROKU_TCL,
                5555 to DeviceType.ANDROID_TV
            )

            for (i in 1..254) {
                if (!isDiscovering) break
                val targetIp = "$baseSubnet$i"

                for ((port, type) in ports) {
                    jobs.add(launch {
                        if (!isDiscovering) return@launch
                        if (isPortOpen(targetIp, port, 120)) {
                            val devInfo = DeviceInfo(
                                id = "$targetIp:$port",
                                name = "${type.name.replace('_', ' ')} ($targetIp:$port)",
                                host = targetIp,
                                port = port,
                                protocol = type.name,
                                deviceType = type
                            )
                            synchronized(devicesLock) {
                                if (discoveredDevices.none { it.host == targetIp && it.port == port }) {
                                    discoveredDevices.add(devInfo)
                                }
                            }
                            handler.post { discoveryListener?.invoke(getDiscoveredDevices()) }
                        }
                    })
                }
            }
            jobs.joinAll()
        }
    }

    private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getLocalSubnetPrefix(): String {
        return getLocalIpv4AndPrefix()?.second ?: "192.168.1."
    }

    private fun getLocalIpv4AndPrefix(): Pair<String, String>? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            // Prefer likely Wi-Fi interfaces over VPN/tunnel interfaces.
            val ordered = interfaces.sortedBy { iface ->
                val name = iface.name.lowercase()
                if (name.contains("wlan") || name.contains("wifi") || name.contains("wl")) 0 else 1
            }
            for (iface in ordered) {
                if (!iface.isUp || iface.isLoopback) continue
                for (ia in iface.interfaceAddresses) {
                    val addr = ia.address
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("192.") || host.startsWith("10.") || host.startsWith("172.")) {
                        val lastDot = host.lastIndexOf('.')
                        if (lastDot > 0) {
                            val prefix = host.substring(0, lastDot + 1)
                            Log.i("TvDiscovery", "Detected active network interface ${iface.name} IP $host -> Subnet prefix: $prefix")
                            return Pair(host, prefix)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("TvDiscovery", "Error finding active NetworkInterface: ${e.message}")
        }
        return null
    }

    private fun sendLanUdpBroadcastPing() {
        scope.launch {
            try {
                val udpSocket = DatagramSocket()
                udpSocket.broadcast = true
                udpSocket.soTimeout = 1500

                val pingMsg = "{\"cmd\":\"ping\"}\n".toByteArray(Charsets.UTF_8)
                val addresses = mutableListOf(InetAddress.getByName("255.255.255.255"))
                getLocalIpv4AndPrefix()?.second?.let { prefix ->
                    runCatching { addresses.add(InetAddress.getByName(prefix + "255")) }
                }

                for (address in addresses) {
                    try {
                        val packet = DatagramPacket(pingMsg, pingMsg.size, address, 9090)
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
            if (!isDiscovering) return
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
            if (!isDiscovering) return
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
