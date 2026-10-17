# Remote desktop mouse mode

Choose **Remote desktop** in Settings → Mouse mode, or from the existing mouse-mode
selector in the streaming menu. The existing Remember mouse mode preference and
profile settings apply. Choosing another mode removes the desktop controls.

- Drag a finger inside the right-hand trackpad to move the pointer.
- Tap to left-click. Hold still for the existing 650 ms threshold to press
  the right mouse button; lift to release. A two-finger tap also right-clicks.
- Two-finger movement uses the existing relative trackpad scrolling behavior.
- The larger circular keyboard button opens the existing keyboard.
- The circular home button sends Windows/Super through the existing key sender.
- The circular mouse button toggles full-screen relative tracking. In this state,
  the trackpad panel disappears and touches anywhere outside controls move/click.
- The eye hides the new controls and remains at 20% opacity. Tap it to restore
  them. Hiding controls preserves the selected input area; it does not switch
  mouse modes or disable mouse input.

A gesture must begin inside the trackpad when full-screen tracking is off.
Touches outside that area are consumed without reaching the remote desktop.
Buttons consume their complete gestures. Mode changes, activity pause and overlay
removal cancel active touches so a held mouse button is released.

The existing stream menu and zoom toggle remain reachable. Hardware mouse and
stylus events retain the existing input route. Connection-level view-only and
keyboard/mouse permissions continue to gate remote input.

## Verification

`DesktopTouchTest` exercises left-click taps, right-button holds and two-finger right-clicks and cancellation,
movement without clicks and compatibility with the existing gaming trackpad mode.
`DesktopControlsTest` exercises confinement, full-screen tracking, button isolation,
visibility and circular control dimensions.

CI runs:

```
bash ./gradlew :app:testNonRoot_gameDebugUnitTest :app:lintNonRoot_gameDebug :app:assembleNonRoot_gameDebug
```

Physical-device checks still needed: Pixel landscape safe areas, keyboard show/hide,
held mouse-button release during app backgrounding, two-finger scrolling, changing modes
from the stream menu, and mouse/stylus input with the overlay enabled.

## Layout editor and game layouts

Settings opens with **Common settings**. Use **Search settings** to find an option,
including a mouse-mode choice by name, or choose a section from the section picker.
**All settings** retains every available preference; longer categories expand with
**More**. Existing stored settings, device compatibility filters and dependencies
are preserved.

Open **Settings → Overlay configuration** (also under **Overlays & controls**).
The editor displays a sample desktop in a 16:9 preview using the streaming renderer.
Drag a control to move it, or select it from the list and use the size slider.
The selected control has a brighter outline. Built-in buttons remain round.

- **Save** saves and selects the layout for future streams.
- **Layouts** opens saved configurations. Load one, edit it, and press Save to select it.
- **New** creates an independent configuration. Layouts have individual names.
- **Game name** is optional. It matches the launched application's name, ignoring
  case. Saving the same game assignment reassigns it to the newly saved layout.
  Other games use the selected general layout. The Remote desktop mouse mode
  must be selected to display these controls.
- **Add button** creates a circular button with a keyboard key, shortcut, or built-in action.
  For example, Shift creates a **Shift** button. It stays pressed while touched,
  then releases on lift, cancellation, hiding controls, backgrounding or mode exit.
  **Assign action** updates any cluster or added button; **Delete button** removes the selected button after confirmation. The eye and trackpad remain dedicated controls.
- **Import** opens Android's document picker and previews a validated JSON layout.
  Press Save to keep it. Imports get a new identity and do not overwrite a saved file.
- **Export** saves the current configuration with Android's document picker. A
  layout can be shared and reimported on another device. No storage permission is needed.
- **Reset positions** resets the built-in controls while retaining custom keys.

New files use version 2 of the JSON schema; version 1 layouts are upgraded on load. Positions are normalized centers (`x`, `y`)
from 0 to 1. Circular button `size` is a fraction of the preview's shorter side.
Trackpad `width` and `height` are fractions of the full preview. Buttons use `action` for a keyboard key (`key`) or a built-in function (`keyboard`, `home`, `mouse`, `eye`). Keyboard shortcuts use `keys`, an array of up to eight distinct virtual-key codes; legacy `key` remains supported. Labels are derived by the app.
Import rejects unsupported versions, invalid types/positions, duplicate IDs,
missing eye/trackpad controls, unsupported key codes, more than 67 buttons,
and files larger than 256 KB. At most 50 layouts can be saved.

Keyboard shortcuts hold their modifiers before their main keys and release in reverse order. F1–F24, numpad, media/browser and navigation keys are listed; other virtual-key codes from 08 to FE can be entered with host-dependent support. Built-in actions can be assigned to any cluster/added button. Macros, scripts and automatic game detection outside the streamed launch name are outside this format.
