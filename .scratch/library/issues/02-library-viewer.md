# 02: Full-screen viewer with pinch-zoom and swipe

**Status:** built and checked on the Pixel 10 Pro (open, swipe, double-tap zoom, back; Review re-tested after the refactor).
Pinch gestures can't be driven over adb, so the two-finger pinch in the viewer is awaiting the user's check. Blocked by: 01 (done). Blocks: 03.

Tap a photo in the grid to see it full screen.

- [x] Pinch/pan/double-tap handler extracted from `ReviewScreen` into a shared `Modifier.zoomable` / `zoomLayer` with a `ZoomState`
  (`ui/zoom/Zoomable.kt`); Review uses it and behaves as before (swipe between looks, double-tap zoom re-tested on the device)
- [x] `Screen.Viewer(photoId)`: a `HorizontalPager` over the Library list. At fit the pager takes one-finger swipes; zoomed, the zoom
  handler consumes the drag (it pans), so the two can't fight. `onSwipe` is optional in `zoomable` for exactly this reason
- [x] Decodes the saved JPEG with `ImageDecoder` at up to 4096px on the long edge (`decodeSampleSize`), so a ~4000px Export opens at
  full size and zooms to a true 100%; far sharper than Review's ~2000px detail render
- [x] Shows the look name (none for older `FujiVibe_*` photos), "n / total", and the shooting-info line read from the saved file's EXIF
  (`ExifInterface.toShootingInfo`, now shared with `CaptureStore`)
- [x] While zoomed, everything but the back arrow fades away so it doesn't cover the photo
- [x] Back (arrow or system) returns to the grid; the grid state lives above the screens so its scroll position is kept, and it scrolls
  to the photo the viewer ended on if that isn't on screen
- [x] Rotation keeps the same photo: the current id is saved with the screen, and the list is reloaded if the process lost it
- [x] Library tile look label changed to an acronym with no background (CN, NN, K, CH, O), via `FilmSimulation.shortName`
- [ ] User check: pinch in the viewer; swipe feel; whether the chrome fading while zoomed is what they want

## Notes
- Charcoal's acronym is "CH" (the user supplied CN, NN, K, O); a lone "C" sat too close to "CN".
- The viewer holds at most the current and a neighbouring page; each is a ~50MB bitmap for a 12MP photo. Fine on a Pixel 10 Pro; if
  memory ever becomes a problem, lower `MaxDecodeEdge`.
- An unexpected app exit during testing was the user closing it, not a crash (checked in the log: no exception).
