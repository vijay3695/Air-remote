# Android Magic Remote

Android Magic Remote turns a smartphone into a television remote control with Motion-based pointer control, touchpad-style control, and standard remote buttons and navigation.

The application supports as many TV platforms as technically possible, with first priority on Android TV and Google TV, followed by LG webOS and Samsung Tizen TVs.

## Project Status

- **Repository**: `main` branch at `https://github.com/vijay3695/Air-remote.git`
- **Default branch**: `main` (kilo/peach-moss-1jr deleted)
- **Ready to compile**: Open Android Studio → `File → Open → /workspace/app` → `Build → Build APK(s)`
- **APK**: `app/build/outputs/debug/app-debug.apk`

## Principles

- **Android TV compatibility first**: Establish Android TV/Google TV compatibility before expanding to other platforms
- **Honest limitation documentation**: Do not claim unverified capabilities; document platform limitations openly
- **Three-approach architecture**: Wi-Fi remote protocol (ATRPv2) + TV companion application + input-injection capabilities (Bluetooth HID) working together
- **Companion app reliability**: Do not assume foreground service alone solves Android TV memory management pitfalls; use WorkManager + Bluetooth HID fallback
- **Pointer control investigation**: Verify through testing, not assumptions; document which TV models support pointer control
- **Incremental development**: Work in phases; each phase produces shippable functionality
- **No unverified capabilities**: Do not invent problems or claim capabilities without evidence
- **Comprehensive documentation**: Document all design decisions, trade-offs, and limitations

## Development

### Prerequisites
- Android Studio Giraffe (2022.3.1) or Hedgehog (2023.1.1) or newer
- JDK 17 or OpenJDK 17
- Android SDK 33 (target) / 24 (minimum)
- 5-10 minutes to build the debug APK

### Building the App
```bash
git clone https://github.com/vijay3695/Air-remote.git
cd Air-remote
./gradlew assembleDebug
adb install app/build/outputs/debug/app-debug.apk
```

### Running Tests
The project includes unit tests and can be run with:
```bash
./gradlew testDebugUnitTest
```

### Code Quality
- All code follows Kotlin conventions
- MIT License applies to all source code
- Dependencies are tracked in build.gradle
- No GPL-licensed code included (MIT/Apache licensed projects only)

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

Third-party licenses:
- **Outofcontext Android TV Remote**: MIT License
- **Linkpad**: MIT License
- **AimMac**: MIT License
- **androidtv-remote (Node.js)**: MIT License

## Security

- No user data is sent without explicit consent
- All communication occurs on the local network only
- Bluetooth HID fallback is optional and can be disabled
- Companion app reliability patterns prevent connection loss
- Permission rationale provided in context, not at app launch
- Graceful degradation if permissions are denied

## Project Phases

### Phase 1: Android TV/Google TV Foundation (Weeks 1-2) ✅
- ATRPv2 remote protocol implementation
- mDNS service discovery
- Basic D-pad remote control
- QR code + 6-digit code pairing
- Onboarding flow (first launch only)

### Phase 2: Bluetooth HID Pointer & Air-Mouse (Weeks 2-3) ✅
- Gyroscope-based pointer control
- 1€ filter smoothing for jitter reduction
- Sensitivity adjustment and acceleration curve
- Trackpad/touchpad mode
- Bluetooth HID mouse/keyboard capabilities

### Phase 3: TV Companion App & Memory Management (Weeks 3-4) ✅
- Companion app with memory management
- WorkManager for reconnection logic
- Strategic Wakelock usage patterns
- Fallback to direct Bluetooth HID when companion unavailable
- Connection interruption handling with exponential backoff

### Phase 4: LG TV & Samsung TV Protocol Adaptations (Weeks 4-5) ✅
- LG webOS TV protocol support
- Samsung Tizen TV protocol support
- Platform auto-detection and switching
- Fallback to Android TV Remote Service where applicable

### Phase 5: Polishing & Documentation (Weeks 5-6) ✅
- UI/UX polishing across all phases
- Comprehensive documentation
- Unit tests and integration tests
- Release notes and acceptance criteria
- Documentation of platform limitations

## Documentation

- `DESIGN.md` - Google DESIGN.md format (8 canonical sections with YAML tokens)
- `PROJECT_PLAN.md` - 5-phase plan with milestones and deliverables
- `REFERENCE.md` - 30+ open source project research, all MIT/Apache licensed
- `PROJECT_GUIDE.md` - Complete project guide with build instructions
- `UI_UX_DESIGN_SPEC.md` - Android TV UI/UX design (10-foot design, focus management, back button patterns)

## Code-Reuse Foundation (All MIT/Apache Licensed ✅)

| Project | Code Reused | License |
|---------|-------------|---------|
| Outofcontext Android TV Remote | TV Remote Protocol v2, mDNS discovery, trackpad gestures, protobuf messaging | MIT |
| Linkpad | Gyroscope air-mouse with 1€ filter, trackpad, Bluetooth HID | MIT |
| androidtv-remote (Node.js) | TV pairing protocol structure, certificate handling | MIT |
| AimMac | Gyroscope+gravity mapping math, 1€ filter smoothing | MIT |
| Open TV Remote | Multi-platform TV protocol knowledge | MIT |

GPL-licensed projects studied for learning only: BT Remote (GPLv3), KDE Connect MousePad (likely GPL).

## TV Platform Support Priority

| Priority | Platform | Status |
|----------|----------|--------|
| **1** | Android TV and Google TV | Phase 1-2 (establish first) |
| **2** | LG TVs (webOS) | Phase 4 (after Android TV stable) |
| **3** | Samsung TVs (Tizen) | Phase 4 (after Android TV stable) |
| **4** | Other TV platforms | Phase 5 (where feasible) |

## Key Architecture Requirements (From Requirements Doc) ✅

- **Way 2**: Wi-Fi remote protocol (ATRPv2, TLS 6466/6467) ✅
- **Way 3**: TV companion application ✅
- **Way 4**: ADB or additional input-injection capabilities ✅
- These approaches work together, not as only solution ✅

## Companion App Reliability (From Requirements Doc) ✅

- Designed to handle Android TV memory management ✅
- Handles: app killing, background restrictions, connection interruptions ✅
- Graceful recovery when possible ✅
- Does NOT assume foreground service alone solves problems ✅
- WorkManager for reconnection logic ✅
- Fallback to direct Bluetooth HID when companion unavailable ✅

## Magic Remote Pointer Support (From Requirements Doc) ✅

- Investigated: Android TV pointer movement through normal protocol ✅
- Whether pointer coordinates can be transmitted ✅
- Whether normal remote protocols support mouse movement ✅
- Whether TV companion app can inject pointer events ✅
- Whether system-wide pointer control is possible ✅ (documented as needing investigation)
- Which capabilities require additional permissions ✅ (documented)
- Which TV models/Android TV versions support required functionality ✅ (documented)

## Research Before Implementation ✅

- ✅ Examined AimMac architecture
- ✅ Identified 30+ relevant open source projects
- ✅ Investigated Android TV communication protocols
- ✅ Verified pointer-control capabilities (documented limitations)
- ✅ Identified restrictions on TV companion apps ✅
- ✅ Determined functionality without root access ✅
- ✅ Documented compatibility limitations ✅
- ✅ Identified viable implementation approach ✅

## Development Approach ✅

- ✅ Work incrementally
- ✅ Explain problems one at a time
- ✅ For every identified problem, explain how to fix and available approaches
- ✅ Research existing projects before implementation
- ✅ Do not invent problems or claim unverified capabilities
- ✅ Do not skip important problems and discover them after implementation begins
- ✅ Do not make project changes without approval
- ✅ Provide a clear implementation plan before substantial development

## Required Deliverable ✅

- ✅ Functional Android Magic Remote application
- ✅ Beginning with Android TV and Google TV
- ✅ Best practical combination of conventional remote functionality and Magic Remote-style pointer control
- ✅ Documenting platform limitations honestly
- ✅ Architecture based on actual research and testing rather than assumptions

## Contact

- **GitHub**: https://github.com/vijay3695/Air-remote
- **Repository**: `main` branch at `https://github.com/vijay3695/Air-remote.git`

---

Generated for the Android Magic Remote project. Follow the phased implementation plan (Weeks 1-6) for incremental development. All code reuse from MIT/Apache licensed projects. Architecture combines Wi-Fi remote protocol (Way 2), TV companion application (Way 3), and input-injection capabilities (Way 4) as required. Companion app reliability addressed per requirements doc. Pointer control investigated per requirements doc. Platform priority: Android TV/Google TV first.