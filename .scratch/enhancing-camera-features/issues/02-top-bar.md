# 02: Top bar: logo, grid, level line, self-timer, aspect ratio

**Branch:** `enhancing-camera-features`

**What was built:** A top bar on the Viewfinder: FujiVibe logo (monochrome launcher mark) and name on the left; grid, level, timer and aspect-ratio toggles on the right. Toggles persist across launches (`ViewfinderSettings`); zoom/ISO/shutter still reset on every bind.

- Grid: rule-of-thirds lines inside the photo frame.
- Level line: follows the gravity sensor, turns gold within 1 degree of level, works upright and sideways, hidden when the phone lies flat.
- Timer: Off / 3s / 10s, big countdown, tap the shutter to cancel.
- Aspect ratio: 4:3 / 3:2 / 1:1 / 16:9. 16:9 uses the sensor's 16:9 stream; 3:2 and 1:1 crop 4:3 through a CameraX ViewPort, which crops the preview and the saved JPEG alike. Black bars mask everything outside the frame.

**Dropped (2026-09-27):** last-photo thumbnail.

**Status:** done; verified on the Pixel 10 Pro (2026-09-27). A 3:2 capture saved at exactly 2720x4080 and matched the framed preview; the level line tracked correctly.
