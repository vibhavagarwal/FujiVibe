# 01: Project scaffold + Viewfinder capture-to-discard loop

**What to build:** Opening the app goes straight into a live camera preview (no setup screens), prompting for camera permission only if it hasn't already been granted. A single shutter button takes a photo, which becomes the app's one in-flight temp Capture (no queue or history — saving, discarding, or loading it is the only vocabulary). The app automatically navigates to a Review screen showing that Capture as the unmodified Original (Original is modeled as the absence of a Film Simulation, not an entry in a simulation list). Tapping Discard deletes the temp Capture, cleans up its temp storage, and returns to the Viewfinder ready for the next shot. Film Simulations, swiping, and Export are out of scope for this ticket — Review only shows Original for now.

**Blocked by:** None (can start immediately)

**Status:** done — implemented (8e7aba8), on-device fixes for rotation/cutout/preview-crop (4a96108, 73be4b1), verified on a real device by the user (2026-09-08)

- [ ] Android project exists targeting minSdk 29, scoped storage only
- [ ] Launching the app shows a live camera preview with no intermediate screens
- [ ] Camera permission is requested only when not already granted; already-granted state skips the prompt
- [ ] A single shutter tap captures a photo and stores it as the app's one temp Capture
- [ ] The app auto-navigates to Review immediately after capture
- [ ] Review displays the Capture as Original with no Film Simulation applied
- [ ] Discard deletes the temp Capture and its temp storage, then returns to the Viewfinder
- [ ] The Viewfinder/capture path has no processing logic running on it or its capture code path
- [ ] Verified manually on a real device (no automated test seam for this path, per the spec's Testing Decisions)
