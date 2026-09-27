<p align="center"><img src="docs/brand/fujivibe-logo.svg" width="96" alt="FujiVibe logo"></p>

# FujiVibe

**An Android camera app that gives phone photos a Fujifilm-style film look, tuned for how a Pixel phone actually renders its JPEGs.**

Film-simulation LUTs (color lookup tables) made for Fujifilm cameras look flat or muddy on phone photos. They assume the camera's own neutral rendering as input. A Pixel JPEG has already been through Google's HDR+ processing, which lifts shadows and flattens contrast. FujiVibe keeps the familiar looks but ships its own **"(Pixel)" variants**: each LUT is rebuilt offline with corrections tuned by eye against real Pixel photos. The app itself stays deliberately simple: shoot, swipe between looks, save the ones you like.

![One synthetic landscape rendered through each look](docs/images/looks-comparison.jpg)

<sub>The scene above is computer-generated (see [`docs/images/synthetic_landscape.py`](docs/images/synthetic_landscape.py)), so no personal photos are published. It was rendered with the app's own render engine and LUTs via `./gradlew :render:previewFilmSimulation`. Real photos show the looks' character more subtly than this flat, illustrative scene.</sub>

## Demo

**How it works on the phone**

1. **Viewfinder:** full-screen live camera. Pinch or use the zoom slider; the readout always shows the current zoom (e.g. `1x`, `2.3x`).
2. **Shoot:** one tap captures a JPEG into the app's private cache.
3. **Review:** swipe left and right between *Original*, *Classic Neg. (Pixel)*, *Nostalgic Neg. (Pixel)* and *Koda64 (Pixel)*. Dots show your position.
4. **Export:** saves the current look at full resolution to `Pictures/FujiVibe`. Review stays open, so you can save several looks from one shot. Saved looks get a ✓ and can't be saved twice.
5. **Done / Discard:** ends the shot and returns to the Viewfinder at 1x.

**Try it:** no prebuilt download is published yet. Build a debug APK with the commands below and sideload it onto an Android 10+ phone. It was developed and accepted on a Pixel 10 Pro; other phones should run it, but the "(Pixel)" tuning targets Pixel output.

## What's distinctive

- **Looks corrected for phone input, not just applied.** The source LUTs are Fujifilm conversion tables. FujiVibe bakes a derived LUT per look: the original table's output plus pointwise corrections (tone curve, split-tone, hue and saturation shifts). Because the corrections are pointwise, they compose losslessly into one grid, so the app still does a single LUT lookup per pixel. ([ADR 0005](docs/adr/0005-classic-neg-pixel-derived-variant.md))
- **A recipe-style look built from parts.** *Koda64 (Pixel)* approximates a Kodachrome 64 recipe. It starts from Classic Chrome with most of that LUT's own contrast backed out, then applies warm white balance, deeper shadows, a highlight roll-off, richer color, deeper skies and a partial skin-tone restore, each a named, tunable constant. ([ADR 0010](docs/adr/0010-koda64-pixel-derived-variant.md))
- **Shoot once, choose later.** Looks are compared after capture on the real photo, not guessed in a live preview, and one shot can be exported in several looks. ([ADR 0009](docs/adr/0009-export-keeps-review-open.md))

## Key decisions

| Decision | Why |
|---|---|
| CPU LUT processing, JPEG only, no RAW | Simplest reliable pipeline; avoids GPU and memory complexity for a one-person project ([ADR 0001](docs/adr/0001-cpu-based-lut-processing.md)) |
| Third-party LUT pack instead of Fujifilm's download | Fujifilm's official pack only covers log-video conversions, not stills looks ([ADR 0003](docs/adr/0003-lut-source-third-party-abpy-pack.md)) |
| Ship only "(Pixel)" variants; drop the stock looks | The unmodified looks read as flat on Pixel JPEGs ([ADR 0006](docs/adr/0006-remove-original-classic-neg.md), [ADR 0008](docs/adr/0008-remove-original-nostalgic-neg.md)) |
| Bake corrections offline into one `.cube` | Runtime stays a single lookup; tuning is a constant change plus a re-bake |
| Export keeps Review open | Lets one shot produce several looks without re-shooting ([ADR 0009](docs/adr/0009-export-keeps-review-open.md)) |
| Portrait (background blur) mode dropped | An on-device check showed the Pixel 10 Pro doesn't expose portrait blur (CameraX `BOKEH` extension) to third-party apps; building a custom blur was judged not worth it |

**Structure:** two Gradle modules. `:render` is plain Kotlin/JVM: `.cube` parsing, trilinear LUT sampling, grain, the look registry, and the offline bake and preview tools. It has no Android dependency, so it is unit-tested fast on the JVM. `:app` is the Android UI: Jetpack Compose + CameraX, with the Viewfinder, Review, capture cache and MediaStore export. Domain terms are defined in [`CONTEXT.md`](CONTEXT.md).

## Build and validate

Requires JDK 21 and the Android SDK (set `sdk.dir` in `local.properties`, or `ANDROID_HOME`). The Gradle wrapper downloads Gradle 8.10.2.

```bash
./gradlew :render:test :app:testDebugUnitTest :app:assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Preview any photo through the looks without a phone (writes a side-by-side JPEG):

```bash
./gradlew :render:previewFilmSimulation --args="in.jpg out.jpg ORIGINAL,CLASSIC_NEG_PIXEL,NOSTALGIC_NEG_PIXEL,KODA64_PIXEL"
```

Re-baking a look after changing its constants (e.g. `./gradlew :render:bakeKoda64PixelLut`) needs the source pack cloned into `raw-assets/abpy-fujifilm-camera-profiles/`. It isn't committed; the baked results in `derived-luts/` are.

Unit tests cover LUT parsing and sampling, the render pipeline and grain, each bake's corrections, the swipe order, saved-look tracking, zoom math and gallery-write requests. Camera and UI behavior is accepted by hand on a real phone.

## Limitations

- **Slow full-resolution export.** Processing is CPU-only at roughly 1 µs per pixel, so a full-size export can take tens of seconds (a spinner covers it).
- **Tuned by eye on few photos.** The "(Pixel)" corrections are hand-tuned, not colorimetric. HDR+ is adaptive per scene, so no exact inverse exists. *Koda64 (Pixel)* has so far been judged on non-Pixel photos; low-light scenes render dark.
- **Exported JPEGs have no EXIF metadata** (capture date, camera settings).
- **Back camera only; no flash, timer or portrait mode.** Default Material styling.
- **Not published.** Debug build only; tested on one phone (Pixel 10 Pro, Android 10+ required).

## Roadmap

- Judge and retune *Koda64 (Pixel)* on real Pixel photos.
- Faster export (parallel rendering, or GPU).
- Keep capture metadata (EXIF) on export.
- Visual polish beyond default Material.
- A signed release APK for easy sideloading.

## Privacy

FujiVibe is **local-only**. It requests only the camera permission and has **no internet permission**: no accounts, analytics, ads or network calls. A captured photo is held in the app's private cache until you tap Done or Discard. Exports are written only to your phone's `Pictures/FujiVibe` folder. No location is requested or recorded.

## License and attributions

- **Code:** [MIT](LICENSE), covering source, build scripts and docs.
- **LUTs** (`derived-luts/*.cube`): adaptations of [abpy/FujifilmCameraProfiles](https://github.com/abpy/FujifilmCameraProfiles) by abpy, licensed **[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)**, and released under the same license. Details and the list of changes are in [`derived-luts/LICENSE.md`](derived-luts/LICENSE.md). Because the app bundles these files, distributed builds carry the non-commercial, share-alike terms for them. Photos you process are your own.
- **Trademarks:** FujiVibe is an independent personal project, not affiliated with or endorsed by Fujifilm, Kodak or Google. "Fujifilm", "Classic Neg.", "Nostalgic Neg." and "Classic Chrome" are trademarks of FUJIFILM Corporation; "Kodachrome" is a trademark of Eastman Kodak Company; "Pixel" is a trademark of Google LLC. The names are used only to describe the looks the app approximates.

## How this project was built

I led this project as its product owner: I set the product direction and scope, made the product and trade-off decisions (what to ship, what to cut, how each look should feel), judged every look by eye on real photos, and accepted each change on my own phone. AI coding agents (Claude Code) did most of the implementation, working to my direction.

Practices that kept the work reliable:

- **Durable project docs.** A product spec ([`docs/FujiVibe_PRD.md`](docs/FujiVibe_PRD.md)), a domain glossary ([`CONTEXT.md`](CONTEXT.md)) and ten architecture decision records ([`docs/adr/`](docs/adr/)) record *why*, so each session starts from written decisions instead of memory.
- **Bounded tasks.** Work was split into small specs and tickets (under `.scratch/`), each with a clear finish line, plus a backlog recording raised issues and their resolution.
- **Validation before acceptance.** Every change passed the unit-test suite and a build; looks were checked on rendered side-by-sides before being baked.
- **Manual acceptance on a device.** UI changes were installed and checked on a Pixel 10 Pro, and my feedback drove fixes. Some ideas were dropped after a real-device check, such as portrait mode.
- **Readable Git history.** Small, descriptive commits trace each decision and fix.
