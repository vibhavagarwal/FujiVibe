# FujiVibe

FujiVibe is a personal Android camera app that captures a JPEG photo and lets the user preview and apply authentic Fujifilm Film Simulations before saving the result to the gallery.

## Language

**Capture**:
The full-resolution JPEG produced by a single shutter press, kept on the Roll until the user is Done with it, Discards it, or it expires. Any number of looks may be Exported from it in between.
_Avoid_: Photo, Shot, Image

**Original**:
The unmodified Capture, with no Film Simulation applied. Not itself a Film Simulation — it represents the absence of one.
_Avoid_: No filter, Untouched, Raw (Raw means `.dng` sensor data, which this app excludes; Fuji's own sensor files are **RAF**, below)

**Film Simulation**:
A named Fuji color look (e.g. Provia, Velvia, Classic Chrome) produced by applying a LUT to the Capture. FujiVibe may also ship its own derived variant of a stock look (e.g. Classic Neg. (Pixel), see ADR 0005) when the stock look needs compensating for how this app's Capture differs from a Fuji camera's JPEG — still produced by applying a single LUT, just not itself a stock Fuji-named look.
_Avoid_: Filter, Preset, Effect

**LUT**:
The 3D lookup table (a `.cube` file) that mathematically implements a single Film Simulation.
_Avoid_: Filter data, Color table

**Roll**:
The Captures still waiting to be developed (see ADR 0012). A shutter press adds to it and the Viewfinder stays up, so the next shot can follow at once; a thumbnail with a count beside the shutter opens it. Holds at most 50 Captures (a full Roll takes no more shots) and drops any older than 30 days. Not the Library, which holds what has been Exported.
_Avoid_: Queue, Inbox, Drafts

**Viewfinder**:
The live camera preview screen shown before a Capture is taken.
_Avoid_: Camera screen, Live view

**Review**:
The screen where the user develops a Capture from the Roll: inspects it, swiping between Original and the available Film Simulations (and pinch-zooming any of them to check detail), Exporting as many looks as wanted, then closing with Done or Discard, or going Back to leave it on the Roll for later.
_Avoid_: Preview screen, Filter screen

**Export**:
Saving the currently selected rendering of the Capture — a Film Simulation applied, or Original — permanently to the system gallery. Does not end the Capture's lifecycle: Review stays open so other looks can be Exported too, and each look can be Exported at most once per Capture (see ADR 0009).
_Avoid_: Save, Confirm

**Library**:
FujiVibe's in-app browser of the photos it has Exported (the files in `Pictures/FujiVibe/`), where one can be opened, zoomed and deleted. Reached from the Viewfinder's top bar. Not the system gallery, which is Android's own photo app and where Export writes to.
_Avoid_: Gallery (collides with the system gallery)

**RAF**:
A Fuji RAW file (`.RAF`) shot on the user's X-T5. The app never decodes it; the camera, connected over USB, develops it into a JPEG (see ADR 0013). A RAF is not a Capture: it comes from the camera, not from the Viewfinder.
_Avoid_: Raw, Negative

**Recipe**:
A named set of camera settings (Film Simulation plus grain, Color Chrome, white balance shift, tone and so on) that the X-T5 applies, either while shooting (from a Slot) or when it develops a RAF. Held by the app. Unlike a Film Simulation, a Recipe is rendered by the camera, not by applying a LUT.
_Avoid_: Preset, Profile, Look

**Slot**:
One of the seven custom settings (C1 to C7) of the X-T5, each holding a full Recipe that the camera applies while shooting. FujiVibe writes Recipes into Slots over USB (see ADR 0014). Not a place on the Roll or in the Library.
_Avoid_: Preset, Bank, Memory

**Discard**:
Abandoning a Capture without saving anything, deleting it from the Roll. Ends the Capture's lifecycle.
_Avoid_: Cancel, Delete

**Done**:
Closing Review after at least one look has been Exported: deletes the Capture from the Roll. Ends the Capture's lifecycle. Takes Discard's place once anything has been saved.
_Avoid_: Finish, Close
