# Approved UI redesign: manual verification

The redesign and shader compatibility changes pass the debug build, lint and
140 unit tests. The debug APK was installed on the Nova without clearing app data.
Downloaded Mattias reached real RUNNING output at 1280×960. Full visual/controller
and orientation checks below remain manual. No release or remote push was made.

From the repository directory:

```bash
./scripts/verify-in-toolbox.sh
```

This builds and checks the source; it does **not** update the installed app.
If successful, install over the existing app without clearing its settings:

```bash
toolbox run --container android-bfi bash -c '
  source "$HOME/Android/bfi-env.sh"
  adb install -r app/build/outputs/apk/debug/app-debug.apk
'
```

Manual checks:

- Follow system theme: light and dark; inspect logo, text contrast, yellow enabled
  states and violet focus. Check blueprint continuity behind the title/system bars.
- Landscape, portrait, larger font and keyboard: stationary card outlines, internal
  scrolling and reachable controls. Home Start/Stop remains visible above its
  scroller. No permanent animated previews.
- BFI editor: try `1 visible / 1 black / 2 visible / 1 black`; add/remove pairs;
  counts 1 and 120; maximum 16 pairs. Empty, zero, 121 and overflowing numbers must
  show errors without changing the live pattern. Rotate with an invalid draft.
- Confirm valid changes apply immediately while enabled. Restore defaults asks
  for confirmation, restores this screen and preserves Resume after unlock.
- Check per-app default/custom pattern editing, exclusions, selection checkboxes,
  search and System wide / Selected apps. Reopen to confirm saved state.
- Shaders: Bundled/Favourites categories and ◆/★ markers, favourites after reopening,
  downloaded-library checks and the Unavailable shaders report, import-failure
  dialogs, cleanup confirmation/cancel, search/category filtering, actual
  parameter controls and defaults, library download/cancel. Start/Stop above the
  selector must work while switching shaders and after an effect failure.
- Cancel screen-capture consent: nothing starts. Stop while a start is pending;
  delayed consent must not restart effects. Check Home and shader-screen Stop.
- Controller: D-pad focus, A selects/toggles/opens lists, B returns; badges are
  circular and appear on controller input, disappear on touch. Try sliders,
  pattern steppers, app checkboxes and Test effects.
- BFI-only, CRT-only and combined effects over the app and another app; touch
  passes through as before. Test effects explicitly starts its timed animation.
- Lock/unlock preserves the agreed behavior: optional BFI resume, fresh consent
  before CRT resumes. Navigation or rotation alone must never start effects.

Report build errors verbatim and screenshots of any layout problem. Screen
captures do not establish physical panel timing or optical BFI quality.

## Reference layout refinement
- Home: status/Start/Stop and Test effects stay reachable without scrolling the
  summary; effect configuration and scope fit the Nova landscape window.
- Changing Home scope autosaves and appears identically in Apps; initialization
  and returning to Home must not overwrite the saved mode.
- BFI: square visible/black count diagram; compact pair editor; invalid drafts
  survive rotation and remain unsaved.
- Apps: scope and list form separate landscape panels; checkbox and chevron keep
  their distinct selection/edit behavior. Narrow windows stack the panels.
- Shaders: Start/Stop precedes selection; parameters use compact name/value rows.
- Settings: grouped existing sections, transparent header actions, visible focus.
- Check enlarged text, controller focus and portrait independently; do not assume
  landscape screenshots demonstrate every window size.

Verified on Nova for this refinement: debug APK installed without clearing data;
Home/BFI/Shaders/Settings reviewed in landscape, light and dark Home reviewed,
Apps navigation opened successfully. Original light theme and free rotation mode
restored after inspection. Build and 140 unit tests pass; lint has no errors
(36 warnings, including nested native layout weights and numeric display format).
A rotation-lock attempt did not produce a portrait window, so portrait, enlarged
text and controller interaction remain manual checks. No optical timing or new
full-library GPU scan was performed for these presentation-only changes.

## Approved VT323 implementation (supersedes the earlier layout refinement)
- Compare against the approved prototype, allowing the requested stronger 2dp
  borders: separate effect switch/navigation rows, content-sized groups, segmented
  scope, Home Resume switch and no permanent previews.
- Verify local VT323 in both themes, static CRT finish and Settings font credit /
  full original license. Header fill must not hide the card border.
- Shader list: select a shader and verify the saved ID after its GPU probe; test
  star separately from shader selection, Favourites, empty/search states and More
  categories. Start/Stop must remain visible when scrolling the library column.
- Library management: scan/report/delete remain available with original busy
  gating, confirmations and reports. Never delete the user's library for a UI test.
- BFI: Add/remove pairs, validate numeric input, and rotate with an invalid draft;
  no invalid value may persist. V/B diagrams and real/nominal statistics must agree.
- Apps checkbox versus edit chevron, controller focus, portrait and enlarged text
  need their own checks. Do not use landscape screenshots as evidence for them.

Verified for the approved VT323 build on Nova:
- Debug build and 140 unit tests pass; lint reports no errors.
- Installed with adb install -r, without clearing data. Native Home, BFI and
  Shaders inspected in light/dark during development; final dark screenshots
  reflect the approved content-sized groups, static finish and stronger borders.
- Visible shader selection was GPU-validated and persisted; favourite star,
  Favourites and its empty state were checked. Original preferences were restored
  byte-for-byte afterward, including the original shader and parameter values.
- Library management exposes Check installed shaders, Unavailable shaders and
  Keep bundled shaders only. No deletion/download/full-library scan was run.
- Settings exposes the VT323 attribution and full original font license.
- D-pad input displays A/B legend. Physical controller mapping, portrait, enlarged
  text and optical timing remain separate manual checks.
- Original device theme restored; no capture session or effect was started by the
  UI checks. Shader/capture/rendering architecture was not modified in this pass.

## Jersey 15 / shader studio revision (2026-11-12)
Supersedes the VT323 typography, combined library/parameter layout and A/B-only
controller legend above. The rendering/capture service architecture is unchanged.

- Jersey 15 is bundled offline; Settings exposes its attribution and original OFL.
- Browser: one recycling list with synthetic still shader thumbnails. No ancestor
  scroller. Opening a row enters a separate studio; Back returns to the browser.
- Studio: actual shader applied to a synthetic animated ball on the left; actual
  shader parameters on the right. Changing a parameter also updates the preview.
  Local preview never requests capture or activates system-wide effects.
- Restore shader defaults is inside the parameter group. Y confirms a reset of
  shader parameters only; on BFI it confirms resetting the pattern only. X stops
  effects. All shortcuts are local to BFI/test Activities, including app-owned
  dialogs; OS permission dialogs retain platform behavior.
- Pattern pairs have an explicit 112x56dp high-contrast Remove control. Diagram
  expands every individual frame (1,1,2,1 gives V B V V B), wrapping within its
  bounded scroller. Fill and outline use the same corner radius.
- Status bar continues the app header's tinted grid and static finish. Card-header
  inner curves preserve the outer 2dp border; focus retains each control's shape.

Run device checks with preferences backed up and restored. Inspect dark/light,
long patterns, search/category empty states, preview return/rotation, high font
scales, touch and physical controller mapping. Shader thumbnail compilation is
on demand; no full-library download/scan/deletion is needed for a UI check.

Verified on the Nova for this revision:
- Debug APK, 140 unit tests and lint pass (no lint errors).
- Native browser hierarchy has no recycling list nested inside another scroller.
- Status/header background pixel check passes; 1,1,2,1 draws three independent
  white frames and two black frames. Rounded card headers inspected on device.
- Add/remove pair persists correctly. Controller-source Y opens the scoped BFI
  reset confirmation, and confirming it preserves every non-pattern preference.
- CRT Lottes studio renders differing animated preview frames with no preview
  error while global effects remain disabled. A real slider changes saved shader
  values; Y reset clears only shader values and preserves all other preferences.
- Controller-source X stops a running BFI-only session inside BFI. These are ADB
  injected gamepad events, not a physical-controller or optical-timing test.
- Original current-turn preferences restored byte-for-byte; library retained.
  No full-library scan, download or deletion was performed in this revision.
- Final APK installed without clearing data. Light and dark status/header pixel
  comparisons pass after Activity recreation. Original dark system mode and all
  original preferences verified afterward; effect architecture unchanged.

## Shader value label stability (2026-11-12)
The native UI regression reproduced maskLight 1.0 widening its label from 39px to
102px when 1.8000001 was displayed. Values now use declaration-aware display
precision and reserve a fixed 40–100dp value column; underlying saved/uniform
values are unchanged. Unit cases retain off-grid defaults (0.031), endpoints
(0.125), fine offsets, small values and negatives. Re-run the native label-width
check at multiple slider positions and restore original preferences afterward.
New landscape-A / portrait-navigation refinements remain in the concept editor;
the shader-label fix does not port those layouts or change the native X shortcut.
Verified: the same maskLight test now renders 1 / 1.8 at 80px each. Debug build,
143 unit tests and lint pass. Updated APK installed without clearing data; original
preferences restored byte-for-byte. No shader-engine or capture change.

## Native composition and live glass follow-up (2026-11-12)
Supersedes the concept-only status above. The approved landscape-A composition,
portrait picker/parameter route, fixed saving slot, Home fades, compact Pattern
row, switch/segment shapes and scrollbar gutters are now in the native APK.

- Debug build, 145 unit tests and lint pass; installed with adb install -r.
- Nova light/dark screens inspected. Live glass shares one native blurred grid
  node per page, with one timestamp for blurred/unblurred drawing and explicit
  invalidation of visible glass owners. System bars share the header treatment.
  No captured pixels, foreground blur or extra dependency is used.
- Automated device frame comparison passes: stationary card, header and exposed
  grid all change over consecutive frames. This guards against the prior cached
  child-display-list freeze. User preferences match the pre-update bytes exactly.
- Shader list drag reduces the category strip from 96px to 30px on a partial
  drag, rather than toggling visibility. List and card gutter inspected.
- Short final Home UI sample: 2727 frames, 1 janky frame (0.04%),
  95th-percentile frame duration 8ms / GPU duration 3ms. This is a
  short UI observation, not a battery or gameplay performance benchmark.
- Original dark theme/free rotation restored. Nova firmware kept this window in
  landscape despite a temporary portrait setting; portrait visual verification
  remains manual. No shader selection/parameter, global effect permission or
  capture architecture change was needed for the live-glass correction.
- Long gameplay, battery usage, physical controller and optical timing were not
  re-tested for this decorative UI change. No release or GitHub push was made.

### Fósforo branding and shader-navigation checks — 2026-11-12

- Built and installed the native update on Nova/Android 13 with the same package.
  Build, 143 unit tests and lint pass. Two tests specific to the removed category
  collapse behavior were removed along with that now-unused helper.
- Reviewed native light/dark Home captures: Fósforo wordmark, violet surfaces and
  phosphor selected controls. Main text/control pairs pass 4.5:1 contrast checks.
  Original system dark mode was restored. Adaptive and monochrome CRT artwork is
  native vector; editable sources are in assets/branding.
- Native list test: no top Shader preview, fixed category bounds across a list
  swipe, no legacy No CRT shader row, selected zfast thumbnail opens the local
  Test effects screen. BFI page has the expanded title and lower still test card.
- Two snapshots of the embedded BFI source 600ms apart have exactly zero changed
  pixels in its viewport. It uses WHEN_DIRTY and a fixed source time, not BFI.
- Existing offscreen GLES ShaderProbe passes Lottes, Newpixie, zfast, Geom and
  locally prepared Geom Deluxe on the Nova. Full Deluxe preset and 11 dependency
  files were copied from the prototype and recorded with hashes.
- Prototype manifest verification passes all 38 upstream files; unresolved bundle
  terms are still an explicit publication decision. No new release/push occurred.
- Original pre-sleep-test settings were restored; subsequent install, navigation,
  selected-thumbnail preview and GPU probes preserved settings byte-for-byte.
- These checks do not establish physical controller feel, portrait rendering,
  optical BFI timing, sustained gameplay cost or successful unlock resumption.
  The earlier sleep test remained inconclusive and is not counted as a pass.

### Complete curated bundle and preview refinements — 2026-11-12

- All nine bundled presets pass the Nova native-resolution offscreen GPU probe:
  Mattias, Hyllian NTSC Rainbow, VHS/Godot, NTSC VHS, Lottes, Newpixie, zfast,
  Geom and Geom Deluxe. Full sources/includes/textures are bundled; preserved
  dependency hashes and notices accompany each addition.
- The downloaded Mattias preset regression was reproduced by the existing archive
  test and fixed by detecting single-stage files by extension rather than preset ID.
- Native NTSC VHS initially failed strict GLES numeric typing. Targeted adapter
  casts and float-only Jzazbz helper literal conversion fix it without changing
  upstream files. Regression tests preserve uint operations and source precision.
- Native layout assertions pass: BFI still demo first and visible, overrides help
  on the right, Favourites first when populated, VHS contains both VHS presets,
  and Bundled appears last. Thumbnail images now sit within their rounded border.
  No settings changed during those native checks.
- Category tests cover empty favourites, available favourites and stale IDs.
  The complete current suite contains 148 passing tests; lint/build pass.

### Final heading/navigation/Scope follow-up — 2026-11-12

- Shared selector setup, larger icon-only Back, Home-only larger Settings,
  phosphor page titles, purple compact card headings, Start/Stop power glyphs,
  and suppression of the redundant Home capture reason are implemented.
- Full build, 148 tests and lint pass after these styling changes. Purple card
  title contrast is 7.72:1 light / 5.93:1 dark against the base card surface.
- Nova disconnected before this final APK could be installed. Those latest styling
  changes are therefore build-verified only until the follow-up installation and
  native visual check are recorded below. The earlier nine-shader and layout
  checks above describe the immediately preceding installed build.

### Follow-up installation completed — 2026-11-12

- Installed the final APK on Nova with `adb install -r`; SHA256
  `9f0e79c49e4e1e7d47049536f84e0b29cdac598595c3dabfc8138992d307b77e`.
- App preferences remained byte-identical before and after installation and
  navigation. Restored the previously authorized accessibility service.
- Native Home screenshot confirms phosphor wordmark, purple card headings and
  the Start effects power icon. UI assertions confirm the full BFI page heading,
  icon-only Back and absence of the generic Settings action outside Home.
- Returned to Home. Did not activate effects or repeat the earlier GPU probes,
  controller, portrait, or sleep/unlock tests during this installation.

### Per-app shader settings and global icon — 2026-11-12

- Build, lint and 151 unit tests pass, including per-app shader round trips,
  inheritance, parameter isolation and malformed/duplicate override rejection.
- Installed version 0.7.2 (16) on Nova. Installation preserved the preferences
  file byte-for-byte. Own app-info screenshot confirms the purple/yellow CRT icon.
  Manifest application/roundIcon point to the new shared ic_fosforo resource;
  components inherit it. Notification/action icons use the monochrome CRT mark.
- Native UI verified: app details offers Shader settings and BFI pattern; the
  contextual browser shows Use global shader. Changing a parameter creates only
  that package's override; choosing Mattias saves it without changing the global
  shader/values. Use global shader removes the override. Temporary test edits
  were reverted; original preference values are preserved (new empty map key).
- Home no longer contains Resume after unlock. Its persisted legacy key and
  internal sleep behavior were intentionally not changed or re-investigated.
- No optical timing, sustained gameplay, every Android permission screen or
  launcher-specific icon cache was re-tested in this pass.
- Restored the previously authorized accessibility connection after installation.
  A launcher/Recents navigation attempt did not produce reliable own-icon evidence;
  it is not counted as an icon-rendering pass. App info remains the verified
  Android-rendered icon check. No launcher storage or settings were cleared.

### First-run permission ordering — 1.0, 2026-11-12

- Disposable application ID used for fresh installs; the main app's settings and
  shader files were not cleared. Old accessibility-first behavior reproduced as
  failure to show the still-missing Stop notification permission after return.
- Fixed build: direct setup without the acknowledgement wall; native checks pass
  for both grant orders, notification denial, cancel/retry, no automatic output
  after grants, BFI blocked with either permission missing, BFI Start and Stop.
- CRT access barrier also checked: no capture consent before required access.
  Combined activation and capture closure use shell-only inspection; reading the
  capture consent dialog with UIAutomator disconnects accessibility and invalidates
  its pending command token, so that harness attempt is not counted as a failure
  of normal app activation. The retry uses already learned dialog coordinates.
- Build/lint and all 154 unit tests pass. No optical panel timing or new-device
  compatibility claim follows from these checks.
- Combined CRT/BFI reached RUNNING after both permissions and explicit capture
  consent; Stop closed capture. Main Fósforo updated to 1.0/versionCode 17 after
  removing only the disposable test app. Main preferences remained byte-identical,
  its app-private file list was unchanged by installation, and original enabled
  accessibility services matched after normalizing abbreviated ComponentNames.

### Public 1.0 final bundle (12 November 2026)

The five-preset public APK passed assembleDebug, lintDebug and all 154 unit tests. All five presets (Lottes, Newpixie, Geom, zfast Composite, one-pass CC0 VHS/Godot) passed native-resolution two-frame offscreen GPU checks on the Nova after installation. APK and source assets were checked to exclude Mattias, Rainbow, Geom Deluxe, NTSC VHS and the Godot font/OSD pass. Manifest hashes matched the actual APK entries. Settings were byte-identical before/after the final update. These probes do not establish sustained game performance, visual equivalence or optical panel timing.
