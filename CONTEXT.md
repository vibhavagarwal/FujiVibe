# FujiVibe

FujiVibe is a personal Android camera app that captures a JPEG photo and lets the user preview and apply authentic Fujifilm Film Simulations before saving the result to the gallery.

## Language

**Capture**:
The full-resolution JPEG produced by a single shutter press, held temporarily until the user Exports or Discards it.
_Avoid_: Photo, Shot, Image

**Original**:
The unmodified Capture, with no Film Simulation applied. Not itself a Film Simulation — it represents the absence of one.
_Avoid_: No filter, Untouched, Raw (Raw specifically means `.dng` sensor data, which this app excludes entirely)

**Film Simulation**:
A named Fuji color look (e.g. Provia, Velvia, Classic Chrome) produced by applying a LUT to the Capture.
_Avoid_: Filter, Preset, Effect

**LUT**:
The 3D lookup table (a `.cube` file) that mathematically implements a single Film Simulation.
_Avoid_: Filter data, Color table

**Viewfinder**:
The live camera preview screen shown before a Capture is taken.
_Avoid_: Camera screen, Live view

**Review**:
The screen where the user inspects a Capture, swiping between Original and the available Film Simulations before deciding to Export or Discard.
_Avoid_: Preview screen, Filter screen

**Export**:
Saving the currently selected rendering of the Capture — a Film Simulation applied, or Original — permanently to the system gallery. Ends the Capture's lifecycle.
_Avoid_: Save, Confirm

**Discard**:
Abandoning a Capture without saving anything and returning to the Viewfinder. Ends the Capture's lifecycle.
_Avoid_: Cancel, Delete
