package com.vijay.aimagicremote.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vijay.aimagicremote.R

class HelpFaqActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        fun section(title: String, body: String) {
            val t = TextView(this).apply {
                text = title
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                setPadding(0, 24, 0, 8)
            }
            val b = TextView(this).apply {
                text = body
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#A0A5B5"))
                setLineSpacing(4f, 1.2f)
            }
            root.addView(t)
            root.addView(b)
        }

        val header = TextView(this).apply {
            text = "Help & FAQ"
            textSize = 26f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 16)
        }
        root.addView(header)

        section("Getting Started", "1. Select target (Android TV, Windows PC, LG, Samsung, Roku).\n2. Tap Rescan or enter IP manually.\n3. Tap Connect.\n4. Use Remote / Air Mouse / Trackpad tabs.")

        section("Windows PC", "1. Copy pc_receiver.ps1 to the PC.\n2. Run start_pc_receiver.bat (or install_pc_receiver_autostart.bat once).\n3. Allow TCP/UDP 9090 in Windows Firewall.\n4. Connect phone to PC IP:9090.\n\nTrackpad and Air Mouse require an active connection.")

        section("Android TV / Google TV / Mi Box / Airtel / TCL", "1. Enable 'Control by mobile apps' / Android TV Remote Service.\n2. Keep TV awake.\n3. Use TV Code pairing (6-character code).\n4. Air Mouse uses D-pad key mapping (no native cursor).")

        section("LG webOS", "1. Accept registration prompt on TV.\n2. Client key is saved for reconnect.\n3. Pointer socket opens after registration.\n4. Real pointer movement supported.")

        section("Samsung Tizen", "1. Allow connection on TV.\n2. Token saved for reconnect.\n3. Mouse mode (ProcessMouseDevice) used when enabled.\n4. Text uses base64 SendInputString.")

        section("Troubleshooting", "No devices found: check Wi-Fi, TV settings, receiver running.\nWrong IP: enter IP:port manually.\nPairing code: enter all 6 characters (letters+numbers).\nClick not working: ensure connected; tap feedback shows status.\nManic cursor: reduce sensitivity slider and recalibrate gyro.")

        section("Bluetooth HID", "Experimental. Pair phone as Bluetooth keyboard/mouse on target. Many TVs do not support phone-as-HID. Wi-Fi remains fallback.")

        scroll.addView(root)
        setContentView(scroll)
    }
}
