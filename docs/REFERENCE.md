# Android Magic Remote - Project Reference

## Research Summary (2026-11-08)

### Primary Open Source Projects (license must be verified per repository and component)

| Project | License | Key Reusable Code |
|---------|---------|------------------|
| **Outofcontext Android TV Remote** (`github.com/outofcontextapps/androidtvremote`) | MIT | TV Remote Protocol v2, mDNS discovery, trackpad gestures, protobuf messaging, keyboard input, device discovery/reconnection |
| **Linkpad** (`github.com/Devdas-gupta/linkpad`) | MIT | Gyroscope air-mouse with 1€ filter, trackpad, Bluetooth HID mouse/keyboard, multi-device profiles, TV remote layout |
| **BT Remote** (`gitlab.com/Atharok/BtRemote` / `github.com/bartoszp1992/BtRemote`) | GPLv3 (deps Apache 2.0) | Bluetooth HID mouse/keyboard control without companion app, integrated mouse+keyboard UI, game controller support |
| **androidtv-remote (Node.js)** (`github.com/louis49/androidtv-remote`) | MIT | PairingSession + RemoteManager protocols over TLS, certificate handling, event system (secret/powered/volume/current_app/ready), commands (sendKey, sendAppLink), used by 30+ projects including Home Assistant |
| **AimMac** (`github.com/Coolerwave/AimMac`) | MIT | Gyroscope+gravity to pointer mapping, 1€ filter smoothing, Bluetooth advertising/packet structure, pairing with approval flow, protocol in Shared/Protocol.swift |
| **Open TV Remote** (`github.com/iamnbutler/open-tv-remote`) | MIT | Multi-platform TV protocol knowledge (Google TV, Android TV, Roku, Samsung Tizen, LG webOS), direct Wi-Fi communication |

### TV Companion & Protocol Projects

| Project | Key Features |
|---------|-------------|
| **Mi TV Remote / MiLink** (`github.com/EndLess728/MiTV-Remote`) | Two protocols: MiLink (TCP 6091, no pairing) + Android TV Remote Service (TLS 6466/6467, one-time pairing). Fallback logic, auto-discovery, auto-reconnect with heartbeats |
| **GTV Desktop Remote** (`github.com/usrivastava92/gtv-desktop-remote`) | Network scan + one-time pairing, IP-change resilient (tracks by MAC), global shortcuts, launch at login, keyboard controls, text input |
| **RemoteGTV** (`github.com/geosub-m/RemoteGTV`) | Bonjour mDNS discovery, TLS certificate-based pairing (Swift), full remote control UI, Wake-on-LAN support |
| **Home Assistant androidtv-remote integration** | Production-grade protocol implementation, discovery, reconnection logic (Apache 2.0 core, MIT frontend) |

### Pointer/Mouse Control Projects

| Project | Key Features |
|---------|-------------|
| **KDE Connect MousePad** (`invent.kde.org/network/kdeconnect-android`) | Gyroscope-controlled mouse pointer ("MousePad" feature), inspired by LG TV magic remote |
| **AimMac** (again) | Gyroscope+gravity mapping, 1€ filter, Bluetooth pointer - math reusable even though BT-only |

## Implementation status update (2026-11-09)

- The current app has a new Android TV Remote v2 implementation in `Atrpv2Protocol.kt` and a bounded protobuf wire codec in `AtvWire.kt`. The codec is adapted from OpenTV-Remote's MIT-licensed `Wire.kt`; attribution is in `docs/THIRD_PARTY_NOTICES.md`.
- No Linkpad, AimMac, BT Remote, or other candidate project's implementation has been copied into Air Remote yet. Those remain research references only.
- Android TV Remote v2 supports key injection and text/IME messages, but its standard remote-message schema does **not** define a general gyro mouse-pointer injection message. Air Mouse pointer support for Android TV therefore remains a separate companion-app / accessibility / Bluetooth HID investigation; do not mark it as working.
- LG webOS pointer control uses its separate SSAP pointer socket, not the Android TV protocol.
- Samsung, LG, Roku, and Android TV code still require real-TV pairing and command tests. A successful build is not evidence of hardware compatibility.
- Roku ECP applies to Roku OS devices (including Roku-based TCL models), not TCL Android TV/Google TV models.
- “DLNA” is not a universal TV remote-key protocol. DLNA/UPnP media-renderer support may enable media transport commands on some devices, but it does not guarantee D-pad, power, text, or mouse control. Treat each protocol as a separately verified capability.

### Android TV Remote Protocol v2 (ATRPv2) - Critical Specs

- **Ports**: 6466 (remote) + 6467 (pairing)
- **Transport**: TLS 1.2 (apps pin 1.2, Apple's 1.3 fails against TV servers)
- **Messages**: protobuf, each prefixed with varint length
- **Pairing flow**:
  1. Client generates 2048-bit RSA key + self-signed X.509 cert
  2. TV shows 6-digit code
  3. Secret = `SHA-256(client modulus, client exponent, TV modulus, TV exponent, last 2 bytes of code)`
  4. Code's first byte must equal hash's first byte (catches typos)
  5. Once TV acks secret, trusts cert permanently
- **Events**: secret, powered, volume, current_app, ready, error
- **Commands**: sendKey, sendAppLink, sendCode, etc.
- **Key codes**: Android KeyEvent codes (UP=19, DOWN=20, LEFT=21, RIGHT=22, ENTER=23, etc.)

### TV Companion App Reliability (From Requirements Doc)

- **Foreground service alone is NOT sufficient** to prevent Android TV killing the app
- **Problems**:
  - Android TV killing companion application
  - Background execution restrictions
  - Connection interruptions
  - Companion becoming unavailable
  - Losing pointer-control functionality after app stops
- **Solutions needed**:
  - Bounded foreground service + strategic Wakelock usage
  - WorkManager for reconnection logic
  - Fallback to direct Bluetooth HID control when companion unavailable
  - System-level pointer injection as last resort (requires investigation)

### Pointer Control Through Android TV

- **Investigate**: Whether Android TV accepts pointer movement through normal remote protocol
- **May require**: TV companion app injecting pointer events
- **ADB alternative**: `input mouse` commands via ADB (but needs USB debugging enabled on TV)
- **Bluetooth HID**: Can work as standalone mouse without companion app on some TVs

### Implementation Priority Order

| Phase | Weeks | Focus |
|-------|-------|-------|
| 1 | 1-2 | Android TV Remote Protocol v2 implementation (TLS + protobuf), mDNS discovery, basic remote (D-pad, OK, Back, Home, Volume, Power) |
| 2 | 2-3 | Bluetooth HID air-mouse using gyroscope, 1€ filter smoothing, trackpad mode (phone screen as touchpad) |
| 3 | 3-4 | Pairing flow with 6-digit code entry, TLS certificate management, reconnection logic with exponential backoff |
| 4 | 4-5 | TV companion app with memory management, foreground service + WorkManager pattern, fallback when companion is killed |
| 5 | 5-6 | LG TV protocol adaptations (if needed), Samsung TV protocol adaptations (if needed), multi-device pairing management |

### Code Reuse Candidates (MIT/Apache licensed ✅)

| Project | What to Reuse |
|---------|--------------|
| Outofcontext Android TV Remote | TV Remote Protocol implementation, mDNS service discovery, trackpad gesture handling, protobuf message schema, keyboard input to TV, device discovery and reconnection logic |
| Linkpad | Gyroscope-to-pointer calibration, 1€ filter smoothing, trackpad implementation, Bluetooth HID protocol, multi-device profile management, TV remote tab layout |
| androidtv-remote (Node.js) | TV pairing protocol structure, certificate handling, key injection events/commands, event system patterns |
| AimMac | Gyroscope+gravity mapping math, 1€ filter algorithm, Bluetooth pairing flow documentation |
| Open TV Remote | Multi-platform TV protocol knowledge, discovery mechanisms, protocol variations across brands |

### GPL-Licensed Projects (Require Source Sharing if Modified/Distributed)

- **BT Remote** (GPLv3) - Bluetooth HID mouse/keyboard, widget UI
- **KDE Connect MousePad** (likely GPL) - Gyroscope mouse control
- **Check licenses** before modifying and redistributing

### TV Platform Support Priority

1. **First priority**: Android TV and Google TV (establish compatibility first)
2. **Later**: LG TVs (webOS protocol variations)
3. **Later**: Samsung TVs (Tizen protocol variations)
4. **Expand**: Other TV platforms where feasible

### Critical Functions Not Yet Verified

- Whether Android TV accepts pointer movement through normal remote protocol
- Whether TV companion app can inject pointer events
- Whether system-wide pointer control is possible
- Which capabilities require additional permissions or user configuration
- Which TV models/Android TV versions support required functionality

### Repository Setup

- **Current branch**: `main` (checked out)
- **Remote**: `origin` at `https://github.com/vijay3695/Air-remote.git`
- **Tracking**: Local `main` tracks `origin/main`
- **Deleted**: Pre-existing `kilo/peach-moss-1jr` branch
- **Initial commit**: `be9a0e4` (empty commit to establish main branch)

### Next Development Steps

1. **Set up Android Studio project** with Kotlin + Material 3
2. **Integrate Outofcontext app's TV Remote Protocol v2** code
3. **Implement mDNS discovery** (`_androidtvremote._tcp`)
4. **Build basic remote control UI** (D-pad, buttons)
5. **Implement Bluetooth HID pointer** using Linkpad's gyroscope approach
6. **Add pairing flow** with 6-digit code entry
7. **Add TV companion app** with memory management (WorkManager + foreground service)
8. **Test on Android TV/Google TV hardware**
9. **Add LG/Samsung protocol adaptations**
10. **Document platform limitations honestly**

---
*Reference file for Android Magic Remote project. All code reuse from MIT/Apache licensed projects. GPL projects studied for learning only - not for direct code inclusion without source-sharing compliance.*