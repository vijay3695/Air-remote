# Air Remote — Error Report and Recovery Plan

**Repository:** [vijay3695/Air-remote](https://github.com/vijay3695/Air-remote)  
**Report date:** 2026-11-09  
**Purpose:** Record the currently observed failures, source-level defects, protocol research, and a practical repair plan before implementation.

> **Current real-world status reported by the project owner:** Only **Windows Air Mouse** works. It requires calibration. All other remote-control functions are currently considered non-working. TV connection/pairing by code does not work.
>
> This document is a diagnosis and plan, not a claim that fixes have been implemented or tested.

## 1. Scope and constraints

- Do not edit application source code as part of this report.
- Do not run Gradle or change any Gradle file, wrapper, dependency, build setting, or build configuration.
- Do not claim a feature works merely because a socket opens, a packet is sent, or a callback fires.
- Treat Windows Air Mouse as the one owner-reported working path, with calibration still required.
- Establish Android TV / Google TV first. Add other TV brands only after the first platform is genuinely working.
- Keep all user-facing connection, pairing, and command status truthful.
- Verify on real hardware, not only mocks or source inspection.

## 2. Current functional status

| Feature | Current status | Notes |
|---|---|---|
| Windows Air Mouse over Wi-Fi | **Works according to owner** | Calibration is required; motion quality and calibration behavior still need a controlled test. |
| Windows D-pad / buttons / text / trackpad clicks | **Not working / unverified** | Do not assume the PC receiver accepts every payload merely because Air Mouse movement works. |
| Android TV / Google TV connection | **Not working** | Pairing and remote protocol implementation have major defects. |
| Android TV / Google TV pairing code | **Not working** | Current code does not implement the real pairing sequence or code verification. |
| Android TV / Google TV D-pad, volume, power, text | **Not working** | Connection and command framing/authentication are not reliable. |
| Android TV pointer / trackpad | **Not working** | The app attempts unsupported/unverified pointer frames and invented HTTP fallbacks. |
| Samsung Tizen TV | **Not working** | Connection approval/token handling and command routing are incomplete. |
| LG webOS TV | **Not working** | Pairing/client-key registration and pointer-channel setup are missing. |
| TCL / Roku TV | **Not working** | Basic ECP calls exist, but the app routes commands incorrectly and has not verified network-control permission or model compatibility. |
| Bluetooth HID phone-as-mouse/keyboard | **Not working** | Current Bluetooth target is not a Bluetooth HID host connection. |
| TV discovery | **Not reliable / unverified** | Discovery results do not guarantee the discovered device supports the selected protocol or is paired. |
| QR pairing | **Broken** | The QR bitmap generator draws a decorative pattern; it does not encode the pairing payload as a standards-compliant QR code. |

## 3. Confirmed source-level defects

These findings come from reading the current source. They explain why the app can report success without actually controlling a device.

### 3.1 Connection state is falsely reported as successful

**File:** `app/src/main/java/com/vijay/aimagicremote/protocol/RemoteManager.kt`

In the Android TV connection failure callback, the implementation sets `isConnected = true` and calls `onConnected()` instead of reporting the failure.

Consequences:
- A failed Android TV connection is shown as successful.
- The UI can enable remote buttons even though no valid TV session exists.
- The user cannot distinguish a wrong IP, unavailable TV, pairing requirement, protocol mismatch, or rejected connection.

**Required fix:** A connection must only become connected after the selected protocol completes its real handshake and receives a valid protocol-level ready/acknowledgement. Propagate the actual failure to the UI. Never convert failure into success.

### 3.2 Android TV Remote Protocol v2 (ATRPv2) is not implemented correctly

**File:** `app/src/main/java/com/vijay/aimagicremote/protocol/Atrpv2Protocol.kt`

The implementation is not interoperable with the Android TV Remote Service v2 protocol as documented by working implementations.

Major issues:

1. **Wrong message structure.** The code treats a frame as a raw one-byte message type followed by a nested protobuf payload. The actual protocol uses an outer protobuf message containing protocol version, status, and one payload field. The current framing/message layout is incompatible.
2. **Pairing sequence is incomplete.** The current pairing code sends a PairingRequest and then waits for a PIN. The real pairing flow requires the request, options, and configuration messages, including negotiated input/output encoding and client role.
3. **PIN is not the pairing secret.** The implementation sends the six-character PIN as a string. The real protocol derives a SHA-256 secret from the client and TV RSA public-key components plus the TV-provided nonce/code bytes, then sends the secret in the proper protobuf message.
4. **Pairing response parsing is incorrect.** It assumes a simple string or integer payload rather than decoding the actual outer message, status, and typed protobuf payload.
5. **Pairing and control TLS identity are mishandled.** Pairing and remote channels require the client certificate identity to be presented and the same trusted identity to be reused. The current pairing SSL context has no client key managers. Control-channel success is also treated as pairing success without proof that the TV has trusted the certificate.
6. **The control connection skips protocol readiness.** After TLS, the app writes a guessed Configure frame and immediately treats the socket as ready. It does not correctly decode TV Configure/SetActive/error messages or wait for the actual ready state.
7. **The fallback TCP connection is invalid.** The implementation tries plain TCP on port 6466 after TLS fails. The Android TV Remote Service v2 control channel is TLS-authenticated; plain TCP is not a valid fallback.
8. **The fallback HTTP/UDP commands are invented.** Calls such as `http://TV:8080/remote/key` and UDP strings like `KEY:<code>` are not standard Android TV Remote Service commands. They will not control a stock Android TV unless a separate custom receiver exists on the TV.
9. **No reliable server acknowledgement.** Checking `InputStream.available()` does not prove the TV accepted or executed a command. It only reports currently buffered bytes.
10. **Socket lifecycle and reconnect are incomplete.** No robust protocol reader, state machine, heartbeat/ping handling, error decoding, or paired-certificate recovery exists.
11. **Pointer injection is not a verified feature of the standard remote service.** The app emits guessed MotionEvent protobuf fields and assumes the TV will accept them. Standard Android TV Remote Service is primarily for remote keys/text/media; generic phone gyro mouse injection is not guaranteed. Pointer support needs a separate supported mechanism or TV companion app.

**Required fix:** Replace the hand-guessed ATRPv2 implementation with a proven, license-compatible implementation or port its complete protocol state machine. Do not patch isolated bytes until the message schema and pairing state machine are correct.

### 3.3 Pairing UI starts and submits pairing out of order

**File:** `app/src/main/java/com/vijay/aimagicremote/ui/MainActivity.kt`

The pairing dialog:
- Starts pairing asynchronously.
- Immediately presents a code-entry dialog.
- Calls code submission and then `connectToDevice()` without waiting for pairing success.
- Does not correctly coordinate the pairing socket/session lifetime.
- Does not reliably display the actual TV pairing code prompt/state.
- May start the remote connection before the TV has stored the client certificate.

**Required fix:** Implement pairing as an explicit UI state machine: Connecting → Negotiating → TV code prompt → User enters code → Secret verified by TV → Certificate trusted → Remote channel connects → Ready. Each transition must wait for the previous protocol result. Display errors and allow retry/cancel.

### 3.4 QR pairing is not a real QR code

**File:** `app/src/main/java/com/vijay/aimagicremote/ui/MainActivity.kt`

`generateQrCodeBitmap()` paints a hand-made pixel pattern. It does not encode the supplied URI or payload and is not scannable as a QR code.

The shown `air_remote://pair?... ` payload also has no working TV-side scanner/handler in this app.

**Required fix:** Remove the misleading QR pairing option until there is a real QR encoder and a supported receiver/scanner flow. Do not advertise “scan to pair” unless an actual TV-side flow exists.

### 3.5 Samsung Tizen protocol is incomplete

**File:** `app/src/main/java/com/vijay/aimagicremote/protocol/SamsungTizenProtocol.kt`

Issues:
- No persistence or reuse of the authorization token returned after the TV approves the first WebSocket connection.
- Does not decode the WebSocket welcome/authorization response and capture the token.
- Reconnects without a token, potentially requiring repeated approval or failing.
- Does not clearly handle TV approval prompt, rejection, or timeout.
- Sends remote key payloads without checking whether the connection is actually authorized.
- The fallback HTTP GET to `/api/v2/` does not send a remote key and cannot replace the WebSocket command.
- The app launches Samsung, LG, Roku, and Android TV connection attempts simultaneously for a generic Android TV target, which can cause misleading state and unwanted requests.

**Required fix:** Use the Samsung WebSocket API flow: connect to the correct port (8001 or 8002 according to TV support), request authorization, wait for approval, capture and securely persist the returned token, reconnect with it, and send supported key commands only on the authorized socket. Handle token revocation and pairing retry.

### 3.6 LG webOS protocol is incomplete

**File:** `app/src/main/java/com/vijay/aimagicremote/protocol/LgWebOsProtocol.kt`

Issues:
- Opens a WebSocket but does not implement the required SSAP registration/pairing request.
- Does not parse and save the TV-issued `client-key`.
- Does not reuse the client key on later connections.
- Does not request the appropriate permissions for keyboard/mouse/pointer control.
- D-pad commands are mapped to unrelated media-play behavior instead of real navigation-key messages.
- Power, Home, input, and mute commands are sent without checking registration, authorization, or response status.
- LG navigation keys are not generally sent through the generic SSAP URI path. Compatible TVs use the registered pointer/input channel and its required button frames.
- Does not establish or manage the second pointer socket returned by the TV.
- No robust handling of TV approval prompt, connection timeout, rejection, or reconnect.

**Required fix:** Implement the complete SSAP registration and client-key persistence flow. Request appropriate permissions and use the correct SSAP URIs for supported actions. Implement the pointer socket for navigation keys only after registration grants it. Test on a real webOS TV and document version-specific limitations.

### 3.7 TCL / Roku ECP implementation is not integrated correctly

**File:** `app/src/main/java/com/vijay/aimagicremote/protocol/RokuTclProtocol.kt`

Issues:
- The current target selection does not include a separate Roku/TCL target; these calls are fired alongside other TV protocols.
- It assumes every TCL TV uses Roku OS/ECP. TCL Android TV and Google TV models do not use Roku ECP.
- Network-control authorization and model support are not checked.
- Connection success is based on an HTTP device-info response but is not reflected in the unified connection state.
- `Power` mapping uses `PowerOff`; it cannot correctly express a power toggle/state-dependent operation.
- Errors from key commands are only logged and not surfaced to the UI.

**Required fix:** Separate Roku OS/ECP from TCL Android/Google TV. Detect or ask the user for the TV platform, verify ECP access, check HTTP response codes, map commands according to Roku ECP, and expose failures. Do not call Roku commands on unrelated TV brands.

### 3.8 Bluetooth HID mode is not implemented

**Files:** `BluetoothController.kt`, `RemoteManager.kt`

The Bluetooth target only checks whether Bluetooth is enabled. It marks the target connected when Bluetooth is available, but does not create a Bluetooth HID connection that makes the phone act as a mouse/keyboard.

Air Mouse sensor input is separate from Bluetooth HID. The working Windows Air Mouse uses the PC Wi-Fi receiver path.

**Required fix:** Either implement a genuine supported Bluetooth HID Device profile (phone acts as HID peripheral; requires compatible Android APIs/device support and pairing) or remove/rename the current Bluetooth HID target so it does not claim functionality it lacks. Do not silently route Bluetooth mode to unrelated TV/PC protocols.

### 3.9 Windows PC remote receiver contract is missing or undocumented

**File:** `app/src/main/java/com/vijay/aimagicremote/protocol/PcRemoteProtocol.kt`

The phone sends custom newline-delimited JSON commands to TCP port 9090, or tries UDP. This is not a built-in Windows remote-control protocol. A matching receiver program must be installed and running on the Windows PC.

Current Windows Air Mouse works according to the owner, but the following remain unverified:
- Which receiver application/version is listening on port 9090.
- Whether it accepts the exact JSON fields and newline framing.
- Whether TCP is used consistently or commands silently fall back to UDP.
- Whether Windows remote buttons, click, scroll, and text share the same receiver implementation.
- Whether receiver firewall rules and permissions are required.

**Required fix:** Document the exact receiver, protocol schema, install/run steps, firewall requirements, and supported commands. Add connection handshake and command acknowledgements to the phone/receiver protocol. Do not report a command as delivered just because the local socket write succeeded.

### 3.10 Air Mouse calibration is not a complete calibration workflow

**File:** `app/src/main/java/com/vijay/aimagicremote/bluetooth/BluetoothController.kt`

Owner-reported behavior: Windows Air Mouse works but requires calibration.

Source concerns:
- The “calibrate” button resets bias and filter state; it does not perform a user-guided calibration capture.
- Bias is estimated continuously during stillness, which can be affected by how the user holds the phone.
- Motion is suppressed while stillness is detected; movement immediately after calibration may be ignored or feel delayed.
- Sensor axes and screen orientation are assumed rather than explicitly calibrated for the user's grip/orientation.
- Sensitivity and dead-zone thresholds are hard-coded/tuned values without a calibration wizard or saved profile.
- Rotation-vector data is used for projection, but the implementation does not provide a clear orientation/recenter reference or explain the calibration pose.
- The UI reports pointer deltas rather than actual Windows cursor position, so it cannot verify end-to-end cursor alignment.

**Required fix:** Add a deliberate calibration process: user places phone in a specified neutral pose, hold still for a measured interval, collect gyro bias samples, validate stability, set a recenter/orientation reference, then test horizontal/vertical movement. Add recalibrate/reset controls, sensitivity presets, and saved per-device settings. Make calibration state visible and avoid claiming calibration succeeded without sensor validation.

## 4. Cross-cutting application defects

### 4.1 One target selection triggers several unrelated protocols
The Android TV selection starts Roku, Samsung, LG, and Android TV connections simultaneously. That is not multi-brand support; it is protocol spraying.

**Fix:** Make target/platform selection explicit (Android TV/Google TV, Samsung Tizen, LG webOS, Roku OS, Windows receiver, Bluetooth HID). Run only the selected protocol. A platform auto-detection stage may suggest a target, but must not mark it connected until the selected protocol succeeds.

### 4.2 UI status is not a source of truth
Remote buttons remain enabled even when disconnected. The UI's local `isConnected` and the protocol object's state can disagree. Error callbacks discard detailed error text.

**Fix:** Use one connection state model (Disconnected, Discovering, Connecting, Pairing, Connected, Reconnecting, Failed) and show actionable error details. Disable or clearly gate commands until the protocol is ready.

### 4.3 Auto-connect can connect to a stale saved IP
The app automatically reconnects to the last saved IP after discovery. A stale IP can cause connection attempts to the wrong device or wrong TV platform.

**Fix:** Show the remembered device and ask for confirmation, or verify identity/platform before auto-connecting. Track a device identity where the protocol exposes one, not only an IP address.

### 4.4 Discovery is not pairing
Finding an IP, mDNS service, SSDP device, or open port does not mean the TV is paired or remotely controllable.

**Fix:** Separate discovery, protocol detection, authorization/pairing, connection readiness, and command testing.

### 4.5 No meaningful integration tests against actual protocols
The current test file is described as protocol/sensor unit testing, but no actual TV or Windows receiver test has been demonstrated in this report. Protocol frame tests alone cannot prove a TV accepts commands.

**Fix:** Add deterministic protocol unit tests plus test fixtures/mock servers, then perform manual real-device acceptance tests. Record TV model, OS/version, app/service version, network conditions, pairing steps, and results.

### 4.6 Logs and UI claim delivery without proof
Some log messages say “delivered”, “ACK confirmed”, or “verified” after socket writes, `available()`, or UDP send. These are not protocol acknowledgements.

**Fix:** Use truthful terminology: “write attempted” vs “TV accepted” vs “command action confirmed”. Only claim confirmation when the remote protocol returns a valid acknowledgement or observable state change.

## 5. Research and reference-file review

### 5.1 Existing `REFERENCE.md` is useful as a research index, but is not an implementation specification

The repository's `REFERENCE.md` is a good starting point for candidate projects and prioritization. It identifies relevant open-source projects and correctly emphasizes Android TV/Google TV first, calibration/filtering, and companion-app reliability.

However, it should **not be followed blindly**:

- Its ATRPv2 description is too simplified for the current implementation. Pairing is not simply “send a request, receive a PIN, send the PIN.” The protocol requires proper protobuf outer messages, options/configuration negotiation, a client certificate, a nonce/code-derived secret, and a persistent trusted certificate.
- Its listed pairing hash description should be reconciled against a current working implementation and protocol evidence. The TV code includes a nonce/code component; sending the displayed code as a plaintext string is incorrect.
- It describes pointer control through Android TV as an investigation item, not a guaranteed supported feature. The current implementation incorrectly treats pointer injection as established.
- It lists multiple open-source candidates but does not establish that the current Air Remote code actually reuses their proven protocol implementation.
- It contains ambitious feature/status statements that conflict with the observed app status. Mark each feature “planned”, “implemented”, or “verified on device” separately.
- It describes a companion app and fallback strategies as complete in places, but the current repo does not have a functioning TV companion app capable of injecting system pointer events.
- It references licenses; verify the actual license file and the exact code being reused before copying or porting. Preserve attribution and license notices when code is actually reused.

### 5.2 Best references to use for recovery

Use these as protocol implementation references, not as proof that Air Remote works:

- **Android TV / Google TV:** [Outofcontext Android TV Remote](https://github.com/outofcontextapps/androidtvremote), [libremote-android-tv protocol notes](https://github.com/sonalder-darlene/libremote-android-tv/blob/main/PROTOCOL.md), and [Gatuque/tv-remote](https://github.com/Gatuque/tv-remote). Compare complete pairing, certificate handling, protobuf framing, ready-state handling, and key command behavior.
- **Windows Air Mouse:** [Linkpad](https://github.com/Devdas-gupta/linkpad) for sensor-to-pointer behavior, filtering, calibration ideas, and Bluetooth HID reference. Confirm the repository license before reuse.
- **Gyroscope math:** [AimMac](https://github.com/Coolerwave/AimMac) for motion mapping and smoothing concepts. It is a reference for math/protocol learning, not a direct Android TV protocol implementation.
- **LG webOS:** [tv-rs-webos docs](https://docs.rs/tv-rs-webos/latest/tv_rs_webos/) and [LG remote reference implementation](https://github.com/MysterWolf/lg-remote). Focus on registration/client-key persistence and the separate pointer socket.
- **Samsung Tizen:** [Samsung TV WebSocket remote reference](https://github.com/pkapteijn/samsungtv-rc). Focus on first-time TV authorization, returned token, token reuse, and key payload.
- **Multi-brand architecture:** [Universal-TV-Remote](https://github.com/arman0824/Universal-TV-Remote) for platform separation and discovery/pairing patterns. Verify license and actual implementation details before reuse.

**Reference conclusion:** Yes, `REFERENCE.md` can guide the repair. It is not enough by itself to fix the current code. The project should first adopt one proven Android TV Remote v2 implementation, test it end-to-end, and only then add other brands one at a time.

## 6. Recommended repair order

### Phase 0 — Truthful baseline and safety
1. Keep the current Windows Air Mouse path as the only known working feature.
2. Record the exact Windows receiver used and its current protocol.
3. Correct feature/status documentation to distinguish “owner-tested”, “source-only”, “planned”, and “verified”.
4. Remove false success callbacks and fake QR pairing before more protocol work.
5. No Gradle changes unless separately approved and proven necessary.

### Phase 1 — Android TV / Google TV remote buttons (first priority)
1. Select one proven ATRPv2 implementation from the reference list and check its license.
2. Implement correct client identity/certificate persistence.
3. Implement full pairing negotiation and code-derived secret calculation.
4. Verify TV certificate handling and remote-channel mutual TLS.
5. Decode protocol responses and wait for the real ready state.
6. Implement D-pad, OK, Back, Home, volume, mute, power, and text only after pairing/connection is confirmed.
7. Remove invalid plain-TCP, HTTP, and UDP fallbacks.
8. Add truthful error reporting and reconnect handling.
9. Test on at least one real Android TV and one Google TV, recording versions and results.

**Acceptance:** TV shows the pairing prompt; code is accepted; app reconnects without repeated pairing; D-pad commands change focus; Back/Home work; volume/power are verified where supported; failure cases show a real error rather than “Connected”.

### Phase 2 — Windows Air Mouse calibration and full receiver validation
1. Identify and document the PC receiver.
2. Add a stable calibration wizard and explicit neutral pose/recenter.
3. Test movement direction, sensitivity, drift, latency, and cursor boundaries.
4. Verify clicks, right-click, scroll, keyboard, and text separately.
5. Add a receiver handshake and acknowledgement if the protocol can be updated compatibly.

**Acceptance:** User can calibrate once, move the cursor without drift, recenter, and use buttons reliably. Document what the receiver actually supports.

### Phase 3 — LG webOS
1. Implement registration prompt and client-key storage.
2. Request correct permissions for keyboard/mouse control.
3. Use proper SSAP URIs for volume, power, and supported actions.
4. Implement pointer socket navigation and its exact frame format.
5. Test real TV approval, reconnect, key commands, and version limitations.

### Phase 4 — Samsung Tizen
1. Implement authorization prompt and token capture.
2. Persist and reuse token safely.
3. Handle rejection, token invalidation, reconnect, and secure port behavior.
4. Test real navigation, volume, Home, Back, and power behavior.

### Phase 5 — Roku OS / TCL variants
1. Make Roku OS a separate target.
2. Verify ECP availability and network-control setting.
3. Implement commands and response validation.
4. Keep TCL Android/Google TV on the Android TV protocol, not Roku ECP.

### Phase 6 — Bluetooth HID and pointer architecture
1. Decide whether Android Bluetooth HID Device support is feasible on the app's supported Android versions/devices.
2. Implement actual HID profile/pairing if feasible; otherwise remove misleading target.
3. Investigate Android TV pointer support separately from D-pad remote protocol.
4. If a TV companion app is required, prototype it as a separate app and test whether the TV OS allows the needed input injection.
5. Do not promise universal Air Mouse support until hardware tests establish it.

## 7. Manual test matrix (must be filled during implementation)

| Test | Device / version | Expected | Actual result | Evidence |
|---|---|---|---|---|
| Windows Air Mouse calibration | Windows receiver + phone model | Stable neutral, low drift | Owner reports calibration required | Record receiver/version and calibration notes |
| Windows cursor movement | Same | Direction/sensitivity correct | Owner reports Air Mouse works | Screen recording / test notes |
| Android TV pairing | TV model + Android TV version | TV code prompt, accepted pairing | Currently fails | Logcat + protocol state |
| Android TV D-pad | Paired TV | Focus moves correctly | Currently fails | Test notes |
| Android TV volume/Home/Back | Paired TV | Correct action | Currently fails | Test notes |
| Google TV pairing | TV model + Remote Service version | Pair and reconnect | Currently fails | Test notes |
| LG registration | LG model + webOS version | Approval prompt/client key saved | Currently fails | Test notes |
| LG D-pad/pointer | Paired LG | Correct pointer/key behavior | Currently fails | Test notes |
| Samsung authorization | Samsung model + Tizen version | Approval/token persisted | Currently fails | Test notes |
| Samsung key commands | Paired Samsung | Navigation responds | Currently fails | Test notes |
| Roku ECP | Roku model/software | ECP allowed and key responds | Currently fails | HTTP response/test notes |
| Bluetooth HID | Phone model + TV model | TV sees phone as mouse/keyboard | Not implemented | Pairing/device test |
| QR pairing | Actual scanner/receiver | Scans real payload and starts supported flow | QR generator is fake | Scan test |

## 8. Definition of done

A platform is **working** only when all of the following are true:

- The app connects to the correct TV platform and completes required authorization/pairing.
- The UI reports Connected only after protocol readiness.
- At least D-pad, OK, Back, Home, and applicable volume/power commands are verified on a real device.
- Disconnect/reconnect and wrong-code/wrong-IP failures are handled honestly.
- The result is repeatable after app restart.
- Tests and device/version details are recorded.
- README and project docs match actual tested behavior.

**Do not mark a platform complete because its source file exists.**

## 9. Sources

- [Android TV Remote Protocol v2 — libremote protocol notes](https://github.com/sonalder-darlene/libremote-android-tv/blob/main/PROTOCOL.md)
- [Gatuque Android TV Remote v2 implementation](https://github.com/Gatuque/tv-remote)
- [Outofcontext Android TV Remote](https://github.com/outofcontextapps/androidtvremote)
- [LG webOS SSAP implementation docs](https://docs.rs/tv-rs-webos/latest/tv_rs_webos/)
- [LG webOS reference remote](https://github.com/MysterWolf/lg-remote)
- [Samsung TV WebSocket remote reference](https://github.com/pkapteijn/samsungtv-rc)
- [Universal-TV-Remote multi-brand reference](https://github.com/arman0824/Universal-TV-Remote)
- [Linkpad motion/HID reference](https://github.com/Devdas-gupta/linkpad)
- [AimMac motion reference](https://github.com/Coolerwave/AimMac)

---

*Prepared after source inspection and protocol-reference research. No application source code or Gradle files were changed as part of this report.*
