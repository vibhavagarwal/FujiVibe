# 00: Name saved files `<look>_<datetime>.jpg` and read the look back

**Status:** done (unit-tested; installed on the Pixel 10 Pro, not yet seen in a photo manager).

Exports used to be `FujiVibe_<datetime>.jpg`, which says nothing about the look. Now `Charcoal_20231114_221320.jpg`,
`ClassicNegPixel_...`, `NostalgicNegPixel_...`, `Koda64Pixel_...`, `Original_...`.

- [x] `lookFileLabel` / `lookFromFileName` (`com.fujivibe.gallery.LookFileName`), derived from `FilmSimulation.displayName`,
  so a new look gets a label with no extra code; test that labels are unique and round-trip for every look
- [x] `galleryWriteRequest(timestamp, selection)`; `GalleryWriter.save` takes the selection; Review passes it
- [x] Older `FujiVibe_*` names, and any unknown prefix, read back as "no look"
- Note: the timestamp in the name is UTC (as before), so it can differ from the phone's local clock time.
