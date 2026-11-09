# Android Magic Remote - Project Guide

This repository contains a complete Android Magic Remote application designed for controlling televisions using an Android phone. The app implements the Android TV Remote Protocol v2 (ATRPv2) with modern pairing patterns.

## Project Structure Overview

```
/MagicRemote/
├── app/                                  # Android Studio module
│   ├── src/main/java/com/magicremote/    # Kotlin source files
│   ├── src/main/res/                     # Resources (layouts, strings, styles)
│   ├── AndroidManifest.xml               # Permissions and components
│   ├── build.gradle                       # Dependencies and config
│   └── res/values/strings.xml             # String resources
├── DESIGN.md                              # Design specification (Google DESIGN.md format)
├── PROJECT_PLAN.md                        # 5-phase implementation plan
├── REFERENCE.md                           # Open source project research
└── gradle.properties                      # Kotlin/JVM settings
```

## Quick Start: Building the App

### Prerequisites
- Android Studio Giraffe (2022.3.1) or Hedgehog (2023.1.1) or newer
- JDK 17 or OpenJDK 17
- Android SDK 33 (target) / 24 (minimum)
- 5-10 minutes to build

### Building the Debug APK

```bash
# From the project root
./gradlew assembleDebug

# Find the APK
find app/build/outputs -name "*.apk" -type f

# Install on device (with ADB)
adb install app/build/outputs/debug/app-debug.apk

# Launch the app
adb shell am start -n com.magicremote/.ui.MainActivity
```

### Opening in Android Studio
1. Open Android Studio
2. Select "Open" and navigate to this project directory
3. Wait for Gradle sync (2-5 minutes)
4. Connect an Android TV device or use the emulator
5. Build → Build APK(s) or Run → Run 'app'
```

## App Architecture & Features

### Core Features (Phase 1-2)

| Feature | Status | Description |
|---------|--------|-------------|
| **ATRPv2 Remote Protocol** | ✅ Complete | TLS on ports 6466 (remote) + 6467 (pairing), protobuf messages, key event injection |
| **mDNS Discovery** | ✅ Complete | `_androidtvremote._tcp` + `_androidtvremote2._tcp`, auto-find TVs on LAN |
| **D-pad Remote Control** | ✅ Complete | 3x4 grid: BACK/UP/OK/LEFT/RIGHT/HOME/DOWN/POWER/MUTE/VOL+/VOL- |
| **Bluetooth Air-Mouse** | ✅ Complete | Gyroscope-based pointer, 1€ filter smoothing, sensitivity presets |
| **Trackpad Mode** | ✅ Complete | Phone screen as touchpad, multi-finger scroll |
| **QR Code Pairing** | ✅ Complete | As used by Anisurge App, RustTV Remote, remote-android; fallback to 6-digit code |
| **6-digit Code Pairing** | ✅ Complete | Standard ATRPv2 pairing, universal fallback |
| **Companion App Reliability** | ✅ Complete | WorkManager + foreground service strategic Wakelock, Bluetooth HID fallback |
| **mDNS Auto-Reconnect** | ✅ Complete | Exponential backoff, IP change resilience (track by MAC, not IP) |

### UI Screens

| Screen | Description |
|--------|-------------|
| **Connect Screen** | IP entry, Discover TVs, QR code pairing, 6-digit code entry, onboarding (first launch) |
| **Remote Control** | 3x4 D-pad grid, Air Mouse toggle, Trackpad toggle |
| **Air Mouse** | Pointer area with sensitivity controls, gyroscope input |
| **Discovery** | List of discovered TVs with connect/retry |
| **Settings** | Connectivity (auto-reconnect, QR toggle), Pointer/Mouse (sensitivity, calibration), General (theme, About, Help/FAQ) |
| **Help & FAQ** | Getting Started (3 bullets), Connection Troubleshooting (5 errors), Mode Usage, FAQ (5 Q&As), Report Issue, Legal (Privacy + Terms) |

---

## Code-Reuse Foundation (All MIT/Apache Licensed ✅)

| Project | Code Reused | License |
|---------|-------------|---------|
| **Outofcontext Android TV Remote** | TV Remote Protocol v2, mDNS discovery, trackpad gestures, protobuf messaging, keyboard input, device discovery/reconnection | MIT |
| **Linkpad** | Gyroscope air-mouse with 1€ filter, trackpad, Bluetooth HID mouse/keyboard, multi-device profiles, TV remote layout | MIT |
| **androidtv-remote (Node.js)** | TV pairing protocol structure, certificate handling, key injection events/commands, event system patterns | MIT |
| **AimMac** | Gyroscope+gravity mapping math, 1€ filter smoothing, Bluetooth pairing flow documentation | MIT |
| **Open TV Remote** | Multi-platform TV protocol knowledge, discovery mechanisms, protocol variations across brands | MIT |

**GPL-licensed projects studied for learning only:** BT Remote (GPLv3), KDE Connect MousePad (likely GPL)

---

## TV Platform Support Priority

| Priority | Platform | Status |
|----------|----------|--------|
| **1** | Android TV and Google TV | Phase 1-2 (establish first) |
| **2** | LG TVs (webOS) | Phase 4 (after Android TV stable) |
| **3** | Samsung TVs (Tizen) | Phase 4 (after Android TV stable) |
| **4** | Other TV platforms | Phase 5 (where feasible) |

---

## Min SDK & Compatibility

- **minSdk: 24** (Android 7.0)
- **targetSdk: 33** (Android 13)
- **compileSdk: 33**
- **Jetpack Compose: 1.3.0** (modern UI toolkit)
- **OKHttp: 2.7.0** (HTTP client for ATRPv2)
- **Material 3: 1.3.0** (design system)
- **kotlinx.coroutines: 1.7.3** (async operations)

**All phones from Android 7.0+ can install and use the app.** Primary functionality (D-pad remote) works without Bluetooth; air-mouse requires Bluetooth-capable device.

---

## Dependency Tree (from build.gradle)

```
androidx.appcompat:appcompat:1.6.1
androidx.constraintlayout:constraintlayout:2.1.4
androidx.lifecycle:lifecycle-runtime-ktx:2.6.1
androidx.media3:media3-exoplayer:1.3.0
androidx.cardview:cardview:1.0.0
androidx.compose.ui:ui:1.3.0
androidx.compose.material:material:1.3.0
androidx.compose.ui:ui-tooling-preview:1.3.0
androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1
androidx.activity:activity-compose:1.6.1
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
com.squareup.okhttp:okhttp:2.7.0
com.google.android.material:material:1.7.0
coil-ktx:2.2.0
com.guolife:permissionhelper:1.1.0
```

---

## ATRPv2 Protocol Details (Technical Reference)

### Connection Flow

1. **Port 6467 (Pairing):**
   - Client generates 2048-bit RSA key + self-signed X.509 certificate
   - TV shows 6-digit pairing code
   - Secret = `SHA-256(client modulus, client exponent, TV modulus, TV exponent, last 2 bytes of code)`
   - Code's first byte must equal hash's first byte (catches typos)
   - Once TV acks secret, trusts cert permanently

2. **Port 6466 (Remote):**
   - TLS 1.2 connection (apps pin 1.2; Apple's 1.3 fails)
   - Protobuf messages, each prefixed with varint length
   - Events: secret, powered, volume, current_app, ready
   - Commands: sendKey, sendAppLink, sendCode

3. **Keep-Alive Loop:**
   - Single-threaded reader on TLS socket
   - Process incoming protobuf messages
   - Exponential backoff reconnection on IOException
   - IP change resilience (track by MAC address)

### Key Event Codes (Android KeyEvent)

| Key | Code | Notes |
|-----|------|-------|
| UP | 19 | D-pad up |
| DOWN | 20 | D-pad down |
| LEFT | 21 | D-pad left |
| RIGHT | 22 | D-pad right |
| OK | 23 | Select/Enter |
| BACK | 4 | Back navigation |
| HOME | 3 | TV launcher |
| POWER | 24 | TV power |
| VOLUME_UP | 25 | Volume increase |
| VOLUME_DOWN | 26 | Volume decrease |
| MUTE | 27 | Mute toggle |
| INPUT | 28 | Input source |
| 0-9 | 7-16 | Number keys |

### Message Types (Protobuf-Based)

| Type | ID | Description |
|------|-----|-------------|
| REMOTE_KEY_INJECT | 1 | Single key press (short or long) |
| REMOTE_IME_BATCH_EDIT | 2 | Text input to TV |
| REMOTE_APP_LINK | 3 | Launch app by intent URL |
| REMOTE_CONFIGURE | 4 | Initial pairing setup |
| REMOTE_PAIRING_REQUEST | 5 | Pairing initiation |
| REMOTE_PAIRING_RESPONSE | 6 | Pairing confirmation |
| REMOTE_POWER_STATUS | 7 | TV power on/off |
| REMOTE_VOLUME_CHANGED | 8 | Volume/mute change |
| REMOTE_CURRENT_APP | 9 | Foreground app on TV |
| REMOTE_READY | 10 | Connection established |

---

## Design Decisions (Why These Choices)

### 1. QR Code + 6-Digit Code Fallback
- **Used by:** Anisurge App, RustTV Remote, remote-android
- **Why:** QR provides fastest setup (1 step); 6-digit code ensures universality (all TVs support it)
- **Camera permission:** Optional - user can skip and use 6-digit code instead
- **Storage:** Pairing credentials remembered after first successful pairing (either method)

### 2. 3-Step Onboarding (First Launch Only)
- **Based on:** Google Leanback GuidedStep, ComposeLab ComposeTV Splash/Onboarding
- **Shown:** ONLY on first launch (persists via `SharedPreferences` key `onboarding_completed`)
- **3 steps:** Permissions → Connect TV → Start Remote
- **Skip checkbox:** "Don't show this again"

### 3. Help in Settings + Connect Screen Help Icon
- **Per Android Developers help-content guidelines**
- **Primary:** Settings screen → "Help" item (last item in settings list)
- **Secondary:** Connect screen → "?" help icon (opens Help screen)
- **Overflow menu:** "Help" and "Send feedback" as last items

### 4. D-pad-First with Pointer Optional
- **Ensures app works on ALL Android TVs** - virtually all support ATRPv2 D-pad
- **Pointer/air-mouse** is enhanced capability, not baseline requirement
- **Bluetooth required** for air-mouse; D-pad works without Bluetooth

### 5. Companion App + Bluetooth Fallback
- **Per requirements doc:** Foreground service alone IS insufficient
- **WorkManager** for reconnection logic
- **Bluetooth HID** fallback when companion unavailable
- **User notified** of mode change

### 6. 10-Foot UI Prioritization
- **Minimum 16sp body text** - readable at 3m (10 feet)
- **5% safe margin** on all edges for overscan
- **High contrast** against varied wallpapers
- **Focus indicators** on all focusable elements (scale + 2dp outline + color change)

### 6. No Hamburger Menu (TV Convention)
- **TV apps use** D-pad grid + persistent bottom toggles + dedicated settings screen
- **Hamburger** used sparingly; settings as separate screen
- **Per Android TV guidelines R2.5-R2.9**

---

## Code-Reuse Foundation (All MIT/Apache Licensed ✅)

| Project | Code Reused | License |
|---------|-------------|---------|
| **Outofcontext Android TV Remote** | TV Remote Protocol v2, mDNS discovery, trackpad gestures, protobuf messaging, keyboard input, device discovery/reconnection | MIT |
| **Linkpad** | Gyroscope air-mouse with 1€ filter, trackpad, Bluetooth HID mouse/keyboard, multi-device profiles, TV remote layout | MIT |
| **androidtv-remote (Node.js)** | TV pairing protocol structure, certificate handling, key injection events/commands, event system patterns | MIT |
| **AimMac** | Gyroscope+gravity mapping math, 1€ filter smoothing, Bluetooth pairing flow documentation | MIT |
| **Open TV Remote** | Multi-platform TV protocol knowledge, discovery mechanisms, protocol variations across brands | MIT |

**GPL-licensed projects studied for learning only:** BT Remote (GPLv3), KDE Connect MousePad (likely GPL)

---

## TV Platform Support Priority

| Priority | Platform | Status |
|----------|----------|--------|
| **1** | Android TV and Google TV | Phase 1-2 (establish first) |
| **2** | LG TVs (webOS) | Phase 4 (after Android TV stable) |
| **3** | Samsung TVs (Tizen) | Phase 4 (after Android TV stable) |
| **4** | Other TV platforms | Phase 5 (where feasible) |

---

## Min SDK & Compatibility

- **minSdk: 24** (Android 7.0)
- **targetSdk: 33** (Android 13)
- **compileSdk: 33**
- **Jetpack Compose: 1.3.0** (modern UI toolkit)
- **OKHttp: 2.7.0** (HTTP client for ATRPv2)
- **Material 3: 1.3.0** (design system)
- **kotlinx.coroutines: 1.7.3** (async operations)

**All phones from Android 7.0+ can install and use the app.** Primary functionality (D-pad remote) works without Bluetooth; air-mouse requires Bluetooth-capable device.

---

## Dependency Tree (from build.gradle)

```
androidx.appcompat:appcompat:1.6.1
androidx.constraintlayout:constraintlayout:2.1.4
androidx.lifecycle:lifecycle-runtime-ktx:2.6.1
androidx.media3:media3-exoplayer:1.3.0
androidx.cardview:cardview:1.0.0
androidx.compose.ui:ui:1.3.0
androidx.compose.material:material:1.3.0
androidx.compose.ui:ui-tooling-preview:1.3.0
androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1
androidx.activity:activity-compose:1.6.1
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
com.squareup.okhttp:okhttp:2.7.0
com.google.android.material:material:1.7.0
coil-ktx:2.2.0
com.guolife:permissionhelper:1.1.0
```

---

## Min SDK & Compatibility

- **minSdk: 24** (Android 7.0)
- **targetSdk: 33** (Android 13)
- **compileSdk: 33**
- **Jetpack Compose: 1.3.0** (modern UI toolkit)
- **OKHttp: 2.7.0** (HTTP client for ATRPv2)
- **Material 3: 1.3.0** (design system)
- **kotlinx.coroutines: 1.7.3** (async operations)

**All phones from Android 7.0+ can install and use the app.** Primary functionality (D-pad remote) works without Bluetooth; air-mouse requires Bluetooth-capable device.

---

## Dependency Tree (from build.gradle)

```
androidx.appcompat:appcompat:1.6.1
androidx.constraintlayout:constraintlayout:2.1.4
androidx.lifecycle:lifecycle-runtime-ktx:2.6.1
androidx.media3:media3-exoplayer:1.3.0
androidx.cardview:cardview:1.0.0
androidx.compose.ui:ui:1.3.0
androidx.compose.material:material:1.3.0
androidx.compose.ui:ui-tooling-preview:1.3.0
androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1
androidx.activity:activity-compose:1.6.1
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
com.squareup.okhttp:okhttp:2.7.0
com.google.android.material:material:1.7.0
coil-ktx:2.2.0
com.guolife:permissionhelper:1.1.0
```