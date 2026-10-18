# 04 - A screen in the app: slots, recipes, send

Status: needs-testing
Type: task

Reached from the Viewfinder's top bar. Shows connection help (USB host, camera mode, awake and
charged), the seven slots, the recipe set, a paste box, and the confirm step with what cannot be
written. Blocked by 03.

## Built

`ui/camera/CameraRecipesScreen` (icon in the Viewfinder's top bar), with `CameraRecipesModel`,
`camera/SlotLoader` (backup, write, read back, restore; C1 never written), `CameraConnector`,
`SlotPlanStore`, `recipe/RecipeLibrary` (shipped recipes plus pasted ones). Choosing a recipe per
slot C2 to C7, a confirm that names what is replaced, a report per slot, restore of the last backup,
and paste-to-add. Unit-tested without a camera (`SlotLoaderTest`); not yet run on a camera.

## First run on the camera, 2026-10-18

Loaded Pacific Blues into C6 and Newspaper into C7 from the screen. Both loaded and were backed up,
with notes that exposed more camera rules, now handled and tested with a fake camera
(`FujiRawSessionSlotTest`):

- Changing the white balance mode resets the shifts, so after a mode change the shifts (and, in
  kelvin mode, the temperature) are written even when they matched before. Newspaper's B shift -3
  was lost this way.
- The temperature is accepted only in kelvin mode; Color is refused by black-and-white
  simulations; the monochrome colour settings are refused by colour ones. Each is now written only
  where it applies, and otherwise left as the slot has it.
- Image quality (D18F) reads back differently from what the slot held (the camera reports 4, RAW +
  JPEG); it and the size are only kept, so they are not checked.

## Comments
