# Remote desktop mouse mode

Choose **Remote desktop** in Settings → Mouse mode, or from the existing mouse-mode
selector in the streaming menu. The existing Remember mouse mode preference and
profile settings apply. Choosing another mode removes the desktop controls.

- Drag a finger inside the right-hand trackpad to move the pointer.
- Tap to right-click. Hold still for the existing 650 ms drag threshold to press
  the left mouse button; move to drag and lift to release.
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
removal cancel active touches so a held left button is released.

The existing stream menu and zoom toggle remain reachable. Hardware mouse and
stylus events retain the existing input route. Connection-level view-only and
keyboard/mouse permissions continue to gate remote input.

## Verification

`DesktopTouchTest` exercises right-click taps, left-button holds and cancellation,
movement without clicks and compatibility with the existing gaming trackpad mode.
`DesktopControlsTest` exercises confinement, full-screen tracking, button isolation,
visibility and circular control dimensions.

CI runs:

```
bash ./gradlew :app:testNonRoot_gameDebugUnitTest :app:lintNonRoot_gameDebug :app:assembleNonRoot_gameDebug
```

Physical-device checks still needed: Pixel landscape safe areas, keyboard show/hide,
left-drag release during app backgrounding, two-finger scrolling, changing modes
from the stream menu, and mouse/stylus input with the overlay enabled.
