# Android Magic Remote - UI/UX Design Specification

## Document Version
1.0 - Initial design specification

## Target Platforms
- **Primary:** Android TV (min SDK 24, Android 7.0+) - Phase 1 focus
- **Secondary:** Google TV - Phase 1 focus
- **Tertiary:** LG webOS TV (later phases)
- **Quaternary:** Samsung Tizen TV (later phases)

## Overall Design Philosophy

**10-Foot UI / Lean-Back Experience**
- Designed for viewing from ~3 meters (10 feet)
- Large touch targets and text readable at distance
- D-pad (directional pad) primary navigation
- Lean-back posture, relaxed interaction model
- communal device - consider privacy for shared household use

### Core Design Principles

1. **Efficient Navigation** - Fewest clicks to reach content
   - Users want to access content quickly using minimal clicks
   - Organize information requiring fewest screens

2. **Predictable Behavior** - Consistent navigation patterns
   - Follow best practices, don't reinvent navigation
   - Back button: breadcrumb-style, leads to TV home eventually
   - Consecutive back presses always lead to TV home screen

3. **Intuitive D-Pad Controls** - Seamlessly support remote users
   - Simple enough to support widely adopted user behaviors
   - Don't over-complicate with unnecessary layers
   - D-pad moves focus from one element to nearest in direction

### TV Controller Mapping

| Remote Button | UI Action | Notes |
|---|---|---|
| D-pad Up | Move focus up | Move to nearest focusable element above |
| D-pad Down | Move focus down | Move to nearest focusable element below |
| D-pad Left | Move focus left | Move to nearest focusable element left |
| D-pad Right | Move focus right | Move to nearest focusable element right |
| Select/Enter | Activate focused element | Select/confirm action |
| Back | Return to previous screen | Breadcrumb navigation, eventually to TV home |
| Home | Return to Android TV/Google TV launcher | System-level, not app-specific |
| Microphone | Invoke Google Assistant | If remote has mic button |

### Focus Management

- **Always one element in focus** when app is idle/visible
- **Clear focus indication** - combined visual feedback:
  - Scale increase: 1.025x (default) or 1.05x/1.1x for larger elements
  - Border: 2dp solid Outline (#7E858F - Material Outline color)
  - Color change: Surface emphasis (10% color emphasis)
- **Focus moves** logically: top-to-bottom, left-to-right
- **No focus gaps** - every directional press has valid target
- **Focus wraps** at screen edges gracefully
- **Never zero focusable elements** visible - at least one always in focus

### App Navigation Structure

#### Screen 1: Connect Screen (Launch & Reconnection)

**Purpose:** Connect to TV, discover TVs on network

**Elements:**
- **App name:** "Magic Remote" (top, large, prominent)
- **IP Address field:** Centered, with placeholder "Enter TV IP address"
  - Soft keyboard appears when field focused (if text input needed)
  - 5% safe margin on all sides
- **Connect button:** Prominent, Primary color, wide
  - 48dp minimum height, 48dp minimum width
  - Touch target or D-pad selectable
- **Status text:** Below connection result
  - Messages: "Ready", "Connecting to {IP}", "Connected to {IP}", "Failed to connect", "Auto-discovery started..."
- **Discover link:** "Discover TVs on same network" (optional tap/link)
  - Triggers mDNS discovery
  - Shows list of discovered TVs with names/IPs

**Flow:**
1. User launches app
2. Connect screen shown
3. User enters IP or selects discovered TV
4. App attempts ATRPv2 connection (port 6467 pairing, 6466 remote)
5. If paired: move to Remote Control screen
6. If not paired: show pairing code on TV, user enters on phone
7. Reconnection: last connected TV remembered, auto-reconnect on launch

#### Screen 2: Remote Control Screen

**Purpose:** Standard D-pad remote control functionality

**Layout:** D-pad grid of action buttons (3 columns x 4 rows)

**Button Grid (3 columns x 4 rows):**

| Row 1 | BACK | UP | OK |
| Row 2 | LEFT | -- | RIGHT |
| Row 3 | HOME | DOWN | -- |
| Row 4 | POWER | MUTE | VOL+ |
| | | VOL- | (empty or help text) |

**Grid Details:**
- **3 columns**, equal width, with weight distribution
- **4 rows**, each row height consistent
- **Each button:** 48dp minimum height × 48dp minimum width
- **Spacing:** 8px (1 unit of 8px scale) between buttons
- **Margin:** 5% safe margin on all sides of grid
- **Focus indicator:** 2dp outline + 1.025x scale when focused
- **Active state:** 0.95x scale when pressed (tactile feedback)

**Button Functions:**

| Button | KeyEvent | TV Function |
|---|---|---|
| BACK | KEYCODE_BACK | Return to previous screen |
| UP | KEYCODE_DPAD_UP | Move focus up |
| DOWN | KEYCODE_DPAD_DOWN | Move focus down |
| LEFT | KEYCODE_DPAD_LEFT | Move focus left |
| RIGHT | KEYCODE_DPAD_RIGHT | Move focus right |
| OK | KEYCODE_DPAD_CENTER / KEYCODE_ENTER | Select/activate focused item |
| HOME | KEYCODE_HOME | Return to TV launcher |
| POWER | Custom/proprietary | Toggle TV power (sendKey) |
| MUTE | Custom/proprietary | Toggle TV mute (sendKey) |
| VOL+ | Custom/proprietary | Volume up (sendKey) |
| VOL- | Custom/proprietary | Volume down (sendKey) |

**Additional UI Elements (below grid):**
- **Air Mouse toggle:** Full-width button
  - Text: "Air Mouse" or "Pointer"
  - Toggles gyroscope-based pointer control
  - When active: shows pointer area, sensitivity controls
- **Trackpad toggle:** Full-width button (below Air Mouse)
  - Text: "Trackpad" or "Touchpad"
  - Switches to touchpad mode (phone screen as touchpad)

**Focus Management:**
- **Initial focus:** ON the first button (BACK or UP, depending on context)
- **Cyclic focus:** Pressing Right from OK wraps to first button of next row, or stays depending on grid design
- **Consistent flow:** Always predictable focus movement

#### Screen 3: Air-Mouse / Pointer Control Screen

**Purpose:** Gyroscope-based pointer control for TV

**Layout:**
- **Large central pointer area** - displays current cursor position
- **Current coordinates:** "x: ___ , y: ___" or relative position description
- **Sensitivity controls:**
  - Presets: Gentle, Normal, Aggressive
  - Slider or three-tap buttons
- **Mode indicators:**
  - "Gyroscope mode active"
  - "Phone orientation: [description]"
- **Button to return** to Remote Control screen

**Pointer Interaction:**
- Phone gyroscope + accelerometer → cursor movement on TV
- 1€ filter smoothing for jitter reduction
- Configurable sensitivity/acceleration
- **Default screen orientation mapping:**
  - Top edge of phone → top of screen (12 o'clock)
  - Gravity-based up/down (never drifts)
  - Gyroscope-based left/right (turns around true vertical)
- **Tap to click:** Tap screen → TV select/OK
- **Double-tap:** Double-click
- **Hold to drag:** Hold finger to drag/resize

**Sensitivity Levels:**
- **Gentle:** Low sensitivity, smooth movement
- **Normal:** Medium sensitivity (default)
- **Aggressive:** High sensitivity, fast movement
- User can fine-tune in settings

**Return to Remote:**
- Button: "Remote" or "Back to Controls"
- Smooth transition back to D-pad grid

#### Screen 4: Discovery/TV List Screen

**Purpose:** Discover and select TVs on local network

**Layout:**
- **List of discovered TVs**
- Each list item:
  - TV name (from mDNS)
  - IP address
  - Signal strength (approximate)
  - Connection status indicator
- **Searching...** state when discovering
- **No TVs found** state with retry button

**Each TV Item:**
- **Row height:** 48dp minimum
- **Text left:** TV name
- **Text right:** IP address
- **Icon:** Connected/disconnected indicator
- **Action:** On select → attempt connection

**States:**
- **Discovering:** "Searching for TVs..." full-width
- **Found TVs:** List with N items found
- **No TVs:** "No TVs found. Make sure TV and phone are on same Wi-Fi network." + Retry button
- **Error:** "Connection failed. Check IP or network." + Retry

**Flow:**
1. User navigates to Discover screen
2. App initiates mDNS discovery (`_androidtvremote._tcp` + `_androidtvremote2._tcp`)
3. List populates as TVs are found
4. User selects a TV
5. App attempts connection (ATRPv2 pairing flow)
6. On success → Remote Control screen
7. On failure → Error message, retry or return

#### Screen 5: Settings Screen

**Purpose:** Application settings and preferences

**Sections:**
- **Connectivity:**
  - Last connected TV (reconnect button)
  - Auto-reconnect on launch (toggle)
  - Network status indicator
- **Pointer/Mouse:**
  - Air-mouse sensitivity (Gentle / Normal / Aggressive presets + custom slider)
  - Trackpad speed (Slow / Medium / Fast)
  - Invert axes toggle
  - Gyroscope calibration button
- **General:**
  - Theme: Light / Dark / System default
  - About: Version number, credits
  - Feedback: Rate app, report issue

**Settings Item Format:**
- **Toggle switches** for binary options
- **Dropdown/selector** for preset choices
- **Sliders** for continuous values
- **List item** with chevron → details screen

#### Screen 6: Error/Feedback Screen

**Purpose:** Handle errors and user feedback

**Error States:**
- **Connection failed:** "Failed to connect to {IP}. Check network and IP address."
- **Pairing failed:** "Pairing code not received. Try again or enter code manually."
- **TV not responding:** "TV not responding. Check that TV is on and remote protocol is enabled."
- **Network unavailable:** "Network unavailable. Check Wi-Fi connection."

**Each error shows:**
- **Icon:** Warning/exclamation triangle
- **Main message:** Short, descriptive error title
- **Explanation:** 1-2 sentences what went wrong
- **Action button:** "Retry", "Try different IP", "Skip", or "Learn more"
- **Dismiss:** Back button returns to previous screen

**Feedback CTA:**
- "Rate Magic Remote" → Google Play Store link
- "Report issue" → Email or issue tracker link
- "Share app" → Share intent (Bluetooth, message, etc.)

---

## TV-Specific Interaction Patterns

### Back Button Behavior (CRITICAL)

**Rule:** Back button must NEVER act as a toggle (e.g., open/close menu).
**Rule:** Only navigate backward breadcrumb-style.
**Rule:** Consecutive back presses must always eventually lead to Android TV/Google TV home screen.

**Example navigation paths:**
- Game play > Pause screen > Main screen > TV home
- TV show play > App main screen > TV home
- Connect screen → Remote screen → Settings screen → TV home

**Never:**
- Use back to toggle menu open/close
- Infinite loop of closing/opening menu
- Exit confirmation gating (users should exit without confirmation)

### Clear Path to All Focusable Elements

- **Rule:** Let users navigate UI with clear direction
- **Rule:** If no straight path to a control, consider relocating it
- **Rule:** Place controls (like search) in locations that don't overlap other clickable elements
- **Rule:** Avoid layouts with controls in hard-to-reach places

**Design pattern:** Categories traversed on vertical axis, items within category on horizontal axis.

**Anti-pattern:** Complex and nested layout hierarchies where focus gets trapped.

### Focus Indicators (Must-Have)

**Three combined visual cues** for every focusable element:

1. **Scale:** 1.025x when focused (default), 1.05x/1.1x for larger elements
2. **Border:** 2dp solid outline, color #7E858F (Material Outline)
3. **Color emphasis:** Surface with 10% color emphasis

**State transitions:**
- **Default:** Normal size, no outline, normal color
- **Focused:** Scale 1.025x + 2dp outline + color emphasis
- **Pressed:** Scale 0.95x (slightly smaller for tactile feedback)
- **Released:** Return to default state smoothly (200ms animation)

**Focus indicators work** for all focusable elements:
- Buttons (D-pad grid, FAB, standard buttons)
- List items, cards
- Text fields
- Chip, toggle switches
- **Do NOT** use focus indicators on purely decorative elements

### Touch Targets

- **Minimum 48dp × 48dp** (1.9cm × 1.9cm) for all interactive elements
- **Preferred 48dp × 48dp** or larger
- **Even for D-pad navigation** - larger targets reduce miss-presses from distance/viewing angle
- **Button padding:** minimum 12px vertical, 16px horizontal inside container
- **FAB:** 40dp × 40dp touch target, 24dp icon inside
- **Spacing between touch targets:** at least 8px (1× 8px scale)

### 10-Foot Text Legibility

- **Body text minimum 16sp** - must be readable at 3 meters
- **Label/text minimum 14sp** - form fields, buttons
- **Test at 200% font scale** - no clipping, no overflow
- **Line height 1.3-1.5x font size** for body paragraphs
- **Line length limit:** ~12 words per line for readability at distance
- **Avoid ALL CAPS** for body copy or long labels
- **Use title case** for section headers and button labels
- **Contrast ratio:** 4.5:1 for normal text, 3:1 for large text (WCAG AA)
- **Test with varied wallpapers** - colors must remain legible on light, dark, and patterned backgrounds

### Dynamic Color (Material You)

- **Android 12+:** Automatically generate color scheme from wallpaper
- **Primary:** Derived from wallpaper prominent color
- **Secondary:** Complementary scheme
- **Surface:** Optimized for readability against generated primary
- **Fallback static scheme** for Android 11 and below
- **Primary color used sparingly** - only for most important action per screen
- **Surface colors** always use surface role, not primary/secondary

### Accessibility (TalkBack + D-Pad Navigation)

- **Every interactive element needs contentDescription**
  - Describe action/meaning, NOT visual appearance
  - Example: "Select", not "Blue button"
- **contentDescription set programmatically** or via contentDescription attribute
- **Focus order:** Logical (top-to-bottom, start-to-end)
- **Focus moves** to logical target after navigation/dialog dismissal
- **TalkBack operable** - all actions accessible via screen reader
- **customActions** for swipe/long-press actions if needed
- **Minimum 48dp touch targets** even for D-pad (finger accessibility)
- **Text contrast:** 4.5:1 normal, 3:1 large (WCAG AA)
- **Never use color alone** to convey information
- **Support bold text** setting and high contrast system settings
- **Custom canvas views** must use ExploreByTouchHelper for virtual accessibility tree
- **Rationale before permission request** - show system dialog explaining why permission needed

### Permission Handling

- **Never request permissions at app launch**
- **Request in context** when the feature is about to be used
- **Show rationale first** - system dialog with "Allow" and "Don't allow"
- **Example rationale:** "Magic Remote needs Bluetooth to control your TV as a air-mouse."
- **Graceful degradation** if permission denied
- **Example:** "Bluetooth limited functionality. Air-mouse unavailable. D-pad remote still works."

### TV Permission Context

- **Bluetooth:** For air-mouse/gyroscope control. "Magic Remote needs Bluetooth to control your TV as a air-mouse."
- **Network/Screen mirroring:** For TV discovery and remote protocol. "Magic Remote needs network access to discover and control your TV."
- **Location:** Optional, for network discovery optimization. "Helps find nearby TVs."

### Error Handling & User Messages

**All error messages follow this format:**
- **Icon:** Warning/exclamation triangle (outlined, Material icon)
- **Title (1 line):** Descriptive, concise error reason
- **Explanation (1-2 sentences):** What went wrong, simple language
- **Action button:** "Retry", "Try different IP", or "Skip"
- **Dismiss:** Back button returns to previous screen (no modal dismissal required)

**Specific error messages:**

| Error | Message |
|---|---|
| Connection failed | "Failed to connect to {IP}. Check network and IP address." |
| Pairing timeout | "Pairing code not received. Try again or enter code manually." |
| TV not responding | "TV not responding. Check that TV is on and remote protocol is enabled." |
| Network unavailable | "Network unavailable. Check Wi-Fi connection." |
| Unknown TV | "Unknown TV. This TV may not support the Android TV Remote Protocol." |
| Service unavailable | "Remote protocol service unavailable. Try again later." |

**Success messages (brief):**
- "Connected to {TV name}"
- "Disconnected"
- "TV discovered: {name}"
- "Settings saved"

### Feedback Messages (Snackbar-style, brief):

- "Connected to {TV name}" (bottom of screen, auto-dismiss after 3s)
- "Disconnected from TV"
- "Settings saved"
- "Changes not saved" (if applicable)

### Notification Integration

- **Foreground service notification** when connection active (minimal)
  - Small icon, "Connected to {TV name}", tap → switch to app
  - Can be expanded for additional info (pause/resume)
- **Do not interrupt user** with ongoing notifications unless necessary
- **Settings → Notifications** toggle to opt-out of connection status bar

---

## UI Component Specification Table

| Component | State Normal | State Focused | State Pressed | Minimum Size | Notes |
|---|---|---|---|---|---|
| **D-pad Grid Button** | Surface bg, On Surface text | Scale 1.025x + 2dp Outline #7E858F | Scale 0.95x | 48dp × 48dp | Grid focusable, cyclic focus |
| **FAB (Floating Action Button)** | Primary Container bg, On Primary text | Scale 1.025x + ripple | Scale 0.95x, ripple active | 40dp icon, 48dp touch | One per screen, bottom-end |
| **Standard Button** | Transparent/Surface bg, On Surface text | Scale 1.025x + 2dp outline | Scale 0.95x | 48dp × 48dp | Padding 12px vertical, 16px horizontal |
| **Toggle Switch** | Inactive track + inactive thumb | Active track + active thumb (rightmost) | Ripple on tap | 40dp × 24dp | Binary options in Settings |
| **Dropdown/Selector** | Closed: selected value Open: list of options | Keyboard focus same as normal | N/A (click/tap) | Width: 100% parent | Settings presets |
| **Slider** | Thumb at position, track background | Thumb with focus outline | Thumb pressed, drag active | 200dp minimum width | Sensitivity, speed presets |
| **List Item (TV discovery)** | Row bg, TV name left, IP right | Row bg highlight + focus outline | Row bg highlight + press effect | 48dp height | Each TV in discovered list |
| **Divider** | 1dp color Outline #7E858F | Same | Same | 1dp height | Separate sections, optional |
| **Card/Container** | Surface bg, shadow level 3 | Surface bg + shadow level 5 + focus outline | Surface bg + shadow level 5 + press effect | Varies by content | Group related elements |

---

## Interaction Flows

### Flow 1: First-Time Connection

1. User launches Magic Remote app
2. **Connect screen** appears (default on launch)
3. User enters TV IP address or selects "Discover TVs"
4. If "Discover": **Discovery screen** shows found TVs
5. User selects TV from list
6. **ATRPv2 pairing flow:**
   - App connects to port 6467 (pairing)
   - TV displays 6-digit pairing code
   - User enters code on phone (or auto-detect if 6 digits, all numbers)
   - App connects to port 6466 (remote)
   - Connection established
7. **Remote Control screen** appears
8. **Success message:** "Connected to {TV name}"
9. **Foreground service starts** (keeps connection alive)
10. **Navigation:** Use D-pad, Air Mouse, Trackpad as needed

**If connection fails:**
- Error message shown
- "Retry" → try again
- "Try different IP" → enter new IP
- User returns to Connect screen

### Flow 2: Re-connecting (App Already Used)

1. User launches Magic Remote app
2. **Last connected TV remembered**
3. App attempts auto-reconnect to last TV
4. If successful: **Remote Control screen** shown immediately
5. If failed: **Connect screen** shown with last IP pre-filled
6. User can manually enter new IP or discover

### Flow 3: Switching Between Modes

**From Remote to Air-Mouse:**
1. User presses "Air Mouse" button on Remote screen
2. App switches to **Air-Mouse screen**
3. Gyroscope starts, pointer appears on TV
4. User controls cursor with phone orientation

**From Air-Mouse to Remote:**
1. User presses "Remote" button on Air-Mouse screen
2. App switches back to **Remote Control screen**
3. Gyroscope stops, pointer disappears
4. Focus returns to first D-pad button

**From Remote to Trackpad:**
1. User presses "Trackpad" button on Remote screen
2. App switches to **Trackpad mode** (full-screen touchpad indicator)
3. Phone screen functions as touchpad
4. Swipe to move cursor/selection
5. Tap to select
6. **Return to Remote:** "Remote" button

### Flow 4: App Background/Lock Screen

**When user presses Home or switches apps:**
- Foreground service continues running (keeps TLS connection)
- **Small notification** in status bar: "Magic Remote - Connected to {TV name}"
  - Tap → switch to app
  - Collapsible: shows connection stats, pause/resume
- D-pad input stops working when app is in background
- **Connection preserved** - resumes when app returns to foreground
- **If service killed** by Android TV memory management: auto-reconnect on foreground return

### Flow 5: App Restart After Kill

1. Android process killed (memory management, system optimization)
2. User relaunches Magic Remote from launcher
3. **Connect screen** shown (or last TV auto-reconnect attempt)
4. If last TV still on network: auto-reconnect
5. If not: user enters IP or discovers
6. **Remote Control screen** shown on success
7. **Connection state preserved** (remembered certificates, pairing)

---

## TV-Safe Design Checklist

### Pre-Submission/Release Checks

- [ ] **10-foot text legibility:** Body ≥16sp, tested at 3m simulated distance
- [ ] **Focus indicators** on all focusable elements (scale + border + color)
- [ ] **Back button behavior:** Predictable, breadcrumb-style, no toggles, no confirmations
- [ ] **No exit gating:** Users can exit without confirmation
- [ ] **48dp minimum touch targets** on all interactive elements
- [ ] **Contrast ratio:** 4.5:1 normal text, 3:1 large text (WCAG AA)
- [ ] **Dynamic color fallback:** Static scheme for Android <12
- [ ] **Every interactive element has contentDescription** (TalkBack accessible)
- [ ] **No color-alone information** - always combine with text/icon
- [ ] **Permission rationale** in context, not at launch
- [ ] **Test with 3+ wallpapers** for dynamic color harmony
- [ ] **Test at 200% font scale** - no clipping/overflow
- [ ] **Overscan safe area:** 5% margin, critical content within 90%
- [ ] **One FAB maximum** per screen, bottom-end position
- [ ] **Back button never a toggle**
- [ ] **Consecutive back presses lead to TV home**
- [ ] **No virtual back button** on screen (remote handles it)
- [ ] **Portrait/landscreen:** App supports landscape only (TV convention)
- [ ] **No fixed pixel breakpoints** - use WindowSizeClasses
- [ ] **Ripple effect** on all tappable elements
- [ ] **Haptic feedback** on long press and primary actions
- [ ] **Snackbar feedback** for brief messages (auto-dismiss)
- [ ] **Settings → Notifications** opt-out for connection status
- [ ] **Privacy:** No user data sent without consent; local network only
- [ ] **Test with real TV:** Actual Android TV/Google TV hardware if possible

### Accessibility Checklist

- [ ] contentDescription on every interactive element
- [ ] Describes action/meaning, not visual appearance
- [ ] Focus order logical (top-to-bottom, left-to-right)
- [ ] TalkBack operable for all actions
- [ ] Minimum 48dp touch targets
- [ ] Contrast WCAG AA: 4.5:1 normal, 3:1 large
- [ ] Never color-alone information
- [ ] Support bold text and high contrast system settings
- [ ] Custom canvas → ExploreByTouchHelper virtual tree
- [ ] Permission rationale in context
- [ ] Graceful degradation if permission denied

---

## Design Assets & Tokens Summary

### Color Token Summary (Hex)
- Primary: #1A1C1E
- On Primary: #FFFFFF
- Primary Container: #D0D6E6
- On Primary Container: #212529
- Secondary: #6C7278
- On Secondary: #212529
- Secondary Container: #E8EAED
- On Secondary Container: #212529
- Tertiary: #B8422E
- On Tertiary: #212529
- Surface: #F8F9FA
- On Surface: #1A1C1E
- Outline: #7E858F
- Error: #B00020
- On Error: #FFFFFF

### Typography Scale (sp)
- Display Large: 57px
- Display Medium: 45px
- Display Small: 36px
- Headline Large: 32px
- Headline Medium: 28px
- Headline Small: 24px
- Title Large: 22px
- Title Medium: 16px
- Title Small: 14px
- Body Large: 16px (minimum)
- Body Small: 12px (small print)
- Label Large: 14px
- Label Medium: 12px
- Label Small: 11px

### Length Scale (dp)
- 8px scale: 4px, 8px, 16px, 24px, 32px
- Safe margins: 5% on all edges
- Focus border: 2dp
- Touch target minimum: 48dp × 48dp
- Component padding: 12px vertical, 16px horizontal
- Divider: 1dp

### Elevation Levels (dp)
- Level 0: 0dp (default)
- Level 1: 1dp
- Level 2: 2dp
- Level 3: 3dp (standard)
- Level 3+2: 5dp (focused state)
- Level 4: 4dp
- Level 5: 6dp
- Level 6: 8dp
- Level 7: 16dp (card)
- Level 8: 24dp (drawer/modal)
- Level 9: 32dp (overlay/popup)

### Component Specs
- **D-pad grid button:** 48dp × 48dp, 8px spacing, cyclic focus
- **FAB:** 40dp icon, 48dp touch, one per screen, bottom-end
- **Toggle switch:** 40dp × 24dp, track + thumb
- **List item (TV discovery):** 48dp height, TV name + IP
- **Divider:** 1dp, Outline #7E858F
- **Safe margins:** 5% on all edges

---

## Design Rationale Summary

### Why This Design?

1. **ATRPv2 protocol choice** - Industry standard for Android TV Remote, supported by most Google TV/Android TV devices, TLS + protobuf, proven by Home Assistant and Outofcontext app
2. **Three-approach architecture** - Wi-Fi protocol + companion app + Bluetooth HID fallback provides resilience no single approach can match
3. **10-foot UI prioritization** - Most TV apps ignore this; designing for 3m viewing distance differentiates the app
4. **D-pad-first with pointer fallback** - Most TV remote apps are D-pad-only; adding pointer/air-mouse provides enhanced capability without removing baseline functionality
5. **Companion app reliability** - Addresses the documented Android TV memory management pitfalls; foreground service alone insufficient, WorkManager + Bluetooth fallback
6. **MIT/Apache licensed code reuse** - Primary candidates identified: Outofcontext Android TV Remote (protocol), Linkpad (gyroscope/air-mouse math), AimMac (Bluetooth pointer protocol)

### Trade-offs Made

1. **D-pad baseline + pointer optional** vs. pointer-only: Ensures app works on ALL Android TVs, even those without pointer support through normal protocol
2. **Companion app + Bluetooth fallback** vs. companion-less: Complexity vs. reliability - documented Android TV killing companion apps makes fallback essential
3. **Wi-Fi first, Bluetooth secondary** vs. Bluetooth-only: Wider TV compatibility (Wi-Fi ATRPv2 supported on virtually all Android TV/Google TV), Bluetooth HID limited to some models
4. **Single FAB per screen** vs. multiple primary actions: Clarity vs. convenience - one primary action per screen is design rule, supports user decision-making

### What Was Deliberately OMITTED (and Why)

- **Touch screen assumed:** Design is D-pad-first; touch/touchpad is optional companion feature
- **Vertical letterboxing/pillarboxing:** TV apps must support landscape only
- **Complex nested hierarchies:** Focus gets trapped; kept to 2-3 level depth maximum
- **Pure black dark theme:** Material tonal mapping; pure black (#000000) causes issues on OLED and looks bad on LCD with wallpaper bleed
- **Exit confirmation modals:** Users should exit without confirmation; consistent with TV conventions
- **Multiple FABs per screen:** Violates Material Design primary action clarity rule
- **Hardcoded hex colors:** Violates Material 3 color roles + dynamic color support
- **Fixed breakpoints:** TV screen sizes vary widely; use WindowSizeClasses instead

---
*UI/UX Design Specification for Android Magic Remote application. Following Material Design 3, Android TV design guidelines, and Google DESIGN.md format principles. Version 1.0.*