# Air Remote (Air Mouse, Trackpad & Multi-Brand Smart TV Control)

**Air Remote** is an Android application (`com.vijay.aimagicremote`) that turns an Android smartphone into a zero-drift **Air Mouse (3D Sensor Fusion)**, **Trackpad**, and **Universal Remote Control** for **Android TV / Google TV**, **Samsung Tizen TV**, **TCL Roku TV**, **LG webOS TV**, and **Windows PC**.

---

## 🚀 Key Features & Architecture

- **iOS 18 Dark Glassmorphism UI**: Header status pill badge (`● Connected`), 3 target device modes, 3 mode tabs, and a 3x4 D-pad remote button grid.
- **Multi-Brand TV Protocol Engine (`RemoteManager.kt`)**:
  - **Android TV / Google TV (ATRPv2)**: Persistent TLS 1.2 control socket on port `6466` & Protobuf 6-digit PIN pairing server on port `6467`.
  - **Samsung Tizen Smart TV**: WebSocket JSON control channel on ports `8001` (`ws://`) and `8002` (`wss://`).
  - **TCL / Roku Smart TV**: HTTP External Control Protocol (ECP) on port `8060`.
  - **LG webOS Smart TV**: SSAP WebSocket channel on ports `3000` / `3001`.
  - **Windows PC**: Low-latency TCP/UDP control socket on port `9090`.
  - **Bluetooth HID**: Direct hardware combo mouse/keyboard profile.
- **3D Gravity-Compensated Motion Engine (`BluetoothController.kt`)**:
  - `Sensor.TYPE_GAME_ROTATION_VECTOR` + `Sensor.TYPE_GYROSCOPE` 3D sensor fusion.
  - **1€ Filter** smoothing algorithm (`minCutoff = 1.2f`, `beta = 0.08f`) for zero jitter.
  - **Continuous Dynamic Auto-Calibration**: Stillness Exponential Moving Average (EMA) tracking automatically zeroes out resting DC bias.
- **Auto-Discovery Engine (`TvDiscoveryManager.kt`)**: mDNS (`_androidtvremote._tcp`, `_workstation._tcp`) and LAN UDP Broadcast Ping on port `9090`.

---

## ✅ What Is Working

| Feature / Subsystem | Status | Technical Implementation Details |
| :--- | :--- | :--- |
| **Android TV ATRPv2 Protocol** | ✅ **WORKING** | Protobuf frame encoding, TLS 1.2 socket connection, and key injection on port `6466`. |
| **Google TV 6-Digit PIN Pairing** | ✅ **WORKING** | Sends Protobuf `PAIRING_REQUEST` on port `6467` to trigger TV PIN screen overlay. |
| **Samsung Tizen TV Control** | ✅ **WORKING** | WebSocket JSON channel on port `8001`/`8002` with on-screen TV connection prompt. |
| **TCL / Roku TV ECP Control** | ✅ **WORKING** | HTTP POST requests to `http://<tv-ip>:8060/keypress/<key_name>`. |
| **LG webOS SSAP Control** | ✅ **WORKING** | SSAP WebSocket command frames on port `3000`/`3001`. |
| **Windows PC Remote & Receiver** | ✅ **WORKING** | TCP/UDP socket control on port `9090` (compatible with `pc_receiver.ps1`). |
| **Air Mouse 3D Gyro Fusion** | ✅ **WORKING** | Real-time gravity-compensated motion tracking with 1€ Filter smoothing. |
| **Trackpad & Gestures** | ✅ **WORKING** | Single-finger cursor drag, tap-to-click, and 2-finger scroll. |
| **mDNS & LAN UDP Auto-Discovery** | ✅ **WORKING** | Automatic Wi-Fi scanning for active TVs and PCs on launch. |
| **Bluetooth HID Direct Remote** | ✅ **WORKING** | Phone acts as a Bluetooth Wireless Remote/Mouse when paired with TV. |

---

## ⚠️ Requirements & TV Menu Settings (What Needs TV Setup)

For third-party Wi-Fi remote apps to control your TV, specific TV menu settings must be enabled on the TV OS using your physical TV remote:

### 1. Google TV / TCL Android TV Wi-Fi Pairing (`<TV_IP>:6467`)
- **TV Menu Settings Required**:
  - **Settings** → **Network & Internet** → **Network Standby** or **Control by Mobile App** → Set to **ON**.
  - **Settings** → **Apps** → **Special App Access** → **Android TV Remote Service** → Set to **Allowed**.
- *Note: If "Network Standby" or "Control by Mobile App" is turned OFF in your TV menu, the TV's firewall drops incoming Wi-Fi pairing requests on port 6467.*

### 2. Samsung Tizen Smart TV (`<TV_IP>:8001`)
- **On-Screen Prompt Required**:
  - When connecting to a Samsung TV for the first time, look at your Samsung TV screen for the pop-up prompt **"Allow Air Remote to connect?"** and select **Allow** using your physical remote.

### 3. Alternative: Bluetooth HID Direct Mode (No PIN / No Wi-Fi Port Restrictions)
- If your TV drops Wi-Fi ports, select **Bluetooth HID** target mode in the app.
- Pair your phone to your TV via Bluetooth (**Settings** → **Remotes & Accessories** → **Add Accessory** on TV).
- Your phone will act as an official Bluetooth Wireless Hardware Remote & Air Mouse directly without Wi-Fi port dependencies.

---

## 🛠️ Quick Start & Build Instructions

```bash
# 1. Clone Repository
git clone https://github.com/vijay3695/Air-remote.git
cd Air-remote

# 2. Run Unit Tests
./gradlew testDebugUnitTest

# 3. Build Debug APK
./gradlew assembleDebug

# 4. Install onto Connected Phone
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📂 Project Structure

```
app/src/main/java/com/vijay/aimagicremote/
├── AppInitializer.kt          # Global Application initializer & discovery singleton
├── bluetooth/
│   ├── BluetoothController.kt # 3D Gravity-compensated sensor fusion & 1€ Filter
│   ├── BluetoothBroadcastReceiver.kt
│   └── PairingResultReceiver.kt
├── discovery/
│   └── TvDiscoveryManager.kt  # mDNS & LAN UDP Broadcast ping discovery
├── protocol/
│   ├── Atrpv2Protocol.kt      # Android TV ATRPv2 TLS 1.2 Protobuf engine (ports 6466/6467)
│   ├── SamsungTizenProtocol.kt# Samsung Tizen WebSocket engine (ports 8001/8002)
│   ├── RokuTclProtocol.kt      # TCL / Roku HTTP ECP engine (port 8060)
│   ├── LgWebOsProtocol.kt     # LG webOS SSAP WebSocket engine (ports 3000/3001)
│   ├── PcRemoteProtocol.kt    # Windows PC TCP/UDP engine (port 9090)
│   └── RemoteManager.kt       # Multi-brand protocol dispatcher
└── ui/
    └── MainActivity.kt        # iOS 18 Glassmorphism Dark UI controller
```

---

## 📄 License

```text
Copyright 2026 Vijay

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
