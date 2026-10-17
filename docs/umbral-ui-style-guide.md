# Umbral UI style: Purple Horizon

This guide records the Occultation overlay maker's visual language, its loading-screen extension, and a proposed Eclipse overlay migration. Android loading artwork and dialogs are implemented in 0.1.7-dev. Eclipse changes below are a plan, not implemented behavior.

## Art direction

**Layered geometric vector landscapes with a restrained purple glass interface.** Flat angular mountains create depth through overlapping plum and lavender silhouettes. A small desktop window ties the scenery to remote computing; a crescent/eclipse and sparse square stars connect it to the Umbral family. The result should feel quiet, legible and atmospheric, rather than crowded or neon.

The overlay maker's existing `overlay_preview_desktop.xml` is the reference. New `loading_horizon.xml` uses the same landscape colors and window motifs, at a wide 480 × 180 vector viewport. Shapes remain crisp across densities. The crescent is formed by overlapping circles; depth comes from color and layering, not simulated 3D lighting. There are no photos, raster textures, live blur, particle effects or decorative animation in this implementation.

## Shared palette

Android hexadecimal colors use #AARRGGBB when alpha is present.

| Role | Value | Use |
| --- | --- | --- |
| Base | #171320 | Editor background |
| Loading surface | #F2171320 | Approximately 95% opaque plum card; stable text contrast |
| Primary text | #F4EAFF | Titles, icons, selected controls |
| Secondary text | #CEC2DD | Live status and supporting text |
| Muted hint | #B2A3C4 | Editor hints |
| Accent | #C8A6F5 | Progress indicator and focus family |
| Border | #A5C8A6F5 | Approximately 65% opaque lavender outline |
| Streaming control fill | #5F6637A6 | Approximately 37% opaque purple; preserve visibility of the stream |
| Landscape | #292242, #4A3567, #6A5180 | Sky and layered mountain silhouettes |
| Art accent | #B08ACB | Crescent and desktop details |

A loading card is intentionally more opaque than a streaming control because its text must remain readable over arbitrary content. “Glass” means translucent fills and fine borders here; it does not mean a backdrop blur. Do not apply a global opacity to a card with text, since that also fades its labels.

## Geometry, typography and behavior

- Keep the overlay's action buttons circular, including the tighter GameCube-inspired cluster. Preserve user layout positions, sizing and saved bindings.
- Use a 28dp radius for trackpads and loading cards, with a 1dp lavender border. The editor selection outline remains 3dp pale lavender.
- Loading content uses 24dp padding, a 22sp bold title, 16sp status, a 28dp indicator and a 16dp indicator-to-text gap. Use the system font and scalable text units.
- Loading cards cap at 480dp wide and leave at least 16dp on each screen edge. Content scrolls on short displays or at large font sizes. Artwork fits without cropping or text embedded in the image.
- Keep actual stage messages supplied by the connection/app-loading code. The spinner is indeterminate; do not invent percentages or add delays to showcase art.
- Artwork is decorative and excluded from accessibility focus. Status changes use a polite live region. Back cancels a connection using the existing finish-on-cancel behavior; app refresh retains its existing non-cancelable behavior.
- Keep streaming controls clear and lightweight: the eye remains reachable at 20% alpha when hidden. Tap means left click; hold or two-finger tap means right click. Restyling must not alter input behavior.
- Only the ordinary platform progress indicator animates. Do not add parallax, looping landscapes or GPU blur to a latency-sensitive client.

## Android implementation map

| File | Responsibility |
| --- | --- |
| `app/src/main/res/drawable/overlay_preview_desktop.xml` | Existing overlay-maker art reference |
| `app/src/main/res/drawable/loading_horizon.xml` | New matching landscape, crescent and desktop illustration |
| `app/src/main/res/drawable/loading_card.xml` | Rounded translucent card and lavender border |
| `app/src/main/res/values/loading_theme.xml` | Named loading palette resources |
| `app/src/main/res/layout/dialog_loading.xml` | Responsive loading card, title and live stage text |
| `app/src/main/java/com/limelight/utils/SpinnerDialog.java` | Shared connection and app-refresh dialog lifecycle |

The shared dialog replaces the platform ProgressDialog while keeping its public calling API. Dismiss is idempotent, late status callbacks are safe, and per-activity cleanup does not close another activity's dialog. This pass does not claim to redesign every settings page or Android's OS-managed cold-start splash.

## Eclipse overlay migration plan

The desktop source uses multiple rendering paths. Match their visual tokens while preserving each path's event handling and renderer compatibility.

| Surface | Existing location under `release-overlay/Eclipse/app` | Proposed work |
| --- | --- | --- |
| In-stream control center | `streaming/controlcenter.cpp` | Replace hardcoded gray cards and pink selection with shared plum/lavender tokens; rounded cards, circular icon actions where appropriate, readable labels and consistent focus rings |
| Statistics/status overlays | `streaming/video/overlaymanager.cpp` and `.h` | Apply the same typography, dark text surface and border; keep compact statistics readable without covering gameplay |
| Desktop shell/loading | `gui/main.qml`, `gui/AppView.qml`, `gui/PcView.qml` | Add matching Qt palette/component definitions and reuse landscape art through the resource bundle |
| Asset registration | `resources.qrc` | Bundle vector artwork/icons and keep runtime resource paths stable |

1. **Inventory and shared tokens.** Audit all hardcoded colors, rendered surfaces, focus states and loading entry points. Introduce equivalent Qt/QPainter tokens for the palette, alpha, corner radius and spacing. Do not copy Android dp directly to physical desktop pixels; account for logical scale and display density.
2. **One control-center prototype.** Restyle the existing QPainter surface rather than replacing input routing. Preserve hit rectangles, keyboard/controller navigation, sliders, recording and statistics actions. Draw translucent backgrounds separately from fully opaque labels. Use artwork only in loading/empty states, not behind dense streaming controls.
3. **Status/statistics consistency.** Match compact cards and warning/error text, retaining semantic status colors alongside labels/icons. Ensure the overlay renderer uploads changed surfaces only when needed. Avoid per-frame blur or rebuilding decorative art every video frame.
4. **Shell/loading alignment.** Reuse the vector landscape in Qt loading states with genuine stage messages. Share styles between QML views and make inactive, focused, pressed and disabled states explicit.
5. **Verification before release.** Compare screenshots at 720p/1080p/4K and multiple DPI scales, check long/localized text and large font settings, test mouse/keyboard/controller navigation and transparency over bright/dark streams. Benchmark overlay-open frame pacing and rendering overhead against the current version. Verify each supported renderer/backend and preserve all current stream/input settings.

Completion means a recognizably shared visual family, readable overlays, unchanged input bindings and no measurable frame-pacing regression. Decide Eclipse's exact button layout with a visual preview before changing established desktop control placement.
