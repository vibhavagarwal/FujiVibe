# 04 - Recipes: stored on the phone, applied by the camera

Status: in-progress (2026-10-18: the recipe store now serves .scratch/camera-slots; the Develop screen items below are parked)
Type: task
Blocked by: 02, 03

Recipes are named sets of camera settings (film simulation plus grain, Color Chrome, tone, white
balance and so on). The user already has a collection of them on the phone; how they are stored is
not yet known, so ask first (see spec.md). Build the store, import, and the swipe order, so the
looks in the Develop screen can be recipes and not only bare film simulations.

## Done so far (branch raw-develop)

- `com.fujivibe.recipe`: the Recipe model, a loader for `app/src/main/assets/recipes.json` (the 15
  researched recipes, with best-use tags and a recommendation text for each), translation of a
  Recipe into the camera's recipe block (`applyTo`, listing what it cannot yet write), and a
  paste parser for "Setting: value" text that reports what it cannot read instead of guessing.
  All unit-tested (19 tests).
- The RAF Spike screen has a button per recipe and a "Paste recipe" button, to try them on a camera.

## Still to do

- Confirm on the camera which fields take effect (ticket 02), then fix `applyTo` where the
  encoding is wrong.
- Noise reduction (a lookup table) and monochromatic colour (position unknown) are not written yet.
- Dynamic range Auto has no known value; the camera keeps what the RAF recorded.
- A stock look inherits the RAF's tone, colour and sharpness settings, because a null setting means
  "as shot". Decide whether stock looks should reset them to the camera's defaults.
- The recipe list and paste screen inside the real app (part of ticket 03), and saving the user's
  own recipes (the loader reads the shipped set only).
- Share-to-app import and reading the camera's custom slots C1 to C7 over USB.

## Comments
