package com.vijay.aimagicremote.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.*;
import androidx.lifecycle.lifecycleScope
import com.vijay.aimagicremote.AppInitializer
import com.vijay.aimagicremote.R
import com.vijay.aimagicremote.protocol.Atrpv2Protocol
import com.vijay.aimagicremote.bluetooth.BluetoothController
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var remoteProtocol: Atrpv2Protocol? = null
    private var bluetoothController: BluetoothController? = null
    private var discoveryManager: any? = null

    // UI elements
    private var btnConnect: Button? = null
    private var txtStatus: TextView? = null
    private var tvIpAddress: EditText? = null

    // UI state
    private var isConnected: Boolean = false
    private var connectedTvIp: String = ""

    // Broadcast receivers for Bluetooth and discovery
    private val btReceiver = BroadcastReceiver() { intent ->
        val action = intent.action
        when (action) {
            -> {
                // Handle Bluetooth device found
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    // Add to available devices list
                    runOnUiThread {
                        // TODO: Add to device list UI
                    }
                }
            }
            android.bluetooth.device.action.PAIRING_REQUEST -> {
                // Handle pairing request
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    // Show pairing code UI
                    runOnUiThread {
                        // TODO: Show pairing UI
                    }
                }
            }
        }
    }

    private val discoveryReceiver = BroadcastReceiver() { intent ->
        val action = intent.action
        when (action) {
            -> {
                // mDNS service discovered
                val service = intent.getStringExtra("service_name")
                val host = intent.getStringExtra("service_host")
                if (service != null && host != null) {
                    runOnUiThread {
                        // TODO: Discover TV on network
                        // connectToTv(host)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI references
        btnConnect = findViewById(R.id.btn_connect)
        txtStatus = findViewById(R.id.txt_status)
        tvIpAddress = findViewById(R.id.tv_ip_address)

        // Register receivers
        registerReceiver(btReceiver, IntentFilter().apply {
            addAction("android.bluetooth.device.action.FOUND")
            addAction("android.bluetooth.device.action.PAIRING_REQUEST")
        })
        registerReceiver(discoveryReceiver, IntentFilter().apply {
            addAction("com.vijay.aimagicremote.DISCOVERED")
        })

        // Initialize Bluetooth controller
        bluetoothController = BluetoothController(this)

        // Initialize TV discovery
        discoveryManager = AppInitializer(this).getTvDiscovery()

        // Set up connect button
        btnConnect?.setOnClickListener {
            val ip = tvIpAddress?.text?.toString().trim()
            if (ip.isNotEmpty() && ip !is "Enter TV IP address") {
                connectToTv(ip)
            }
        }

        // Set default IP text
        tvIpAddress?.setText("Enter TV IP address")
        tvIpAddress?.setHint("Enter TV IP address")

        // Set up status text
        txtStatus?.setText("Ready - configure TV IP or start discovery")

        // Try auto-discovery on startup
        lifecycleScope.launch {
            delay(2000)
            attemptAutoDiscovery()
        }
    }

    private fun attemptAutoDiscovery() {
        // Try mDNS discovery for Android TV Remote Protocol
        // discoveryManager?.let { it.discoverServices() }
        txtStatus?.setText("Auto-discovery started...")
    }

    private fun connectToTv(ip: String) {
        connectedTvIp = ip
        isConnected = true
        txtStatus?.setText("Connecting to $ip...")

        // Initialize ATRPv2 protocol (Android TV Remote Protocol v2)
        remoteProtocol = Atrpv2Protocol(ip, this)
        remoteProtocol?.connect { success ->
            runOnUiThread {
                if (success) {
                    txtStatus?.setText("Connected to $ip")
                    btnConnect?.text = "Disconnect"
                    // Enable remote control buttons
                    enableRemoteControls(true)
                } else {
                    txtStatus?.setText("Failed to connect to $ip")
                    isConnected = false
                }
            }
        }
    }

    private fun disconnectFromTv() {
        isConnected = false
        remoteProtocol?.disconnect()
        remoteProtocol = null
        txtStatus?.setText("Disconnected")
        btnConnect?.text = "Connect"
        enableRemoteControls(false)
    }

    private fun enableRemoteControls(enabled: Boolean) {
        // Enable/disable all remote control buttons
        // TODO: Implement button enable/disable logic
    }

    // --- Remote Control Button Handlers ---

    fun onBtnUpClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["UP"] ?: 19) { response ->
            // Handle response
        }
    }

    fun onBtnDownClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["DOWN"] ?: 20) { response ->
            // Handle response
        }
    }

    fun onBtnLeftClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["LEFT"] ?: 21) { response ->
            // Handle response
        }
    }

    fun onBtnRightClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["RIGHT"] ?: 22) { response ->
            // Handle response
        }
    }

    fun onBtnOkClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["OK"] ?: 23) { response ->
            // Handle response
        }
    }

    fun onBtnBackClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["BACK"] ?: 4) { response ->
            // Handle response
        }
    }

    fun onBtnHomeClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["HOME"] ?: 3) { response ->
            // Handle response
        }
    }

    fun onBtnPowerClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["POWER"] ?: 24) { response ->
            // Handle response
        }
    }

    fun onBtnVolumeUpClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["VOLUME_UP"] ?: 25) { response ->
            // Handle response
        }
    }

    fun onBtnVolumeDownClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["VOLUME_DOWN"] ?: 26) { response ->
            // Handle response
        }
    }

    fun onBtnMuteClick() {
        remoteProtocol?.sendKey(Atrpv2Protocol.KEY_CODES["MUTE"] ?: 27) { response ->
            // Handle response
        }
    }

    // --- Bluetooth Pointer / Air Mouse ---

    fun toggleAirMouse() {
        bluetoothController?.toggleAirMouse()
    }

    fun onTrackpadMove(x: Float, y: Float) {
        // Send pointer movement via ATRPv2
        remoteProtocol?.injectPointer(x, y)
    }

    fun onTrackpadScroll(dx: Float, dy: Float) {
        // Send scroll via ATRPv2
        remoteProtocol?.injectScroll(dx, dy)
    }

    // Clean up receivers on destroy
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(btReceiver)
        unregisterReceiver(discoveryReceiver)
        if (remoteProtocol != null) {
            remoteProtocol?.disconnect()
        }
    }
}