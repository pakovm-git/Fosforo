# BFI 0.7.0 integration checkpoint

The app remains `dev.pako.bfi` and updates the existing installation without clearing preferences. This checkout is `outputs/bfi-main`; `outputs/android-bfi` and the private `outputs/android-shader` prototype are preserved. The upstream starting commit is `9e77e4a`.

## Accepted scope

- Separate BFI / CRT switches, one Start/Stop, existing app scope, settings autosave and notification/tile Stop.
- Real native-resolution CRT and native-vsync BFI from Shader 0.5.0. Beam is excluded.
- Remove normal Home/Settings animations and the in-app decorative BFI mask. Add a separate moving-source Test effects screen, explicitly started, 15 seconds maximum and stopped on leaving.
- CRT parameters and local Slang/ZIP imports. Bundle Lottes and Newpixie plus the explicitly licensed zfast Composite and Geom sources with their original notices and GPL text. An explicit complete Libretro source download exposes the remaining Shader prototype presets and upstream folder categories; local imports remain available.
- After lock: release capture. If BFI was selected and Resume after unlock is on, resume BFI alone; CRT needs new consent. CRT-only ends. Do not save a changed CRT preference on lock.
- Further UI polish is deferred at the user's request.

## Architecture retained

BfiRuntime/BfiController remain the enabled-intent owner, including epochs, deadlines, scopes and Stop. ExperimentService is still the foreground resource adapter. On API33+ EffectsSession joins the proven Shader pipeline to this controller: trusted accessibility FrameLayout, persistent TextureView/EGL output, separate native blackout View driven by preferred expected-presentation timestamps, ImageReader CPU upload at native dimensions, bounded one-frame gate, multipass GLES3.1 compiler. No root, Shizuku, input injection or accessibility tree inspection.

CaptureExclusion uses HiddenApiBypass 6.1 and a private SurfaceControl transaction only on this app's own output root. This unsupported private API is essential to avoiding recursive capture with the existing architecture on Nova/API33; failure must stop safely. Other devices/OS versions are unverified. Internet permission is used for explicit HTTPS source downloads only. Protected content remains uncapturable. Captures are not recorded or uploaded.

On API33+ the real output stays visible over BFI controls, using the global pattern; app rules apply when leaving BFI. While scope suspends output, the trusted root is hidden, its vsync work pauses and the existing virtual display surface is detached. Resume reuses the same projection/virtual display rather than consuming a token twice. Stop closes projection, ImageReader, EGL, window and clock. Rotation with CRT ends the session rather than reusing stale dimensions. Original SurfaceView BFI remains on API26–32 and pauses over controls because it can block touch. Multi-step BFI is a new API33+ path; legacy two-count app overrides retain their prior meaning.

## Files to start with

- `effects/EffectsSession.kt`, `effects/EffectAccessibilityService.kt`: Android resources and lifecycle.
- `effects/NativeBfiView.kt`, `PresentationClock.kt`, `OverlayTextureView.kt`: working display timing and window composition. Keep this architecture.
- `effects/ShaderPipeline.kt`, `ShaderSource.kt`, `ShaderCatalog.kt`, `ShaderImports.kt`: validated Slang subset, parameter controls, dependency closures, native resolution/GPU budget.
- `state/BfiRuntime.kt`, `ExperimentService.kt`: app scope, capture readiness, lock fallback and service cleanup.
- `ui/TestEffectsActivity.kt`: independent real OpenGL source, explicit timed effects.
- `ui/ShaderActivity.kt`: basic bounded settings UI, parameters and imports.
- `third_party/newpixie-frame`: pinned licensed original image source and standard-library generator; not an APK dependency.
- `app/src/debug/.../InspectOutputReceiver.kt`: DUMP-permission shell-only output/cadence capture. Absent from release builds.

## Persistence

Existing schema1 keys remain unchanged. Added effect selection keys are `useBfi` (true by default), `useCrt` (false), shaderId, parameter map, optional alternating multiStepPattern. They are preferences, never session enabled intent. Old stores therefore keep BFI and do not begin screen capture. Invalid inputs fail validation. Imported files remain app-private and outside Git.

## Next UI pass

Keep the logo, blueprint under system bars, system theme and bounded-card rules. Polish wording/layout only after device verification. In particular, distinguish two-count patterns from a nonempty multi-step override; indicate BFI-only fallback after unlock; make capture/setup prerequisites obvious. Do not add inline previews or beam.

## Observed issue on 11 November

Over a static Dolphin interface, Android stopped delivering new capture frames and the copied Shader watchdog stopped the session after three seconds. The unchanged Shader 0.5.0 prototype also stopped on the same screen. The BFI integration now retains the last frame when the source is static; it still stops if the first frame never arrives or an accepted frame remains unprocessed. Rendering and BFI timing remain unchanged.

## Complete source library

`ShaderLibrary` resolves official Libretro master to a commit, downloads the immutable GitHub archive, and activates a validated `ShaderLibraryArchive` snapshot atomically. Limits: 128 MiB compressed, 256 MiB expanded, 32 MiB per file, 50,000 entries; checked paths, duplicate rejection, CRC checks and cancellation. Complete source files and notices are retained; nothing is executed. Incomplete downloads never replace the active snapshot. Snapshots are immutable and previous revisions are retained so active shader sessions cannot lose their dependencies. Re-downloading the installed revision skips the archive transfer. Updates are explicit, not scheduled. The current archive is ~53 MiB compressed / ~70 MiB installed.

Choices are indexed from `.slangp` paths into upstream top-level categories. Stable path-based IDs and featured names preserve selection. Search/category filtering never saves a new selection. Selected sources have bounded expansion and parser/adaptation checks before saving; final GPU compilation may still reject unsupported features. The existing 128 MiB shader-target/lookup allocation budget remains. See SHADER-LIBRARY-SOURCES.md for source and license evidence.

## Activation review before publication

Pending settings saves and screen-capture consent carry a runtime command token; Stop or another activation command invalidates stale requests. Changing CRT during a timed session retains the original absolute deadline across capture consent; expired requests cannot become continuous sessions. Advanced custom-duration CRT tests request consent directly and retain the chosen duration. The native blackout follows the saved BFI selection even in post-lock BFI-only fallback; disabling BFI there ends the fallback until CRT is started again with fresh consent.
