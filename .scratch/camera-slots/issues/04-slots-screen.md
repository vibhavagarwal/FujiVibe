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

## Comments
