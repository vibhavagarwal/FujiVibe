# 03: Delete a photo from the Library

**Status:** built and installed; unit-tested; **not yet exercised on the device** (needs the user: it is their photo library, and the
confirmation is Android's own dialog). Blocked by: 02 (done).

- [x] Delete (bin) icon in the viewer's top bar, next to the position; fades with the rest of the chrome while zoomed
- [x] Android 11+ (`MediaStore.createDeleteRequest`): Android's own confirmation, and the photo goes to the system **trash** (restorable
  for a while) rather than vanishing. This one path also covers photos another app (Google Photos) now owns, which a plain delete is
  refused for. No second in-app prompt on top. The pending photo id is saved, so a rotation while the dialog is up doesn't lose it
- [x] Android 10 (minSdk 29 has no delete request): in-app "Delete this photo?" confirmation, then a direct delete. **Untested** (no
  Android 10 device here); it can only delete photos the app owns
- [x] After a delete the viewer moves to the next older photo, else the one before it; if it was the last photo, back to the grid
  (`photoAfterDelete`, tested). The list and the screen change together (`onDeleted`), so the viewer is never asked to show a photo
  that is gone
- [x] `LibraryModel.remove` updates the list at once; the next load replaces it, so a delete that didn't stick comes back
- [x] Cancelling Android's dialog leaves everything as it was; a failed delete shows a snackbar ("Couldn't delete this photo")
- [x] Tests: `photoAfterDelete`, `LibraryModel` (183+ total, none failing)
- [ ] **User check on the Pixel 10 Pro:** (1) delete a photo FujiVibe owns, (2) delete one Google Photos owns, (3) cancel the dialog,
  (4) delete the last remaining photo / one in the middle and see where the viewer lands, (5) confirm it appears in the system trash

## Notes
- A throwaway test photo is waiting in the Library: `Charcoal_20261011_022706.jpg` (a black frame exported while testing the file
  naming), the newest photo, so it is the first tile. Safe to use for the delete check.
- The Library's grid look labels and the viewer were already verified on the device (ticket 02); only the delete step itself is open.
- During testing, taps sent over adb landed in another app (`com.vibhavagarwal.tend`, "Reflect") after the user switched apps. They hit
  non-interactive areas and the screen did not change, but from then on the phone was left alone.
