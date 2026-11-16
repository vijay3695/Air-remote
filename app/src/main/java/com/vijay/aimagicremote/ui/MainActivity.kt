package com.vijay.aimagicremote.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vijay.aimagicremote.AppInitializer
import com.vijay.aimagicremote.R
import com.vijay.aimagicremote.bluetooth.BluetoothController
import com.vijay.aimagicremote.bluetooth.BluetoothHidController
import com.vijay.aimagicremote.discovery.TvDiscoveryManager
import com.vijay.aimagicremote.protocol.Atrpv2Protocol
import com.vijay.aimagicremote.protocol.LgWebOsProtocol
import com.vijay.aimagicremote.protocol.PcRemoteProtocol
import com.vijay.aimagicremote.protocol.RemoteManager
import com.vijay.aimagicremote.protocol.SamsungTizenProtocol
import kotlin.math.abs

/**
 * Main Activity for Air Remote App
 *
 * Truthful status handling:
 * - A target only reaches "connected" after its real handshake succeeds.
 * - Remote buttons are disabled unless the app is connected to the selected target.
 * - Pointer actions are dispatched only through protocols that expose a real
 *   pointer transport; otherwise motion is exposed as D-pad navigation keys.
 */
class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("air_remote_prefs", Context.MODE_PRIVATE) }

    // UI Header & Status Components
    private var txtWifiStatus: TextView? = null
    private var drawerLayout: androidx.drawerlayout.widget.DrawerLayout? = null
    private var navView: com.google.android.material.navigation.NavigationView? = null
    private var btnMenu: android.widget.ImageButton? = null
    private var btnToggleControls: Button? = null
    private var panelControls: LinearLayout? = null
    private var txtCompactStatus: TextView? = null
    private var targetTvBtn: Button? = null
    private var targetPcBtn: Button? = null
    private var targetBtBtn: Button? = null
    private var targetSamsungBtn: Button? = null
    private var targetLgBtn: Button? = null
    private var targetRokuBtn: Button? = null
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
    private var btnAirBack: Button? = null
    private var btnAirHome: Button? = null
    private var btnAirOk: Button? = null
    private var btnAirVolUp: Button? = null
    private var btnAirVolDown: Button? = null
    private var btnAirMute: Button? = null

    private var btnTrackBack: Button? = null
    private var btnTrackHome: Button? = null
    private var btnTrackOk: Button? = null
    private var btnTrackVolUp: Button? = null
    private var btnTrackVolDown: Button? = null
    private var btnTrackMute: Button? = null
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

        bindViews()

        // Hamburger drawer
        btnMenu?.setOnClickListener { drawerLayout?.openDrawer(androidx.core.view.GravityCompat.START) }
        btnToggleControls?.setOnClickListener {
            val visible = panelControls?.visibility != View.GONE
            panelControls?.visibility = if (visible) View.GONE else View.VISIBLE
            txtCompactStatus?.visibility = if (visible) View.VISIBLE else View.GONE
            btnToggleControls?.text = if (visible) "Show" else "Hide"
        }
        navView?.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_devices -> { }
                R.id.nav_manual -> showManualConnectDialog()
                R.id.nav_tv_code -> showPairingCodeDialog()
                R.id.nav_keyboard -> showKeyboardTextInputDialog()
                R.id.nav_qr -> showQrInfoDialog()
                R.id.nav_settings -> showSettingsDialog()
                R.id.nav_faq -> startActivity(Intent(this, HelpFaqActivity::class.java))
                R.id.nav_about -> showAboutDialog()
            }
            drawerLayout?.closeDrawers()
            true
        }

        // Register Broadcast Receivers
        registerReceiverCompat(
            btReceiver,
            IntentFilter().apply {
                addAction("android.bluetooth.device.action.FOUND")
                addAction("android.bluetooth.device.action.PAIRING_REQUEST")
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        registerReceiverCompat(
            discoveryReceiver,
            IntentFilter().apply {
                addAction("com.vijay.aimagicremote.DISCOVERED")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Initialize Bluetooth controller
        bluetoothController = BluetoothController(this)

        // Live Pointer update listener for Air Mouse cursor feedback & dispatch
        bluetoothController?.pointerUpdateListener = { dx, dy ->
            runOnUiThread {
                airMousePosX = (airMousePosX + dx).coerceIn(-350f, 350f)
                airMousePosY = (airMousePosY + dy).coerceIn(-200f, 200f)

                txtAirMouseCoords?.text = "Pointer delta: X: ${airMousePosX.toInt()}, Y: ${airMousePosY.toInt()}"

                // Send 3D Gyro Air Mouse motion to the selected target.
                syncTargetIp()
                RemoteManager.sendPointerMove(dx, dy)
            }
        }

        // Initialize TV discovery from Application instance
        val app = applicationContext as AppInitializer
        discoveryManager = app.getTvDiscovery()

        // Set up target selector click handlers
        targetTvBtn?.setOnClickListener { v -> triggerHaptic(v); setTargetDevice(RemoteManager.TargetDeviceType.ANDROID_TV) }
        targetPcBtn?.setOnClickListener { v -> triggerHaptic(v); setTargetDevice(RemoteManager.TargetDeviceType.WINDOWS_PC) }
        targetBtBtn?.setOnClickListener { v ->
            triggerHaptic(v)
            val perms = arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE)
            if (perms.any { ActivityCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                ActivityCompat.requestPermissions(this, perms, 1001)
            }
            setTargetDevice(RemoteManager.TargetDeviceType.BLUETOOTH_HID)
            BluetoothHidController.start(this)
            isConnected = BluetoothHidController.isConnected()
            txtStatus?.text = if (isConnected) "Bluetooth HID connected" else "Bluetooth HID advertising. Pair this phone from the target device."
            refreshControlAvailability()
            Toast.makeText(this, if (isConnected) "Bluetooth HID connected" else "Pair this phone as a Bluetooth keyboard/mouse on the target", Toast.LENGTH_LONG).show()
        }
        targetSamsungBtn?.setOnClickListener { v -> triggerHaptic(v); setTargetDevice(RemoteManager.TargetDeviceType.SAMSUNG_TIZEN) }
        targetLgBtn?.setOnClickListener { v -> triggerHaptic(v); setTargetDevice(RemoteManager.TargetDeviceType.LG_WEBOS) }
        targetRokuBtn?.setOnClickListener { v -> triggerHaptic(v); setTargetDevice(RemoteManager.TargetDeviceType.ROKU_TCL) }

        // Set up manual connect button
        btnConnect?.setOnClickListener { v ->
            triggerHaptic(v)
            if (isConnected) {
                disconnectDevice()
            } else {
                val raw = tvIpAddress?.text?.toString()?.trim()?.removePrefix("/") ?: ""
                if (raw.isNotEmpty() && !raw.startsWith("Enter")) {
                    val (ip, port) = if (raw.contains(":")) {
                        val parts = raw.split(":", limit = 2)
                        parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                    } else {
                        raw to 0
                    }
                    connectToDevice(ip, port)
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
        setupPointerSecondaryButtons()

        // Set up remote control button click listeners
        setupRemoteControlButtons()

        // Check Wi-Fi state & update status bar
        updateWifiNetworkState()

        // Restore last connected IP if saved
        val lastSavedIp = prefs.getString("last_device_ip", "")?.removePrefix("/") ?: ""
        if (lastSavedIp.isNotEmpty()) {
            tvIpAddress?.setText(lastSavedIp)
        }

        // Automatically start Wi-Fi device discovery on launch
        startWifiDeviceDiscovery()

        // Reflect the initial (disconnected) state in the UI.
        refreshControlAvailability()
    }

    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter, flags: Int) {
        try {
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                flags
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to register receiver", e)
        }
    }

    private fun triggerHaptic(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        btnMenu = findViewById(R.id.btn_menu)
        btnToggleControls = findViewById(R.id.btn_toggle_controls)
        panelControls = findViewById(R.id.panel_controls)
        txtCompactStatus = findViewById(R.id.txt_compact_status)
        txtWifiStatus = findViewById(R.id.txt_wifi_status)
        targetTvBtn = findViewById(R.id.target_tv)
        targetPcBtn = findViewById(R.id.target_pc)
        targetBtBtn = findViewById(R.id.target_bt)
        targetSamsungBtn = findViewById(R.id.target_samsung)
        targetLgBtn = findViewById(R.id.target_lg)
        targetRokuBtn = findViewById(R.id.target_roku)
        discoveryProgress = findViewById(R.id.discovery_progress)
        txtStatus = findViewById(R.id.txt_status)
        containerDiscoveredTvs = findViewById(R.id.container_discovered_tvs)

        tvIpAddress = null
        btnConnect = findViewById(R.id.btn_connect)
        btnQrCode = null
        btnPairingCode = null
        btnKeyboardText = null
        btnRescan = findViewById(R.id.btn_rescan)

        tabDpad = findViewById(R.id.tab_dpad)
        tabAirMouse = findViewById(R.id.tab_air_mouse)
        tabTrackpad = findViewById(R.id.tab_trackpad)

        panelDpad = findViewById(R.id.panel_dpad)
        panelAirMouse = findViewById(R.id.panel_air_mouse)
        panelTrackpad = findViewById(R.id.panel_trackpad)

        viewAirMouseTarget = findViewById(R.id.view_air_mouse_target)
        txtAirMouseCoords = findViewById(R.id.txt_air_mouse_coords)
        btnRecalibrate = null
        btnAirLeftClick = findViewById(R.id.btn_air_left_click)
        btnAirRightClick = findViewById(R.id.btn_air_right_click)
        btnAirBack = findViewById(R.id.btn_air_back)
        btnAirHome = findViewById(R.id.btn_air_home)
        btnAirOk = findViewById(R.id.btn_air_ok)
        btnAirVolUp = findViewById(R.id.btn_air_volup)
        btnAirVolDown = findViewById(R.id.btn_air_voldown)
        btnAirMute = findViewById(R.id.btn_air_mute)

        btnTrackLeftClick = findViewById(R.id.btn_track_left_click)
        btnTrackRightClick = findViewById(R.id.btn_track_right_click)
        btnTrackBack = findViewById(R.id.btn_track_back)
        btnTrackHome = findViewById(R.id.btn_track_home)
        btnTrackOk = findViewById(R.id.btn_track_ok)
        btnTrackVolUp = findViewById(R.id.btn_track_volup)
        btnTrackVolDown = findViewById(R.id.btn_track_voldown)
        btnTrackMute = findViewById(R.id.btn_track_mute)
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

        // Clear the previous session state; only the newly selected protocol may set it again.
        isConnected = false
        connectedDeviceIp = ""

        targetTvBtn?.setBackgroundColor(
            if (type == RemoteManager.TargetDeviceType.ANDROID_TV) highlightColor() else inactiveColor()
        )
        targetTvBtn?.setTextColor(
            if (type == RemoteManager.TargetDeviceType.ANDROID_TV) onHighlightColor() else inactiveTextColor()
        )

        targetPcBtn?.setBackgroundColor(
            if (type == RemoteManager.TargetDeviceType.WINDOWS_PC) highlightColor() else inactiveColor()
        )
        targetPcBtn?.setTextColor(
            if (type == RemoteManager.TargetDeviceType.WINDOWS_PC) onHighlightColor() else inactiveTextColor()
        )

        targetBtBtn?.setBackgroundColor(
            if (type == RemoteManager.TargetDeviceType.BLUETOOTH_HID) highlightColor() else inactiveColor()
        )
        targetBtBtn?.setTextColor(
            if (type == RemoteManager.TargetDeviceType.BLUETOOTH_HID) onHighlightColor() else inactiveTextColor()
        )

        targetSamsungBtn?.setBackgroundColor(
            if (type == RemoteManager.TargetDeviceType.SAMSUNG_TIZEN) highlightColor() else inactiveColor()
        )
        targetSamsungBtn?.setTextColor(
            if (type == RemoteManager.TargetDeviceType.SAMSUNG_TIZEN) onHighlightColor() else inactiveTextColor()
        )

        targetLgBtn?.setBackgroundColor(
            if (type == RemoteManager.TargetDeviceType.LG_WEBOS) highlightColor() else inactiveColor()
        )
        targetLgBtn?.setTextColor(
            if (type == RemoteManager.TargetDeviceType.LG_WEBOS) onHighlightColor() else inactiveTextColor()
        )

        targetRokuBtn?.setBackgroundColor(
            if (type == RemoteManager.TargetDeviceType.ROKU_TCL) highlightColor() else inactiveColor()
        )
        targetRokuBtn?.setTextColor(
            if (type == RemoteManager.TargetDeviceType.ROKU_TCL) onHighlightColor() else inactiveTextColor()
        )

        val targetName = when (type) {
            RemoteManager.TargetDeviceType.ANDROID_TV -> "Android TV / Google TV"
            RemoteManager.TargetDeviceType.SAMSUNG_TIZEN -> "Samsung Tizen"
            RemoteManager.TargetDeviceType.LG_WEBOS -> "LG webOS"
            RemoteManager.TargetDeviceType.ROKU_TCL -> "Roku / TCL"
            RemoteManager.TargetDeviceType.WINDOWS_PC -> "Windows PC"
            RemoteManager.TargetDeviceType.BLUETOOTH_HID -> "Bluetooth HID"
        }
        val capabilityText = when (type) {
            RemoteManager.TargetDeviceType.WINDOWS_PC -> "Windows PC pointer control available."
            RemoteManager.TargetDeviceType.LG_WEBOS -> "LG webOS pointer channel available."
            RemoteManager.TargetDeviceType.BLUETOOTH_HID -> "Bluetooth HID experimental. Pair this phone from the target device."
            else -> "Using the available TV remote key/pointer transport."
        }
        txtStatus?.text = "Target Mode: $targetName. ${capabilityText}"

        discoveryManager?.let {
            updateDiscoveredDeviceList(it.getDiscoveredDevices())
        }
    }

    private fun highlightColor(): Int = intColor(0xFF00E676)
    private fun onHighlightColor(): Int = intColor(0xFF0F1115)
    private fun inactiveColor(): Int = intColor(0xFF282D3C)
    private fun inactiveTextColor(): Int = intColor(0xFFA0A5B5)

    private fun intColor(hex: Long): Int = android.graphics.Color.parseColor("#%06X".format(hex))

    private fun switchMode(mode: String) {
        activeMode = mode

        panelDpad?.visibility = if (mode == "dpad") View.VISIBLE else View.GONE
        panelAirMouse?.visibility = if (mode == "air_mouse") View.VISIBLE else View.GONE
        panelTrackpad?.visibility = if (mode == "trackpad") View.VISIBLE else View.GONE

        tabDpad?.setBackgroundColor(if (mode == "dpad") highlightColor() else inactiveColor())
        tabDpad?.setTextColor(if (mode == "dpad") onHighlightColor() else inactiveTextColor())

        tabAirMouse?.setBackgroundColor(if (mode == "air_mouse") highlightColor() else inactiveColor())
        tabAirMouse?.setTextColor(if (mode == "air_mouse") onHighlightColor() else inactiveTextColor())

        tabTrackpad?.setBackgroundColor(if (mode == "trackpad") highlightColor() else inactiveColor())
        tabTrackpad?.setTextColor(if (mode == "trackpad") onHighlightColor() else inactiveTextColor())

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

        refreshControlAvailability()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAirMouseAndTrackpad() {
        var airDownTime = 0L
        var airDownX = 0f
        var airDownY = 0f
        viewAirMouseTarget?.setOnTouchListener { v, event ->
            if (activeMode != "air_mouse") return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    triggerHaptic(v)
                    airDownTime = System.currentTimeMillis()
                    airDownX = event.x
                    airDownY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - airDownTime
                    val moved = kotlin.math.abs(event.x - airDownX) + kotlin.math.abs(event.y - airDownY)
                    if (duration < 400 && moved < 40) {
                        if (!isConnected) {
                            Toast.makeText(this@MainActivity, "Connect a device first", Toast.LENGTH_SHORT).show()
                        } else {
                            syncTargetIp()
                            if (activeTarget == RemoteManager.TargetDeviceType.WINDOWS_PC) {
                                RemoteManager.sendClick(isRightClick = false)
                                Toast.makeText(this@MainActivity, "Click sent", Toast.LENGTH_SHORT).show()
                            } else if (activeTarget == RemoteManager.TargetDeviceType.ANDROID_TV) {
                                Atrpv2Protocol.sendKey(Atrpv2Protocol.KEY_CODES.OK) { err ->
                                    runOnUiThread {
                                        if (err == null) Toast.makeText(this@MainActivity, "OK sent", Toast.LENGTH_SHORT).show()
                                        else Toast.makeText(this@MainActivity, "Failed: $err", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.OK, "ENTER")
                                Toast.makeText(this@MainActivity, "Key sent", Toast.LENGTH_SHORT).show()
                            }
                            Toast.makeText(this@MainActivity, "Click sent", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        btnAirLeftClick?.setOnClickListener { v ->
            triggerHaptic(v)
            if (activeMode != "air_mouse") return@setOnClickListener
            syncTargetIp()
            RemoteManager.sendClick(isRightClick = false)
        }

        btnAirRightClick?.setOnClickListener { v ->
            triggerHaptic(v)
            if (activeMode != "air_mouse") return@setOnClickListener
            syncTargetIp()
            RemoteManager.sendClick(isRightClick = true)
        }

        btnTrackLeftClick?.setOnClickListener { v ->
            triggerHaptic(v)
            if (activeMode != "trackpad") return@setOnClickListener
            syncTargetIp()
            RemoteManager.sendClick(isRightClick = false)
        }

        btnTrackRightClick?.setOnClickListener { v ->
            triggerHaptic(v)
            if (activeMode != "trackpad") return@setOnClickListener
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
            if (activeMode != "trackpad") return@setOnTouchListener false
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

                    if (pointerCount == 1) {
                        if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                            trackpadPosX = (trackpadPosX + dx * 1.5f).coerceIn(-400f, 400f)
                            trackpadPosY = (trackpadPosY + dy * 1.5f).coerceIn(-250f, 250f)

                            syncTargetIp()
                            RemoteManager.sendPointerMove(dx * 2.5f, dy * 2.5f)
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

                    if (duration < 400 && distMoved < 40) {
                        triggerHaptic(v)
                        if (!isConnected) {
                            Toast.makeText(this@MainActivity, "Connect a device first", Toast.LENGTH_SHORT).show()
                        } else {
                            syncTargetIp()
                            if (activeTarget == RemoteManager.TargetDeviceType.WINDOWS_PC) {
                                RemoteManager.sendClick(isRightClick = false)
                                Toast.makeText(this@MainActivity, "Click sent", Toast.LENGTH_SHORT).show()
                            } else if (activeTarget == RemoteManager.TargetDeviceType.ANDROID_TV) {
                                Atrpv2Protocol.sendKey(Atrpv2Protocol.KEY_CODES.OK) { err ->
                                    runOnUiThread {
                                        if (err == null) Toast.makeText(this@MainActivity, "OK sent", Toast.LENGTH_SHORT).show()
                                        else Toast.makeText(this@MainActivity, "Failed: $err", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.OK, "ENTER")
                                Toast.makeText(this@MainActivity, "Key sent", Toast.LENGTH_SHORT).show()
                            }
                            Toast.makeText(this@MainActivity, "Click sent", Toast.LENGTH_SHORT).show()
                        }
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
            var ssid = wifiInfo.ssid?.replace("\"", "") ?: ""
            if (ssid.isBlank() || ssid == "<unknown ssid>") {
                // Android 12+ may hide SSID without location permission.
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1002)
                }
                ssid = "Wi-Fi"
            }
            txtWifiStatus?.text = "● Connected ($ssid)"
            txtWifiStatus?.setTextColor(intColor(0xFF00E676))
        } else {
            txtWifiStatus?.text = "○ Wi-Fi Disconnected"
            txtWifiStatus?.setTextColor(intColor(0xFFFF5252))
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
            RemoteManager.TargetDeviceType.SAMSUNG_TIZEN -> dev.deviceType == TvDiscoveryManager.DeviceType.SAMSUNG_TIZEN
            RemoteManager.TargetDeviceType.LG_WEBOS -> dev.deviceType == TvDiscoveryManager.DeviceType.LG_WEBOS
            RemoteManager.TargetDeviceType.ROKU_TCL -> dev.deviceType == TvDiscoveryManager.DeviceType.ROKU_TCL
            else -> false
        }
    }

    private fun updateDiscoveredDeviceList(deviceList: List<TvDiscoveryManager.DeviceInfo>) {
        containerDiscoveredTvs?.removeAllViews()

        val filteredList = deviceList.filter { matchesActiveTarget(it) }

        for (dev in filteredList) {
            val cleanHost = dev.host.removePrefix("/")
            val devCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 14, 18, 14)
                setBackgroundColor(intColor(0xFF151821))
            }

            val devDetails = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val nameView = TextView(this).apply {
                text = "${dev.name} (${dev.deviceType.name.replace('_', ' ')})"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val ipView = TextView(this).apply {
                text = "IP: $cleanHost • Port: ${dev.port}"
                setTextColor(intColor(0xFFA0A5B5))
                textSize = 12f
            }

            devDetails.addView(nameView)
            devDetails.addView(ipView)

            val isThisConnected = isConnected && connectedDeviceIp == cleanHost
            val connectBtn = Button(this).apply {
                text = if (isThisConnected) "Connected" else "Connect"
                setBackgroundColor(if (isThisConnected) intColor(0xFF00E676) else intColor(0xFF282D3C))
                setTextColor(if (isThisConnected) intColor(0xFF0F1115) else android.graphics.Color.WHITE)
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener { v ->
                    triggerHaptic(v)
                    val targetType = when (dev.deviceType) {
                        TvDiscoveryManager.DeviceType.WINDOWS_PC -> RemoteManager.TargetDeviceType.WINDOWS_PC
                        TvDiscoveryManager.DeviceType.SAMSUNG_TIZEN -> RemoteManager.TargetDeviceType.SAMSUNG_TIZEN
                        TvDiscoveryManager.DeviceType.LG_WEBOS -> RemoteManager.TargetDeviceType.LG_WEBOS
                        TvDiscoveryManager.DeviceType.ROKU_TCL -> RemoteManager.TargetDeviceType.ROKU_TCL
                        else -> RemoteManager.TargetDeviceType.ANDROID_TV
                    }
                    setTargetDevice(targetType)
                    tvIpAddress?.setText(cleanHost)
                    connectToDevice(cleanHost, dev.port)
                }
            }

            devCard.addView(devDetails)
            devCard.addView(connectBtn)

            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.setMargins(0, 4, 0, 4)
            devCard.layoutParams = cardParams

            containerDiscoveredTvs?.addView(devCard)
        }
    }

    private fun showManualConnectDialog() {
        val input = EditText(this).apply {
            hint = "Enter TV or PC IP address"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
        AlertDialog.Builder(this)
            .setTitle("Manual Connect")
            .setView(input)
            .setPositiveButton("Connect") { dialog, _ ->
                val raw = input.text.toString().trim().removePrefix("/")
                if (raw.isNotEmpty()) {
                    val (ip, port) = if (raw.contains(":")) {
                        val parts = raw.split(":", limit = 2)
                        parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                    } else {
                        raw to 0
                    }
                    connectToDevice(ip, port)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setMessage("Sensitivity: use the slider in Air Mouse mode.\\nCalibration: use 'Calibrate Gyro' in Air Mouse mode.")
            .setPositiveButton("Calibrate Gyro") { dialog, _ ->
                showRecalibrateDialog()
                dialog.dismiss()
            }
            .setNegativeButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showFaqDialog() {
        AlertDialog.Builder(this)
            .setTitle("Help & FAQ")
            .setMessage(
                "Windows:\\n1. Run start_pc_receiver.bat on the PC.\\n2. Allow TCP/UDP 9090 in firewall.\\n3. Connect phone to PC IP:9090.\\n\\n" +
                "Android TV / Mi Box / Airtel / TCL:\\n1. Enable Control by mobile apps.\\n2. Keep TV awake.\\n3. Use TV Code pairing.\\n\\n" +
                "LG webOS:\\nAccept registration prompt; pointer opens after registration.\\n\\n" +
                "Samsung Tizen:\\nAllow connection; enable mouse mode for pointer.\\n\\n" +
                "If no devices are found, check Wi-Fi and TV settings."
            )
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showAboutDialog() {
        val message = "Air Remote\\nVersion 1.0\\n\\n" +
            "Developer: Vijay\\n" +
            "GitHub: <a href=\\"https://github.com/vijay3695/Air-remote\\">https://github.com/vijay3695/Air-remote</a>\\n\\n" +
            "Local Wi-Fi and Bluetooth remote control for Windows, Android TV, LG webOS, and Samsung Tizen.\\n\\n" +
            "License: Apache 2.0"

        val textView = TextView(this).apply {
            text = android.text.Html.fromHtml(message.replace("\\n", "<br>"), android.text.Html.FROM_HTML_MODE_LEGACY)
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#A0A5B5"))
            setPadding(24, 16, 24, 16)
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }

        AlertDialog.Builder(this)
            .setTitle("About Air Remote")
            .setView(textView)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
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
        if (activeTarget != RemoteManager.TargetDeviceType.ANDROID_TV) {
            Toast.makeText(this, "The 6-digit TV Code pairing flow is available for Android TV / Google TV targets.", Toast.LENGTH_LONG).show()
            return
        }
        val targetIp = tvIpAddress?.text?.toString()?.trim()?.removePrefix("/") ?: "192.168.1.4"

        Atrpv2Protocol.startPairing(targetIp) { success, msg ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }

        val input = EditText(this).apply {
            hint = "Enter 6-character code shown on TV"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
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
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
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

    private fun setupPointerSecondaryButtons() {
        // Secondary buttons for Air Mouse mode
        btnAirBack?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.BACK, "BACK") } }
        btnAirHome?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.HOME, "HOME") } }
        btnAirOk?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.OK, "ENTER") } }
        btnAirVolUp?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.VOLUME_UP, "VOL_UP") } }
        btnAirVolDown?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.VOLUME_DOWN, "VOL_DOWN") } }
        btnAirMute?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.MUTE, "MUTE") } }

        btnTrackBack?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.BACK, "BACK") } }
        btnTrackHome?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.HOME, "HOME") } }
        btnTrackOk?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.OK, "ENTER") } }
        btnTrackVolUp?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.VOLUME_UP, "VOL_UP") } }
        btnTrackVolDown?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.VOLUME_DOWN, "VOL_DOWN") } }
        btnTrackMute?.setOnClickListener { v -> triggerHaptic(v); if (isConnected) { syncTargetIp(); RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.MUTE, "MUTE") } }
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

    private fun connectToDevice(ip: String, port: Int = 0) {
        val cleanIp = ip.removePrefix("/")
        connectedDeviceIp = cleanIp
        tvIpAddress?.setText(cleanIp)
        txtStatus?.text = "Connecting to $cleanIp..."
        btnConnect?.isEnabled = false

        RemoteManager.connect(
            this,
            activeTarget,
            cleanIp,
            port,
            bluetoothController,
            object : RemoteManager.ConnectCallback {
                override fun onConnected() {
                    runOnUiThread {
                        isConnected = true
                        btnConnect?.isEnabled = true
                        btnConnect?.text = "Disconnect"
                        val deviceTypeStr = when (activeTarget) {
                            RemoteManager.TargetDeviceType.ANDROID_TV -> "Android TV"
                            RemoteManager.TargetDeviceType.SAMSUNG_TIZEN -> "Samsung Tizen"
                            RemoteManager.TargetDeviceType.LG_WEBOS -> "LG webOS"
                            RemoteManager.TargetDeviceType.ROKU_TCL -> "Roku / TCL"
                            RemoteManager.TargetDeviceType.WINDOWS_PC -> "Windows PC"
                            RemoteManager.TargetDeviceType.BLUETOOTH_HID -> "Bluetooth HID"
                            else -> "TV"
                        }
                        txtStatus?.text = "Connected to $deviceTypeStr ($cleanIp)"
                        prefs.edit().putString("last_device_ip", cleanIp).apply()
                        refreshControlAvailability()
                    }
                }

                override fun onConnectionFailed(error: String) {
                    runOnUiThread {
                        isConnected = false
                        btnConnect?.isEnabled = true
                        btnConnect?.text = "Connect"
                        txtStatus?.text = "Connection Failed: $error"
                        showConnectionGuidanceDialog(cleanIp, error)
                        refreshControlAvailability()
                    }
                }

                override fun onDisconnected() {
                    runOnUiThread {
                        isConnected = false
                        btnConnect?.isEnabled = true
                        btnConnect?.text = "Connect"
                        txtStatus?.text = "Disconnected from device"
                        refreshControlAvailability()
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
        refreshControlAvailability()
    }

    private fun refreshControlAvailability() {
        val dpadActive = activeMode == "dpad"
        btnUp?.isEnabled = dpadActive && isConnected
        btnDown?.isEnabled = dpadActive && isConnected
        btnLeft?.isEnabled = dpadActive && isConnected
        btnRight?.isEnabled = dpadActive && isConnected
        btnOk?.isEnabled = dpadActive && isConnected
        btnCenterOk?.isEnabled = dpadActive && isConnected
        btnBack?.isEnabled = dpadActive && isConnected
        btnHome?.isEnabled = dpadActive && isConnected
        btnPower?.isEnabled = dpadActive && isConnected
        btnVolumeUp?.isEnabled = dpadActive && isConnected
        btnVolumeDown?.isEnabled = dpadActive && isConnected
        btnMute?.isEnabled = dpadActive && isConnected

        val airMouseActive = activeMode == "air_mouse"
        btnRecalibrate?.isEnabled = airMouseActive
        btnAirLeftClick?.isEnabled = airMouseActive && isConnected
        btnAirRightClick?.isEnabled = airMouseActive && isConnected

        val trackpadActive = activeMode == "trackpad"
        btnTrackLeftClick?.isEnabled = trackpadActive && isConnected && RemoteManager.supportsPointer(activeTarget)
        btnTrackRightClick?.isEnabled = trackpadActive && isConnected && RemoteManager.supportsPointer(activeTarget)
    }

    private fun enableRemoteControls(enabled: Boolean) {
        // Buttons are visibility/enabled driven by refreshControlAvailability; kept for compatibility.
        refreshControlAvailability()
    }

    // --- Remote Control Button Handlers ---

    fun onBtnUpClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.UP, "UP")
    }

    fun onBtnDownClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.DOWN, "DOWN")
    }

    fun onBtnLeftClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.LEFT, "LEFT")
    }

    fun onBtnRightClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.RIGHT, "RIGHT")
    }

    fun onBtnOkClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.OK, "ENTER")
    }

    fun onBtnBackClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.BACK, "ESCAPE")
    }

    fun onBtnHomeClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.HOME, "WIN")
    }

    fun onBtnPowerClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.POWER, "POWER")
    }

    fun onBtnVolumeUpClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.VOLUME_UP, "VOL_UP")
    }

    fun onBtnVolumeDownClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.VOLUME_DOWN, "VOL_DOWN")
    }

    fun onBtnMuteClick() {
        if (activeMode != "dpad" || !isConnected) return
        syncTargetIp()
        RemoteManager.sendKey(Atrpv2Protocol.KEY_CODES.MUTE, "MUTE")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            updateWifiNetworkState()
        }
    }

    override fun onPause() {
        super.onPause()
        bluetoothController?.stopAirMouse()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(btReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(discoveryReceiver)
        } catch (_: Exception) {}
        bluetoothController?.shutdown()
        RemoteManager.disconnect()
    }
}
