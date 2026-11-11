# Licensed Newpixie frame artifact

The generated `crtframe.png` is decoded directly from Mattias Gustavsson's original licensed `source/crt_frame.h`, pinned to commit **4fe66d98bfbb2d343239eb435cc749589b75a907**:

[Original file and full grant](https://github.com/mattiasgustavsson/crtview/blob/4fe66d98bfbb2d343239eb435cc749589b75a907/source/crt_frame.h), [source commit](https://github.com/mattiasgustavsson/crtview/commit/4fe66d98bfbb2d343239eb435cc749589b75a907).

The source explicitly offers MIT OR Unlicense. Select MIT for the BFI distribution, retain the whole original notice in `LICENSE-Mattias-Gustavsson.txt`, and copy `NOTICES.txt` into the app's third-party notices. The checked original header is preserved unmodified. The converter and its small tests are MIT under `LICENSE-conversion-script.txt`.

## Files to integrate

- Runtime asset: place this generated `crtframe.png` where Newpixie's `frametexture` resolves. No shader source changes are needed.
- Source/provenance: retain `crt_frame.h`, `convert.py`, `test_convert.py`, `source.json`, this README, and the license/notice files in the repository's third-party source or tooling area.
- APK notices: include `LICENSE-Mattias-Gustavsson.txt` and a third-party notice with the attribution/pin/decoding description. Do not package the 1 MB C header in the APK merely to satisfy runtime loading: it is provenance/source material.
- Preserve all four Newpixie shader files' own original dual-license headers and hunterk adaptation credit as well. This frame notice does not substitute for their notices.
- `verification.json` and `comparison.png` are local review evidence, optional for the public repository and unnecessary in the APK.

## Offline regeneration and test

From this directory, with Python 3 and its standard library:

```sh
python3 convert.py
python3 -m unittest -v test_convert.py
```

The script validates the pinned input hash, decodes the original little-endian packed RLE, and stops at 1024 x 1024 pixels exactly like the original C routine. It writes an 8-bit grayscale-plus-alpha PNG with original-source and copyright text chunks. PNG compression can differ across zlib versions; decoded pixels are the stable reproducibility check.

- Original header SHA-256: `cd4cd08fa6c4af1965e312a7a789d87905f029fc0f4690ecf54b56f20ed1fd16`.
- Generated PNG SHA-256: `36222d142248b4ee98f164cf7557c7fc383edacf05b387c89604d46c613780aa`.
- Decoded LA pixels SHA-256: `1440983cd7771b874fe55762b1be6a589ae2785177facb22960cf3a8d6586f1c`.

## Verification on 2026-11-10

Three converter tests passed (PNG integrity/golden pixels/copyright, tampered-source rejection, malformed RLE rejection). The author's unchanged C decoder was also compiled separately; **all 2,097,152 decoded grayscale/alpha bytes exactly match the Python converter**.

Compared to the existing Libretro `crtframe.png` (SHA-256 `c92494c76f60e3fdc33285e5fc95856f943a3e139651ac797aded6884fff4306`): every alpha sample and every nontransparent grayscale sample is identical. There are 82,143 grayscale differences only at alpha zero. Pixel composites over black, white and purple are byte-identical. A side-by-side image was rendered and visually inspected.

The source-derived texture is not claimed to be byte-identical to the Libretro PNG. Transparent RGB differences can marginally affect interpolation near alpha boundaries if a shader samples straight RGBA before blending, so the integrated shader should receive the normal device smoke check. No production repo or installed APK was changed by this artifact task.
