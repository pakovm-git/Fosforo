# Architecture

The app uses native Android Views and Kotlin through Android Gradle Plugin's built-in Kotlin support. It has one application module, a foreground overlay service, and Preferences DataStore for settings. The package remains `dev.pako.bfi`.

## Session and rendering

The runtime/controller separates enabled intent from whether the overlay is currently running or suspended. Session and render generation checks reject stale commands and callbacks. Stop invalidates pending activation and recovery before removing output. Continuous sessions and timed tests have separate behavior; editing a future test duration cannot extend an already running deadline.

On API33+, `EffectsSession` uses the Shader prototype’s trusted accessibility TextureView and native blackout View with preferred expected-presentation timestamps. CRT captures native-resolution ImageReader frames and excludes only its own output layer using a private API. BfiRuntime/BfiController remain the sole activation owner. The same projection and output are retained across scope pauses; Stop releases them. Lock releases capture, optionally resuming BFI alone. CRT rotation stops for a fresh session. See [integration details](CRT-INTEGRATION.md).

On API26–32, the original `OverlayRenderer` schedules black/uncovered slots using display callbacks and the absolute `SlotClock` phase. `CallbackIntervals` estimates callback cadence for diagnostics. Reported display rate, requested rate, app callback cadence, and physical presentation are distinct quantities. Physical presentation remains unmeasured.

Sleep removes output. Continuous operation can resume after unlock when the saved setting and prerequisites allow it. Process death and reboot start a new process Off. Optional recoverable-error retries are bounded and are cancelled by Stop.

## Settings and app rules

`SettingsCommitQueue` serializes valid settings writes and tracks the latest draft. Runtime changes use committed settings. Invalid numeric drafts preserve the last valid saved value, and activity recreation preserves drafts without activating output.

`AppRuleResolver` handles scope, membership, exclusions and overrides. `ForegroundAppMonitor` uses bounded usage-event polling only when identity-dependent rules require it. Historical usage events and ambiguous multi-window identity can suspend rules; they cannot provide an instantaneous barrier.

## UI

Home and settings use bounded native cards whose contents scroll inside fixed borders. `BlueprintDrawable` supplies the static background through the transparent system bars. Shared resource tokens control light/dark appearance.

Home and Settings no longer show inline animation previews or a local blackout decoration. `TestEffectsActivity` presents a real OpenGL animation with an explicitly started 15-second overlay test; leaving ends it.

The Quick Settings tile reflects enabled intent. Tapping toggles the preferred scope and Off; long press opens explicit scope choices. Merely opening or rotating the menu cannot activate output.
