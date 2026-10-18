# Recipes into the camera's slots

Status: going ahead (ADR 0014, proposed)

The user shoots on an X-T5 and wants a recipe applied while shooting. FujiVibe holds a recipe set
(15 researched ones, with best-use text) and writes chosen recipes into the camera's seven custom
slots over USB. RAW work stays with X RAW STUDIO on a computer. Phone shooting keeps the existing
Fuji-style looks (the original FujiVibe).

## Flow (target)

1. Camera in USB RAW CONV./BACKUP RESTORE mode, on the cable, phone as USB host (as in
   .scratch/raw-develop/issues/01-usb-develop-spike.md).
2. The app shows the camera's seven slots (names and a summary) and the recipe set.
3. The user picks a recipe and a slot, sees what will be written and what cannot be, and confirms.
4. The app backs up the slots, writes, reads back, and reports.

## Constraints

- Only the camera's own settings can be written; ISO and exposure compensation are shooting advice.
- The slot's encodings are not the same as the RAW recipe block's and are partly unknown (ticket 01).
- Seven slots; the user chooses what to carry.

## Open questions

- The slot encodings on the X-T5 (grain, Color Chrome, white balance, noise reduction, monochrome colour).
- Whether the camera takes a write to every one of these properties in this mode.
- How a recommendation (best seven for a trip or a scene) should work. Later.
