# 01: Library grid and entry point

**Status:** done on the Pixel 10 Pro, except the allowed-access state, which needs the user to grant the permission.
Blocks: 02, 03.

A screen listing the photos FujiVibe has exported, newest first, as a thumbnail grid, each labelled with its look.

- [x] `LibraryReader` + `MediaStoreLibraryReader` (`Pictures/FujiVibe/`, newest first); row ordering, look-from-name and the
  permission choice are unit-tested on the JVM (`LibraryPhotoTest`)
- [x] Grid screen (`ui/library/LibraryScreen.kt`) with lazy `loadThumbnail` thumbnails and a small look label per photo ("Charcoal",
  etc.; none for older `FujiVibe_*` photos)
- [x] `Screen.Library` in `FujiVibeApp`; back button and system back return to the Viewfinder; the screen is saved across rotation
- [x] Library icon at the right of the Viewfinder top bar, white (it navigates; it isn't a setting that is on or off)
- [x] Empty state ("No photos yet")
- [x] Permission verified on the device (see below); `READ_MEDIA_IMAGES` added, with `READ_EXTERNAL_STORAGE` (max SDK 32)
- [x] `CONTEXT.md` defines **Library** vs **system gallery**
- [ ] User check with access granted: all 13 photos listed, newest first, thumbnails sharp; export a photo to see its look label

## Finding: the permission is needed
Queried the phone's MediaStore on 2026-10-10: 13 photos in `Pictures/FujiVibe/`, but only **3 are owned by `com.fujivibe`**; the
other 10 are owned by `com.google.android.apps.photos` (Google Photos took them over, presumably when backing them up). Android
shows an app only the media it currently owns unless it holds the media permission, so with no permission the Library lists
just those 3 (confirmed on screen). The Library therefore asks for the permission, with a banner (Allow / Open Settings, the same
pattern as the Viewfinder's camera permission) and still shows what it can see in the meantime.

## Notes for the next tickets
- Delete (03) will hit the same ownership limit: photos Google Photos owns can't be deleted directly, so it must use
  `MediaStore.createDeleteRequest` for them, which Android confirms itself.
- Thumbnails are loaded per tile with no cache; fine for a few dozen photos. Add an in-memory cache if the Library grows large.
