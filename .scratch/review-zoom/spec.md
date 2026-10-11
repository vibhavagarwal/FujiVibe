# Review Zoom Spec: pinch-zoom on every look

## Problem Statement
On Review, once a look is applied the photo can't be pinched to inspect detail; the user had to Export and open a photo
manager to see it. The Review preview is also only ~510px on its long edge (kept small so swiping between looks is
fast), so simply magnifying it would show blur, not detail.

## Solution
Pinch-zoom, pan and double-tap on Original and every Film Simulation. On first zoom the screen decodes a sharper
(~2000px) version of the Capture and renders the current look through the same pipeline, then swaps it in; the preview
shows meanwhile with a small spinner. Each look's sharp render is cached (3 looks) so zooming back in is instant.

## Implementation Decisions
- `ReviewZoom` (`com.fujivibe.review`): pure, unit-tested scale/offset state. Scale 1..6x, anchored to the fingers,
  offset clamped so the photo can't leave the screen, a sliver of zoom snaps back to fit, double-tap toggles 2.5x/fit.
- One gesture handler on `ReviewScreen` replaces the old horizontal-drag detector, so pinch, pan, double-tap and
  swipe can't fight: at fit, a one-finger swipe changes look; **once zoomed, a drag pans and swiping looks is off**
  (pinch out or double-tap to return to fit). A tap on Export/Done is never counted as a double-tap.
- Zoom is shared across looks and not saved across rotation.
- Detail render size: `loadPreview(1500)`, which lands on the ~2000px sample bracket (~3MP, the size measured at ~3s
  per render on an older device). Full resolution is not used: ~10-40s per look (see render-pipeline-perf note).

## Result (verified on the Pixel 10 Pro, 2026-10-10)
First version went black after any gesture ended: `calculateCentroid()` returns an unspecified (NaN) position on the
finger-lift event, which poisoned the offsets. Fixed in the gesture handler, with `ReviewZoom` also ignoring any non-finite
step. The first version was also slow in principle (single-threaded, ~15 allocations per pixel), so `Cube3DLut.sampleInto`
is now allocation-free, `LutRenderPipeline` and `GrainRenderPipeline` render in parallel bands, and grain runs off the UI
thread. Measured on-device for a 1360x2040 detail render: decode 86 ms, render 217-272 ms, cached re-zoom 0 ms.

## Known limits
- Beyond ~2x on a 1080px-wide screen the detail render is magnified rather than sharp, so it won't match a photo
  manager's 100% view. Fix if that matters: raise `DetailMaxDimension` for a higher-resolution detail render (now affordable
  since rendering is parallel; re-measure on-device first), or render only the visible region. The Library viewer
  (`.scratch/library/spec.md`) shows the saved JPEG and can zoom to true 100%.
- The bottom controls and shooting-info chip stay on screen while zoomed and cover part of the photo's lower edge.

## Out of Scope
Zooming the Viewfinder (already exists as camera zoom); region/tiled rendering; remembering zoom between looks.
