# 01: Enhancing camera features: manual ISO, shutter speed, selfie camera

**Branch:** `enhancing-camera-features` (worktree `.worktrees/enhancing-camera-features`)

**What to build:** Two more Viewfinder controls beside the zoom readout, ISO and Shutter, sharing one dial with the zoom slider (tap a readout to pick which control the dial drives; the dial hides after a short idle). Each has an **Auto** position at the left end of its dial. With one set and the other on Auto, the other is chosen to keep the photo as bright as the camera's own metering did the moment manual mode began; with both set, exposure is fully manual. The dial offers only values the current camera supports (standard third-stop values within the camera's reported range; shutter capped at 1s so the preview stays usable). Controls are hidden on a camera that doesn't support manual exposure.

A switch-camera button next to the shutter swaps between back and selfie cameras. The chosen camera is kept when returning from Review. Every camera bind resets zoom to 1x and ISO/Shutter to Auto.

**Out of scope (decided 2026-09-27):** aperture (fixed on phone lenses), Temp/Tint white balance, portrait blur (no BOKEH on Pixel 10 Pro), video settings.

**Status:** in progress

- [ ] ISO dial: Auto + supported values; manual choice changes the Capture, not just the preview
- [ ] Shutter dial: Auto + supported values up to 1s
- [ ] One manual, other Auto: the other is derived from the last metered exposure and shown on its readout
- [ ] Both back on Auto returns to the camera's automatic exposure
- [ ] Switch-camera button; selfie choice survives a trip through Review; hidden if there's no front camera
- [ ] Zoom, ISO and Shutter reset on every camera bind
- [ ] Unit tests for the stop lists, readouts and exposure pairing
- [ ] Verified on the Pixel 10 Pro (both cameras)
