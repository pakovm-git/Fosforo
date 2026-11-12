# Overlay permission, BFI, and capture-free CRT

Research date: 2026-11-11. Target: Retroid Pocket Nova, Android 13. Research and source inspection only; no device or application changes.

## Conclusion

**Draw over other apps does not provide a new way to obtain another app's pixels.** It is useful for placing an output window, but it does not replace screen capture for the current CRT shaders. There is no documented timing improvement from changing the current trusted accessibility overlay to a regular application overlay. The current implementation already avoids capture for BFI-only sessions.

## What the permission actually grants

`SYSTEM_ALERT_WINDOW` authorizes `TYPE_APPLICATION_OVERLAY` windows. Android's permission reference describes creating windows over other applications; it does not grant their framebuffer contents or access to their rendering trees. Accordingly, treating the permission as a compositor-wide shader hook would be an unsupported inference. [Android permission reference](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW)

Regular application overlays are untrusted for touch routing. Android 12+ only passes touches through such windows under specified exceptions, including sufficiently transparent window alpha; the documented maximum obscuring opacity is 0.8. Accessibility overlay windows are trusted for this rule. **Inference:** replacing BFI's trusted window with an opaque application overlay can reintroduce the earlier touch-blocking problem. Reducing window alpha to permit touch would also prevent complete black replacement. This threshold concerns window opacity, not just how many pixels a drawing happens to leave transparent. [WindowManager flags and overlay types](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE)

## Current code inspection

- `app/src/main/AndroidManifest.xml` already declares `SYSTEM_ALERT_WINDOW`.
- `effects/EffectAccessibilityService.kt` uses `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_TOUCHABLE`, and `FLAG_NOT_FOCUSABLE`.
- `effects/NativeBfiView.kt` calculates black slots using `Choreographer.VsyncCallback` and the preferred frame timeline's expected presentation timestamp, then draws black over CRT in that same trusted window.
- `effects/EffectsSession.kt:create()` constructs MediaProjection, ImageReader, and a virtual display only when CRT is enabled and not awaiting restart. BFI alone uses no screen-capture session.
- `ExperimentService.kt` retains the regular application-overlay path.

**Inference:** permission changes do not improve this presentation clock, guarantee optical panel timing, remove CRT sampling cost, or remove capture latency. Actual timing differences would require controlled physical measurements; none were performed for this report.

## Why RuntimeShader does not solve pixel access

`RenderEffect.createRuntimeShaderEffect()` receives the contents of the RenderNode to which it is attached. The AGSL guide demonstrates processing a View and its children. It does not expose unrelated applications behind a transparent View. Consequently, an overlay with a RuntimeShader cannot directly resample an emulator image for curvature, neighboring-pixel bloom, or the other input-dependent operations in the imported CRT shaders. [RenderEffect API](https://developer.android.com/reference/android/graphics/RenderEffect#createRuntimeShaderEffect(android.graphics.RuntimeShader,%20java.lang.String)), [AGSL guide](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl#using_runtimeshader_with_rendereffect)

MediaProjection provides the display images to an application-supplied Surface, including an ImageReader. Saving a recording is optional: frame processing alone does not require a video file or encoder. That matches the inspected CRT path, which consumes images and submits pixels to its renderer. [MediaProjection guide](https://developer.android.com/media/grow/media-projection)

## Narrow alternatives and limitations

- **Capture-free decorative CRT:** fixed transparent scanlines, a shadow-mask pattern, vignette, and black borders can be drawn over another app without reading its pixels. This is an approximation, not execution of the existing image-dependent shaders. It cannot reproduce true curvature or content-aware bloom. This is an engineering deduction from the documented overlay and shader input models above.
- **Cross-window blur:** Android 12 has real backdrop blur APIs, an exception worth acknowledging. They expose blur radius, not an arbitrary programmable shader over the composited backdrop. Availability depends on device support and runtime conditions; no Nova compatibility was checked. [AOSP window-blur documentation](https://source.android.com/docs/core/display/window-blurs)
- **Accessibility screenshots:** Android exposes `takeScreenshot()` with declared screenshot capability, but reports an error for requests made too soon. It is a screenshot API, not a promised display-rate video feed; it still captures pixels. The window-specific variant is API 34, so it is unavailable on the Nova's Android 13. These APIs do not establish a suitable capture-free replacement for the existing CRT stream. [AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot(int,%20java.util.concurrent.Executor,%20android.accessibilityservice.AccessibilityService.TakeScreenshotCallback))

Recommendation: retain the current BFI and CRT paths. Only add a capture-free decorative mode if the user explicitly wants that reduced visual feature set. No evidence here justifies changing the working architecture or requesting another permission.
