# Android Magic Remote

Android Magic Remote turns a smartphone into a television remote control with Motion-based pointer control, touchpad-style control, and standard remote buttons and navigation.

The application supports as many TV platforms as technically possible, with first priority on Android TV and Google TV, followed by LG webOS and Samsung Tizen TV platforms.

## Project Status

- **Repository**: `main` branch at `https://github.com/vijay3695/Air-remote.git`
- **Ready to compile**: Open Android Studio → `File → Open → /workspace/app` → `Build → Build APK(s)`
- **APK**: `app/build/outputs/debug/app-debug.apk`

## Project Structure

```
app/src/main/java/com/vijay/aimagicremote/
├── AppInitializer.kt          # Application class, global components
├── Atrpv2Protocol.kt          # ATRPv2 protocol (ports 6466/6467, TLS 1.2, protobuf)
├── BluetoothController.kt     # Air-mouse, gyroscope, HID mouse/keyboard
├── TvDiscoveryManager.kt      # mDNS discovery, TV auto-find, reconnection
├── MainActivity.kt            # Connect screen + D-pad grid (3x4) + toggles
└── protocol/adapters/LgSamsungProtocolAdapters.kt  # LG/Samsung TV adaptations

## 5 Phases (Plan: Weeks 1-6)

- **Phase 1**: ATRPv2 remote, mDNS discovery, D-pad remote ✅
- **Phase 2**: Bluetooth air-mouse, gyroscope, sensitivity ✅
- **Phase 3**: Companion app with memory management ✅
- **Phase 4**: LG/Samsung TV protocol adaptations ✅
- **Phase 5**: Polishing & documentation ✅

## Build Configuration

- **AGP**: 8.2.0, **Kotlin**: 1.9.22, **SDK**: 34 (minSdk 24)
- **Dependencies**: Compose 1.6.1, Material 1.11.0, OkHttp 4.12.0, Coroutines 1.8.0, Coil 2.5.0

## Quick Start

```bash
git clone https://github.com/vijay3695/Air-remote.git
cd Air-remote
./gradlew assembleDebug
adb install app/build/outputs/debug/app-debug.apk
```

## Features by Phase

- **Remote**: D-pad grid (BACK/UP/OK/LEFT/RIGHT/HOME/DOWN/POWER/MUTE/VOL+/VOL-)
- **Air-mouse**: Gyroscope-based pointer control
- **Trackpad**: Phone screen as touchpad
- **Pairing**: QR code (recommended) or 6-digit code fallback
- **Reliability**: Companion app with auto-reconnect, Bluetooth HID fallback
- **TV Support**: Android TV/Google TV first, then LG webOS, Samsung Tizen

## License

```
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