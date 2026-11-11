# BFI

Native Android black frame insertion and optional screen-wide CRT processing, with no root or Shizuku. BFI and CRT can run separately or together. This branch integrates the working Shader prototype into the original BFI package; beam is intentionally excluded.

## Current integration (0.7.0)

- Separate BFI/CRT selections and one Start/Stop. Existing scopes, per-app patterns, autosave and Quick Settings Stop remain.
- API33+: trusted accessibility output with touch passing through, native-vsync BFI, native-resolution CRT capture, Lottes, Newpixie, zfast Composite and Geom, editable parameters, local Slang/ZIP imports and an optional complete Libretro source download.
- A dedicated explicitly started 15-second moving-source test replaces ordinary inline previews.
- After lock, capture ends. BFI can resume alone after unlock when selected and enabled by the saved preference; restarting CRT needs fresh screen-capture consent. CRT-only sessions end on lock. CRT rotation ends the session for a fresh capture.
- Android26–32 retain original BFI only, with the existing overlay touch restrictions. CRT requires API33+, GLES3.1 and compatible own-layer capture exclusion. Only Nova/Android13 has device verification; private capture-exclusion API support on other builds is not guaranteed.

Internet access is used only for explicit downloads from GitHub’s official Libretro repository. Screen pixels stay in memory and are neither recorded nor uploaded. Protected/secure content may be black. Full-screen shader processing costs GPU, memory, capture latency and power; it cannot provide emulator-internal timing or access uncapturable content. Reported refresh/vsync statistics are not optical proof of physical panel behavior. BFI flashes and reduces brightness.

Enable BFI’s accessibility service on Android13+, allow notifications, select effects, then Start. CRT additionally opens Android screen-capture consent. Usage access is needed for app rules. On Android 13+ the actual selected effects stay active over BFI’s own controls; app rules apply after leaving the app. On older Android versions, output pauses over the controls to keep them usable. Opening screens, granting access, rotation, process restart and reboot never activate output by themselves. Stop remains available on Home, notification and Quick Settings.

Imports accept compatible self-contained `.slang` files or ZIPs containing `.slangp` and all referenced files. This implements a validated subset of Slang, not every RetroArch backend feature; unsupported shaders fail visibly. Imported shaders remain private and are not bundled or uploaded. Lottes is public domain; Newpixie and its source-derived frame retain their original permissive terms. Bundled zfast Composite and CRT Geom retain GPL-2.0-or-later, with full source and GPL text included; these assets are not relicensed as MIT. See `app/src/main/assets/THIRD-PARTY-NOTICES.txt` and `third_party/newpixie-frame`.

In **CRT settings**, use **Download Libretro library**, then category/search and the shader selector. The complete upstream tree keeps presets, shader stages, includes, LUTs and original notices. Mattias, Hyllian NTSC Rainbow, Geom Deluxe and VHS/Godot are available from that download. Once installed it works offline. The checked snapshot has 2,662 presets in 36 categories, about 53 MiB downloaded and 70 MiB installed. Some presets require RetroArch features the current GLES renderer does not implement; downloading is not a compatibility guarantee. Downloads resolve and retain an immutable source revision and never auto-select a shader or start effects.

Build with the checked-in wrapper and existing Java/Android SDK setup:

```sh
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
# Existing Fedora Silverblue toolbox:
./scripts/verify-in-toolbox.sh
# Separate installation; never clear app data for an update:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app keeps package `dev.pako.bfi`, schema1 preferences and the existing signing identity when built with the same private debug key. Do not distribute or commit signing keys. Debug keys differ between computers; a mismatched signature must not be fixed by silently uninstalling the existing app.

See [integration checkpoint](docs/CRT-INTEGRATION.md), [architecture](docs/ARCHITECTURE.md), [testing](docs/TESTING.md), and [design guide](docs/DESIGN-GUIDE.txt). UI polish is deferred. The published release is not changed automatically by a source push.

Copyright (c) 2026 PakoVM. Original BFI code/assets: [MIT](LICENSE). Third-party code and shaders keep their own licenses and notices.
