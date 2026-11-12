# Verification

Run `./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug`. Unit tests cover scheduling/math, rule resolution, settings serialization and queued changes, controller/session guards, refresh request selection and the local preview clock. These tests do not measure optical display output.

The original development device was a Retroid Pocket Nova on Android 13 with a reported 120 Hz mode. The user observed system BFI output for 1 visible / 1 black, and reported blocked touches beneath the overlay. Native layout, autosave, inline app selection, local preview black frames and theme checks were also performed during development. Other devices, physical display phase and controller input need further verification.

## Manual checks

1. Rotate Home, Settings and Apps while Off, then during a user-started session. Preserve drafts and session intent without crashes or unwanted activation.
2. Check both themes, portrait/landscape, large text and keyboard insets. Card borders stay stationary while overflowing content scrolls.
3. Edit patterns rapidly; reject empty, zero, 13 and overflowing counts without replacing the last valid value. Check valid changes apply to an enabled session after saving.
4. Select apps, use overrides/exclusions, and switch scopes explicitly. Tile taps preserve the preferred scope; long press opens the mode menu without activating output by itself.
5. Check notification Stop, Stop during pending activation/recovery, permission revocation, and lock/unlock with CRT capture ending. The obsolete Resume after unlock control is removed in 1.0. Force-stop/reboot must leave activation Off.
6. Run a timed test and edit the next test duration; the original deadline must hold.
7. Check real overlay output and controller input with a separate game. On Android 13+, check the actual effects and touch input over BFI’s controls too.
8. Measure physical frame presentation and motion clarity with suitable external equipment before claiming display-level timing accuracy.

Device tests can alter preferences. Preserve and restore the user's current settings, themes and display configuration. Do not publish raw device logs or screenshots containing personal information.

## 0.7.0 integration

Run all existing tests plus the ported clock, Slang conversion/import, capture gate, dimensions, pixel packing and settings-migration tests. The debug-only InspectOutputReceiver requires Android DUMP permission and samples only this app's output TextureView; it is absent from release builds. Output is intentionally excluded from normal screenshots while CRT is active, so a normal screenshot alone cannot establish shader operation.

Preserve current device preferences before tests. Check BFI-only, CRT-only, combined, actual GPU output at native dimensions, notification/Home/QS Stop, effects over BFI controls on API33+, scope suspension/resume, a static capture lasting over three seconds, timed test/exit, and lock/unlock BFI-only fallback. Do not run UIAutomator while capturing: it temporarily disconnects accessibility services and invalidates the test. Use known controls/capture coordinates instead. Keep physical/optical claims separate from software timestamps.

## Complete library verification (11 November 2026)

The real immutable Libretro archive was installed with the app's archive code on the host: 2,662 presets, 36 source categories, all eight featured presets parsed/adapted with their complete dependencies. The Nova downloaded and indexed the same full archive through the UI; rechecking showed up-to-date without another archive transfer. Mattias, zfast Composite, Geom, Geom Deluxe, Hyllian NTSC Rainbow and VHS/Godot each reached RUNNING with an attached alpha-1 accessibility window, and their app-owned GPU output was sampled at 1280×960. This is software/render evidence, not optical timing or a game performance benchmark. Unit coverage includes path escape, cancellation, dependency reference resolution, source notices, categories and incomplete-install preservation. Search filtering left settings byte-for-byte unchanged.

The Nova firmware kept the tested window in landscape even after rotation requests; a genuine portrait re-layout was not established in this pass. Search persistence and unchanged saved preferences were checked, but this is not a verified portrait-layout result.

The complete library occupied 85,233 KiB (~83.2 MiB allocated) on the Nova filesystem, compared with ~68.3 MiB of source payload. Archive download files were removed after installation. Original BFI preferences were verified byte-for-byte restored; window-manager rotation ended in its original automatic/free mode.

### Shader library compatibility and automation (2026-11-11)

`Check installed shaders` checks the complete local snapshot using the current
OpenGL ES driver and resolution. It compiles/links actual shader stages, loads
LUTs, allocates framebuffer targets under the existing memory limit and renders
two offscreen frames. Shared malformed parameter sources and failed stages are
cached only for that check. No screen capture, videos or external uploads occur.
The committed compatible index excludes failures; the report remains reopenable.
Manual imports and selection use the same probe, before changing the selection.
Passing is not an optical, performance or visual-correctness guarantee.

The debug-only DUMP-protected InspectOutputReceiver supports `check_library`,
`check_shader` and `state` for unattended checks. These hooks are absent from the
release manifest. State includes library progress. Results are app-private.

On the Nova, UI Automator dumping suppresses accessibility services temporarily,
which stops effects with ACCESSIBILITY and can produce false-negative CRT tests.
Use shell-only state/GPU checks while effects are active; do not run UI Automator
between starting capture and evaluating output. The downloaded Mattias shader
was verified RUNNING with capture and 1280x960 source/output in that workflow.

On this firmware, restoring test DataStore bytes requires temporarily unbinding
only BFI accessibility before force-stop, then restoring the original service list
after restarting. Otherwise the system can immediately rebind the process and
retain the old in-memory preferences. Other accessibility services must be kept.

### Jersey 15 and shader studio
The shader browser owns one recycling scroller and renders cached synthetic still
thumbnails with the existing GPU pipeline. Studio previews use synthetic pixels
and need no capture consent. Check that they animate only while the studio is
open, react to shader parameters, and are not filtered locally a second time when
live CRT already covers the app. Controller X stops locally; Y confirms a reset
of only the current shader parameters or BFI pattern. Test dialog buttons as well
as footer hints. BFI 1,1,2,1 must show five separate tiles V B V V B. Check the
status-bar/header seam and card corner borders in both system themes. These UI
checks do not prove optical BFI timing or every downloaded shader's output.
