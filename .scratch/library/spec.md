# Library Spec: browse, zoom and delete the photos FujiVibe has saved

**Status:** done. All four tickets (00-03) are built and were checked on the Pixel 10 Pro on 2026-10-10 (delete checked by the user,
except the system trash, by choice).
Grew out of backlog section 6, item 1 ("In-app gallery"), and the pinch-zoom work (`.scratch/review-zoom/spec.md`).

## Problem Statement
After Export, a photo can only be looked at by leaving FujiVibe and opening a photo manager. The user wants to look back at
what they saved, with the detail view Review now has, and delete the ones they don't want, without leaving the app.

## Solution
A **Library** screen listing the photos FujiVibe has exported, newest first, as a thumbnail grid, each marked with its look.
Tapping one opens a full-screen viewer with pinch-zoom, double-tap, swipe to the next/previous photo, and Delete. Reached
from an icon in the Viewfinder's top bar; back returns to the Viewfinder.

## Decisions (from the user, 2026-10-10)
- **Name:** "Library" (not "Gallery", which collides with the *system gallery* Export writes into; see `CONTEXT.md`).
- **Entry point:** an icon in the Viewfinder's top bar.
- **Actions:** Delete only. No share.
- **Look label:** carried in the **file name**, `<look>_<datetime>.jpg` (e.g. `Charcoal_20231114_221320.jpg`), rather than in
  EXIF. This also makes the look visible in any other photo manager. **Built (slice 00).** The Library reads the look back from
  the name with `lookFromFileName`; photos saved before the change are named `FujiVibe_<datetime>.jpg` and show no look.

## What the code gives us (facts checked against the repo)
- Exports are written by `MediaStoreGalleryWriter` into `Pictures/FujiVibe/`. MediaStore is therefore already the source of
  truth: **no database, no second copy.** A photo deleted in another app disappears from the Library by itself.
- Navigation is a sealed `Screen` in `FujiVibeApp.kt` (Viewfinder, Review), kept across rotation with a `Saver`. The
  Library adds `Library` and `Viewer` cases.
- `ReviewZoom` (pure scale/offset math) and the one-handler gesture code in `ReviewScreen` can be shared with the viewer.
- The writer lives in `com.fujivibe.gallery`; the reader goes in a new `com.fujivibe.library` package, so the two names
  don't blur.

## Implementation Decisions
- **`LibraryReader`** interface with a MediaStore implementation: query `Pictures/FujiVibe/`, order by `DATE_ADDED`
  descending, return `LibraryPhoto(uri, displayName, look, dateAdded)` with the look from `lookFromFileName`. The mapping
  from rows to `LibraryPhoto` is pure and unit-tested; only the query is Android-specific.
- **Thumbnails** via `ContentResolver.loadThumbnail` (API 29+, matches minSdk), loaded lazily by the grid.
- **Viewer** reuses the pinch-zoom: extract the gesture handler from `ReviewScreen` into a shared modifier, don't copy it.
  A saved JPEG needs no render step, so it decodes the real photo and can zoom to true 100% (Review's is capped at a
  ~2000px render). At fit, swipe moves between photos (a pager); zoomed, a drag pans: the same rule as Review.
- **Delete** (as built) uses `MediaStore.createDeleteRequest` on Android 11+: Android's own confirmation, the photo goes to the
  system trash, and it works for photos Google Photos now owns (a direct delete is refused for those, and 10 of the 13 original
  photos are in that state). That makes the planned in-app confirmation redundant there, so there isn't one; Android 10 alone gets
  an in-app dialog and a direct delete. After a delete the viewer moves to the next older photo, or back to the grid when it was
  the last one.
- **Permission (verified on the Pixel 10 Pro, ticket 01): needed.** An app sees only the media it currently *owns* without
  `READ_MEDIA_IMAGES`, and ownership moves: on the phone, 10 of 13 photos in `Pictures/FujiVibe/` are now owned by Google
  Photos, so without the permission the Library shows 3. It asks for the permission via an in-screen banner.

## Tickets
- `00` Name saved files `<look>_<datetime>.jpg` and read the look back: **done**.
- `01` Library grid and entry point: **done**, checked on the device by the user.
- `02` Full-screen viewer with pinch-zoom and swipe: **done**, checked on the device by the user.
  Grid look labels are now acronyms with no background (`FilmSimulation.shortName`: CN, NN, K, CH; O for Original).
- `03` Delete: **done**, checked on the device by the user (system trash not checked, by choice).

## Out of Scope
Share; editing or re-rendering a saved photo with another look (the Capture is deleted on Done, so there is nothing to
re-render from); albums, favourites, search; showing photos FujiVibe didn't save; video; cloud/backup; renaming existing
`FujiVibe_*` files to carry a look (it can't be known).
