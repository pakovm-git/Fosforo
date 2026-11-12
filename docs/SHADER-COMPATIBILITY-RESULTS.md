# Shader compatibility check — 2026-11-11

Nova, Android 13, OpenGL ES 3.2, 1280×960. Source snapshot:
`2345ee91602a7404d3a1a6a5df6108d7305fdad4`.

The complete installed snapshot was checked on the device: **2,662 presets**.
**258 passed** and **2,404 failed** the current app/device/resolution checks.
The compatible index and full unavailable report remain on the device. Open
Shaders → Unavailable shaders to browse names and inspect individual reasons.
The full source tree remains intact so shared dependencies are preserved.

Checks compile/link actual shader stages, load textures, allocate targets within
the app memory budget and render two frames offscreen. They do not record the
screen or generate videos. Passing does not guarantee visual correctness, motion
quality, sustained performance, all temporal-history modes or other resolutions.

Major reasons included malformed upstream parameter bounds, desktop/Vulkan GLSL
constructs rejected by GLES, unsupported uniform arrays, and unsupported backend
features or resource requirements. These are failures in this app/check, not a
claim that the preset is broken in RetroArch. The app deliberately leaves them
out of selection rather than installing them as apparently working choices.

Build, lint and all **140 unit tests** pass. The APK was installed without clearing
data. Downloaded Mattias reached real RUNNING output with capture at 1280×960.
Favourites persistence, bundled indicators and the automatic failure-report dialog
were verified on-device. Cleanup confirmation/cancel was tested without deleting
the library. Original preference bytes were restored exactly; source files and
compatibility results were retained.
Full portrait/controller and optical timing verification remain manual.
