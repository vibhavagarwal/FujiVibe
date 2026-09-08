# 04: Export: save chosen look to gallery, with failure-safe cleanup

**What to build:** Tapping Export while any Film Simulation or Original is showing on Review renders the full-resolution Capture through the same render pipeline (ticket 02) using whatever is currently displayed, then writes the result into the system gallery under `Pictures/FujiVibe/` via MediaStore. Only the chosen rendering is saved — never the Original as well when a Film Simulation was picked. The temp Capture and its temp storage are only cleaned up after a confirmed successful save; if the save fails, the temp Capture is preserved rather than lost, and the user is left able to retry rather than silently dropped. Export returns to the Viewfinder ready for the next shot, the same as Discard does.

**Blocked by:** 01, 03

**Status:** implemented (7ae448d), reviewed via mattpocock-skills:code-review with fixes applied for an ignored MediaStore `update()` return value, a swallowed `CancellationException`, a gallery→UI layering inversion, and export-time peak-memory mitigation (all folded into the same commit). Also adds a loading spinner and disabled Export/Discard buttons during export, beyond the ticket's original text, per the known risk that full-resolution CPU LUT interpolation can take 10-40+ seconds. Not yet verified on a real device — the gallery-write seam's actual insert-and-persist round trip needs that per the spec's Testing Decisions.

- [x] Export while a Film Simulation is showing saves that rendered look, full-resolution, to the gallery
- [x] Export while Original is showing saves the unfiltered Capture, full-resolution, to the gallery
- [x] Only the displayed rendering is saved per Export — never an extra copy of Original alongside a chosen simulation
- [x] Saved files land under `Pictures/FujiVibe/` via MediaStore (scoped storage, no legacy file-path writes)
- [x] The temp Capture is deleted only after a confirmed successful save
- [x] A failed save leaves the temp Capture in place instead of losing the shot
- [x] Export returns to the Viewfinder ready for the next shot
- [x] Gallery-write seam has thin automated coverage of the MediaStore request shape (file name, MIME type, relative path); the actual insert-and-persist round trip is verified manually on a real device per the spec's Testing Decisions — automated part done, on-device verification still outstanding
