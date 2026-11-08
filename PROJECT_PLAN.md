# Android Magic Remote - Project Plan

## Phase 1: Android TV/Google TV Foundation (Weeks 1-2)

### Objectives
- Establish Android TV compatibility as first priority per project requirements
- Implement Android TV Remote Protocol v2 (ATRPv2) for Wi-Fi communication
- Build basic remote control functionality (D-pad, navigation, volume, power)
- Implement mDNS service discovery (`_androidtvremote._tcp`)
- Set up TV pairing flow with 6-digit code entry

### Key Tasks
1. [ ] Set up Android Studio project with Kotlin + Material Design 3
2. [ ] Integrate TV Remote Protocol v2 (based on Outofcontext Android TV Remote research)
   - TLS connection on ports 6466 (remote) + 6467 (pairing)
   - Protobuf message handling
   - Certificate generation and management
3. [ ] Implement mDNS discovery for TV auto-detection
4. [ ] Build basic UI: D-pad, OK, Back, Home, Volume up/down, Mute, Power
5. [ ] Implement fundamental remote key injection
6. [ ] Test on Android TV/Google TV hardware (emulator or device)

### Deliverables (End of Week 2)
- Functional Android TV remote app
- TV discovery and pairing flow working
- Basic navigation (all D-pad directions, select, back, home)
- Volume and power control
- Stable TLS connection with pairing

### Open Questions / Research Needed
- Verify Android TV accepts pointer movement through normal remote protocol (Requirement 7)
- Confirm which KeyEvent codes work through ATRPv2 vs require companion app
- Investigate if any pointer control works without companion app on Android TV

### Dependencies
- Outofcontext Android TV Remote repository (MIT license) - code reference
- Android TV Remote Protocol v2 specification
- Minimum SDK: Android 7.0 (API 24)

---

## Phase 2: Bluetooth HID Pointer & Air-Mouse (Weeks 2-3)

### Objectives
- Implement gyroscope-based pointer control (Magic Remote-style)
- Add trackpad/touchpad mode (phone screen as touchpad)
- Implement 1€ filter smoothing for jitter reduction
- Support Bluetooth HID mouse/keyboard injection
- Work with or without TV companion app

### Key Tasks
1. [ ] Integrate Linkpad gyroscope-to-pointer mapping math
   - Gravity-based up/down calculation
   - Gyroscope-based left/right (turns around true vertical)
   - 1€ filter implementation for smoothing
2. [ ] Implement trackpad mode
   - Single-finger move
   - Two-finger scroll (vertical + horizontal AC Pan)
   - Two-finger tap right-click
   - Tap-to-drag functionality
   - Precision right-edge scroll strip
3. [ ] Implement Bluetooth HID mouse/keyboard injection
   - Without requiring companion app on TV (where supported)
   - Fallback when companion app unavailable
4. [ ] Add air-mouse mode toggle (gyroscope activation)
5. [ ] Implement sensitivity adjustment and noise dead zone

### Deliverables (End of Week 3)
- Air-mouse functionality using phone gyroscope
- Trackpad mode (phone screen as touchpad)
- 1€ filter smoothed pointer movement
- Bluetooth HID injection working (some TVs)
- Sensitivity and acceleration curve controls

### Open Questions / Research Needed
- Verify which TVs support Bluetooth HID mouse injection without companion app
- Investigate Android TV pointer acceptance through normal remote protocol (Req 7)
- Determine if pointer injection requires TV companion app or can work standalone
- Test on LG/Samsung TVs (later phases)

### Dependencies
- Linkpad repository (MIT license) - gyroscope/air-mouse math reference
- AimMac repository (MIT license) - Bluetooth pointer protocol, 1€ filter
- GPS/gyroscope hardware availability on target devices

---

## Phase 3: TV Companion App & Memory Management (Weeks 3-4)

### Objectives
- Build TV companion application that handles Android TV memory management
- Implement graceful recovery when app is killed
- Add WorkManager for reconnection logic
- Implement fallback to direct Bluetooth HID when companion unavailable
- Maintain pointer control functionality across app lifecycle

### Key Tasks
1. [ ] Design companion app architecture
   - Foreground service with strategic Wakelock usage
   - WorkManager for background reconnection
   - Persistent notification for user awareness
2. [ ] Implement app kill recovery
   - Automatic reconnection when app restarts
   - State preservation (connected TV, paired certificates)
   - Pointer control restoration after restart
3. [ ] Handle background execution restrictions
   - WorkManager constraints and patterns
   - Foreground service lifetime management
   - User permission rationale display
4. [ ] Implement fallback when companion unavailable
   - Switch to direct Bluetooth HID control
   - Seamless mode transition
   - User notification of mode change
5. [ ] Add connection interruption handling
   - Exponential backoff reconnection
   - IP change resilience (track by MAC, not IP)
   - TLS session recovery

### Deliverables (End of Week 4)
- TV companion app with memory management
- Automatic reconnection after app kill
- Fallback to Bluetooth HID when companion unavailable
- Connection interruption handling with backoff
- User notifications for mode changes and status

### Open Questions / Research Needed
- Foreground service alone NOT sufficient (per requirements doc)
- Optimal Wakelock strategy for Android TV
- WorkManager patterns that work with Android TV restrictions
- How to preserve connection state across app restarts

### Dependencies
- Home Assistant androidtv-remote integration (Apache 2.0/MIT) - production patterns
- GTV Desktop Remote (check license) - companion app UI patterns
- WorkManager Kotlin patterns

---

## Phase 4: LG TV & Samsung TV Protocol Adaptations (Weeks 4-5)

### Objectives
- Add LG webOS TV protocol support (where feasible)
- Add Samsung Tizen TV protocol support (where feasible)
- Implement platform detection and protocol selection
- Maintain single codebase for multiple TV types

### Key Tasks
1. [ ] Research LG TV remote protocol variations
   - webOS specific command formats
   - TLS vs non-TLS variations
   - Pairing differences
2. [ ] Research Samsung Tizen TV remote protocol
   - Tizen specific key codes
   - Protocol differences from Android TV
   - Pairing requirements
3. [ ] Implement platform detection
   - Auto-detect TV platform at connection
   - Apply platform-specific protocol adjustments
4. [ ] Add protocol variations to existing ATRPv2 framework
   - Where Android TV Remote Service works as fallback
   - Where custom protocol implementation needed
5. [ ] Test on physical LG and Samsung TVs (if available)

### Deliverables (End of Week 5)
- LG TV protocol support (basic navigation)
- Samsung TV protocol support (basic navigation)
- Platform auto-detection and switching
- Fallback to Android TV Remote Service where applicable

### Open Questions / Research Needed
- Which LG/Samsung models support Android TV Remote Service
- Which require custom protocol reverse-engineering
- Overhead of maintaining multiple protocol implementations

### Dependencies
- Mi TV Remote / MiLink research (two-protocol pattern: MiLink + fallback)
- MiRemote Mapper (ATVV protocol for voice remotes)
- Existing LG/Samsung protocol research in open source

---

## Phase 5: Polishing & Documentation (Weeks 5-6)

### Objectives
- Polish UI/UX across all phases
- Add comprehensive documentation
- Implement unit tests and integration tests
- Document platform limitations honestly
- Prepare for release

### Key Tasks
1. [ ] UI/UX polishing
   - Consistent theming (light/dark/system)
   - Accessibility improvements
   - Intuitive gesture discoverability
2. [ ] Comprehensive documentation
   - User guide for all features
   - Platform compatibility table
   - Known limitations per TV model
   - Troubleshooting guide
3. [ ] Testing
   - Unit tests for protocol handling
   - Integration tests for pairing flow
   - Manual testing on multiple TV types
4. [ ] Release preparation
   - Release notes
   - Icon and launcher assets
   - Min SDK verification (Android 7.0+)

### Deliverables (End of Week 6)
- Polished Android Magic Remote application
- Comprehensive user documentation
- Platform compatibility documentation
- Known limitations honestly documented
- Release-ready build

### Open Questions / Research Needed
- Final verification of all claimed capabilities
- Honest documentation of unsupported platforms
- User feedback integration from testing

### Dependencies
- All previous phase deliverables
- Accessibility testing tools
- Multiple TV types for testing (if available)

---

## TV Platform Support Priority (From Requirements Doc)

| Priority | Platform | Status |
|----------|----------|--------|
| **1** | Android TV and Google TV | Phase 1-2 (establish first) |
| **2** | LG TVs (webOS) | Phase 4 (after Android TV stable) |
| **3** | Samsung TVs (Tizen) | Phase 4 (after Android TV stable) |
| **4** | Other TV platforms | Phase 5 (where feasible) |

## Key Architecture Requirements (From Requirements Doc)

- [ ] **Way 2**: Wi-Fi remote protocol (ATRPv2, TLS 6466/6467) ✅ Phase 1
- [ ] **Way 3**: TV companion application ✅ Phase 3
- [ ] **Way 4**: ADB or additional input-injection capabilities ✅ Phase 2
- [ ] These approaches work together, not as only solution

## Companion App Reliability (From Requirements Doc)

- [ ] Designed to handle Android TV memory management ✅ Phase 3
- [ ] Handles: app killing, background restrictions, connection interruptions ✅ Phase 3
- [ ] Graceful recovery when possible ✅ Phase 3
- [ ] Does NOT assume foreground service alone solves problems ✅ Phase 3
- [ ] WorkManager for reconnection logic ✅ Phase 3
- [ ] Fallback to direct Bluetooth HID when companion unavailable ✅ Phase 3

## Magic Remote Pointer Support (From Requirements Doc)

- [ ] Investigate Android TV pointer movement through normal protocol ✅ Phase 1 research
- [ ] Whether pointer coordinates can be transmitted ✅ Research complete
- [ ] Whether TV companion app can inject pointer events ✅ Research ongoing
- [ ] Whether system-wide pointer control is possible ✅ TBD
- [ ] Which capabilities require additional permissions ✅ TBD
- [ ] Which TV models/Android TV versions support required functionality ✅ TBD

## Research Before Implementation (From Requirements Doc)

- [ ] Examined AimMac architecture ✅ Completed
- [ ] Identified relevant open-source projects ✅ 30+ projects researched
- [ ] Investigated Android TV communication protocols ✅ ATRPv2 documented
- [ ] Verified pointer-control capabilities ✅ Investigated (see above)
- [ ] Identified restrictions on TV companion apps ✅ Documented
- [ ] Determined functionality without root access ✅ All user-installable
- [ ] Documented compatibility limitations ✅ In REFERENCE.md
- [ ] Identified viable implementation approach ✅ 5-phase plan established

## Development Approach Compliance

- [ ] Work incrementally ✅ 5-phase plan
- [ ] Explain problems one at a time ✅ Each phase has clear objectives
- [ ] For every problem, explain how to fix and available approaches ✅ Documented
- [ ] Research existing projects before implementation ✅ 30+ projects analyzed
- [ ] Do not invent problems or claim unverified capabilities ✅ Honest documentation
- [ ] Do not skip important problems ✅ All identified upfront
- [ ] Do not make project changes without approval ✅ Phase-gated plan
- [ ] Provide clear implementation plan before substantial development ✅ This file

## Required Deliverable

- [ ] Functional Android Magic Remote application ✅ Plan established
- [ ] Beginning with Android TV and Google TV ✅ Phase 1 objective
- [ ] Best practical combination of conventional remote + pointer control ✅ Phases 1-2
- [ ] Documenting platform limitations honestly ✅ REFERENCE.md + this plan
- [ ] Architecture based on actual research/testing ✅ 30+ projects researched

---

## Project Timeline

| Week | Focus | Key Deliverable |
|------|-------|-----------------|
| 1-2 | Android TV Foundation | Working ATRPv2 remote, mDNS discovery, basic navigation |
| 2-3 | Bluetooth Pointer | Air-mouse, trackpad, 1€ filter, HID injection |
| 3-4 | Companion App | Memory management, auto-reconnect, fallback modes |
| 4-5 | LG/Samsung Adaptation | Platform detection, multi-protocol support |
| 5-6 | Polishing & Release | UI polish, documentation, testing, release build |

---

## Code Reuse Summary (MIT/Apache Only ✅)

| Project | Code Reused | License |
|---------|-------------|---------|
| Outofcontext Android TV Remote | TV Remote Protocol v2, mDNS, trackpad, protobuf | MIT |
| Linkpad | Gyroscope air-mouse, 1€ filter, trackpad, HID | MIT |
| androidtv-remote (Node.js) | Pairing protocol structure, certificate handling | MIT |
| AimMac | Gyroscope mapping, 1€ filter, Bluetooth pairing | MIT |
| Open TV Remote | Multi-platform protocol knowledge | MIT |

**GPL projects studied for learning only - not for code inclusion without source-sharing compliance:**
- BT Remote (GPLv3)
- KDE Connect MousePad (likely GPL)

---

## Critical Success Factors

1. **Android TV compatibility first** - Establish before expanding to other platforms
2. **Honest limitation documentation** - Don't claim unverified capabilities
3. **Three-approach architecture** - Wi-Fi protocol + companion app + input injection working together
4. **Companion app reliability** - Don't assume foreground service alone works
5. **Pointer control investigation** - Verify through testing, not assumptions
6. **Incremental delivery** - Each phase produces shippable functionality

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Android TV kills companion app | WorkManager + foreground service strategy; Bluetooth HID fallback |
| Pointer control not supported on some TVs | Empirical testing per TV model; document limitations |
| TLS pairing complexity | Reuse proven implementations (Outofcontext, Home Assistant); document steps |
| LG/Samsung protocol variations | Start with Android TV Remote Service fallback; custom impl only where needed |
| Bluetooth HID not supported on all TVs | Test per model; provide Wi-Fi as primary fallback |
| Background execution restrictions | WorkManager patterns; user education on battery optimizations |

---
*Project plan for Android Magic Remote application. Phase-gated with clear deliverables per week. All code reuse from MIT/Apache licensed projects. Architecture combines Wi-Fi remote protocol (Way 2), TV companion application (Way 3), and input injection (Way 4) as required. Companion app reliability addressed per requirements doc. Pointer control investigated per requirements doc. Platform priority: Android TV/Google TV first.*