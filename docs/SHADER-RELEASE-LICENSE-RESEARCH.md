# Shader release licensing check

Checked 12 November 2026 for the Fósforo 1.0 public source/APK release. This is a source/provenance check, not a claim of legal clearance for the entire shader collection.

## Release recommendation

- **Mattias CRT:** omit the supplied `crt-mattias.glsl` from the public source and APK. An explicit redistribution grant for this particular version could not be verified. Keep importing user-provided shader files; do not silently substitute Newpixie while calling it Mattias CRT.
- **VHS and CRT Godot:** the CRT/VHS effect itself can remain as a clearly documented **single-pass, no-OSD variant**. Its source explicitly declares CC0, corroborated by the original author’s publication. Remove the separate `vhs_font.slang` pass and its font data from the distributed source and APK. The original complete two-pass preset is not cleared by the first pass’s license alone.

These recommendations follow the user's instruction to omit assets whose distribution rights cannot be resolved, while retaining the independently licensed part where practical.

## Mattias CRT

The exact local file begins with “CRT Emulation,” attribution to Mattias, and [Shadertoy lsB3DV](https://www.shadertoy.com/view/lsB3DV). It contains no explicit license grant. The corresponding [Libretro GLSL port](https://github.com/libretro/glsl-shaders/blob/master/crt/shaders/crt-mattias.glsl) and [Slang port](https://raw.githubusercontent.com/libretro/slang-shaders/master/crt/shaders/crt-mattias.slang) identify the same source; the inspected Slang source likewise has no grant. Availability in those repositories does not establish a license for this release.

The author also publishes a newer CRT implementation, [Newpixie `crtemu.h`](https://raw.githubusercontent.com/mattiasgustavsson/newpixie/master/source/pixie/crtemu.h), with MIT/Unlicense alternatives. Fósforo’s existing Newpixie files carry their own explicit notices. This is a separate implementation with separate blur/accumulation passes; its license must not simply be copied onto the older Shadertoy-derived file. The [Libretro discussion of the original versus newer implementation](https://github.com/libretro/slang-shaders/issues/134) is useful provenance context, but a reporter quoting the author is not an express grant for Fósforo’s exact file.

**Resolution:** no sufficiently specific first-party grant was found for the bundled old file. Obtaining permission or porting a demonstrably licensed implementation could resolve it later; neither is required for 1.0 because Newpixie is already available.

## VHS and CRT Godot

The [upstream preset](https://raw.githubusercontent.com/libretro/slang-shaders/master/vhs/vhs_and_crt_godot.slangp) runs two independent passes: `vhs_and_crt_godot.slang`, then `vhs_font.slang`.

The [first pass](https://raw.githubusercontent.com/libretro/slang-shaders/master/vhs/shaders/vhs_and_crt_godot.slang) explicitly says it is under CC0. The [original publication by pend00](https://godotshaders.com/shader/vhs-and-crt-monitor-effect/) independently identifies the shader code as CC0; its images/videos are excluded from that grant. [Creative Commons’ CC0 explanation](https://creativecommons.org/publicdomain/zero/1.0/) permits copying, modification and distribution, subject to its stated limits. Preserve the existing attribution/header and link even though this is a permissive dedication.

The [separate font pass](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/vhs/shaders/vhs_font.slang) credits Astherix, [Shadertoy NdGyDK](https://www.shadertoy.com/view/NdGyDK), and hunterk’s Slang adaptation. Its inspected header provides no license grant. It also states that its embedded glyph array came from STV5730 ROM. Neither the shader’s attribution nor the ROM/datasheet reference establishes permission to redistribute that code and glyph data.

### Minimal technical resolution

Create a local variant of the preset with `shaders = 1`, retain only `shader0`, `scale_type0 = viewport`, `scale0 = 1.0`, and `filter_linear0 = true`. Leave the first shader unchanged. Document this preset modification and remove the font source from the public tree/assets, rather than merely disabling that pass while distributing it anyway.

Source inspection shows the retained pass only samples the input `Source` image and has no `#include`, external image, or dependency on the font pass. It retains the scanlines, aperture grille, rolling distortion, noise, aberration, brightness, discoloration, curvature, vignette and associated parameters. The removed pass samples the already processed image and adds the PLAY/REWIND symbols and SP/timecode OSD; removing it removes that text overlay. It does not remove the CRT/VHS simulation. One render pass is also eliminated, but no performance improvement was measured in this research.

This is a technically supported source-based recommendation; compile/GPU/device validation of the modified preset remains the implementing agent's responsibility.

## Exact inspected local inputs

Libretro snapshot referenced by the existing bundle: `2345ee91602a7404d3a1a6a5df6108d7305fdad4`. Hashes were recorded before any release modification:

| Input | SHA-256 |
| --- | --- |
| `app/src/main/assets/crt-mattias.glsl` | `b6de4b3a951662697826e864303e91835c38b645e9d06f093aa97c17a937c083` |
| `shaders/upstream/slang/vhs/shaders/vhs_and_crt_godot.slang` | `90d0948f5e0966a73016ecc7fb60bf82f35137ff207626f960462b537b471b94` |
| `shaders/upstream/slang/vhs/shaders/vhs_font.slang` | `2a4cae46efe3152f9f179809b32f839b98d89ada29abf52032d4b9f9e4febee5` |
| `shaders/upstream/slang/vhs/vhs_and_crt_godot.slangp` | `83c27dceadcf8482038b3b53c6b26ac7f755bf230f8f81b097d31f27c3905b27` |

The last three paths are relative to `app/src/main/assets/`.

## Verification limits and release checks

The Shadertoy shader pages and terms could not be read reliably through the research tool (including HTTP 402 responses). No default Shadertoy license has therefore been assumed. No authors were contacted; searches and third-party copies cannot prove that no grant exists elsewhere. The conclusion for Mattias/font is **unverified permission**, not a claim that redistribution is necessarily forbidden.

Before publishing, inspect both the Git snapshot being pushed and the actual APK/ZIP contents. Hiding a catalog row is insufficient if an excluded shader remains in assets, fixtures, generated archives, or newly introduced commits. Keep existing private/device imports intact; those are distinct from what Fósforo distributes. Update bundle manifests, notices, catalog tests and release notes to describe the one-pass VHS/Godot variant accurately.

## Bounded follow-up: other bundled support files

The release agent requested a quick follow-up because earlier notes also flagged support files in Rainbow, Geom Deluxe and NTSC VHS. This is not an exhaustive legal/provenance audit of those presets.

The pinned local upstream snapshot has no root license file or license/notice files in the relevant parent directories (`crt/`, `crt/shaders/geom-deluxe/`, `crt/shaders/hyllian/support/`, `ntsc/shaders/maister/`, `vhs/` and their inspected parents). Its root README contains the shader-system specification, not a blanket grant. More decisively, the project's own [licensing issue #150](https://github.com/libretro/slang-shaders/issues/150) has a maintainer explaining that the collection has mixed licenses and some sources with none listed. No whole-repository MIT/GPL/public-domain license should be invented.

- **Geom Deluxe:** the [main program](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/crt/shaders/geom-deluxe/crt-geom-deluxe.slang) explicitly declares GPL-2.0-or-later. A program-wide grant can cover its associated support files without a notice in every file; missing headers in `gaussx`, `gaussy`, phosphor passes or their shared parameter include alone do **not** establish that those files are unlicensed. However, this short check did not independently confirm the scope/provenance of every support file and mask texture. Retain the GPL notice and avoid calling every dependency independently cleared.
- **Hyllian Rainbow:** the final CRT pass has its MIT grant, but the inspected NTSC-Adaptive-Lite support, multi-LUT pass and LUT images do not independently declare their terms. No directory-wide grant resolving them was found. This remains a provenance uncertainty, not proof of a prohibition.
- **NTSC VHS:** [the static/distortion pass](https://github.com/libretro/slang-shaders/blob/2345ee91602a7404d3a1a6a5df6108d7305fdad4/vhs/shaders/static.slang) names hunterk and three separately authored Shadertoys (`4dBGzK`, `ldjGzV`, `MlfSWr`), without an explicit license in the inspected file. Thus it has a separate unresolved provenance question from the Godot font. The [original NTSC author publication](https://forums.libretro.com/t/ntsc-shader/305) does identify its older shader as GPLv3, a useful lead; an exact derivation/permission match for every current Slang include and later modification was not established in this check. The older grant must not simply be treated as proof for the whole current VHS chain.

**Documentation consequence:** removing Mattias and the Godot font resolves those specific findings. It does not justify a statement that every remaining support asset has confirmed redistribution terms. If publication policy requires explicit confirmed terms for every bundled dependency, these three complete presets need further provenance resolution or exclusion too; this note does not silently extend the user's decision beyond the two assets they specifically asked to investigate.
