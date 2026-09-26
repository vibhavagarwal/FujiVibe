---
status: accepted
---

# Export keeps Review open; Done/Discard ends the Capture

Ticket 04 made Export end the Capture's lifecycle: save to the gallery, delete the temp Capture,
return to the Viewfinder. In use, that made it impossible to save more than one look from the same
shot (e.g. both Classic Neg. (Pixel) and Nostalgic Neg. (Pixel)) without re-taking it. See backlog
item 1.

**Decision:** Export saves the current look and stays on Review. The Capture is deleted only when
the user closes Review (Discard, or Done once anything is saved) or when the next shot overwrites it
(`CaptureStore.fileForNewCapture`, unchanged). The single-in-flight-Capture model is unchanged;
the Capture just lives a little longer.

- Each look can be Exported at most once per Capture. `SavedLooks` tracks what's been saved this
  Review. A saved look shows a checkmark and its Export button is disabled, so there are no duplicate
  gallery files. It is saved-instance state, so it survives rotation along with the swipe position.
- The closing button reads "Discard" until something is saved, then "Done". Both delete the
  Capture and return to the Viewfinder. The label change reflects that nothing is lost by closing.
- Export shows a snackbar: "Saved to Pictures/FujiVibe" on success, or "Export failed - tap Export
  to retry" on failure. The Capture is kept on failure as before (story 20).

**Explicitly deferred**, not decided against:
- No cap on the number of looks exported per Capture beyond "one file per look".
- An in-progress Export is still cancelled if the Activity is recreated mid-Export (e.g. rotation).
  The Capture survives, so the user can Export again.
