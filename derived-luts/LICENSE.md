# License for the LUT files in this folder

The `.cube` files in this folder are licensed under
[Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)](https://creativecommons.org/licenses/by-nc-sa/4.0/).
The MIT license covering the rest of this repository does not apply to them.

## Attribution

They are adaptations of LUTs from
[abpy/FujifilmCameraProfiles](https://github.com/abpy/FujifilmCameraProfiles) by abpy,
licensed CC BY-NC-SA 4.0.

| File | Adapted from | Changes made |
|---|---|---|
| `Classic Neg Pixel sRGB.cube` | `Provia to Classic Neg sRGB.cube` | Tone curve, split-tone and hue/saturation corrections for Pixel-phone JPEGs, baked into the grid (ADR 0005) |
| `Nostalgic Neg Pixel sRGB.cube` | `Provia to Nostalgic Neg sRGB.cube` | Corrections for Pixel-phone JPEGs, baked into the grid (ADR 0007) |
| `Koda64 Pixel sRGB.cube` | `classic chrome_sRGB.cube` | Most of the LUT's own tone curve backed out, then a Kodachrome 64-style recipe correction (warm white balance, deeper shadows, highlight roll-off, color, sky depth, partial skin restore) (ADR 0010) |

The original, unmodified source LUTs are not included in this repository.

## What this means

You may share and adapt these files for non-commercial purposes, as long as you credit the
source as above and release your adaptations under the same license. Because the app bundles
these files, any distributed build of FujiVibe (for example an APK) carries the same
non-commercial and share-alike terms for these files.

Photos you process with these LUTs are your own work. The license applies to the LUT files,
not to your images.
