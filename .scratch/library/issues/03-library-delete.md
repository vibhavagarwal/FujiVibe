# 03: Delete a photo from the Library

**Status:** built, installed and unit-tested; deleting a FujiVibe-owned photo confirmed working on the device by the user. The
Google Photos-owned case and a few edge cases are still unchecked (below). Blocked by: 02 (done).

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
- [x] **User check on the Pixel 10 Pro, 2026-10-10:** deleted FujiVibe-owned photos from the viewer; they are gone from MediaStore
  (confirmed over adb: the three newest exports left the list, the 13 older photos remain)
- [ ] **Still unchecked:** (a) deleting a photo Google Photos owns (the case `createDeleteRequest` was chosen for), (b) cancelling Android's
  dialog, (c) where the viewer lands after deleting a middle / the last photo, (d) that deleted photos show up in the system trash

## Notes
- A throwaway test photo is waiting in the Library: `Charcoal_20261011_022706.jpg` (a black frame exported while testing the file
  naming), the newest photo, so it is the first tile. Safe to use for the delete check.
- The Library's grid look labels and the viewer were already verified on the device (ticket 02); only the delete step itself is open.
- During testing, taps sent over adb landed in another app (`com.vibhavagarwal.tend`, "Reflect") after the user switched apps. They hit
  non-interactive areas and the screen did not change, but from then on the phone was left alone.
