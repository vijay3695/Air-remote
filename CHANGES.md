# Air Remote - Repair Change Log

## Build verification
- `./gradlew compileDebugKotlin` -> BUILD SUCCESSFUL
- `./gradlew testDebugUnitTest` -> BUILD SUCCESSFUL, 13 tests passed
- `./gradlew assembleDebug` -> BUILD SUCCESSFUL
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Windows receiver handshake test: READY + ACK for move/click

## Main code changes

### Protocol routing
- `RemoteManager.kt`: one active target at a time; stale callback protection; truthful connection state; pointer/click/scroll/text routing per target; Samsung included in pointer support.

### Windows PC
- `PcRemoteProtocol.kt`: handshake (`ready`), serialized writes, ack tracking, no UDP command fallback.
- `pc_receiver.ps1`: SendInput relative movement, click/down/up, scroll, keys, text, acks.
- `start_pc_receiver.bat`, `install_pc_receiver_autostart.bat`.

### Android TV
- `Atrpv2Protocol.kt`: deployed v2 pairing/control, `SHORT=3`, full feature mask.
- Pointer is DPAD key mapping (no native pointer in stock ATRPv2).

### LG webOS
- `LgWebOsProtocol.kt`: registration, client key, pointer socket, integer move deltas with `down`, `type:click`, `replace:true`.

### Samsung Tizen
- `SamsungTizenProtocol.kt`: token persistence, mouse mode, base64 text, ProcessMouseDevice move/click, timeout handling.

### Discovery
- `TvDiscoveryManager.kt`: Wi-Fi interface preference, subnet scan across ports 9090, 6466, 6467, 8001, 8002, 3000, 3001, 8060, 5555.

### UI
- `MainActivity.kt`: target chooser, truthful button enabling, alphanumeric TV code, trackpad gestures, air mouse dispatch.

### Sensitivity
- `BluetoothController.kt`: default sensitivity reduced and motion scaling lowered to reduce manic cursor movement.

## Known limitations
- Android TV / Mi Box have no native pointer injection in stock ATRPv2.
- Bluetooth HID is not implemented.
- TV hardware not available for live acceptance testing.