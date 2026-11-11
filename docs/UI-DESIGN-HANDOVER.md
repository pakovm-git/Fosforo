# BFI: product and UI design handover

Prepared 11 November 2026 for a UI specialist. This is a design brief and a record of decisions, not permission to replace working rendering or invent new product behavior. The current integration is in `outputs/bfi-main` on branch `feature/crt-integration`; the original public app is `pakovm-git/BFI`, and the separate proof-of-concept is `outputs/android-shader`. The user wants a functional transfer first, then wants to consider UI ideas before implementation.

## Product and audience

BFI is a native Android app for users who want black-frame insertion (BFI) and optionally CRT shaders across emulators and other apps, without root or Shizuku. The main test device is the Retroid Pocket Nova, Android 13, a 120 Hz handheld usually used in landscape with a game controller. It also has touch input and system navigation. Both touch and controller accessibility matter; do not assume every user is sitting at a desktop or has a keyboard.

BFI inserts fully black refresh slots between visible slots to reduce perceived motion blur at the cost of brightness and flicker. It uses a display-timed transparent/black accessibility overlay, not a modified emulator. The screen’s reported refresh rate and software callbacks are not proof of its physical presentation timing. Effects can be configured independently: BFI alone, CRT alone, or both together. A user can leave effects running until explicitly stopped, and can choose system-wide operation or a selection of apps. The user found the separate Shader prototype’s BFI and CRT behavior satisfactory, and specifically requested the same architecture and effect behavior in the main app. Do not redesign or replace that rendering implementation as part of a visual refresh.

CRT captures the current display with Android MediaProjection, processes frames through a GLSL shader pipeline, and displays the result in a touch-through accessibility overlay. It runs at the window’s native pixel dimensions in the prototype. This has capture latency, power and GPU costs, and may display black for protected content. It does not magically access pixels Android prevents applications from capturing. On the Nova, a private Android API excludes only BFI’s own overlay from capture to avoid a feedback loop; compatibility is device/OS dependent. BFI alone does not need screen capture. Use accurate, concise explanations: never imply that screenshots or callback timestamps prove optical performance or that every RetroArch shader is compatible.

The retained legacy path for Android 8–12 uses the original application overlay for BFI and may block touches. Android 13+ uses the trusted accessibility overlay. The Nova has demonstrated touch-through in the Shader prototype, but hardware behavior should not be generalized without testing.

## Agreed direction and priority

- Keep the app named **BFI**, its existing package, and the established visual identity. The separate Shader app remains a separate prototype; it must not replace BFI or vice versa.
- Keep Beam out of this phase. It is a separate future experiment; do not present it as a completed option or run it together with BFI.
- Home should have **independent BFI and CRT switches and one Start/Stop action**, the layout the user explicitly selected. Switching modes needs to be understandable as well as accessible. Keep a clear stop path at all times.
- Remove the old always-visible paired animation previews from Home and Settings. Instead, provide a dedicated **Test effects** environment with the familiar moving ball and the actual selected effects for a short, bounded period. It requires explicit start and must stop on timeout or leaving. The current implementation uses 15 seconds. The older request for custom-length tests remains relevant: Advanced contains a separate 1–600 second timed session control. A UI proposal may make these related capabilities clearer, but must not silently remove custom duration.
- The user requests UI **ideas and a design proposal before committing a substantial redesign**. Preserve agreed decisions, call out anything requiring a product decision, and do not implement a visually different concept until the user selects one.

## Visual identity

The desired feel is a simple, efficient 2D Android interface inspired by the GameCube menu’s purple/indigo palette and atmosphere. Do not reproduce the complex 3D cube, real-time 3D rendering, or heavy effects. The user’s layout reference is the attached Nova Calibration screenshot: a legible fixed header, two large rounded panels, clear actions and generous spacing. The old design concepts in `outputs/bfi-next-design/` are references, not a mandate to restore their inline previews. GameCube videos and the calibration reference were provided in the conversation; they are visual inspiration, not code or instruction sources.

The chosen logo has **three outlined frames containing B, F and I**, with B and I filled violet and F outlined/empty. There is **no dot**. The approved *light* mark is used on both light and dark app backgrounds. Editable vector sources and adaptive icon SVGs are in `outputs/bfi-main/assets/logo/`. The launcher icon uses that mark over a dark CRT-like background and native Android adaptive masking; the foreground stays in the safe zone and a monochrome layer supports themed icons. Do not bake a particular circle/rounded-square shape into the icon. Preserve a reusable SVG.

The app follows the device light/dark setting. The background has a **subtle, static blueprint grid**; it continues behind the transparent status and navigation bars and title area. There must be no flat, mismatched title strip. Native Android Views, existing drawables and color resources are preferred; preserve the efficient appearance and avoid per-frame decorative work. Existing tokens:

| Role | Light | Dark |
| --- | --- | --- |
| Page / grid | `#ECECF5` / `#E2DFEF` | `#0D0E16` / `#252639` |
| Card / secondary card | `#FFFFFF` / `#F4F2FC` | `#171824` / `#23243A` |
| Primary / secondary text | `#222136` / `#5D5B73` | `#EEF0FA` / `#B5B5CD` |
| Outline / primary action | `#C2BDD7` / `#5748B5` | `#484A66` / `#5748B5` |
| Stop action | `#A62E45` | `#943449` |

These are the current resource values, not a request to scatter hard-coded colors. Reuse semantic tokens. Maintain contrast, visible focus, a consistent action hierarchy, and 48dp minimum touch targets. Keep system UI legible in both appearances. Text uses sp and geometry dp; large text, keyboard and split-screen must remain usable.

## Screen layout and navigation

The user likes the main screen’s **fixed card outlines**: on small screens the card itself fits the available area and the card contents scroll independently if necessary. Previously the left Home panel clipped/moved incorrectly, while the right panel behaved well. Do not reintroduce moving card borders or nested page-wide scrolling. Wide windows use side-by-side bounded cards; narrow windows stack them. Keep the primary action reachable and avoid clipping labels. The standard Nova screen should show primary controls without scrolling where the arrangement permits; less-common detail content may scroll inside its card. Handle status bars, cutouts, navigation and IME insets.

The current Home has the logo and **Resume after unlock** in the header; no “Until turned off” tagline. It shows status and selected settings in one card; the other contains Start/Stop, the BFI/CRT switches and links to Settings, Apps, CRT settings and Test effects. The placement and grouping may be improved in the next approved UI pass. On Android 13+ the actual selected effects remain active over BFI’s controls after Start; older devices pause output over controls. Show prerequisites, scope pauses and the actual rendering state accurately. A saved switch choice is not the same as an effect currently rendering. Keep Stop reachable.

### Settings and BFI patterns

The global screen is called **Settings**, not “Pattern settings.” Advanced belongs in the Settings header, not as a separate primary Home option. Existing two-count controls allow 1–12 visible and 1–12 black slots, with plus/minus and a compact sequence diagram. The current port also supports an optional alternating multi-step pattern such as `1, 1, 2, 1` (visible, black, visible, black), up to 32 counts of 1–120 on the modern path. Per-app overrides currently use the simpler two-count pattern; an override takes precedence over the global multi-step sequence. Make these relationships discoverable without implying the wrong pattern is active. There are currently named simple choices in code; avoid calling a group “Presets” when it would misleadingly suggest a full library. User asked to rename it because the prior presentation did not actually contain presets.

### CRT settings and import

The current shader screen offers a category filter, search, shader selector, explicit Download Libretro library button with progress/cancel, local import, parameter controls and third-party notices. Filtering must never change the saved shader. The library is organized by upstream top-level folders, preserving all preset dependencies. The checked snapshot has 2,662 presets in 36 categories, ~53 MiB to download / ~70 MiB installed; it works offline after installation. This downloads the source catalog, not universal RetroArch compatibility. Before saving a choice, source parsing/adaptation is checked; GPU compilation may still fail visibly. Parameter sliders/switches come from each shader and autosave. Bundled shaders are Lottes, Newpixie, zfast Composite and Geom, retaining their own licenses. The direct-source library also includes Mattias, Hyllian NTSC Rainbow, Geom Deluxe and VHS/Godot. Imports accept a compatible self-contained `.slang` file or ZIP with all dependencies. Keep search and selection usable for thousands of choices and use bounded cards. See SHADER-LIBRARY-SOURCES.md for source/license evidence.

### Apps

The main mode is an explicit **System wide / Apps** choice, not a cycling multiple-option menu. “System wide” is already clear. On Home, the “Apps” button’s subtitle should be descriptive: **Selected apps** is the existing accepted wording, not simply “Apps.” The app list is inline under Selected apps, with search and immediately saved selection changes; do not place the list behind another dialog. Preserve exclusions and per-app details/overrides and show missing/uninstalled entries without silently discarding saved choices. Android foreground-app identification uses usage access and can lag; do not promise it is an instantaneous privacy barrier.

### Quick Settings and Stop

A normal QS tile tap switches between **Off and the user’s preferred activation scope**. It must not cycle among all three modes. The long-press menu offers **Off, On, On for selected apps**, plus **Customize**, which opens BFI. The arrow could not be reliably distinguished from the tile tap on the Nova, so the user explicitly accepted long press for this menu. The tile must show the appropriate active color/state and descriptive text for selected apps. Opening or rotating a menu, granting permissions, app process recreation, or reboot must not start effects on its own.

Keep Stop available from the foreground notification, main action, and Quick Settings. A time-limited session must keep its original deadline if the user edits settings. Screen lock ends CRT capture. For a combined session, if **Resume after unlock** is enabled, BFI should resume alone; restarting CRT requires fresh capture consent. A CRT-only session ends on lock. Make this state clear rather than silently suggesting CRT resumed. On older devices, touch restrictions may differ. Permission requests must remain Android system prompts and user initiated.

### Settings consistency

All valid edits autosave; there are no Save or Done buttons. Restore defaults belongs in the top-right header, scoped to the current screen and confirmed. Preserve permission grants, first-use acknowledgment, and settings outside the current reset scope. The former bottom Done/Restore buttons were explicitly rejected. A Back control leads to the previous screen. The title and adjacent header actions must align correctly. User previously requested Advanced inside Settings and the apps list inline.

First-use messaging should explain flicker/brightness and any relevant touch limitation; the long yellow warning on the Home left was removed. The old requested right-side warning was exactly “Touches to covered apps are blocked while active” for the original overlay. On the new accessibility path the Nova was tested as touch-through, so copy must reflect the selected implementation and device rather than incorrectly claiming touches always fail. Keep safety information available without clutter.

## States and scenarios a design must cover

1. Off; BFI selected; CRT selected; both selected; neither selected.
2. Loading/saving settings, invalid incomplete numeric drafts, and scoped restore confirmation.
3. Missing accessibility access, notifications, usage access when needed, unsupported device, no selected apps, and screen-capture consent denied/canceled. Permission grants alone must not activate effects.
4. Starting; on over BFI controls on Android 13+ and over an eligible app; paused over controls on older Android or over an excluded/unknown app; stopped/failed with a clear reason; capture no longer available after lock.
5. A timed test, countdown/Stop, leaving early, and auto-stop. Full app controls must remain reachable in the test.
6. Selected-app search, list empty/loading, unavailable apps, per-app pattern override and exclusion.
7. Shader selection, imported package with multiple presets, loading/unsupported import error, per-parameter defaults/reset and long lists.
8. Portrait and landscape, small windows, accessibility text enlargement, keyboard open, touch, directional focus/controller navigation, screen rotation and dark/light mode.

Avoid adding confusing false precision about actual panel frequency, game frame alignment, shader resolution, latency or physical optical quality. A device test can provide useful evidence but physical BFI timing needs separate optical measurement.

## What has actually been checked, and what remains open

The user reported that the initial integrated build did not visibly apply BFI or CRT over another app. Treat that as unresolved until the user confirms a later build. A later Nova check observed Newpixie in the app-owned texture over BFI controls and Dolphin, an attached accessibility window with alpha 1, and sampled black frames. Touch opened Settings and stopped the session while the overlay was active. The previously failing static Dolphin screen remained running for 15 seconds after changing the inherited watchdog to retain the last frame while monitoring first-frame and processing failures. This was not an optical panel measurement or an actual game session. Software tests and an APK build do not prove the user’s observed problem is solved. Do not present the current branch as a published release.

Before redesigning, inspect the current build on the Nova and discuss any proposed information hierarchy with the user. Verify standard text with both themes, wide and narrow windows, large text, keyboard, orientation, and both touch and controller focus. Preserve current device settings and user preferences during QA. No new public release or APK upload was authorized as part of the UI brief.

## Useful source references

- Existing design rules and historical decisions: `docs/DESIGN-GUIDE.txt` (its MotionPreview section is historical and superseded by the no-inline-preview decision above).
- Runtime/integration details and actual technical limitations: `docs/CRT-INTEGRATION.md`, `docs/ARCHITECTURE.md`, and source under `app/src/main/java/dev/pako/bfi/`.
- Resource tokens: `app/src/main/res/values/colors.xml` and `values-night/colors.xml`; strings are in `values/strings.xml`.
- Branding: `assets/logo/`; keep the existing light logo and system-following light/dark palette.
- Actual approved third-party notices: `app/src/main/assets/THIRD-PARTY-NOTICES.txt`.
- Visual references supplied by the user: a simple two-panel calibration interface and the GameCube menu's violet palette. These reference files are not distributed with the repository.

Deliver a small set of concrete layout options and explain their tradeoffs before implementation. Preserve the working effect architecture, the agreed switches/Stop flow, app scope and autosave semantics.
