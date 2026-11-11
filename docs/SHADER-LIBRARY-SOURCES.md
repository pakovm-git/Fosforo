# Shader library sources and distribution notes

Checked 2026-11-11 against the official Libretro repository and the local Shader prototype. This report records source facts and implementation constraints; mixed source notices are not replaced by BFI’s MIT license.

## Exact source snapshot

The official `master` resolved to `2345ee91602a7404d3a1a6a5df6108d7305fdad4`, also the revision already used by the Shader prototype. Resolve a revision first, then download that immutable revision, retaining it in installed-library metadata. Sources: [commit API](https://api.github.com/repos/libretro/slang-shaders/commits/master), [pinned repository](https://github.com/libretro/slang-shaders/tree/2345ee91602a7404d3a1a6a5df6108d7305fdad4).

Official immutable ZIP:

`https://codeload.github.com/libretro/slang-shaders/zip/2345ee91602a7404d3a1a6a5df6108d7305fdad4`

Measured by downloading the ZIP and inspecting every entry with Python’s standard `zipfile` library:

| Measurement | Value |
|---|---:|
| ZIP bytes | 55,064,156 (52.5 MiB) |
| Expanded file bytes | 71,664,790 (68.3 MiB) |
| Files | 5,258 |
| ZIP entries including directories | 5,799 |
| `.slangp` presets | 2,662 |
| Largest file | 3,555,351 bytes |
| ZIP SHA-256 at check time | `353fe21b0a853498494ae0bdfefe6b42cdd64ec22c8969ee84c777607cc95bd3` |

The recursive [Git tree API](https://api.github.com/repos/libretro/slang-shaders/git/trees/2345ee91602a7404d3a1a6a5df6108d7305fdad4?recursive=1) reported `truncated=false`; its file sizes agree with the expanded ZIP. The ZIP has one `slang-shaders-<commit>/` wrapper directory. GitHub-generated archive bytes may change even for unchanged source, so the measured archive hash is evidence for this download, not a universal future server guarantee.

## What Shader currently has

Before this change BFI bundled Lottes, Newpixie and a passthrough choice. The prototype additionally lists Mattias, Hyllian NTSC Rainbow, zfast Composite, Geom, Geom Deluxe and VHS/Godot. The full upstream Slang snapshot contains corresponding presets for every one of these, including `crt/crt-mattias.slangp`; BFI need not ship the separately supplied Mattias GLSL to make Mattias available through the official-source library.

| Preset | Source-notice evidence | Recommended handling |
|---|---|---|
| CRT Lottes | Shader header dedicates it to public domain. | Keep existing bundled version. |
| Newpixie | Pass sources offer MIT or Unlicense; BFI already selects MIT and reconstructs its frame texture from the licensed original. | Keep existing bundled version and notices. |
| zfast CRT Composite | Single shader pass explicitly GPL-2.0-or-later; preset has no extra texture/include dependencies. | Can be bundled with its original source/header, full GPL text and separate attribution. Otherwise available from source download. |
| CRT Geom | Single shader pass explicitly GPL-2.0-or-later; preset has no extra texture/include dependencies. | Same as zfast; do not call the shader MIT. |
| Hyllian NTSC Rainbow | Final CRT pass has MIT notice; the preset also needs NTSC passes, LUT source, two PNGs and stock pass. Supporting files do not all state an independent license. | Expose from official-source download; preserve all files/notices. |
| CRT Geom Deluxe | Main pass explicitly GPL-2.0-or-later; four other passes, includes and three mask textures form the preset and do not all independently state terms. | Expose complete upstream preset from source download. |
| VHS and CRT Godot | Main effect says CC0; separate `vhs_font.slang` credits Astherix/Shadertoy and contains ROM-derived font data without an explicit grant in its header. | Download the full upstream preset; do not treat the whole preset as CC0 solely from its first pass. |
| Mattias CRT | Upstream Slang and supplied GLSL credit Mattias/Shadertoy; inspected source has no explicit redistribution grant. | Expose upstream Slang via user-triggered download; retain local import support. |

Primary source headers and dependencies:

- [Lottes](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/shaders/crt-lottes.slang), [Newpixie](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/shaders/newpixie/newpixie-crt.slang).
- [zfast shader](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/shaders/zfast_crt/zfast_crt_composite.slang), [Geom shader](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/shaders/crt-geom.slang).
- [Hyllian preset](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/crt-hyllian-ntsc-rainbow.slangp), [MIT final pass](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/shaders/hyllian/crt-hyllian-pass1.slang), [supporting NTSC pass](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/shaders/hyllian/support/ntsc/shaders/ntsc-adaptive-lite/ntsc-lite-pass1.slang).
- [Geom Deluxe preset](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/crt-geom-deluxe.slangp), [main shader](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/shaders/geom-deluxe/crt-geom-deluxe.slang).
- [VHS main effect](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/vhs/shaders/vhs_and_crt_godot.slang), [VHS font](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/vhs/shaders/vhs_font.slang), [Mattias Slang](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/shaders/crt-mattias.slang).

GPL is a confirmed license, not evidence of a prohibition on bundling. If bundling zfast and Geom, retain them as separately attributed GPL source assets and supply the complete GPL license. This recommendation does not assert a legal conclusion about combined-work licensing. The conservative alternative remains direct user-triggered source downloads with the original notices retained. Downloading does not grant rights absent from upstream; avoid representing the entire downloaded repository as MIT or cleared for redistribution.

## Automatic organization

Use the first path component as category and retain the remaining preset path as a secondary label. Do not flatten filenames: presets often reference shared shaders, parent directories, includes and lookup textures. Preserve all repository files once in private app storage; enumerate `.slangp` paths as selectable presets. Root `bilinear.slangp` and `nearest.slangp` can use a “General” category. This mirrors the [upstream directory structure](https://github.com/libretro/slang-shaders/tree/2345ee91602a7404d3a1a6a5df6108d7305fdad4).

At this snapshot: Bezel 1,494; Presets 248; HDR 150; CRT 132; Edge smoothing 117; Handheld 78; Downsample 59; ReShade 55; Interpolation 45; Border 43; Misc 38; NTSC 32; Pixel art scaling 23; Blurs/Dithering 17 each; PAL 16; Deinterlacing 11; Anti aliasing/Scanlines 9 each; Motion blur/Stereoscopic 3D 8 each; Denoisers/VHS 7 each; Sharpen/Subframe BFI/Test 6 each; NES raw palette 5; Cel/GPU 3 each; Deblur/Film/General 2 each; Anamorphic/Linear/Motion interpolation/Warp 1 each. Category labels are presentation formatting, not a new source taxonomy.

The existing import path’s 32-package cap and dependency-only extraction are appropriate for individual imports, not for downloading 2,662 presets. Reuse the preset loader and path validation, but store one library tree and build its category index without trying to compile all shaders during installation. Show the source revision and a download/update button; stage installs and replace the active tree only after successful validation, leaving the previous library usable on failure. Bound archive bytes, expanded bytes, entry count and paths; reject traversal, duplicates and corrupt ZIPs. A cancel/failure must remove staging files.

## Compatibility and performance boundary

A repository download is not proof that every preset runs in BFI. Libretro specifies a broad Slang pipeline, normally reflected through glslang and SPIRV-Cross; BFI uses the prototype’s smaller OpenGL ES adapter. Its current limits include 16 passes, parameter/include bounds and supported framebuffer formats. Do not silently simplify an incompatible chain. Keep the selected/previous working effect if loading fails and report the error. The [upstream shader-system specification](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/README.md) explains the richer pipeline contract.

Presets designed for low-resolution emulator input may look different or cost substantially more on a native-resolution screen capture. Heavy bezel, HDR, neural scaling and interpolation chains are not automatically appropriate for the Nova. Preserve the existing BFI timing and renderer architecture; this library feature does not establish hardware performance for every preset.
