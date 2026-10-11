# Verification

Run `./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug`. Unit tests cover scheduling/math, rule resolution, settings serialization and queued changes, controller/session guards, refresh request selection and the local preview clock. These tests do not measure optical display output.

The original development device was a Retroid Pocket Nova on Android 13 with a reported 120 Hz mode. The user observed system BFI output for 1 visible / 1 black, and reported blocked touches beneath the overlay. Native layout, autosave, inline app selection, local preview black frames and theme checks were also performed during development. Other devices, physical display phase and controller input need further verification.

## Manual checks

1. Rotate Home, Settings and Apps while Off, then during a user-started session. Preserve drafts and session intent without crashes or unwanted activation.
2. Check both themes, portrait/landscape, large text and keyboard insets. Card borders stay stationary while overflowing content scrolls.
3. Edit patterns rapidly; reject empty, zero, 13 and overflowing counts without replacing the last valid value. Check valid changes apply to an enabled session after saving.
4. Select apps, use overrides/exclusions, and switch scopes explicitly. Tile taps preserve the preferred scope; long press opens the mode menu without activating output by itself.
5. Check notification Stop, Stop during pending activation/recovery, permission revocation, and lock/unlock with both resume settings. Force-stop/reboot must leave activation Off.
6. Run a timed test and edit the next test duration; the original deadline must hold.
7. Check real overlay output and controller input with a separate game. Confirm the unfiltered local reference remains visible and preview black phases remain black.
8. Measure physical frame presentation and motion clarity with suitable external equipment before claiming display-level timing accuracy.

Device tests can alter preferences. Preserve and restore the user's current settings, themes and display configuration. Do not publish raw device logs or screenshots containing personal information.
