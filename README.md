# Air Remote (Air Mouse, Trackpad & Multi-Brand Smart TV Control)

**Air Remote** is an Android application (`com.vijay.aimagicremote`) that turns an Android smartphone into a zero-drift **Gyroscope Air Mouse**, **Trackpad**, and **Universal Remote Control** for **Android TV / Google TV**, **Samsung Tizen TV**, **TCL Roku TV**, **LG webOS TV**, and **Windows PC**.

---

## Documentation

Project plans, design notes, protocol references, and the current issue/repair report are in the [docs folder](docs/README.md).

---

## 🚀 Key Features & Architecture

- **iOS 18 Dark Glassmorphism UI**: Header status pill badge (`● Connected`), 3 target device modes, 3 mode tabs, and a 3x4 D-pad remote button grid.
- **Multi-Brand TV Protocol Engine (`RemoteManager.kt`)**:
  - **Android TV / Google TV (ATRPv2)**: Persistent TLS 1.2 control socket on port `6466` & Protobuf 6-digit PIN pairing server on port `6467`.
  - **Samsung Tizen Smart TV**: WebSocket JSON control channel on ports `8001` (`ws://`) and `8002` (`wss://`).
  - **TCL / Roku Smart TV**: HTTP External Control Protocol (ECP) on port `8060`.
  - **LG webOS Smart TV**: SSAP WebSocket channel on ports `3000` / `3001`.
  - **Windows PC**: Low-latency TCP/UDP control socket on port `9090`.
  - **Bluetooth HID**: Not implemented yet; the app reports this target as unavailable instead of falsely connecting.
- **3D Gravity-Compensated Motion Engine (`BluetoothController.kt`)**:
  - `Sensor.TYPE_GAME_ROTATION_VECTOR` + `Sensor.TYPE_GYROSCOPE` 3D sensor fusion.
  - **1€ Filter** smoothing algorithm (`minCutoff = 1.2f`, `beta = 0.08f`) for zero jitter.
  - **Continuous Dynamic Auto-Calibration**: Stillness Exponential Moving Average (EMA) tracking automatically zeroes out resting DC bias.
- **Auto-Discovery Engine (`TvDiscoveryManager.kt`)**: mDNS (`_androidtvremote._tcp`, `_workstation._tcp`) and LAN UDP Broadcast Ping on port `9090`.

---

## Feature status

**Build and protocol code are not the same as hardware verification.** This repository has unit/build checks, but TV compatibility still needs testing against actual models and firmware.

| Feature / subsystem | Current status |
| :--- | :--- |
| Android TV / Google TV pairing and key protocol | Protocol implementation updated; real-TV pairing and key tests pending. |
| Samsung Tizen Wi-Fi remote | Registration/token flow implemented; real-TV approval and key tests pending. |
| LG webOS SSAP remote and pointer channel | Registration, client-key persistence, and pointer channel implemented; real-TV test pending. |
| Roku ECP remote | Device-info validation and key/text requests implemented; real Roku hardware test pending. |
| Windows PC remote | TCP receiver script and Android TCP command path included; test on a Windows PC before relying on it. |
| Gyroscope Air Mouse | Sensor pipeline and target routing implemented; cursor behavior needs device-level calibration. |
| Trackpad | UI gestures are implemented; behavior depends on target protocol capabilities. |
| Bluetooth HID | **Not implemented.** The app deliberately reports it as unavailable rather than claiming a connection. |
| Auto-discovery | mDNS and UDP discovery code is present; network/router behavior varies. |

---

## ⚠️ Requirements & TV Menu Settings (What Needs TV Setup)

For third-party Wi-Fi remote apps to control your TV, specific TV menu settings must be enabled on the TV OS using your physical TV remote:

### 1. Android TV / Google TV
- The phone and TV must be on the same local network, and the TV must have Android TV Remote Service available.
- Open the pairing flow in Air Remote and enter the six-character hexadecimal code displayed by the TV.
- TV firmware, network isolation, or Android TV Remote Service versions can affect compatibility. Do not change unrelated TV settings unless the TV itself reports a pairing issue.

### 2. Samsung Tizen Smart TV (`<TV_IP>:8001`)
- **On-Screen Prompt Required**:
  - When connecting to a Samsung TV for the first time, look at your Samsung TV screen for the pop-up prompt **"Allow Air Remote to connect?"** and select **Allow** using your physical remote.

### 3. Alternative: Bluetooth HID Direct Mode (No PIN / No Wi-Fi Port Restrictions)
- If your TV drops Wi-Fi ports, select **Bluetooth HID** target mode in the app.
- Pair your phone to your TV via Bluetooth (**Settings** → **Remotes & Accessories** → **Add Accessory** on TV).
- Your phone will act as an official Bluetooth Wireless Hardware Remote & Air Mouse directly without Wi-Fi port dependencies.

---

## Windows PC receiver setup

1. Connect the Windows PC and Android phone to the same LAN.
2. On Windows, run PowerShell in an interactive desktop session from the repository folder:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\\pc_receiver.ps1
   ```

3. Allow inbound **TCP 9090** and **UDP 9090** in Windows Firewall if Windows asks or discovery/connection fails.
4. In Air Remote, choose **Windows PC**, enter the PC's LAN IP, and connect.

The receiver script supports relative mouse movement, left/right click, scroll, key presses, text input, and UDP discovery. Keep the PowerShell window open while using the remote. It does not run as a Windows service and does not control the PC from outside the local network.

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
