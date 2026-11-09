---
version: alpha
name: MagicRemote
description: Android Magic Remote application for controlling televisions using Android phone
---
## Overview

Android Magic Remote turns a smartphone into a television remote control with Motion-based pointer control, touchpad-style control, and standard remote buttons and navigation. The application supports Android TV and Google TV as first priority, with later support for LG webOS and Samsung Tizen TV platforms.

The application provides Magic Remote-style control rather than simply functioning as a conventional button remote. Key functionality includes motion-based pointer control using the phone's gyroscope, touchpad-style control, standard remote buttons and navigation, Wi-Fi-based communication, Bluetooth-based communication where supported, TV discovery and pairing, and reliable reconnection.

The architecture combines three approaches:
- Way 2: Wi-Fi remote protocol (Android TV Remote Protocol v2, ATRPv2)
- Way 3: TV companion application (for reliability, memory management, reconnection)
- Way 4: ADB or additional input-injection capabilities where supported

These approaches work together rather than treating one as the only solution.

## Colors

The color palette follows Material Design 3 guidelines adapted for 10-foot UI viewing distance (typically 3 meters / 10 feet from TV). Colors are optimized for visibility from a distance.

- **Primary (#1A1C1E):** Deep ink for primary text and core UI elements. High contrast against light backgrounds. Used for D-pad active state, primary text.
- **Primary Container (#D0D6E6):** Surfaces and backgrounds for primary content areas. Used for main UI backgrounds.
- **On Primary (#FFFFFF):** Text and icons on Primary color. Used on Primary Container.
- **Secondary (#6C7278):** Subtle accent for secondary elements. Used for disabled states, dividers, secondary text.
- **Secondary Container (#E8EAED):** Surfaces for secondary content. Used for inactive UI elements.
- **On Secondary Container (#212529):** Text on Secondary Container.
- **Tertiary (#B8422E):** Accent color for the single most important action per screen (Floating Action Button, primary touch target). Used sparingly.
- **Tertiary Container (#F4C9C4):** Surface for tertiary elements.
- **On Tertiary Container (#212529):** Text on Tertiary Container.
- **Error (#B00020):** For error states, invalid inputs. Used cautiously.
- **On Error (#FFFFFF):** Text on Error color.
- **Surface (#F8F9FA):** Default background color for UI screens. Warm white, softer than pure white (#FFFFFF).
- **On Surface (#1A1C1E):** Text and icons on Surface. Primary text color.
- **Surface Variant (#E2E7EC):** Alternative surface for grouped or secondary content.
- **On Surface Variant (#495057):** Text on Surface Variant.
- **Outline (#7E858F):** Border and divider color. Used for separation between UI sections.
- **On Outline (#1A1C1E):** Text on Outline.

### Color Usage Rules

- **Primary color used only for the single most important action per screen** (e.g., connect button, FAB)
- **Surface used for all screen backgrounds**, not Primary/Secondary
- **On Surface (1A1C1E) used for all body text and primary icons**
- **Contrast ratio >= 4.5:1 for normal text**, >= 3:1 for large text (WCAG AA)
- **Do not use color alone to convey information** - always combine with text or icons
- **Dynamic color support** for Android 12+ (material you), with fallback static scheme for older devices

## Typography

Type scale follows Material Design 3 type ramp, using `sp` units so user font scaling applies. All text sizes defined relative to 16px base.

- **Display Large (57px):** Headers at the top of screens, sparse use. Font: Public Sans or system UI font.
- **Display Medium (45px):** Section headers within screens.
- **Display Small (36px):** Panel labels, important text.
- **Headline Large (32px):** Title text, screen titles.
- **Headline Medium (28px):** Subsection headers.
- **Headline Small (24px):** Caption-style headers.
- **Title Large (22px):** Toolbar titles, card titles.
- **Title Medium (16px):** Item labels in lists, forms.
- **Title Small (14px):** Secondary labels, helper text.
- **Body Large (16px):** Body text, instructions, descriptions. **Minimum size**.
- **Body Small (12px):** Secondary text, metadata, timestamps. **Only for small print**.
- **Label Large (14px):** Form labels, button text. **Minimum for interactive text**.
- **Label Medium (12px):** Helper text, secondary labels.
- **Label Small (11px):** Placeholder text, subtle annotations.

### Typography Rules

- **All text sizes in `sp` units** - user font scaling applies automatically
- **Body text minimum 16sp** - readability at 10-foot distance
- **Label text minimum 14sp** - form fields, buttons
- **Test at 200% font scale** - no clipping, no overflow
- **Line height 1.2-1.5x font size** for body text
- **Use MaterialTheme.typography roles**, not hardcoded sizes
- **Font family:** System UI font (Roboto on Android, SF Pro on iOS fallback)
- **All-caps labeling only for buttons** (never for body copy)
- **Maximum two font weights per screen** - regular (400) and medium (500)

## Layout

Layout strategy for 10-foot UI (lean-back experience, viewed from ~3 meters). Designed for D-pad navigation with directional focus movement.

### Grid System

- **8px spacing scale** with 4px half-step for micro-adjustments
- All margins, paddings, gutters follow this scale
- Components aligned to this rhythmic grid

### Screen Types

- **Primary remote screen:** D-pad grid navigation, connect section, button grid
- **Air-mouse screen:** Full-screen pointer area, sensitivity controls
- **Discovery screen:** TV list from mDNS, pairing code display

### Safe Margins

- **5% margin on all edges** to handle overscan on older TV sets
- Critical elements (text, buttons) stay within 90% of screen width/height
- Non-critical decorative elements can extend to edges

### D-pad Focus Navigation

- **Logical, predictable focus flow** - top-to-bottom, left-to-right
- **No focus gaps** - every direction has a valid target
- **Clear focus indicator** - visible on focused element (scale + border + color)
- **Always one element in focus** when app is idle
- **Back button** navigates breadcrumb-style to previous screen, never a toggle

### Layout by Screen

#### Connect Screen

- IP address field centered, with placeholder text
- Connect button prominent (Primary color, large touch target)
- Status text below connection result
- Link to discover TVs on same network

#### Remote Control Screen

- **D-pad grid:** 3 columns x 4 rows of action buttons
- **Row 1:** BACK, UP, OK
- **Row 2:** LEFT, (OK centered), RIGHT
- **Row 3:** HOME, DOWN
- **Row 4:** POWER, MUTE, VOL+, VOL-
- **Air Mouse toggle:** full-width button below grid
- **Trackpad toggle:** below Air Mouse button

#### Pointer/Air-Mouse Screen

- Large central area for pointer display
- Current cursor coordinates displayed
- Sensitivity slider or preset levels
- Buttons: Default, Aggressive, Gentle modes
- Button to switch to Remote control view

## Elevation & Depth

Following Material Design 3 elevation system, adapted for 10-foot UI. Elevation provides visual hierarchy and focus indication.

### Elevation Values (dp)

- **Level 0:** 0dp - Default surface, no shadow
- **Level 1:** 1dp - Minimal separation
- **Level 2:** 2dp - Subtle separation
- **Level 3:** 3dp - Standard elevation
- **Level 4:** 4dp - Elevated element
- **Level 5:** 6dp - Prominent element
- **Level 6:** 8dp - Significant element
- **Level 7:** 16dp - Card elevation
- **Level 8:** 24dp - Modal, drawer
- **Level 9:** 32dp - Overlay, popup

### Focus Indication (Combined with Elevation)

When an element receives D-pad focus, elevation increases by 2 levels above its base, combined with border/scale changes. This provides clear visual feedback for navigation.

- **Focused element:** Base elevation + 2 levels + border (2dp) + scale 1.025x
- **Pressed element:** Base elevation + 3 levels + ripple effect

### Surface Colors

- **Default Surface:** Surface (#F8F9FA)
- **Focused Surface:** Surface with 10% color emphasis + increased elevation
- **Pressed Surface:** Surface with 15% color emphasis + ripple animation

## Shapes

Corner radius follows Material Design 3 specifications, adapted for D-pad focus visibility.

- **0dp:** Sharp corners (rarely used, modern buttons)
- **2dp:** Minimal rounding
- **4dp:** Small rounding (default for interactive items)
- **8dp:** Medium rounding (cards, modals)
- **16dp:** Pill shape, full rounding
- **9999dp:** Fully rounded (circles)

### Focus Corner Priority

- Buttons and interactive: **4dp** (small enough to see focus, large enough to be recognizably rectangular)
- Cards and containers: **8dp**
- Toggle switches, chips: **4dp**
- Text fields: **4dp**

**Do not mix rounded and sharp corners in the same view** - inconsistent.

## Components

### Primary Action Button (FAB - Floating Action Button)

- **One FAB maximum per screen**
- **Bottom-end position**, above Navigation Bar if present
- **Primary Container** background color
- **24dp x 24dp** icon, with 40dp x 40dp touch target
- **Label** (Extended FAB) preferred over icon-only
- **On release:** Performs primary action
- **Only one primary action per screen** - discern what the user should do first

### Button (Standard)

- **Padding:** 12px vertical, 16px horizontal (minimum 48dp x 48dp touch target)
- **Background:** Transparent or Surface color
- **Text color:** On Surface (#1A1C1E)
- **Hover/focus state:** Surface Variant + outline (2dp Outline #7E858F)
- **Pressed state:** Ripple effect, slight color elevation
- **Disabled state:** On Surface Variant, 40% opacity
- **Corners:** 4dp radius

### D-pad Navigation Grid Buttons

Same as standard button but with additional focus ring:
- **Focus ring:** 2dp solid Outline (#7E858F) + 1.025x scale when focused
- **Active state:** Scale 0.95x when pressed (provides tactile feedback)
- **State order:** Default → Focused (scale + outline) → Pressed (scale down) → Released

### Trackpad Area (Touchpad Mode Indicator)

- **Background:** Surface Variant
- **Minimum size:** 400dp x 240dp (large enough for finger-like tracking area indication)
- **Text:** "Trackpad mode - swipe to control" or similar
- **Toggle button:** Below main remote grid

### Status Bar / Info Area

- **Height:** 40dp minimum
- **Background:** Surface (transparent on remote screen)
- **Text:** On Surface (#1A1C1E)
- **Content:** Currently selected app, connection status, pointer coordinates, battery

### Dividers

- **Height:** 1dp
- **Color:** Outline (#7E858F)
- **Usage:** Separate logical sections, not decorative

### Divider-free approach

- Prefer spacing (8px scale) over visual dividers where possible
- Use elevation or background color changes instead of dividers

## Do's and Don'ts

### Do's

- **Design for 10-foot UI** - large text, high-contrast colors, bold graphics visible from ~3 meters
- **Use D-pad navigation** - all UI elements reachable with up/down/left/right/selected on remote
- **Provide clear focus indication** - scale + border + color change when element has focus
- **Make back button predictable** - breadcrumb-style navigation to previous screen, eventually to Android TV Home
- **Maintain 48dp minimum touch target** - even for D-pad, larger is better for reliability
- **Use Material Design 3 color roles** - not hardcoded hex values
- **Support dynamic color** (Android 12+) with static fallback for older devices
- **Test at 200% font scale** - no clipping, no overflow
- **Place primary action FAB at bottom-end** of screen, above navigation bar if present
- **Limit to one FAB per screen** - discern the single primary action
- **Use cancel buttons** (not exit confirmation) for leaving screens
- **Display predictable back button behavior** - never gate with confirmation screens
- **Ensure clear path to all focusable elements** - no unreachable controls with D-pad
- **Organize navigation by axes** - categories vertical, items within category horizontal
- **Use predictable back navigation** - consecutive back presses always lead to TV home
- **Maintain WCAG AA contrast** - 4.5:1 normal text, 3:1 large text
- **Every interactive element needs contentDescription** - for accessibility TalkBack
- **Describe action/meaning, not visual appearance** in contentDescription
- **Support dark theme** with tonal mapping, not pure black (#000000)
- **Use surface roles** for backgrounds, not primary/secondary colors
- **Test with 3+ different wallpapers** for dynamic color harmony on Android 12+
- **Provide one-time permissions rationale** in context, not at app launch
- **Use Photo Picker instead of READ_MEDIA_IMAGES** where possible
- **Prefer ACCESS_COARSE_LOCATION** unless precise location is essential
- **Haptic feedback** on long press and primary action
- **Ripple effect** on all tappable elements
- **Modal bottom sheets** for supplementary information, persistent for ongoing options
- **Bottom sheets must have visible drag handle**
- **Sheet content must be scrollable** if it overflows
- **Dialogs for critical decisions only** - not for non-critical info
- **Confirm button is text button; dismiss on left**
- **Dialog titles are concise questions or statements**
- **Snackbars for brief non-critical feedback**
- **Snackbars above Navigation Bar, below FAB**
- **Include undo action** when operation is reversible
- **Correct chip type** for use case (Filter/Assist/Input/Suggestion)
- **Chips in horizontal scroll or flow layout, not stacked vertically**
- **Expose waiting states immediately**; acknowledge long-running work with visible progress
- **Avoid exit gating** - users should exit without confirmation
- **Never display virtual back button** on TV screen - remote back button handles navigation
- **Never use pure black (#000000)** for dark theme - use Material surface roles with tonal mapping
- **Never hardcode color hex values** - always use theme color roles
- **Never use more than two font weights** per screen
- **Never use fixed pixel breakpoints** - use WindowSizeClasses
- **Never place touch targets** in system gesture zones
- **Never reduce touch targets** to save space
- **Never use color alone** to convey information
- **Never override onBackPressed()** - use BackHandler (Compose) or OnBackInvokedCallback (View-based)
- **Never use dp for text sizes** - always sp
- **Never show exit confirmation** unless unsaved user input is at risk

### Don'ts Summary

The Don'ts are enforcement rules ensuring accessible, predictable, 10-foot UI design for television interaction models.

---

## TV-Specific Design Notes

### 10-Foot UI Considerations

- **Average viewing distance:** 3 meters (10 feet)
- **Screen size assumptions:** 55" 4K TV (but app scales to various sizes)
- **Text legibility:** Minimum 16sp body text, must be readable at 3m
- **Color visibility:** High contrast against varied wallpapers
- **Overscan:** 5% safe margin on all edges - critical content within 90% of screen

### D-Pad Navigation Rules

- **4-way navigation** only: up, down, left, right, select (center), back, home
- **No touch input** assumed on TV devices (companion app provides touch/touchpad)
- **Focus movement** is to the nearest focusable element in the pressed direction
- **No diagonal movement** - cardinal directions only
- **Focus wraps** at screen edges (last element in row, first in next row)

### TV Controller Buttons Mapped

- **D-pad Up/Down/Left/Right:** Navigate focus
- **Select/Enter:** Activate focused element
- **Back:** Navigate previous screen (breadcrumbs)
- **Home:** Return to Android TV/Google TV launcher
- **Microphone:** Invoke Google Assistant (if present on remote)

### Companion App Role

When the companion phone app is active:
- Provides full remote functionality including pointer/air-mouse
- Handles complex operations that are difficult with D-pad alone
- Shows detailed status, battery, connection info
- Allows text input via phone keyboard
- Manages TV discovery and pairing

When companion app is unavailable:
- Fall back to direct Bluetooth HID (where supported)
- D-pad remote control always available as baseline
- User notified of mode change

---
*DESIGN.md following Google Labs format (open-sourced April 2026). Canonical section order: Overview, Colors, Typography, Layout, Elevation & Depth, Shapes, Components, Do's and Don'ts. Tokens are normative values; prose provides design rationale.*