# Air Remote

Air Remote turns an Android smartphone into a television remote control with Motion-based pointer control (Air Mouse), touchpad-style control (Trackpad), and standard D-Pad remote navigation.

The application supports Android TV and Google TV as first priority, followed by LG webOS and Samsung Tizen TV platforms.

## Project Status

- **Repository**: `main` branch at `https://github.com/vijay3695/Air-remote.git`
- **Ready to compile**: Open Android Studio → `Build → Make Project` or `./gradlew assembleDebug`
- **APK Output**: `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```
app/src/main/java/com/vijay/aimagicremote/
├── AppInitializer.kt          # Application class, global components
├── Atrpv2Protocol.kt          # ATRPv2 protocol (ports 6466/6467, TLS 1.2, Protobuf, AndroidKeyStore certs)
├── BluetoothController.kt     # Air-mouse, Gyroscope sensor, 1€ filter jitter smoothing
├── TvDiscoveryManager.kt      # mDNS discovery (_androidtvremote._tcp, _androidtvremote2._tcp)
├── ui/MainActivity.kt         # Air Remote UI, D-pad 3x4 grid, Air Mouse, Trackpad, QR / 6-digit code pairing
└── protocol/adapters/         # LG webOS / Samsung Tizen TV protocol adaptations
```

## Features

- **D-Pad Remote**: 3x4 button layout (BACK, UP, OK, LEFT, SELECT, RIGHT, HOME, DOWN, POWER, MUTE, VOL+, VOL-)
- **Air Mouse Mode**: Gyroscope-based pointer control with 1€ Filter smoothing algorithm
- **Trackpad Mode**: Touchpad surface for single-finger pointer drag, tap-to-click, and 2-finger scroll
- **Pairing & Connectivity**: Hardware-backed RSA 2048 X.509 client certificate, 6-digit code TV prompt initiation, QR Code pairing
- **Auto-Discovery**: mDNS background scan on launch with 1-tap TV connection
- **Reliability**: Foreground connection service with auto-reconnect logic

## Quick Start

```bash
git clone https://github.com/vijay3695/Air-remote.git
cd Air-remote
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

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