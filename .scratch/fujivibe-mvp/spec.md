# FujiVibe MVP Spec

## Problem Statement

I want photos taken on my Android phone to look like they came out of my Fujifilm X-T5, without carrying a real camera or editing photos afterward in a separate app. Existing camera apps either apply no film look at all, or require a multi-step manual edit-and-export workflow after the fact. I want a single minimalist flow on my own device: shoot, glance through the looks my camera actually produces, keep the one I like.

## Solution

FujiVibe is a personal Android app with a Viewfinder screen for shooting a JPEG and a Review screen that lets you swipe between the untouched Original and the Classic Neg. and Nostalgic Neg. Film Simulations before choosing one to Export to the system gallery. No manual editing, no separate app, no configuration beyond swiping.

## User Stories

1. As the app's user, I want to open the app straight into a live camera preview, so that I can shoot without any setup screens getting in the way.
2. As the app's user, I want to be prompted for camera permission only if it hasn't already been granted, so that I'm not nagged every time I open the app.
3. As the app's user, I want a single shutter button on the Viewfinder, so that taking a photo is a single tap.
4. As the app's user, I want the app to automatically move to the Review screen right after I shoot, so that I don't have to navigate there manually.
5. As the app's user, I want Review to open showing the Original (unfiltered) photo first, so that I always see the true shot before any look is applied.
6. As the app's user, I want to swipe left on the photo to move to the next Film Simulation, so that I can compare looks with a single natural gesture.
7. As the app's user, I want to swipe right to move to the previous Film Simulation, so that I can go back without cycling all the way around.
8. As the app's user, I want the current Film Simulation's name shown as a label on screen, so that I always know which look I'm looking at.
9. As the app's user, I want the swipe cycle to wrap around (last Film Simulation → Original → first Film Simulation), so that I can keep swiping in one direction without hitting a dead end.
10. As the app's user, I want the v1 launch Film Simulations to be Classic Neg. and Nostalgic Neg., so that I get looks that actually work correctly on a plain camera JPEG rather than ones that need color-science work the app doesn't do yet (see ADR 0004).
11. As the app's user, I want each Film Simulation's preview to render fast enough to feel instant while swiping, so that comparing looks doesn't feel laggy.
12. As the app's user, I want to tap Export while a specific Film Simulation is showing, so that the photo saved to my gallery has that look baked in.
13. As the app's user, I want to tap Export while Original is showing, so that I can save the shot unfiltered if none of the looks fit.
14. As the app's user, I want the exported photo to appear in my system gallery under Pictures/FujiVibe, so that I can find my FujiVibe shots separately from other photos.
15. As the app's user, I want only the look I actually chose to be saved — not the Original as well — so that I don't end up with duplicate near-identical photos cluttering my gallery.
16. As the app's user, I want to tap Discard on the Review screen, so that I can throw away a bad shot without it ever reaching my gallery.
17. As the app's user, I want Discard and Export to both return me to the Viewfinder ready for the next shot, so that the shoot-review loop feels continuous.
18. As the app's user, I want the next Capture to always start Review at Original again, regardless of what I picked last time, so that I'm not surprised by a leftover look from my previous shot.
19. As the app's user, I want the app to clean up its temporary photo cache after I Export or Discard, so that my phone's storage doesn't slowly fill up with leftover temp files.
20. As the app's user, if an Export fails to save, I want my shot preserved rather than silently lost, so that a transient storage error doesn't cost me the photo.
21. As the app's developer, I want the Film Simulations sourced as `.cube` files matching my X-T5's actual looks, so that the app reflects the camera I own (see Implementation Decisions and ADR 0003 for why this ended up being a third-party pack rather than Fuji's own download).
22. As the app's developer, I want LUT application to run on background coroutines rather than the UI thread, so that swiping through looks never freezes the Review screen.
23. As the app's developer, I want to apply Film Simulation LUTs directly to the JPEG's RGB values with no color-space pre-transform for v1, so that the render pipeline stays simple — which is exactly why the launch set is limited to the two simulations that are documented to work correctly that way (see ADR 0002, superseded, and ADR 0004).
24. As the app's developer, I want LUT processing done in plain CPU code rather than a GPU/OpenGL pipeline, so that the app's threading model stays simple and matches a static-bitmap-per-swipe workload rather than a live video feed (see ADR 0001).

## Implementation Decisions

- **Viewfinder**: live camera preview plus a single shutter control, kept fully isolated from LUT/processing logic — no processing code runs on this screen or its capture path.
- **Capture lifecycle**: the app holds exactly one in-flight temp Capture at a time — save it, discard it, load it full-resolution, or load a downscaled version for preview. There is no multi-capture queue or history.
- **Film Simulation registry**: a fixed, ordered list of the 2 launch simulations, Classic Neg. and Nostalgic Neg. Each maps to a named `.cube` file that the developer supplies locally, sourced from the `abpy/FujifilmCameraProfiles` GitHub pack rather than Fuji's own official download — see ADR 0003 for why. Specifically, the files must come from that pack's `provia conversion luts/` folder (`Provia to Classic Neg sRGB.cube`, `Provia to Nostalgic Neg sRGB.cube`) — the similarly-named files under the pack's `cube lut/` folder are a different, linear-input-only variant and must not be used directly (see ADR 0004). The files themselves are not part of the app's committed source: Fuji's own assets are personal-use-only, and the abpy pack is CC-BY-NC-SA (noncommercial, share-alike), neither of which permits bundling into a distributed app's assets without further work.
- **LUT engine**: parses the `.cube` text format into an in-memory 3D lookup table, then applies it to any bitmap via CPU-side trilinear interpolation. The same application logic is resolution-agnostic — it serves both the downscaled Review preview and the full-resolution Export without a separate code path.
- **"Original" is not a Film Simulation.** Per `CONTEXT.md`, it is modeled as the absence of one — no LUT is loaded or applied when Original is selected, rather than Original being an identity entry in the simulation list.
- **Render pipeline (primary seam)**: a single interface taking (source bitmap, Film Simulation or Original) and returning a rendered bitmap. The rest of the app is built around swapping which simulation is passed in, not around swapping renderer implementations.
- **Review cycling**: an ordered cycle of [Original, Classic Neg., Nostalgic Neg.], tracked as a single index. Swiping left advances the index, right retreats it, and both wrap at the ends. Loading a new Capture always resets the index to Original — nothing about a previous Capture's chosen simulation carries forward.
- **Export orchestration**: renders the full-resolution Capture through the same render pipeline using whichever simulation (or Original) was showing, then writes the result into the system gallery. The temp Capture is only discarded after a confirmed successful save — a failed save must leave the temp Capture in place.
- **Gallery write (secondary seam)**: a separate, thinner interface from the render pipeline, since it crosses into OS-managed scoped storage (`Pictures/FujiVibe/` via MediaStore) rather than pure in-memory computation.
- **Discard**: deletes the temp Capture directly, without ever invoking the gallery write seam.
- **Platform target**: minSdk 29 (Android 10), scoped storage only — no legacy direct-file-path storage path or associated permission handling.
- **Distribution**: personal/sideloaded. No Play Store compliance work, crash reporting, or device-fragmentation testing is factored into this spec.

## Testing Decisions

- Good tests here assert on the render pipeline's *output* — does a given Film Simulation actually change color values as expected, does Original leave the bitmap byte-for-byte unchanged — not on internal steps of the interpolation math being invoked in a particular way. Behavior, not implementation.
- The **primary seam** (render pipeline: source bitmap + Film Simulation-or-Original → rendered bitmap) carries the bulk of automated coverage: `.cube` parsing correctness, correct interpolation at known sample points, Original passing through unmodified, and the Review cycling index/wraparound logic. All of this is runnable on the JVM, no emulator or device required.
- The **secondary seam** (gallery write) gets thin automated coverage: only the shape of what's handed to MediaStore (file name, MIME type, target relative path) is asserted automatically. The actual insert-and-persist round trip against scoped storage is verified manually on a real device — simulated Android storage providers aren't reliable enough to trust for this boundary.
- The Viewfinder/camera capture path has **no automated test seam** — it depends on real camera hardware and is verified by running the app, not by an automated suite.
- This is a greenfield project, so there's no prior art in the codebase to follow — these decisions set the precedent for anything built after v1 rather than matching an existing pattern.

## Out of Scope

- Provia, Velvia, Astia, Classic Chrome, Pro Neg. Std, Pro Neg. Hi, and Reala Ace — deferred past v1, not abandoned. These require either a linear color-space pre-transform or separate verification that direct-apply looks acceptable despite the source pack's own linear-input warning (see ADR 0004).
- Bleach Bypass — available directly-JPEG-safe in the same pack as Classic Neg./Nostalgic Neg., but out of scope per the original grilling decision that it isn't needed.
- Acros / monochrome simulation — stretch goal only, not required for v1; no source has been identified for it at all.
- Film grain overlay — explicitly deferred past v1.
- RAW/.dng capture.
- Any linear- or log-space pre-transform before applying LUTs — v1 sidesteps this entirely by only shipping simulations documented to work directly on gamma-encoded JPEGs (ADR 0004), rather than building a pre-transform (the approach ADR 0002 originally floated and which is now superseded).
- GPU/OpenGL-based processing of any kind (ADR 0001).
- Persisting the last-used Film Simulation across Captures.
- Retaining the unfiltered Original after a photo is Exported.
- Play Store distribution, privacy policy, crash reporting, or analytics.
- Support for pre-scoped-storage Android versions (below API 29).
- Any settings/configuration screen.
- Pinch-to-zoom on the Viewfinder — not part of any of the 24 user stories, and conflicts with the deliberately zero-configuration Viewfinder. Raised during ticket 01 on-device testing (2026-09-08); worth a dedicated post-v1 ticket if framing without it proves limiting in practice, rather than folding into the Viewfinder now.

## Further Notes

- This spec supersedes the film-grain, GPUImage, and "linearize before LUT" language in the original `docs/FujiVibe_PRD.md` — see that file's own "Superseded by grilling session" section, and `docs/adr/0001-cpu-based-lut-processing.md` for why.
- Four ADRs exist and are binding constraints on implementation, not just background — anyone touching the render pipeline or LUT sourcing should re-read them before changing the processing or asset-sourcing approach:
  - ADR 0001: CPU-based LUT processing, no GPU/OpenGL.
  - ADR 0002: **superseded by ADR 0004** — kept for history only; its specific F-Log scenario no longer applies to what's actually shipping.
  - ADR 0003: LUT source is the third-party `abpy/FujifilmCameraProfiles` pack, not Fuji's official download. Carries a live licensing constraint (CC-BY-NC-SA, noncommercial only) that blocks any future decision to distribute this app without re-sourcing the LUTs.
  - ADR 0004: v1 launch set is Classic Neg. and Nostalgic Neg. only, using the pack's `provia conversion luts/` files specifically — the reason the launch set shrank from an originally-planned 9 simulations down to 2.
- `CONTEXT.md` is the source of truth for terminology (Capture, Original, Film Simulation, LUT, Viewfinder, Review, Export, Discard); this spec deliberately reuses those terms rather than inventing new ones.
- No issue tracker is configured for this project yet (`/setup-matt-pocock-skills` hasn't been run), so this spec is saved as a plain markdown file rather than filed as a ticket. It can be migrated into a tracker later once one is set up.
