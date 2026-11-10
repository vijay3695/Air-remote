package com.vijay.aimagicremote.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.vijay.aimagicremote.AppInitializer
import com.vijay.aimagicremote.R
import com.vijay.aimagicremote.bluetooth.BluetoothController
import com.vijay.aimagicremote.discovery.TvDiscoveryManager
import com.vijay.aimagicremote.protocol.Atrpv2Protocol
import com.vijay.aimagicremote.protocol.PcRemoteProtocol
import com.vijay.aimagicremote.protocol.RemoteManager
import kotlin.math.abs

/**
 * Main Activity for Air Remote App
 * Features: iOS 18 Glassmorphism Dark Theme, Haptic Tactile Feedback,
 * Context-Aware Guidance Dialogs, 3D Gyroscope Air Mouse, Trackpad, D-Pad, TV Code Pairing, and Auto-Discovery.
 */
class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("air_remote_prefs", Context.MODE_PRIVATE) }

    // UI Header & Status Components
    private var txtWifiStatus: TextView? = null
    private var targetTvBtn: Button? = null
    private var targetPcBtn: Button? = null
    private var targetBtBtn: Button? = null
    private var discoveryProgress: ProgressBar? = null
    private var txtStatus: TextView? = null
    private var containerDiscoveredTvs: LinearLayout? = null

    // IP & Quick Action Buttons
    private var tvIpAddress: EditText? = null
    private var btnConnect: Button? = null
    private var btnQrCode: Button? = null
    private var btnPairingCode: Button? = null
    private var btnKeyboardText: Button? = null
    private var btnRescan: Button? = null

    // Mode Selection Tabs
    private var tabDpad: Button? = null
    private var tabAirMouse: Button? = null
    private var tabTrackpad: Button? = null

    // Mode Panels
    private var panelDpad: LinearLayout? = null
    private var panelAirMouse: LinearLayout? = null
    private var panelTrackpad: LinearLayout? = null

    // Air Mouse & Trackpad Surface Views
    private var viewAirMouseTarget: View? = null
    private var txtAirMouseCoords: TextView? = null
    private var btnRecalibrate: Button? = null
    private var btnAirLeftClick: Button? = null
    private var btnAirRightClick: Button? = null
    private var seekbarSensitivity: SeekBar? = null

    private var viewTrackpadSurface: View? = null
    private var btnTrackLeftClick: Button? = null
    private var btnTrackRightClick: Button? = null

    // Remote D-Pad Control Buttons
    private var btnUp: Button? = null
    private var btnDown: Button? = null
    private var btnLeft: Button? = null
    private var btnRight: Button? = null
    private var btnOk: Button? = null
    private var btnCenterOk: Button? = null
    private var btnBack: Button? = null
    private var btnHome: Button? = null
    private var btnPower: Button? = null
    private var btnVolumeUp: Button? = null
    private var btnVolumeDown: Button? = null
    private var btnMute: Button? = null

    // Controllers & Services
    private var bluetoothController: BluetoothController? = null
    private var discoveryManager: TvDiscoveryManager? = null

    // State Variables
    private var activeTarget = RemoteManager.TargetDeviceType.ANDROID_TV
    private var activeMode = "dpad" // "dpad", "air_mouse", "trackpad"
    private var isConnected = false
    private var connectedDeviceIp = ""

    // Air Mouse / Trackpad Coordinates Tracking
    private var airMousePosX = 0f
    private var airMousePosY = 0f
    private var trackpadPosX = 0f
    private var trackpadPosY = 0f

    // Bluetooth Broadcast Receiver
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "android.bluetooth.device.action.FOUND" -> {
                    Log.d("MainActivity", "Bluetooth device found")
                }
                "android.bluetooth.device.action.PAIRING_REQUEST" -> {
                    Log.d("MainActivity", "Bluetooth pairing request received")
                }
            }
        }
    }

    // Network Discovery Broadcast Receiver
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.vijay.aimagicremote.DISCOVERED") {
                val host = intent.getStringExtra("host") ?: ""
                val name = intent.getStringExtra("name") ?: "TV/PC"
                Log.i("MainActivity", "Discovered device: $name ($host)")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind UI Views
        bindViews()

        // Register Broadcast Receivers
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            btReceiver,
            IntentFilter().apply {
                addAction("android.bluetooth.device.action.FOUND")
                addAction("android.bluetooth.device.action.PAIRING_REQUEST")
            },
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            discoveryReceiver,
            IntentFilter().apply {
                addAction("com.vijay.aimagicremote.DISCOVERED")
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Initialize Bluetooth controller
        bluetoothController = BluetoothController(this)

        // Live Pointer update listener for Air Mouse cursor feedback & dispatch
        bluetoothController?.pointerUpdateListener = { dx, dy ->
            runOnUiThread {
                airMousePosX = (airMousePosX + dx * 2f).coerceIn(-350f, 350f)
                airMousePosY = (airMousePosY + dy * 2f).coerceIn(-200f, 200f)

                txtAirMouseCoords?.text = "Pointer delta: X: ${airMousePosX.toInt()}, Y: ${airMousePosY.toInt()}"

                // Send 3D Gyro Air Mouse motion directly to active TV or Windows PC!
                syncTargetIp()
                RemoteManager.sendPointerMove(dx * 1.8f, dy * 1.8f, bluetoothController)
            }
        }

        // Initialize TV discovery from Application instance
        val app = applicationContext as AppInitializer
        discoveryManager = app.getTvDiscovery()

        // Set up target selector click handlers
        targetTvBtn?.setOnClickListener { v -> triggerHaptic(v); setTargetDevice(RemoteManager.TargetDeviceType.ANDROID_TV) }
        targetPcBtn?.setOnClickListener { v -> triggerHaptic(v); setTargetDevice(RemoteManager.TargetDeviceType.WINDOWS_PC) }
        targetBtBtn?.setOnClickListener { v -> triggerHaptic(v); setTargetDevice(RemoteManager.TargetDeviceType.BLUETOOTH_HID) }

        // Set up manual connect button
        btnConnect?.setOnClickListener { v ->
            triggerHaptic(v)
            if (isConnected) {
                disconnectDevice()
            } else {
                val ip = tvIpAddress?.text?.toString()?.trim()?.removePrefix("/") ?: ""
                if (ip.isNotEmpty() && !ip.startsWith("Enter")) {
                    connectToDevice(ip)
                } else {
                    Toast.makeText(this, "Please select a discovered device or enter a valid IP address", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Quick action buttons
        btnQrCode?.setOnClickListener { v -> triggerHaptic(v); showQrInfoDialog() }
        btnPairingCode?.setOnClickListener { v -> triggerHaptic(v); showPairingCodeDialog() }
        btnKeyboardText?.setOnClickListener { v -> triggerHaptic(v); showKeyboardTextInputDialog() }
        btnRescan?.setOnClickListener { v -> triggerHaptic(v); startWifiDeviceDiscovery() }

        // Recalibrate Gyro Button
        btnRecalibrate?.setOnClickListener { v ->
            triggerHaptic(v)
            showRecalibrateDialog()
        }

        // Mode Tab Handlers
        tabDpad?.setOnClickListener { v -> triggerHaptic(v); switchMode("dpad") }
        tabAirMouse?.setOnClickListener { v -> triggerHaptic(v); switchMode("air_mouse") }
        tabTrackpad?.setOnClickListener { v -> triggerHaptic(v); switchMode("trackpad") }

        // Set up Air Mouse & Trackpad Listeners
        setupAirMouseAndTrackpad()

        // Set up remote control button click listeners
        setupRemoteControlButtons()

        // Check Wi-Fi state & update status bar
        updateWifiNetworkState()

        // Restore last connected IP if saved
        val lastSavedIp = prefs.getString("last_device_ip", "")?.removePrefix("/") ?: ""
        if (lastSavedIp.isNotEmpty()) {
            tvIpAddress?.setText(lastSavedIp)
        }

        // Always enable all remote buttons so they respond immediately
        enableRemoteControls(true)

        // Automatically start Wi-Fi device discovery on launch
        startWifiDeviceDiscovery()
    }

    private fun triggerHaptic(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun bindViews() {
        txtWifiStatus = findViewById(R.id.txt_wifi_status)
        targetTvBtn = findViewById(R.id.target_tv)
        targetPcBtn = findViewById(R.id.target_pc)
        targetBtBtn = findViewById(R.id.target_bt)
        discoveryProgress = findViewById(R.id.discovery_progress)
        txtStatus = findViewById(R.id.txt_status)
        containerDiscoveredTvs = findViewById(R.id.container_discovered_tvs)

        tvIpAddress = findViewById(R.id.tv_ip_address)
        btnConnect = findViewById(R.id.btn_connect)
        btnQrCode = findViewById(R.id.btn_qr_code)
        btnPairingCode = findViewById(R.id.btn_pairing_code)
        btnKeyboardText = findViewById(R.id.btn_keyboard_text)
        btnRescan = findViewById(R.id.btn_rescan)

        tabDpad = findViewById(R.id.tab_dpad)
        tabAirMouse = findViewById(R.id.tab_air_mouse)
        tabTrackpad = findViewById(R.id.tab_trackpad)

        panelDpad = findViewById(R.id.panel_dpad)
        panelAirMouse = findViewById(R.id.panel_air_mouse)
        panelTrackpad = findViewById(R.id.panel_trackpad)

        viewAirMouseTarget = findViewById(R.id.view_air_mouse_target)
        txtAirMouseCoords = findViewById(R.id.txt_air_mouse_coords)
        btnRecalibrate = findViewById(R.id.btn_recalibrate)
        btnAirLeftClick = findViewById(R.id.btn_air_left_click)
        btnAirRightClick = findViewById(R.id.btn_air_right_click)
        seekbarSensitivity = findViewById(R.id.seekbar_sensitivity)

        viewTrackpadSurface = findViewById(R.id.view_trackpad_surface)
        btnTrackLeftClick = findViewById(R.id.btn_track_left_click)
        btnTrackRightClick = findViewById(R.id.btn_track_right_click)

        btnUp = findViewById(R.id.btn_up)
        btnDown = findViewById(R.id.btn_down)
        btnLeft = findViewById(R.id.btn_left)
        btnRight = findViewById(R.id.btn_right)
        btnOk = findViewById(R.id.btn_ok)
        btnCenterOk = findViewById(R.id.btn_center_ok)
        btnBack = findViewById(R.id.btn_back)
        btnHome = findViewById(R.id.btn_home)
        btnPower = findViewById(R.id.btn_power)
        btnVolumeUp = findViewById(R.id.btn_volup)
        btnVolumeDown = findViewById(R.id.btn_voldown)
        btnMute = findViewById(R.id.btn_mute)
    }

    private fun setTargetDevice(type: RemoteManager.TargetDeviceType) {
        activeTarget = type
        RemoteManager.setTargetType(type)

        targetTvBtn?.setBackgroundColor(if (type == RemoteManager.TargetDeviceType.ANDROID_TV) Color.parseColor("#00E676") else Color.parseColor("#282D3C"))
        targetTvBtn?.setTextColor(if (type == RemoteManager.TargetDeviceType.ANDROID_TV) Color.parseColor("#0F1115") else Color.parseColor("#A0A5B5"))

        targetPcBtn?.setBackgroundColor(if (type == RemoteManager.TargetDeviceType.WINDOWS_PC) Color.parseColor("#00E676") else Color.parseColor("#282D3C"))
        targetPcBtn?.setTextColor(if (type == RemoteManager.TargetDeviceType.WINDOWS_PC) Color.parseColor("#0F1115") else Color.parseColor("#A0A5B5"))

        targetBtBtn?.setBackgroundColor(if (type == RemoteManager.TargetDeviceType.BLUETOOTH_HID) Color.parseColor("#00E676") else Color.parseColor("#282D3C"))
        targetBtBtn?.setTextColor(if (type == RemoteManager.TargetDeviceType.BLUETOOTH_HID) Color.parseColor("#0F1115") else Color.parseColor("#A0A5B5"))

        val targetName = when (type) {
            RemoteManager.TargetDeviceType.ANDROID_TV -> "Android TV"
            RemoteManager.TargetDeviceType.WINDOWS_PC -> "Windows PC"
            RemoteManager.TargetDeviceType.BLUETOOTH_HID -> "Bluetooth HID"
            else -> "TV"
        }
        txtStatus?.text = "Target Mode: $targetName. Ready to send commands."

        discoveryManager?.let {
            updateDiscoveredDeviceList(it.getDiscoveredDevices())
        }
    }

    private fun switchMode(mode: String) {
        activeMode = mode

        panelDpad?.visibility = if (mode == "dpad") View.VISIBLE else View.GONE
        panelAirMouse?.visibility = if (mode == "air_mouse") View.VISIBLE else View.GONE
        panelTrackpad?.visibility = if (mode == "trackpad") View.VISIBLE else View.GONE

        tabDpad?.setBackgroundColor(if (mode == "dpad") Color.parseColor("#00E676") else Color.parseColor("#282D3C"))
        tabDpad?.setTextColor(if (mode == "dpad") Color.parseColor("#0F1115") else Color.parseColor("#A0A5B5"))

        tabAirMouse?.setBackgroundColor(if (mode == "air_mouse") Color.parseColor("#00E676") else Color.parseColor("#282D3C"))
        tabAirMouse?.setTextColor(if (mode == "air_mouse") Color.parseColor("#0F1115") else Color.parseColor("#A0A5B5"))

        tabTrackpad?.setBackgroundColor(if (mode == "trackpad") Color.parseColor("#00E676") else Color.parseColor("#282D3C"))
        tabTrackpad?.setTextColor(if (mode == "trackpad") Color.parseColor("#0F1115") else Color.parseColor("#A0A5B5"))

        if (mode == "air_mouse") {
            airMousePosX = 0f
            airMousePosY = 0f

            val started = bluetoothController?.startAirMouse() ?: false
            if (started) {
                Toast.makeText(this, "Air Mouse Active - Tilt phone to move pointer", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Gyroscope activated - move phone to guide pointer", Toast.LENGTH_SHORT).show()
            }
        } else {
            bluetoothController?.stopAirMouse()
        }

        if (mode == "trackpad") {
            trackpadPosX = 0f
            trackpadPosY = 0f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAirMouseAndTrackpad() {
        viewAirMouseTarget?.setOnClickListener { v ->
            triggerHaptic(v)
            syncTargetIp()
            RemoteManager.sendClick(isRightClick = false)
        }

        btnAirLeftClick?.setOnClickListener { v ->
            triggerHaptic(v)
            syncTargetIp()
            RemoteManager.sendClick(isRightClick = false)
        }

        btnAirRightClick?.setOnClickListener { v ->
            triggerHaptic(v)
            syncTargetIp()
            RemoteManager.sendClick(isRightClick = true)
        }

        btnTrackLeftClick?.setOnClickListener { v ->
            triggerHaptic(v)
            syncTargetIp()
            RemoteManager.sendClick(isRightClick = false)
        }

        btnTrackRightClick?.setOnClickListener { v ->
            triggerHaptic(v)
            syncTargetIp()
            RemoteManager.sendClick(isRightClick = true)
        }

        seekbarSensitivity?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sens = (progress / 50.0f).coerceIn(0.2f, 3.5f)
                bluetoothController?.setSensitivity(sens)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        var lastTouchX = 0f
        var lastTouchY = 0f
        var touchDownTime = 0L
        var initialTouchX = 0f
        var initialTouchY = 0f

        viewTrackpadSurface?.setOnTouchListener { v, event ->
            val action = event.actionMasked
            val pointerCount = event.pointerCount

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    triggerHaptic(v)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    initialTouchX = event.x
                    initialTouchY = event.y
                    touchDownTime = System.currentTimeMillis()
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    lastTouchY = event.y
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY

                    syncTargetIp()

                    if (pointerCount == 1) {
                        if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                            trackpadPosX = (trackpadPosX + dx * 1.5f).coerceIn(-400f, 400f)
                            trackpadPosY = (trackpadPosY + dy * 1.5f).coerceIn(-250f, 250f)

                            RemoteManager.sendPointerMove(dx * 2.5f, dy * 2.5f, bluetoothController)
                        }
                    } else if (pointerCount >= 2) {
                        if (abs(dy) > 1.0f) {
                            RemoteManager.sendScroll(0f, dy * 1.5f)
                        }
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                }

                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchDownTime
                    val distMoved = abs(event.x - initialTouchX) + abs(event.y - initialTouchY)

                    if (duration < 250 && distMoved < 15) {
                        triggerHaptic(v)
                        syncTargetIp()
                        RemoteManager.sendClick(isRightClick = false)
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (pointerCount == 2) {
                        val duration = System.currentTimeMillis() - touchDownTime
                        if (duration < 300) {
                            triggerHaptic(v)
                            syncTargetIp()
                            RemoteManager.sendClick(isRightClick = true)
                        }
                    }
                }
            }
            true
        }
    }

    private fun updateWifiNetworkState() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (isWifi) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo.ssid?.replace("\"", "") ?: "Wi-Fi"
            txtWifiStatus?.text = "● Connected ($ssid)"
            txtWifiStatus?.setTextColor(Color.parseColor("#00E676"))
        } else {
            txtWifiStatus?.text = "○ Wi-Fi Disconnected"
            txtWifiStatus?.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun startWifiDeviceDiscovery() {
        discoveryProgress?.visibility = View.VISIBLE
        txtStatus?.text = "Scanning network for Android TVs and Windows PCs..."
        containerDiscoveredTvs?.removeAllViews()

        discoveryManager?.startDiscovery { deviceList ->
            runOnUiThread {
                discoveryProgress?.visibility = View.GONE
                if (deviceList.isEmpty()) {
                    txtStatus?.text = "No devices automatically discovered. Enter IP or Code to connect."
                } else {
                    val filteredCount = deviceList.count { matchesActiveTarget(it) }
                    txtStatus?.text = "Found $filteredCount matching device(s) on local Wi-Fi:"
                    updateDiscoveredDeviceList(deviceList)
                }
            }
        }
    }

    private fun matchesActiveTarget(dev: TvDiscoveryManager.DeviceInfo): Boolean {
        return when (activeTarget) {
            RemoteManager.TargetDeviceType.WINDOWS_PC -> dev.deviceType == TvDiscoveryManager.DeviceType.WINDOWS_PC
            RemoteManager.TargetDeviceType.ANDROID_TV -> dev.deviceType == TvDiscoveryManager.DeviceType.ANDROID_TV
            else -> true
        }
    }

    private fun updateDiscoveredDeviceList(deviceList: List<TvDiscoveryManager.DeviceInfo>) {
        containerDiscoveredTvs?.removeAllViews()

        val filteredList = deviceList.filter { matchesActiveTarget(it) }

        for (dev in filteredList) {
            val cleanHost = dev.host.removePrefix("/")
            val devCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(18, 14, 18, 14)
                setBackgroundColor(Color.parseColor("#151821"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
            }

            val devDetails = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameView = TextView(this).apply {
                text = "${dev.name} (${if (dev.deviceType == TvDiscoveryManager.DeviceType.WINDOWS_PC) "Windows PC" else "Android TV"})"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val ipView = TextView(this).apply {
                text = "IP: $cleanHost • Port: ${dev.port}"
                setTextColor(Color.parseColor("#A0A5B5"))
                textSize = 12f
            }

            devDetails.addView(nameView)
            devDetails.addView(ipView)

            val isThisConnected = isConnected && connectedDeviceIp == cleanHost
            val connectBtn = Button(this).apply {
                text = if (isThisConnected) "Connected" else "Connect"
                setBackgroundColor(if (isThisConnected) Color.parseColor("#00E676") else Color.parseColor("#282D3C"))
                setTextColor(if (isThisConnected) Color.parseColor("#0F1115") else Color.WHITE)
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener { v ->
                    triggerHaptic(v)
                    if (dev.deviceType == TvDiscoveryManager.DeviceType.WINDOWS_PC) {
                        setTargetDevice(RemoteManager.TargetDeviceType.WINDOWS_PC)
                    } else {
                        setTargetDevice(RemoteManager.TargetDeviceType.ANDROID_TV)
                    }
                    connectToDevice(cleanHost)
                }
            }

            devCard.addView(devDetails)
            devCard.addView(connectBtn)

            containerDiscoveredTvs?.addView(devCard)
        }
    }

    private fun showQrInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("QR Pairing Information")
            .setMessage("Direct TV QR Code scanning requires the Air Remote Companion receiver app on TV OS.\n\nFor stock Google TV & Android TV, tap 'TV Code' to pair using the official 6-digit TV PIN.")
            .setPositiveButton("Use TV Code") { dialog, _ ->
                dialog.dismiss()
                showPairingCodeDialog()
            }
            .setNegativeButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showRecalibrateDialog() {
        AlertDialog.Builder(this)
            .setTitle("Air Mouse 2-Second Calibration Wizard")
            .setMessage("Place your phone flat on a table or hold it completely still in your neutral hand position, then tap 'Start Sampling' below.\n\nThe app will collect gyro bias samples for 2 seconds to zero out drift.")
            .setPositiveButton("Start Sampling") { dialog, _ ->
                txtStatus?.text = "Calibrating Gyroscope... Hold phone still for 2 seconds."
                Toast.makeText(this, "Hold phone completely still...", Toast.LENGTH_SHORT).show()
                bluetoothController?.startNeutralPoseCalibration { success, msg ->
                    txtStatus?.text = msg
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showPairingCodeDialog() {
        val targetIp = tvIpAddress?.text?.toString()?.trim()?.removePrefix("/") ?: "192.168.1.4"

        Atrpv2Protocol.startPairing(targetIp) { success, msg ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }

        val input = EditText(this).apply {
            hint = "Enter 6-digit code shown on TV"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        AlertDialog.Builder(this)
            .setTitle("Enter TV 6-Digit Pairing Code")
            .setMessage("Air Remote sent a pairing request to TV ($targetIp).\nCheck your TV screen for the 6-digit code and enter it below:")
            .setView(input)
            .setPositiveButton("Verify & Connect") { dialog, _ ->
                val code = input.text.toString().trim()
                if (code.length == 6) {
                    Atrpv2Protocol.submitPairingCode(code) { success, msg ->
                        runOnUiThread {
                            if (success) {
                                connectToDevice(targetIp)
                            } else {
                                txtStatus?.text = "Pairing Failed: $msg"
                                showConnectionGuidanceDialog(targetIp, msg)
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Please enter a valid 6-digit code", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showConnectionGuidanceDialog(ip: String, errorText: String) {
        val isTv = activeTarget == RemoteManager.TargetDeviceType.ANDROID_TV
        val message = if (isTv) {
            "Could not complete Wi-Fi pairing to Android TV ($ip).\n\n" +
            "1. Enable 'Control by Mobile App' in TV Settings -> Network & Internet.\n" +
            "2. Allow 'Android TV Remote Service' in TV Settings -> Apps -> Special Access.\n" +
            "3. Or select 'Bluetooth HID' mode at the top to pair directly as a Bluetooth Remote."
        } else {
            "Could not connect to Windows PC ($ip:9090).\n\n" +
            "1. Ensure 'pc_receiver.ps1' is running on your PC.\n" +
            "2. Allow Port 9090 in Windows Defender Firewall.\n" +
            "3. Verify phone and PC are connected to the same Wi-Fi router."
        }

        AlertDialog.Builder(this)
            .setTitle("Connection Guidance")
            .setMessage(message)
            .setPositiveButton("Bluetooth Mode") { dialog, _ ->
                setTargetDevice(RemoteManager.TargetDeviceType.BLUETOOTH_HID)
                dialog.dismiss()
            }
            .setNegativeButton("Dismiss") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showKeyboardTextInputDialog() {
        val input = EditText(this).apply {
            hint = "Type text to send to TV or Windows PC"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        AlertDialog.Builder(this)
            .setTitle("Send Text Input")
            .setMessage("Type text below to send directly to your TV or Windows PC:")
            .setView(input)
            .setPositiveButton("Send") { dialog, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    syncTargetIp()
                    RemoteManager.sendText(text)
                    Toast.makeText(this, "Sent text: '$text'", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun setupRemoteControlButtons() {
        btnUp?.setOnClickListener { v -> triggerHaptic(v); onBtnUpClick() }
        btnDown?.setOnClickListener { v -> triggerHaptic(v); onBtnDownClick() }
        btnLeft?.setOnClickListener { v -> triggerHaptic(v); onBtnLeftClick() }
        btnRight?.setOnClickListener { v -> triggerHaptic(v); onBtnRightClick() }
        btnOk?.setOnClickListener { v -> triggerHaptic(v); onBtnOkClick() }
        btnCenterOk?.setOnClickListener { v -> triggerHaptic(v); onBtnOkClick() }
        btnBack?.setOnClickListener { v -> triggerHaptic(v); onBtnBackClick() }
        btnHome?.setOnClickListener { v -> triggerHaptic(v); onBtnHomeClick() }
        btnPower?.setOnClickListener { v -> triggerHaptic(v); onBtnPowerClick() }
        btnVolumeUp?.setOnClickListener { v -> triggerHaptic(v); onBtnVolumeUpClick() }
        btnVolumeDown?.setOnClickListener { v -> triggerHaptic(v); onBtnVolumeDownClick() }
        btnMute?.setOnClickListener { v -> triggerHaptic(v); onBtnMuteClick() }
    }

    private fun syncTargetIp() {
        val ip = tvIpAddress?.text?.toString()?.trim()?.removePrefix("/") ?: ""
        if (ip.isNotEmpty() && !ip.startsWith("Enter")) {
            RemoteManager.currentTargetIp = ip
            PcRemoteProtocol.currentPcIp = ip
            Atrpv2Protocol.currentTvIp = ip
        }
    }

    private fun connectToDevice(ip: String) {
        val cleanIp = ip.removePrefix("/")
        connectedDeviceIp = cleanIp
        tvIpAddress?.setText(cleanIp)
        txtStatus?.text = "Connecting to $cleanIp..."
        btnConnect?.isEnabled = false

        RemoteManager.connect(
            this,
            activeTarget,
            cleanIp,
            bluetoothController,
            object : RemoteManager.ConnectCallback {
                override fun onConnected() {
                    runOnUiThread {
                        isConnected = true
                        btnConnect?.isEnabled = true
                        btnConnect?.text = "Disconnect"
                        val deviceTypeStr = when (activeTarget) {
                            RemoteManager.TargetDeviceType.ANDROID_TV -> "Android TV"
                            RemoteManager.TargetDeviceType.WINDOWS_PC -> "Windows PC"
                            RemoteManager.TargetDeviceType.BLUETOOTH_HID -> "Bluetooth HID"
                            else -> "TV"
                        }
                        txtStatus?.text = "Connected to $deviceTypeStr ($cleanIp)"
                        prefs.edit().putString("last_device_ip", cleanIp).apply()
                        enableRemoteControls(true)
                    }
                }

                override fun onConnectionFailed(error: String) {
                    runOnUiThread {
                        isConnected = false
                        btnConnect?.isEnabled = true
                        btnConnect?.text = "Connect"
                        txtStatus?.text = "Connection Failed: $error"
                        showConnectionGuidanceDialog(cleanIp, error)
                        enableRemoteControls(true)
                    }
                }

                override fun onDisconnected() {
                    runOnUiThread {
                        isConnected = false
                        btnConnect?.isEnabled = true
                        btnConnect?.text = "Connect"
                        txtStatus?.text = "Disconnected from device"
                        enableRemoteControls(true)
                    }
                }
            }
        )
    }

    private fun disconnectDevice() {
        RemoteManager.disconnect()
        isConnected = false
        btnConnect?.text = "Connect"
        txtStatus?.text = "Disconnected"
        enableRemoteControls(true)
    }

    private fun enableRemoteControls(enabled: Boolean) {
        val buttons = listOf(
            btnUp, btnDown, btnLeft, btnRight, btnOk, btnCenterOk,
            btnBack, btnHome, btnPower, btnVolumeUp, btnVolumeDown, btnMute
        )
        buttons.forEach { it?.isEnabled = true }
    }

    // --- Remote Control Button Handlers ---

    fun onBtnUpClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.UP, "UP")
    }

    fun onBtnDownClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.DOWN, "DOWN")
    }

    fun onBtnLeftClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.LEFT, "LEFT")
    }

    fun onBtnRightClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.RIGHT, "RIGHT")
    }

    fun onBtnOkClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.OK, "ENTER")
    }

    fun onBtnBackClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.BACK, "ESCAPE")
    }

    fun onBtnHomeClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.HOME, "WIN")
    }

    fun onBtnPowerClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.POWER, "POWER")
    }

    fun onBtnVolumeUpClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.VOLUME_UP, "VOL_UP")
    }

    fun onBtnVolumeDownClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.VOLUME_DOWN, "VOL_DOWN")
    }

    fun onBtnMuteClick() {
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.MUTE, "MUTE")
    }

    override fun onPause() {
        super.onPause()
        bluetoothController?.stopAirMouse()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(btReceiver)
        unregisterReceiver(discoveryReceiver)
        bluetoothController?.shutdown()
        RemoteManager.disconnect()
    }
}
