# FujiVibe post-v1 backlog

Ideas raised after v1 (tickets 01-04) was working end-to-end and verified on-device. None of these are `ready-for-agent` tickets yet — each needs a scoping/decision pass before implementation, unlike the numbered issues in `issues/`. Captured 2026-09-08, after ticket 04 (Export) was merged and verified.

## 1. Export shouldn't end the Review session

**Status: DONE (2026-09-26, branch `app-improvements`).** Export now keeps Review open; Done/Discard ends the Capture. Duplicate Export of a look is blocked. See ADR 0009.

**What was asked:** after Export, the app currently jumps back to the Viewfinder (per spec/ticket 04's explicit design). The user wants to instead stay on Review and keep swiping, so they can export more than one look from the same Capture (e.g. save both Classic Neg. and Nostalgic Neg. from one shot).

**Why this isn't a small tweak:** the current spec deliberately makes Export end the Capture's lifecycle — see `CONTEXT.md`'s Export definition ("Ends the Capture's lifecycle"), story 19 ("clean up temp cache after Export or Discard"), and the "exactly one in-flight temp Capture, no queue/history" model in spec.md's Implementation Decisions. Ticket 04 was built and reviewed directly against "Export returns to the Viewfinder ready for the next shot, the same as Discard does" as an acceptance criterion.

**What would actually need to change:**
- Export can no longer delete the temp Capture immediately on success — it'd need to stay alive until the user explicitly signals they're done with it.
- A new, separate "I'm done with this shot" action is needed, since "return to Viewfinder" can no longer be an automatic side effect of Export.
- Multiple exports per Capture becomes a real case to design for — does re-exporting the same look twice save a duplicate file? Any cap?

**Recommendation:** treat as its own small ticket/spec amendment (a domain-modeling/grilling pass on the Capture lifecycle) rather than folding into an existing ticket, since it changes a contract other tickets already built against.

## 2. General UX rough spots

Rough-prioritized, from building/testing tickets 01-04:

- **DONE (2026-09-26): snackbar on Export success/failure.** **Export gives no confirmation.** Silently returns to Viewfinder on success; on failure there's currently no UI signal at all that anything went wrong (story 20 wants the shot preserved on failure, but the user isn't told a failure happened — the Capture is just quietly still there with no explanation).
- **DONE (2026-09-26): position dots under the label.** **Swipe position isn't visible.** Only the text label ("Original" / "Classic Neg." / "Nostalgic Neg.") shows where you are in the cycle — a simple dot indicator (e.g. `●○○`) would make the 3-entry cycle easier to read at a glance.
- **DONE (2026-09-26): Grant permission / Open Settings buttons, re-checked on resume.** **Camera-permission-denied is a dead end.** If the user permanently denies camera permission, `ViewfinderScreen` just shows static text with no retry or "open Settings" affordance.
- **Full-resolution render latency.** CPU trilinear LUT interpolation is roughly ~1µs/pixel (see the `fujivibe-render-pipeline-perf` note from ticket 03/04) — a full-res Export can take 10-40+ seconds. Ticket 04 added a loading spinner to cover this, but it's worth deciding whether that wait is acceptable long-term or worth pipeline-parallelization work.
- **Visual polish is essentially default Material.** Plain text labels, stock buttons, no real branding/hierarchy — Export and Discard currently look equally weighted despite Export being the primary/happy-path action.

No decisions made on any of these yet — this file just records the discussion so it doesn't need to be re-derived.

## 3. BUG: rotating the phone during Review discards the Capture

**Status: DONE (2026-09-26).** Screen and swipe position are `rememberSaveable`; if Review is restored with no cached Capture, the app falls back to the Viewfinder. Orientation is not locked.

**Reported 2026-09-26.** After taking a photo, if the phone's orientation changes (e.g. accidentally rotated), the Capture is lost and the app drops back to the Viewfinder. Annoying because the shot is unrecoverable.

**Likely cause (not yet confirmed):** a rotation recreates the Activity, and the app keeps its Review/Capture state in memory that doesn't survive that. `app/src/main` has no `ViewModel`, no `rememberSaveable`, and no `configChanges`/`screenOrientation` in the manifest, which fits.

**Fix directions to decide between:** lock the orientation; handle `configChanges` so the Activity isn't recreated; or hold the in-flight Capture in a `ViewModel`, or persist its temp file path in saved state, so it survives recreation. The last is the most robust. It also protects against process death, which matters since the Capture lives in a temp file.

Should be prioritized ahead of the UX polish items above, since it loses user data.

## 4. Follow-ups from first on-device test of app-improvements

Raised 2026-09-26 after testing the merged build on the Pixel 10 Pro. Not started; no development yet.

1. **Done button isn't centered** on the Review screen.
2. **Zoom doesn't reset after Done.** Returning to the Viewfinder keeps the last zoom level; it should revert to 1x.
3. **Show "1x" at 1x.** The zoom readout is currently hidden when unzoomed; it should display 1x.
4. **Zoom slider.** A slider-style control while zooming in (in addition to, or instead of, the bare readout).
5. **Rename Koda64Pixel to "Koda64 (Pixel)"** (display name), matching the "Classic Neg. (Pixel)" / "Nostalgic Neg. (Pixel)" style. The enum, class and file names (`KODA64_PIXEL`, `Koda64PixelCorrection`, `Koda64Pixel sRGB.cube`) may stay or change with it; decide when picking this up.
6. **Review Koda64 on more real shots.** The user has some photos taken on the phone to share, to judge the look together and retune if needed (so far tuned only against 5 non-Pixel photos).

## 5. Portrait mode (background blur) when taking a picture

Raised 2026-09-26: wants a Pixel-style Portrait option on the Viewfinder. Not started; needs a feasibility check first.

**Option A (preferred if available): CameraX Extensions, `ExtensionMode.BOKEH`.** Uses the phone's own portrait processing; the Capture comes back as a normal JPEG with the blur baked in, so Review and all Film Simulations (including Koda64) work unchanged. Small amount of code: a Portrait toggle plus binding with an extension-enabled camera selector. Extensions can limit zoom behavior and shooting speed in that mode.

**Availability is unconfirmed for the Pixel 10 Pro.** Docs research (2026-09-26): Google's supported-devices page (developer.android.com/training/camera/supported-devices, updated 2026-09-16) lists Pixel 6 through 9 plus Fold/Tablet but *no Pixel 10 or 10 Pro*, and doesn't say which extension modes each Pixel supports. The only Pixel-specific evidence found (a 2022 Open Camera write-up) shows Night Sight via extensions on Pixel 6, with Bokeh only on Samsung. Bokeh on Pixel looks unlikely but is not proven either way.

**Step 1: on-device check.** Add a throwaway diagnostic that asks `ExtensionsManager.isExtensionAvailable(...)` for `BOKEH` (and the other modes) on the back camera, run it on the Pixel 10 Pro, then remove it. This gives a definitive answer in a few lines of code.

**Decision (2026-09-26): no fallback.** Building our own segmentation-based blur is explicitly ruled out as not worth the effort or need (it would mean a new model dependency, weaker edge quality than Google's, and added full-resolution Export time). If the on-device check shows the Pixel 10 Pro doesn't offer `BOKEH`, portrait mode is simply dropped, not re-approached another way.

### Status of section 4 and item 5 (2026-09-26, second session)

- Item 1: **DONE.** The equal-width button pair was the wrong reading (and wrapped "Discard"), so it was reverted. The real problem: once the shown look was saved, Export became a near-invisible disabled "Saved" button, leaving Done visually off-center. Export is now hidden for a saved look, so Done stands alone, centered; swiping to an unsaved look brings Export back. Built and installed; awaiting the user's on-device check.
- Items 2-4: **DONE, verified on the Pixel 10 Pro.** Zoom resets to 1x whenever the Viewfinder binds; the readout always shows ("1x"); log-scale zoom slider appears on pinch or readout tap and hides after 2.5s.
- Item 5: **DONE.** Display name is "Koda64 (Pixel)"; the LUT file is renamed to `Koda64 Pixel sRGB.cube` like its siblings (bytes unchanged). Code identifiers (`KODA64_PIXEL`, `Koda64PixelCorrection`, `BakeKoda64PixelLut`) kept, since they already follow the Classic Neg. naming pattern.
- Item 6 (review Koda64 on real Pixel photos): **not started.**
- Section 5 (portrait mode): **DROPPED.** The on-device check reported `BOKEH=no HDR=no NIGHT=yes FACE_RETOUCH=no AUTO=no` on the Pixel 10 Pro's back camera. Per the no-fallback decision, portrait mode is dropped; the diagnostic and the camera-extensions dependency were removed.

## 6. Future phase ideas (raised 2026-09-27; nothing to be done yet)

Recorded only. No design, feasibility check or development until the user picks them up.

1. **In-app gallery.** Browse photos taken with FujiVibe inside the app.
2. **Automatic shot-type detection.** Tell whether a photo is a close-up portrait or a landscape shot.
3. **Automatic crop to the main face.** Crop the photo around the main face in the frame. The user will explain the intended behavior later; don't design it before then.
