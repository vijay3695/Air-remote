# Air Remote — Agent Repair Handoff

**Repository:** https://github.com/vijay3695/Air-remote  
**Target branch:** `main`  
**Prepared:** 2026-11-09  
**Purpose:** Give a competent coding agent a concrete, testable repair brief. This document is a handoff, not a claim that the app has been repaired.

## 0. Read these first

Before changing anything, read the full current versions of:

- `docs/REFERENCE.md`
- `docs/Error.md`
- `docs/README.md`
- `README.md`
- `.github/workflows/android-build.yml`
- `.github/workflows/release-apk.yml`

Then inspect the complete current source and git history. Some sections of `docs/Error.md` describe a **historical pre-repair snapshot**; do not blindly apply those old findings. Verify every issue against the current `main` source.

The owner has repeatedly reported that previous repairs did not make the app work. Treat the owner's device results as authoritative over optimistic comments, logs, or old assistant reports.

## 1. Owner's actual requirements and current reported status

The app is intended to be a phone-based remote with:

- Windows PC Wi-Fi Air Mouse (the only feature the owner has reported working, but it needs calibration and has recently regressed).
- Android TV / Google TV remote control and pairing.
- A real TV Air Mouse / Magic Remote-like pointer experience, not merely a pointer coordinate display on the phone.
- Later/secondary targets: LG webOS, Samsung Tizen, Roku OS.
- Honest target-specific capabilities; no generic “works on all TVs” claim.

**Owner-reported baseline:** Windows Air Mouse previously worked (with calibration). TV pairing/control was not working. The owner says recent changes have broken things and wants the entire project repaired. Verify the Windows regression immediately before broad refactoring.

## 2. Critical product distinction: “Air Mouse” is not just sending gyro deltas

The app must not show or claim a TV cursor merely because:

- the phone sensor emits motion,
- local phone coordinates change,
- a socket is open,
- bytes were written,
- a protocol callback ran,
- CI builds successfully.

A TV cursor appears only if the selected TV platform has a real pointer-input mechanism and the TV accepts it.

### Platform capability reality

- **Windows PC:** The app can move the Windows cursor through its companion receiver. This is the primary working Air Mouse path to restore first.
- **LG webOS:** Has a native SSAP pointer channel on compatible TVs. This is the most plausible native TV pointer implementation. Registration, pointer-socket readiness, frame format, click/scroll and disconnect behavior must be verified against a real webOS TV.
- **Android TV / Google TV:** Android TV Remote v2 is a remote-key/text/state protocol; it does **not** provide a standard general-purpose gyro mouse pointer injection command. Do not invent a protobuf field or HTTP fallback. A TV-side companion app/accessibility approach or a genuine Bluetooth HID mouse path would be a separate feature with additional setup/limitations. The phone-only app cannot promise a cursor on arbitrary Android TV devices using ATRPv2 alone.
- **Samsung Tizen:** Do not claim a generic mouse cursor unless a supported native pointer/HID route is implemented and tested. Remote key protocol is not equivalent to pointer input.
- **Roku OS:** ECP remote commands do not provide a generic gyro pointer. Do not claim Magic Remote functionality.
- **Bluetooth HID:** “Phone acts as Bluetooth mouse” requires the Android phone to act as a HID peripheral/device, not merely connect as a Bluetooth client. Verify platform/API/device feasibility. If unavailable, label it unavailable rather than falsely connected.

The UI must distinguish **TV remote control**, **trackpad/focus navigation**, and **actual air-mouse pointer** as separate capabilities.

## 3. Repair order — do not skip ahead

### Phase A — Establish a clean, reproducible baseline

1. Record the current `main` SHA and inspect the latest CI run for that exact SHA.
2. Build the existing project without changing code; record exact build result and warnings.
3. Inspect app crash logs, test failures, GitHub Actions workflow logs, and the owner's documented errors.
4. Check for duplicate protocol connection attempts, stale IP auto-connect, false connection states, and UI commands enabled while disconnected.
5. Produce a short baseline table: feature, current source path, observed result, evidence, status.
6. Do not edit/release until the baseline is documented.

### Phase B — Restore Windows PC Air Mouse first (regression blocker)

Relevant files to inspect:

- `app/src/main/java/com/vijay/aimagicremote/protocol/PcRemoteProtocol.kt`
- `app/src/main/java/com/vijay/aimagicremote/ui/MainActivity.kt`
- `app/src/main/java/com/vijay/aimagicremote/bluetooth/BluetoothController.kt`
- Windows receiver script(s), especially `pc_receiver.ps1` if present.
- Windows receiver setup and protocol documentation.

Tasks:

1. Trace one gyro sample end-to-end: sensor → calibration/filter → pointer callback → target routing → TCP JSON line → receiver parser → Windows cursor API.
2. Confirm phone and receiver agree on packet schema, line framing, coordinate units, sensitivity, and command names.
3. Keep all receiver writes serialized; prevent concurrent writes from corrupting newline-delimited JSON.
4. Make receiver startup, Windows firewall, network profile, IP/port, and permission instructions explicit.
5. Verify discovery and TCP command path separately. UDP discovery must not be mistaken for command delivery.
6. Check pointer movement, click, double-click, drag, scroll, D-pad, keyboard/text, connection loss and reconnect separately.
7. Fix calibration as a real guided process: neutral pose, sample collection, stability validation, recenter, test movement, save settings. Do not say “calibrated” after merely clearing a bias/filter.
8. Do not change the Windows wire protocol without updating both Android and receiver and adding contract tests.

**Acceptance:** On a real Windows PC with the receiver running, the owner can connect, calibrate, and move the actual Windows cursor smoothly; click/scroll and other claimed commands are separately verified. No phone-only coordinate display counts.

### Phase C — Android TV / Google TV remote (not cursor) pairing and keys

Relevant files:

- `Atrpv2Protocol.kt`
- `AtvWire.kt`
- `RemoteManager.kt`
- pairing UI in `MainActivity.kt`
- `ProtocolAndSensorUnitTest.kt`

Use a proven implementation as the protocol source of truth, not guessed packets. Compare against the current `tronikos/androidtvremote2` project and/or another actively tested implementation. Verify license and preserve required notices before copying/adapting code.

Reference: https://github.com/tronikos/androidtvremote2

Requirements:

1. Correct pairing protobuf schema, varint-delimited framing, TLS client certificate generation and persistence, code/secret derivation, and certificate reuse.
2. Correct control-channel handshake and readiness state machine; do not mark connected just after TLS or socket creation.
3. Keep a persistent read loop and handle TV ping/keepalive, errors, disconnects, and reconnects.
4. Pairing UI must wait for actual TV protocol events: request → options/configuration → TV displays code → user enters code → secret accepted → certificate trusted → remote session ready.
5. Wrong code, timeout, rejection, TV not reachable, stale pairing and retry must show useful errors and not falsely report success.
6. Verify D-pad, Back, Home, volume, power (if supported), text/IME and state notifications individually.
7. Test against at least one real Android TV/Google TV device. Emulator or mock success is useful but is not hardware acceptance.
8. Keep Android TV Air Mouse explicitly marked **not supported by ATRPv2** until a separate viable cursor solution is actually implemented and tested.

### Phase D — LG webOS pointer support

Relevant file: `LgWebOsProtocol.kt`.

1. Implement and test SSAP registration, pairing prompt approval, client-key persistence and reconnection.
2. Check every response/error; distinguish registered/authorized from merely WebSocket-open.
3. Request only the permissions needed for implemented commands.
4. Acquire the pointer socket only after the TV's valid response and parse its returned socket path.
5. Confirm pointer socket is actually ready before sending pointer frames.
6. Verify the exact pointer protocol/frame format against a real webOS TV and reliable implementation.
7. Test movement, click, scroll, key navigation, socket failure, TV rejection, reconnect, and client-key reuse.
8. Do not send invented pointer bytes or claim pointer support based on a local callback.

### Phase E — Samsung Tizen / Roku, only after A–D are stable

**Samsung Tizen**
- Correct WebSocket authorization/approval, token capture and secure persistence, reconnect using token, and response handling.
- Do not claim cursor support unless a real supported pointer mechanism is proven.
- Test supported commands against real TV models/firmware.

**Roku OS**
- Keep Roku ECP separate from TCL Android TV/Google TV.
- Verify ECP permission, device identity, HTTP status and command response.
- ECP is remote control, not general pointer injection.
- Test supported keys/text on real Roku hardware.

Do not launch all TV protocols simultaneously from one target selection. User must select platform or explicitly confirm a well-founded detection result.

### Phase F — Fix UI, state, and truthfulness across all targets

Use one explicit state machine, e.g.:

`Disconnected → Discovering → Connecting → Pairing/Authorizing → Connected/Ready → Reconnecting → Failed`

Requirements:

- Only the selected platform protocol is active.
- “Connected” means protocol-level ready, not Bluetooth enabled, IP found, TLS opened, or WebSocket opened.
- Disable/gate commands when disconnected or not authorized.
- Surface meaningful error messages to the user.
- Discovery, platform identification, pairing, connection readiness, and command acceptance are separate states.
- Remove decorative/fake QR pairing. Only show QR if it is a real encoded payload and a real supported TV-side scanner exists.
- Remove or rename nonfunctional Bluetooth HID and unsupported Air Mouse controls.
- Never use logs such as “sent”, “delivered”, “verified”, or “ACK” unless that precise event is proven. Use “write attempted” when that is all the code knows.

## 4. Architecture / implementation rules

- Avoid a large rewrite until Windows is restored and current defects are reproduced.
- Keep each protocol isolated behind a platform-specific adapter with clear supported capabilities.
- Keep protocol code testable without Android UI where possible.
- Avoid blocking the main thread. Serialize writes per socket and preserve frame boundaries.
- Handle cancellation, timeout, socket closure, reconnect, and stale connection callbacks.
- Do not let one protocol's failure/connected callback overwrite another target's state.
- Do not auto-connect to stale saved IPs without confirming target identity.
- Use secure storage for TV tokens/client keys/certificates; never log secrets.
- Check MIT/Apache/GPL license terms before reusing any candidate code. GPL components may impose source-sharing obligations; do not copy them casually.
- Do not change dependencies/build settings/workflows just to make CI green. Keep changes minimal and evidence-driven.
- No cloud/server backend is needed; local Wi-Fi control and local Windows receiver are the intended architecture.

## 5. Test strategy (required)

### Unit tests
- Varint framing, split reads, malformed/oversized frame handling.
- Protobuf message encoding/decoding and protocol payloads.
- Android TV pairing-secret derivation, RSA unsigned byte conversion, certificate persistence.
- Windows JSON schema and newline framing.
- Sensor-to-pointer math, orientation, calibration, filtering, dead-zone and sensitivity.
- Connection state machine transitions and failure paths.
- LG pointer-frame serialization and response parsing.
- TV target selection ensures only one protocol is active.

### Integration / mock tests
- Mock TCP server for Windows receiver: accepts discovery, TCP connection, newline JSON commands, malformed input and disconnect.
- Mock TLS/protobuf server for Android TV pairing/control where practical.
- Mock WebSocket registration and pointer socket for LG.
- Test reconnect and timeout behavior, not just happy path.

### Real hardware acceptance
Record exact TV/PC model, OS/firmware, network, app version, steps and outcome.

| Device target | Required test |
|---|---|
| Windows PC | Air Mouse movement, calibration, click, scroll, keyboard, disconnect/reconnect |
| Android TV / Google TV | Pairing, D-pad, Back/Home, volume, text, reconnect |
| LG webOS | Pairing/registration, D-pad, pointer movement/click/scroll |
| Samsung Tizen | Pairing/authorization, supported keys, reconnect |
| Roku OS | ECP authorization and supported remote keys/text |

No hardware available? Mark the corresponding test **NOT TESTED**. Do not call it ready/live-tested.

## 6. CI and release requirements

1. Run the existing CI on the exact final commit SHA.
2. Ensure unit tests run in CI, not just compilation.
3. Inspect workflow logs for the exact run and commit; do not rely on an older green run.
4. Build debug APK and install/test it before release.
5. Release APK only after the core Windows path is verified and no known regression remains.
6. APK artifact/release must point to the exact source commit and include clear testing status.
7. Never say “all fixed”, “fully working”, or “ready for live testing” if real-device acceptance is incomplete. Report exactly which targets are tested, untested, unsupported, or partially working.

## 7. Definition of done

The project is **not done** until all are true:

- [ ] Windows Air Mouse regression fixed and verified on real Windows hardware.
- [ ] Calibration is understandable and reliable.
- [ ] Android TV/Google TV pairing succeeds on real hardware.
- [ ] Android TV key/text controls work on real hardware.
- [ ] Android TV pointer capability is either implemented through a real supported mechanism or clearly unavailable (never faked).
- [ ] LG pointer is verified on real webOS hardware before being advertised.
- [ ] Samsung/Roku controls are separately verified and correctly platform-gated.
- [ ] No false “connected” state or misleading feature labels remain.
- [ ] Unit/integration tests pass.
- [ ] CI passes for the exact final commit.
- [ ] README and docs match actual verified behavior.
- [ ] APK release is traceable to the tested commit.

## 8. Final report the coding agent must provide

At completion, report:

1. Commit SHA(s) and changed file list.
2. Root cause of each reproduced bug.
3. Exact tests run and pass/fail output.
4. Device/model/OS for each live test.
5. Features fixed and features still unsupported/unverified.
6. CI run URL and commit SHA.
7. APK release URL only if actually built and uploaded.
8. Remaining blockers and user actions required.

**Do not mark tasks complete based on source edits alone. Do not make up hardware test results.**

## Useful protocol references

- Android TV Remote v2 reference implementation: https://github.com/tronikos/androidtvremote2
- Current protocol write-up and real-device testing notes: https://github.com/sonalder-darlene/libremote-android-tv/blob/main/PROTOCOL.md
- Air Remote's own research/known issues: `docs/REFERENCE.md` and `docs/Error.md`

The reference implementations and third-party write-ups are technical evidence, not automatic permission to copy code. Verify licenses and preserve notices.
