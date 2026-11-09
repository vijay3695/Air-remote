# Air Remote — Air Mouse Repair Handoff for Coding Agent

**Repository:** https://github.com/vijay3695/Air-remote  
**Branch:** `main`  
**Updated:** 2026-11-09

## 1. The actual goal — read this before doing anything

**This project is an AIR MOUSE for TVs and Windows PCs. A conventional TV remote is not the product goal.**

The phone should work like a Magic Remote / gyro air mouse:

- Point the phone in the direction you want the cursor to move.
- A real pointer/cursor moves on the target TV or Windows PC.
- The user can perform pointer actions such as click, drag, and scroll where supported.
- Motion should feel stable, responsive, predictable, and properly calibrated.

**Do not spend the project’s main effort on D-pad, Home, Back, volume, text entry, TV remote layouts, or generic remote-control features.** Those are secondary compatibility features only. They must not distract from delivering a real Air Mouse on TVs and Windows.

The goal is not satisfied by a phone-side pointer animation, changing X/Y labels, sending arbitrary bytes, or claiming success because CI passes. The pointer must visibly move on the actual TV/PC.

## 1A. Shared user experience: cursor must be visible on BOTH TV and desktop

This is a cross-platform Air Mouse requirement, not an optional visual enhancement:

- When Air Mouse is active, the user must see a cursor **on the target screen itself** (TV or Windows desktop).
- Phone motion must move that visible target-screen cursor in real time.
- The cursor should be easy to locate, clearly visible against varied backgrounds, and move smoothly without lag or jumps.
- Starting Air Mouse should make the target cursor available/visible; stopping Air Mouse should cleanly end the Air Mouse interaction and restore normal target behavior. Do not leave a stuck cursor or ghost pointer.
- The TV and desktop should offer the same basic interaction model: activate Air Mouse → see target cursor → move it with phone motion → click/drag/scroll only where genuinely supported → stop Air Mouse.
- The implementation mechanism may differ by platform: Windows must move the real OS mouse cursor; TVs must use a native TV pointer channel or a real TV-side cursor/receiver implementation. **Do not fake parity by drawing a cursor only in the phone app or by showing a decorative cursor overlay that does not interact with the TV.**
- If a TV platform cannot expose or support a real target-screen cursor, mark that platform unsupported/unverified for Air Mouse. Do not silently substitute D-pad navigation or pretend it is a cursor.

Add explicit tests for Air Mouse activation/deactivation, target cursor visibility/readiness, cursor movement, and cleanup after disconnect or target switching. The acceptance criteria below apply to both target-screen cursor visibility and movement, not movement alone.

## 2. Read the repository documentation and inspect current code first

Read the complete current contents of:

- `docs/REFERENCE.md`
- `docs/Error.md`
- `docs/README.md`
- `docs/PROJECT_GUIDE.md`
- `docs/PROJECT_PLAN.md`
- `docs/DESIGN.md`
- `docs/UI_UX_DESIGN_SPEC.md`
- `README.md`
- all current Air Mouse, sensor, connection, and receiver code
- current GitHub Actions workflows and recent commit history

Some documentation contains historical findings and prior “repair updates.” Verify all claims against the current code and actual owner test results. **The owner reports prior attempted fixes did not work and the project regressed. Do not assume old commits or reports are correct.**

## 3. Required first deliverable: map the actual Air Mouse data path

Trace the full real-time path for each target:

`phone sensors → orientation/gravity processing → calibration/recenter → smoothing/filter → sensitivity/axis mapping → pointer delta → selected transport → receiver/protocol → OS/TV cursor`

Identify exactly where each path stops working. Document:

- which sensor is used and why;
- whether motion is based on rotation/orientation or raw gyro integration;
- coordinate system, screen/TV orientation, axis inversion and scaling;
- calibration and recenter behavior;
- filtering, dead zone, drift and latency;
- transport protocol and packet schema;
- receiver-side cursor injection;
- click/drag/scroll support;
- disconnect, reconnect, and stale-session behavior.

Do not “fix” a layer until you have traced and reproduced the failure. Do not confuse sensor activity with cursor movement.

## 4. Priority 1 — Restore and perfect Windows Air Mouse

Windows Air Mouse was the only path the owner previously reported working, although it needed calibration. The owner now reports that prior changes broke functionality. **Restore this before working on additional TV brands.**

Inspect at least:

- `app/src/main/java/com/vijay/aimagicremote/bluetooth/BluetoothController.kt`
- `app/src/main/java/com/vijay/aimagicremote/protocol/PcRemoteProtocol.kt`
- `app/src/main/java/com/vijay/aimagicremote/ui/MainActivity.kt`
- the Windows receiver, including `pc_receiver.ps1` if present
- all Air Mouse mode, target selection, sensor listener, calibration, and receiver documentation

### Required repair

1. Reproduce and fix the actual Windows regression. Compare the current code with the last known owner-working revision if needed; inspect git history rather than guessing.
2. Trace one motion sample from Android sensor all the way to the Windows cursor.
3. Confirm that Air Mouse mode actually enables the correct sensor/listener and that target routing sends deltas to the Windows receiver.
4. Confirm phone and receiver agree on transport, IP, port, packet format, newline framing, units, axis mapping, and commands.
5. Ensure TCP writes are serialized and complete JSON frames cannot interleave.
6. Ensure fractional movement is accumulated rather than discarded by rounding tiny sensor deltas.
7. Verify UDP is used for discovery only unless the receiver explicitly supports pointer commands over UDP.
8. Implement real calibration—not just resetting a filter or bias variable:
   - neutral pose / reference orientation;
   - sample collection and stability validation;
   - recenter;
   - axis and sensitivity test;
   - drift check;
   - clear recalibration flow and saved settings if appropriate.
9. Fix drift, jitter, sensitivity, axis inversion, dead zone, smoothing, and latency based on measured behavior.
10. Test actual cursor movement, click, drag, scroll, and receiver reconnection separately. Do not advertise unsupported actions.
11. Ensure the receiver starts reliably, documents Windows firewall/network requirements, and reports useful connection errors.
12. Preserve working behavior while fixing other code. Avoid unrelated refactors.

### Windows acceptance criteria

On the owner’s actual Windows PC:

- [ ] App connects to the correct receiver.
- [ ] Calibration succeeds and is understandable.
- [ ] Pointing phone moves the **real Windows cursor** in the expected direction.
- [ ] Motion is smooth, stable, responsive, and does not drift excessively.
- [ ] Recenter/recalibration works.
- [ ] Click/drag/scroll work if claimed.
- [ ] Disconnect/reconnect works.
- [ ] No regression in existing keyboard/remote behavior.

A mock server and CI are useful, but do not replace the actual Windows cursor test.

## 5. Priority 2 — Real Air Mouse on TVs (the central unresolved challenge)

**The TV must display and move a real pointer.** The Android phone alone cannot force an arbitrary TV to display a cursor through a generic remote protocol. The coding agent must investigate and implement a viable pointer transport for each platform, not assume all TVs behave alike.

### 5.1 LG webOS — investigate native Magic Remote pointer support first

Relevant code: `LgWebOsProtocol.kt`

LG webOS is the most promising direct TV target because compatible models expose a native SSAP pointer channel.

Required:

1. Confirm registration, pairing approval, and client-key persistence.
2. Correctly request and parse the pointer socket/channel from the TV.
3. Treat pointer socket readiness as separate from the main WebSocket registration.
4. Verify the exact pointer message format against a known working open-source implementation and real TV.
5. Send gyro-derived movement through that pointer channel.
6. Implement click, drag and scroll only when protocol support is verified.
7. Verify axis mapping, sensitivity, latency, reconnect, pointer socket closure, and TV firmware differences.
8. Do not claim LG support based only on socket opening or callback firing.

**Acceptance:** actual LG TV cursor moves when the phone is tilted, with working calibration and tested pointer actions.

### 5.2 Android TV / Google TV — do not fake pointer injection

Relevant code: `Atrpv2Protocol.kt`, `AtvWire.kt`, `RemoteManager.kt`

Android TV Remote Protocol v2 provides remote-control commands, but it does **not** define a standard general-purpose gyro mouse pointer injection command.

The agent must research and choose a real viable approach, then prove it:

- **TV-side companion receiver app:** phone sends motion to a companion app running on TV; companion app uses an officially supported pointer/Accessibility mechanism if available. Investigate whether cursor injection is actually possible on target Android TV versions, what permission/setup is required, and what happens when the companion is killed.
- **Bluetooth HID mouse:** investigate whether the phone can act as a Bluetooth HID peripheral/device on supported Android versions and whether target TVs accept it as a mouse. A Bluetooth client connection is not equivalent to a phone acting as a HID mouse. Verify API and device support; do not mark connected unless a real HID session exists.
- Other genuinely supported approaches may be evaluated, but must include compatibility limits, required user setup, and real-device proof.

Do not invent protobuf pointer fields, send unsupported ATRPv2 pointer frames, or claim Android TV cursor support based on phone-local coordinates.

**Acceptance:** On an actual Android TV/Google TV, the user sees a real cursor and can move it with the phone. If no viable universal method exists, clearly report which TV models/methods are supported and what companion/setup is required. Do not call the feature finished.

### 5.3 Samsung Tizen

Investigate whether supported Samsung TV models provide a usable pointer/HID path for a phone air mouse. WebSocket key commands are not mouse movement.

- Find a documented, tested pointer mechanism or compatible HID route.
- Verify model/firmware constraints and required TV settings.
- Do not advertise Air Mouse if only D-pad remote keys work.
- Report unsupported models honestly.

### 5.4 Roku OS

Roku ECP is a remote-control API, not a general gyro pointer protocol. Do not treat ECP key commands as Air Mouse.

- Research whether a genuine pointer/HID method exists for supported Roku hardware.
- If not, label Roku Air Mouse unsupported rather than spending time on unrelated remote buttons.
- Do not confuse Roku-based TCL TVs with TCL Android TV/Google TV.

### 5.5 “Any TV” is not a magic universal capability

Do not promise every TV supports a cursor. Build a truthful support matrix with actual tested models and mechanisms. Prioritize a working TV implementation (LG webOS and/or a proven Android TV companion/HID solution) rather than shallow, non-working code for many brands.

## 6. Motion engine and calibration requirements

Inspect the complete sensor implementation and fix based on observed problems.

- Prefer stable orientation-relative motion and a well-defined neutral reference over unbounded gyro integration.
- Make axes consistent with phone orientation and target display orientation.
- Provide explicit recenter/calibration and explain the required phone pose.
- Validate sensor availability and sample quality.
- Prevent excessive drift when the phone is stationary.
- Avoid sudden jumps after calibration, mode changes, app resume, or reconnect.
- Tune filter, dead zone, sensitivity, acceleration, and smoothing based on real measurements.
- Make calibration state visible and preserve settings appropriately.
- Keep phone-side diagnostic coordinates clearly labeled as diagnostics; never present them as proof that a TV/PC cursor moved.

## 7. Architecture and scope

- Main feature: **Air Mouse**.
- Windows receiver and TV pointer transports are the main deliverables.
- TV remote keys are secondary and must not take priority over Air Mouse.
- Keep each target’s pointer transport separate and capability-aware.
- One selected target/session at a time; do not spray commands across all TV protocols.
- Use explicit connection states: disconnected, connecting, pairing/authorizing, pointer channel ready, connected, reconnecting, failed.
- “Connected” must mean the pointer transport is actually ready—not merely Bluetooth enabled, an IP discovered, TLS opened, or WebSocket opened.
- Do not auto-connect to stale saved IPs without checking target identity.
- Do not log secrets, pairing keys, or tokens.
- Avoid breaking Windows while implementing TV protocols.
- Check licenses before adapting third-party source; preserve required notices.
- No cloud backend is required for the intended local Wi-Fi / local receiver architecture.

## 8. Existing docs and code defects to verify

Read `docs/Error.md` and `docs/REFERENCE.md` fully, but verify historical claims against current code. Specifically investigate:

- false connected state after a failed connection;
- ATRPv2 framing, pairing, certificate and readiness;
- fake/decorative QR pairing;
- TV platform protocols launched concurrently;
- Samsung token authorization/persistence;
- LG registration and pointer socket handling;
- Roku target/platform mismatch;
- Bluetooth HID target falsely reporting connected;
- Windows receiver protocol and calibration;
- stale callbacks after target switching;
- misleading logs/status messages;
- Air Mouse mode not correctly starting/stopping the sensor listener;
- regressions introduced by prior fixes.

Fix only confirmed defects, and add tests for each fix.

## 9. Testing required

### Unit tests
- sensor/orientation mapping and axis direction;
- calibration and recenter math;
- filter/drift/dead-zone/sensitivity behavior;
- fractional delta accumulation;
- receiver JSON/framing;
- serialized writes;
- target routing;
- pointer frame encoding and parsing for supported TV protocols;
- connection/pointer-ready state transitions;
- failure, timeout, disconnect and reconnect paths.

### Integration tests
- Windows receiver mock: discovery, connection, valid/invalid packets, frame boundaries, disconnect and reconnect.
- LG mock WebSocket: registration, pointer socket response, pointer ready/failure and reconnection.
- Android TV companion/HID test only after a concrete architecture is selected.
- Verify switching targets cannot send motion to an old target.

### Live device testing
Must record exact app commit, phone model/Android version, PC OS or TV brand/model/firmware, setup steps, and actual result.

| Target | Required live test |
|---|---|
| Windows PC | Actual cursor movement, calibration, drift, click/drag/scroll if supported |
| LG webOS | Actual TV pointer movement and pointer actions |
| Android TV/Google TV | Actual cursor movement using the selected companion/HID approach |
| Samsung Tizen | Actual pointer movement only if a valid implementation exists |
| Roku | Actual pointer movement only if a valid implementation exists |

If hardware is unavailable, label it **NOT TESTED**. Do not say it works.

## 10. CI, APK and release

- Run build and tests against the exact final commit SHA.
- Inspect the current workflow result, not an older green run.
- Build/install APK and test the Windows Air Mouse path before publishing.
- Do not release an APK as “fixed” if the owner-reported Windows path remains broken.
- A passing build is not proof of cursor behavior.
- Report actual fixes, test output, device results, unsupported targets, and remaining blockers.
- Do not claim “fully working” or “ready for live testing” without the required acceptance evidence.

## 11. Definition of done

- [ ] Windows Air Mouse regression is fixed and tested on real Windows.
- [ ] Calibration/recenter is reliable and understandable.
- [ ] The real Windows cursor moves correctly from phone motion.
- [ ] At least one TV platform has a real, live-tested cursor controlled by phone motion.
- [ ] TV pointer transport is implemented correctly, not faked through remote key APIs.
- [ ] Click/drag/scroll are tested before being advertised.
- [ ] Unsupported TV brands are clearly marked unsupported for Air Mouse.
- [ ] No false connection/pointer-ready claims.
- [ ] Unit/integration tests pass.
- [ ] CI passes on the exact commit.
- [ ] Documentation and UI reflect actual verified capability.
- [ ] APK corresponds to the tested commit.

## 12. Agent's final report

Provide:

1. Commit SHA and files changed.
2. Root cause of Windows regression and TV pointer issue.
3. Exact end-to-end pointer path implemented for Windows and each TV.
4. Tests and outputs (not just “passed”).
5. Real device models/OS/firmware and live-test results.
6. Current support matrix: working / untested / unsupported.
7. CI run URL and tested commit SHA.
8. APK link only if actually built and tested.
9. Remaining blockers and required owner actions.

**Do not report ordinary TV remote buttons as completion of the Air Mouse goal. Do not mark the project done based on source edits, local coordinates, socket writes, or CI alone.**

## References to verify

- Existing project research: `docs/REFERENCE.md`
- Owner’s issue/repair history: `docs/Error.md`
- Android TV Remote v2 reference: https://github.com/tronikos/androidtvremote2
- LG webOS pointer reference listed in `docs/REFERENCE.md`
- Motion/HID reference projects listed in `docs/REFERENCE.md`

Verify protocol details, current maintenance status, and licenses before reusing code.
