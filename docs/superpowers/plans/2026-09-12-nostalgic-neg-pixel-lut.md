# Nostalgic Neg. (Pixel) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a new `NOSTALGIC_NEG_PIXEL` Film Simulation ("Nostalgic Neg. (Pixel)") — a Pixel-JPEG-compensated derived variant of the existing `Provia to Nostalgic Neg sRGB.cube`, following the same pattern as Classic Neg. (Pixel), with film grain included from the start.

**Architecture:** A new, independent `NostalgicNegCorrection` object (pure `Rgb -> Rgb` tone-curve/split-tone/HSL-shift correction, mirroring `LutCorrection`'s shape but with its own tunable constants and its own copy of the HSL math — deliberately not shared/parameterized, per this ticket's design decision) is baked into every entry of the source conversion LUT by a new `BakeNostalgicNegPixelLut` tool, producing a committed `.cube` file. A new `FilmSimulation.NOSTALGIC_NEG_PIXEL` entry wires that file into the existing registry; `ReviewCycle`, `Cube3DLut`, `LutRenderPipeline`, and `ResourceLutLoader` need zero changes since they already work generically off `FilmSimulation.entries`. `NOSTALGIC_NEG_PIXEL` is added to `GrainRenderPipeline`'s existing `GRAIN_ENABLED_SIMULATIONS` set for grain.

**Tech Stack:** Kotlin, JUnit 5, Gradle (`:render` module, `application` plugin + registered `JavaExec` tasks).

**Spec:** `.scratch/nostalgic-neg-pixel-lut/spec.md` (also see `.scratch/nostalgic-neg-pixel-lut/issues/01-bake-nostalgic-neg-pixel-lut.md` and `docs/adr/0007-nostalgic-neg-pixel-derived-variant.md`).

## Global Constraints

- Every correction must be a pure, pointwise `Rgb -> Rgb` function — no runtime pipeline changes (`Cube3DLut`, `LutRenderPipeline`, `ResourceLutLoader` stay untouched).
- All tunable constants live at the top of `NostalgicNegCorrection.kt`, each with an inline rationale comment — they are starting guesses, expected to be retuned via the re-bake loop, not final values.
- `NostalgicNegCorrection` is fully independent of `LutCorrection` (separate constants, separate copy of the HSL math) — this was an explicit design decision, not an oversight; do not refactor toward sharing as part of this plan.
- TDD throughout: write the failing test, then the minimal implementation, for every function.
- Run `./gradlew :render:test` after every task; run `./gradlew :app:testDebugUnitTest` after any task touching `app/`; run `./gradlew :app:assembleDebug` before the plan is considered done.
- Windows dev environment: `JAVA_HOME=C:\Users\vibha\.jdks\jbr-21.0.11`, prepend to `PATH`; invoke Gradle via `C:\Users\vibha\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle` (no java/gradle on PATH by default).

---

### Task 1: `NostalgicNegCorrection` — tone curve

**Files:**
- Create: `render/src/main/kotlin/com/fujivibe/render/tools/NostalgicNegCorrection.kt`
- Test: `render/src/test/kotlin/com/fujivibe/render/tools/NostalgicNegCorrectionTest.kt`

**Interfaces:**
- Consumes: `com.fujivibe.render.Rgb` (`data class Rgb(val r: Float, val g: Float, val b: Float)`).
- Produces: `NostalgicNegCorrection.BLACK_POINT_LIFT: Float`, `SHADOW_CURVE_STEEPNESS: Float`, `HIGHLIGHT_CURVE_STEEPNESS: Float`, `fun toneCurve(x: Float): Float` — later tasks in this plan call these.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.fujivibe.render.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NostalgicNegCorrectionTest {

    @Test
    fun `tone curve lifts true black to the configured black point`() {
        val result = NostalgicNegCorrection.toneCurve(0f)

        assertEquals(NostalgicNegCorrection.BLACK_POINT_LIFT, result, 1e-4f)
    }

    @Test
    fun `tone curve maps full white to full white`() {
        val result = NostalgicNegCorrection.toneCurve(1f)

        assertEquals(1f, result, 1e-4f)
    }

    @Test
    fun `tone curve is monotonically increasing`() {
        val samples = (0..20).map { it / 20f }

        val outputs = samples.map { NostalgicNegCorrection.toneCurve(it) }

        for (i in 1 until outputs.size) {
            assertTrue(
                outputs[i] >= outputs[i - 1],
                "toneCurve(${samples[i]}) = ${outputs[i]} is less than " +
                    "toneCurve(${samples[i - 1]}) = ${outputs[i - 1]}",
            )
        }
    }

    @Test
    fun `tone curve pulls highlights below a plain black-point lift, compressing the white point`() {
        // The brief calls for the white point to be "slightly lowered to compress the
        // highlights, preventing stark digital whites" — near-white input should read dimmer
        // than a plain lift, not pass through unchanged.
        val highlightInput = 0.95f
        val plainLift = NostalgicNegCorrection.BLACK_POINT_LIFT +
            (1f - NostalgicNegCorrection.BLACK_POINT_LIFT) * highlightInput

        val result = NostalgicNegCorrection.toneCurve(highlightInput)

        assertTrue(
            result < plainLift,
            "toneCurve($highlightInput) = $result should be pulled below the plain lift $plainLift",
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :render:test --tests "com.fujivibe.render.tools.NostalgicNegCorrectionTest"`
Expected: FAIL (compile error — `NostalgicNegCorrection` doesn't exist yet)

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.fujivibe.render.tools

/**
 * Pointwise Nostalgic Neg. (Pixel) correction: tone curve, then split-tone, then HSL shift.
 * Deliberately independent of [LutCorrection] (own constants, own copy of the HSL math) — the
 * target look here is a warm push (amber white balance, warm-tinted lifted blacks, compressed
 * highlights, cool-hue desaturation with reds/oranges protected), essentially inverted from
 * Classic Neg. (Pixel)'s cool/muted direction, so independent tuning knobs matter the same way
 * they did for that ticket. See `.scratch/nostalgic-neg-pixel-lut/spec.md`. Every constant here
 * is a starting guess — re-run `BakeNostalgicNegPixelLut` after editing one.
 */
object NostalgicNegCorrection {

    /**
     * Black point lifted for a milky, faded-black look — higher than Classic Neg. (Pixel)'s
     * 0.04, since the reference brief calls for a more pronounced lift ("shadows should not
     * reach true black").
     */
    const val BLACK_POINT_LIFT = 0.08f

    /** Shadow-side S-curve steepness — mild, since the target look is soft/faded, not punchy. */
    const val SHADOW_CURVE_STEEPNESS = 1.3f

    /**
     * Highlight-side steepness, `< 1` so it compresses (pulls down) rather than boosts — the
     * brief calls for the white point to be "slightly lowered," preventing stark digital whites.
     */
    const val HIGHLIGHT_CURVE_STEEPNESS = 0.75f

    /** Tone curve applied identically to each channel: S-curve contrast, then black-point lift. */
    fun toneCurve(x: Float): Float {
        val steepness = if (x < 0.5f) SHADOW_CURVE_STEEPNESS else HIGHLIGHT_CURVE_STEEPNESS
        val curved = sCurve(x, steepness)
        return BLACK_POINT_LIFT + (1f - BLACK_POINT_LIFT) * curved
    }

    private fun sCurve(x: Float, steepness: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val xp = Math.pow(x.toDouble(), steepness.toDouble())
        val oneMinusXp = Math.pow((1.0 - x), steepness.toDouble())
        return (xp / (xp + oneMinusXp)).toFloat()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :render:test --tests "com.fujivibe.render.tools.NostalgicNegCorrectionTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add render/src/main/kotlin/com/fujivibe/render/tools/NostalgicNegCorrection.kt render/src/test/kotlin/com/fujivibe/render/tools/NostalgicNegCorrectionTest.kt
git commit -m "Add Nostalgic Neg (Pixel) tone curve"
```

---

### Task 2: `NostalgicNegCorrection` — split-tone

**Files:**
- Modify: `render/src/main/kotlin/com/fujivibe/render/tools/NostalgicNegCorrection.kt`
- Modify: `render/src/test/kotlin/com/fujivibe/render/tools/NostalgicNegCorrectionTest.kt`

**Interfaces:**
- Consumes: `com.fujivibe.render.Rgb`.
- Produces: `NostalgicNegCorrection.SHADOW_TINT: Rgb`, `HIGHLIGHT_TINT: Rgb`, `SPLIT_TONE_SHADOW_REACH: Float`, `fun splitTone(rgb: Rgb): Rgb` — Task 3's `correct()` calls this.

- [ ] **Step 1: Write the failing tests**

Add to `NostalgicNegCorrectionTest.kt`:

```kotlin
import com.fujivibe.render.Rgb
```

```kotlin
    @Test
    fun `split-tone pushes pure black toward the warm shadow offset`() {
        val result = NostalgicNegCorrection.splitTone(Rgb(0f, 0f, 0f))

        assertEquals(NostalgicNegCorrection.SHADOW_TINT.r, result.r, 1e-4f)
        assertEquals(NostalgicNegCorrection.SHADOW_TINT.g, result.g, 1e-4f)
        assertEquals(NostalgicNegCorrection.SHADOW_TINT.b, result.b, 1e-4f)
    }

    @Test
    fun `split-tone pushes pure white toward the warm highlight offset`() {
        val result = NostalgicNegCorrection.splitTone(Rgb(1f, 1f, 1f))

        assertEquals((1f + NostalgicNegCorrection.HIGHLIGHT_TINT.r).coerceIn(0f, 1f), result.r, 1e-4f)
        assertEquals((1f + NostalgicNegCorrection.HIGHLIGHT_TINT.g).coerceIn(0f, 1f), result.g, 1e-4f)
        assertEquals((1f + NostalgicNegCorrection.HIGHLIGHT_TINT.b).coerceIn(0f, 1f), result.b, 1e-4f)
    }

    @Test
    fun `both shadow and highlight tints are warm (positive red, negative blue)`() {
        // Unlike Classic Neg (Pixel), where highlights fade toward a cool/neutral tint, the
        // brief calls for an amber push across the whole tonal range — both ends stay warm.
        assertTrue(NostalgicNegCorrection.SHADOW_TINT.r > 0f && NostalgicNegCorrection.SHADOW_TINT.b < 0f)
        assertTrue(NostalgicNegCorrection.HIGHLIGHT_TINT.r > 0f && NostalgicNegCorrection.HIGHLIGHT_TINT.b < 0f)
    }

    @Test
    fun `split-tone never pushes a component outside the 0 to 1 range`() {
        val result = NostalgicNegCorrection.splitTone(Rgb(1f, 1f, 1f))

        assertTrue(result.r in 0f..1f, "r = ${result.r} is outside [0, 1]")
        assertTrue(result.g in 0f..1f, "g = ${result.g} is outside [0, 1]")
        assertTrue(result.b in 0f..1f, "b = ${result.b} is outside [0, 1]")
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :render:test --tests "com.fujivibe.render.tools.NostalgicNegCorrectionTest"`
Expected: FAIL (compile error — `splitTone`/`SHADOW_TINT`/`HIGHLIGHT_TINT` don't exist yet)

- [ ] **Step 3: Write the minimal implementation**

Add to `NostalgicNegCorrection.kt` (import `com.fujivibe.render.Rgb` at the top):

```kotlin
    /**
     * Reddish-brown shadow offset, 8-bit +12/+2/-8 normalized to [0, 1] — the "milky, faded
     * blacks with a very subtle warm or reddish-brown undertone" the brief calls for.
     */
    val SHADOW_TINT = Rgb(12f / 255f, 2f / 255f, -8f / 255f)

    /**
     * Warm cream/amber highlight offset, 8-bit +10/+6/-10 — unlike Classic Neg. (Pixel), this
     * does NOT fade toward neutral/cool at the highlight end: the brief wants "creamy, warm,
     * slightly vintage" whites, not stark digital white.
     */
    val HIGHLIGHT_TINT = Rgb(10f / 255f, 6f / 255f, -10f / 255f)

    /**
     * How far [SHADOW_TINT]'s warm cast reaches up the tonal scale before [HIGHLIGHT_TINT]
     * takes over: shadow weight is `1 - luma^SPLIT_TONE_SHADOW_REACH`. Lower than Classic Neg.
     * (Pixel)'s 3f since both tints are warm here — there's no need for the shadow tint to
     * dominate all the way into highlights the way a cool-vs-neutral design would require.
     */
    const val SPLIT_TONE_SHADOW_REACH = 2f

    /**
     * Blends [SHADOW_TINT] into shadows and [HIGHLIGHT_TINT] into highlights. Clamped to
     * `[0, 1]`: an unclamped >1 channel here would otherwise reach [rgbToHsl] out of its
     * documented domain (see the equivalent bug fixed in `LutCorrection.splitTone`).
     */
    fun splitTone(rgb: Rgb): Rgb {
        val luma = 0.2126f * rgb.r + 0.7152f * rgb.g + 0.0722f * rgb.b
        val highlightWeight = Math.pow(luma.toDouble(), SPLIT_TONE_SHADOW_REACH.toDouble()).toFloat()
        val shadowWeight = 1f - highlightWeight
        return Rgb(
            (rgb.r + SHADOW_TINT.r * shadowWeight + HIGHLIGHT_TINT.r * highlightWeight).coerceIn(0f, 1f),
            (rgb.g + SHADOW_TINT.g * shadowWeight + HIGHLIGHT_TINT.g * highlightWeight).coerceIn(0f, 1f),
            (rgb.b + SHADOW_TINT.b * shadowWeight + HIGHLIGHT_TINT.b * highlightWeight).coerceIn(0f, 1f),
        )
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :render:test --tests "com.fujivibe.render.tools.NostalgicNegCorrectionTest"`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add render/src/main/kotlin/com/fujivibe/render/tools/NostalgicNegCorrection.kt render/src/test/kotlin/com/fujivibe/render/tools/NostalgicNegCorrectionTest.kt
git commit -m "Add Nostalgic Neg (Pixel) split-tone"
```

---

### Task 3: `NostalgicNegCorrection` — HSL shift + `correct()` composition

**Files:**
- Modify: `render/src/main/kotlin/com/fujivibe/render/tools/NostalgicNegCorrection.kt`
- Modify: `render/src/test/kotlin/com/fujivibe/render/tools/NostalgicNegCorrectionTest.kt`

**Interfaces:**
- Consumes: `com.fujivibe.render.Rgb`, `toneCurve` (Task 1), `splitTone` (Task 2).
- Produces: `NostalgicNegCorrection.Hsl` (data class: `hueDegrees: Float, saturation: Float, lightness: Float`), `rgbToHsl(Rgb): Hsl`, `hslToRgb(Hsl): Rgb`, `hslShift(Rgb): Rgb`, `fun correct(rgb: Rgb): Rgb` — Task 4's `BakeNostalgicNegPixelLut` calls `correct`.

- [ ] **Step 1: Write the failing tests**

Add to `NostalgicNegCorrectionTest.kt`:

```kotlin
    @Test
    fun `rgb to hsl and back round-trips a mid gray`() {
        val original = Rgb(0.5f, 0.5f, 0.5f)

        val roundTripped = NostalgicNegCorrection.hslToRgb(NostalgicNegCorrection.rgbToHsl(original))

        assertEquals(original.r, roundTripped.r, 1e-4f)
        assertEquals(original.g, roundTripped.g, 1e-4f)
        assertEquals(original.b, roundTripped.b, 1e-4f)
    }

    @Test
    fun `rgb to hsl and back round-trips pure green`() {
        val original = Rgb(0f, 1f, 0f)

        val roundTripped = NostalgicNegCorrection.hslToRgb(NostalgicNegCorrection.rgbToHsl(original))

        assertEquals(original.r, roundTripped.r, 1e-4f)
        assertEquals(original.g, roundTripped.g, 1e-4f)
        assertEquals(original.b, roundTripped.b, 1e-4f)
    }

    @Test
    fun `hsl shift desaturates a green hue`() {
        val green = Rgb(0.2f, 0.6f, 0.2f) // hue 120 degrees
        val originalHsl = NostalgicNegCorrection.rgbToHsl(green)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(green))

        assertTrue(
            shiftedHsl.saturation < originalHsl.saturation,
            "expected saturation below ${originalHsl.saturation}, got ${shiftedHsl.saturation}",
        )
    }

    @Test
    fun `hsl shift desaturates a blue hue`() {
        val blue = Rgb(0.2f, 0.2f, 0.6f) // hue 240 degrees
        val originalHsl = NostalgicNegCorrection.rgbToHsl(blue)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(blue))

        assertTrue(
            shiftedHsl.saturation < originalHsl.saturation,
            "expected saturation below ${originalHsl.saturation}, got ${shiftedHsl.saturation}",
        )
    }

    @Test
    fun `hsl shift boosts saturation for a red-orange hue`() {
        val red = Rgb(0.6f, 0.2f, 0.2f) // hue 0 degrees
        val originalHsl = NostalgicNegCorrection.rgbToHsl(red)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(red))

        assertTrue(
            shiftedHsl.saturation > originalHsl.saturation,
            "expected saturation above ${originalHsl.saturation}, got ${shiftedHsl.saturation}",
        )
    }

    @Test
    fun `hsl shift leaves an unrelated hue's saturation alone`() {
        val yellow = Rgb(0.6f, 0.6f, 0.2f) // hue 60 degrees, outside all targeted bands
        val originalHsl = NostalgicNegCorrection.rgbToHsl(yellow)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(yellow))

        assertEquals(originalHsl.saturation, shiftedHsl.saturation, 1e-4f)
    }

    @Test
    fun `hsl shift never produces saturation outside 0 to 1`() {
        val vividRed = Rgb(1f, 0f, 0f)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(vividRed))

        assertTrue(shiftedHsl.saturation in 0f..1f, "saturation ${shiftedHsl.saturation} outside [0, 1]")
    }

    @Test
    fun `correct applies tone curve, then split-tone, then hsl shift, in that order`() {
        val input = Rgb(0.6f, 0.2f, 0.15f)
        val expected = NostalgicNegCorrection.hslShift(
            NostalgicNegCorrection.splitTone(
                Rgb(
                    NostalgicNegCorrection.toneCurve(input.r),
                    NostalgicNegCorrection.toneCurve(input.g),
                    NostalgicNegCorrection.toneCurve(input.b),
                ),
            ),
        )

        val result = NostalgicNegCorrection.correct(input)

        assertEquals(expected.r, result.r, 1e-5f)
        assertEquals(expected.g, result.g, 1e-5f)
        assertEquals(expected.b, result.b, 1e-5f)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :render:test --tests "com.fujivibe.render.tools.NostalgicNegCorrectionTest"`
Expected: FAIL (compile error — `Hsl`, `rgbToHsl`, `hslToRgb`, `hslShift`, `correct` don't exist yet)

- [ ] **Step 3: Write the minimal implementation**

Add to `NostalgicNegCorrection.kt`:

```kotlin
    /** Hue in `[0, 360)` degrees, saturation and lightness in `[0, 1]`. */
    data class Hsl(val hueDegrees: Float, val saturation: Float, val lightness: Float)

    fun rgbToHsl(rgb: Rgb): Hsl {
        val max = maxOf(rgb.r, rgb.g, rgb.b)
        val min = minOf(rgb.r, rgb.g, rgb.b)
        val delta = max - min
        val lightness = (max + min) / 2f

        if (delta == 0f) return Hsl(0f, 0f, lightness)

        val saturation = delta / (1f - Math.abs(2f * lightness - 1f))

        val hue = when (max) {
            rgb.r -> 60f * (((rgb.g - rgb.b) / delta).mod(6f))
            rgb.g -> 60f * (((rgb.b - rgb.r) / delta) + 2f)
            else -> 60f * (((rgb.r - rgb.g) / delta) + 4f)
        }

        return Hsl(hue, saturation, lightness)
    }

    fun hslToRgb(hsl: Hsl): Rgb {
        val c = (1f - Math.abs(2f * hsl.lightness - 1f)) * hsl.saturation
        val hPrime = hsl.hueDegrees / 60f
        val x = c * (1f - Math.abs(hPrime.mod(2f) - 1f))
        val m = hsl.lightness - c / 2f

        val (r1, g1, b1) = when {
            hPrime < 1f -> Triple(c, x, 0f)
            hPrime < 2f -> Triple(x, c, 0f)
            hPrime < 3f -> Triple(0f, c, x)
            hPrime < 4f -> Triple(0f, x, c)
            hPrime < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Rgb(r1 + m, g1 + m, b1 + m)
    }

    /** Greens desaturated — the brief calls for "pulling vibrancy out of blues and greens." */
    const val GREENS_TARGET_HUE = 120f
    const val GREENS_WIDTH = 45f
    const val GREENS_SAT_REDUCTION = 0.45f

    /** Blues/cyans desaturated, same reasoning as greens. */
    const val BLUES_TARGET_HUE = 225f
    const val BLUES_WIDTH = 55f
    const val BLUES_SAT_REDUCTION = 0.45f

    /**
     * Reds/oranges keep full saturation or gain a little — "elements like the red logo
     * maintain a rich, warm focus against the faded background."
     */
    const val REDS_ORANGES_TARGET_HUE = 15f
    const val REDS_ORANGES_WIDTH = 35f
    const val REDS_ORANGES_SAT_BOOST = 0.10f

    /** `1` at `target`, falling linearly to `0` at `width` degrees away (shortest way around). */
    private fun angularWeight(hueDegrees: Float, target: Float, width: Float): Float {
        val rawDiff = Math.abs(hueDegrees - target) % 360f
        val distance = if (rawDiff > 180f) 360f - rawDiff else rawDiff
        return (1f - distance / width).coerceIn(0f, 1f)
    }

    /**
     * Selective cool-hue desaturation (blues, greens) plus red/orange saturation retention —
     * no blanket global-vibrance cut, unlike `LutCorrection`: desaturation here is targeted at
     * specific hue bands, not applied everywhere.
     */
    fun hslShift(rgb: Rgb): Rgb {
        val hsl = rgbToHsl(rgb)

        val greenWeight = angularWeight(hsl.hueDegrees, GREENS_TARGET_HUE, GREENS_WIDTH)
        val blueWeight = angularWeight(hsl.hueDegrees, BLUES_TARGET_HUE, BLUES_WIDTH)
        val redOrangeWeight = angularWeight(hsl.hueDegrees, REDS_ORANGES_TARGET_HUE, REDS_ORANGES_WIDTH)

        var saturation = hsl.saturation
        saturation *= (1f - GREENS_SAT_REDUCTION * greenWeight)
        saturation *= (1f - BLUES_SAT_REDUCTION * blueWeight)
        saturation *= (1f + REDS_ORANGES_SAT_BOOST * redOrangeWeight)

        return hslToRgb(Hsl(hsl.hueDegrees, saturation.coerceIn(0f, 1f), hsl.lightness))
    }

    /** The full Nostalgic Neg. (Pixel) correction: tone curve, then split-tone, then HSL shift. */
    fun correct(rgb: Rgb): Rgb {
        val toned = Rgb(toneCurve(rgb.r), toneCurve(rgb.g), toneCurve(rgb.b))
        return hslShift(splitTone(toned))
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :render:test --tests "com.fujivibe.render.tools.NostalgicNegCorrectionTest"`
Expected: PASS (16 tests)

- [ ] **Step 5: Commit**

```bash
git add render/src/main/kotlin/com/fujivibe/render/tools/NostalgicNegCorrection.kt render/src/test/kotlin/com/fujivibe/render/tools/NostalgicNegCorrectionTest.kt
git commit -m "Add Nostalgic Neg (Pixel) HSL shift and correction composition"
```

---

### Task 4: `BakeNostalgicNegPixelLut` tool, Gradle task, and the derived `.cube` file

**Files:**
- Create: `render/src/main/kotlin/com/fujivibe/render/tools/BakeNostalgicNegPixelLut.kt`
- Create: `render/src/test/kotlin/com/fujivibe/render/tools/BakeNostalgicNegPixelLutTest.kt`
- Modify: `render/build.gradle.kts`
- Create (generated, not hand-written): `derived-luts/Nostalgic Neg Pixel sRGB.cube`

**Interfaces:**
- Consumes: `com.fujivibe.render.Cube3DLut`, `com.fujivibe.render.CubeLutParser`, `NostalgicNegCorrection.correct` (Task 3), `CubeLutWriter.write` (existing, reused as-is).
- Produces: `BakeNostalgicNegPixelLut.bake(source: Cube3DLut): Cube3DLut`, `main()` — Task 5 depends on the generated `derived-luts/Nostalgic Neg Pixel sRGB.cube` file existing on disk.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.fujivibe.render.tools

import com.fujivibe.render.CubeLutParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BakeNostalgicNegPixelLutTest {

    @Test
    fun `baking runs every grid entry through NostalgicNegCorrection#correct`() {
        val sourceText = """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            0.93 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()
        val source = CubeLutParser.parse(sourceText)

        val baked = BakeNostalgicNegPixelLut.bake(source)

        assertEquals(source.size, baked.size)
        for (r in 0 until source.size) {
            for (g in 0 until source.size) {
                for (b in 0 until source.size) {
                    val expected = NostalgicNegCorrection.correct(source.valueAt(r, g, b))
                    assertEquals(expected, baked.valueAt(r, g, b))
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :render:test --tests "com.fujivibe.render.tools.BakeNostalgicNegPixelLutTest"`
Expected: FAIL (compile error — `BakeNostalgicNegPixelLut` doesn't exist yet)

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.fujivibe.render.tools

import com.fujivibe.render.Cube3DLut
import com.fujivibe.render.CubeLutParser
import java.io.File

/**
 * Derives `derived-luts/Nostalgic Neg Pixel sRGB.cube` from the source conversion LUT by running
 * every grid entry through [NostalgicNegCorrection.correct]. Run via
 * `./gradlew :render:bakeNostalgicNegPixelLut` (working directory pinned to the repo root — see
 * render/build.gradle.kts) after editing any of [NostalgicNegCorrection]'s tunable constants.
 */
object BakeNostalgicNegPixelLut {

    private val SOURCE_LUT =
        File("raw-assets/abpy-fujifilm-camera-profiles/provia conversion luts/Provia to Nostalgic Neg sRGB.cube")
    private val OUTPUT_LUT = File("derived-luts/Nostalgic Neg Pixel sRGB.cube")

    fun bake(source: Cube3DLut): Cube3DLut {
        val table = FloatArray(source.size * source.size * source.size * 3)
        var i = 0
        for (b in 0 until source.size) {
            for (g in 0 until source.size) {
                for (r in 0 until source.size) {
                    val corrected = NostalgicNegCorrection.correct(source.valueAt(r, g, b))
                    table[i++] = corrected.r
                    table[i++] = corrected.g
                    table[i++] = corrected.b
                }
            }
        }
        return Cube3DLut(source.size, table)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val source = CubeLutParser.parse(SOURCE_LUT.readText())
        val baked = bake(source)
        OUTPUT_LUT.parentFile.mkdirs()
        OUTPUT_LUT.writeText(CubeLutWriter.write(baked))
        println("Wrote ${OUTPUT_LUT.path} (${baked.size}^3 grid)")
    }
}
```

Register the new Gradle task in `render/build.gradle.kts` (add after the existing `previewFilmSimulation` task registration, keeping the `application` block's `mainClass` pointed at `BakeClassicNegPixelLut` unchanged):

```kotlin
// ./gradlew :render:bakeNostalgicNegPixelLut
// Regenerates derived-luts/Nostalgic Neg Pixel sRGB.cube from NostalgicNegCorrection's current
// constants. A registered task, not the application block's mainClass, so both bake tools stay
// independently runnable.
tasks.register<JavaExec>("bakeNostalgicNegPixelLut") {
    group = "application"
    description = "Regenerates derived-luts/Nostalgic Neg Pixel sRGB.cube from current NostalgicNegCorrection constants."
    mainClass.set("com.fujivibe.render.tools.BakeNostalgicNegPixelLut")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :render:test --tests "com.fujivibe.render.tools.BakeNostalgicNegPixelLutTest"`
Expected: PASS

- [ ] **Step 5: Generate the derived `.cube` file**

Run: `./gradlew :render:bakeNostalgicNegPixelLut`
Expected: `Wrote derived-luts/Nostalgic Neg Pixel sRGB.cube (32^3 grid)` and the file exists on disk.

- [ ] **Step 6: Commit**

```bash
git add render/src/main/kotlin/com/fujivibe/render/tools/BakeNostalgicNegPixelLut.kt render/src/test/kotlin/com/fujivibe/render/tools/BakeNostalgicNegPixelLutTest.kt render/build.gradle.kts "derived-luts/Nostalgic Neg Pixel sRGB.cube"
git commit -m "Add BakeNostalgicNegPixelLut tool and bake the derived LUT"
```

---

### Task 5: Wire `NOSTALGIC_NEG_PIXEL` into the Film Simulation registry, grain, and the preview tool

**Files:**
- Modify: `render/src/main/kotlin/com/fujivibe/render/FilmSimulation.kt`
- Modify: `render/src/main/kotlin/com/fujivibe/render/GrainRenderPipeline.kt`
- Modify: `render/src/main/kotlin/com/fujivibe/render/tools/PreviewFilmSimulation.kt`
- Modify: `render/build.gradle.kts` (test resources include list)
- Modify: `render/src/test/kotlin/com/fujivibe/render/ResourceLutLoaderWiringTest.kt`
- Modify: `render/src/test/kotlin/com/fujivibe/render/GrainRenderPipelineTest.kt`

**Interfaces:**
- Consumes: `derived-luts/Nostalgic Neg Pixel sRGB.cube` (Task 4).
- Produces: `FilmSimulation.NOSTALGIC_NEG_PIXEL` — Task 6's `ReviewCycleTest` updates depend on this entry's position in `FilmSimulation.entries`.

- [ ] **Step 1: Write the failing tests**

Update `render/build.gradle.kts`'s `test` resources block to include the new file:

```kotlin
        resources.include(
            "Provia to Nostalgic Neg sRGB.cube",
            "Classic Neg Pixel sRGB.cube",
            "Nostalgic Neg Pixel sRGB.cube",
        )
```

Add to `ResourceLutLoaderWiringTest.kt`:

```kotlin
    @Test
    fun `Nostalgic Neg Pixel loads as a real 32-point LUT`() {
        val loaded = ResourceLutLoader().load(FilmSimulation.NOSTALGIC_NEG_PIXEL)

        assertEquals(32, loaded.size)
    }

    @Test
    fun `Nostalgic Neg Pixel and plain Nostalgic Neg are distinct LUTs`() {
        val loader = ResourceLutLoader()

        val nostalgicNegPixel = loader.load(FilmSimulation.NOSTALGIC_NEG_PIXEL)
        val nostalgicNeg = loader.load(FilmSimulation.NOSTALGIC_NEG)

        assertNotEquals(nostalgicNegPixel.sample(0.5f, 0.2f, 0.8f), nostalgicNeg.sample(0.5f, 0.2f, 0.8f))
    }
```

Also update the `@BeforeEach` guard's resource check to require the new file too:

```kotlin
        assumeTrue(
            classLoader.getResource(FilmSimulation.NOSTALGIC_NEG.cubeResourceName) != null &&
                classLoader.getResource(FilmSimulation.CLASSIC_NEG_PIXEL.cubeResourceName) != null &&
                classLoader.getResource(FilmSimulation.NOSTALGIC_NEG_PIXEL.cubeResourceName) != null,
            "raw-assets/ and derived-luts/ .cube files aren't staged locally (see ADR 0003/0005/0007) — skipping",
        )
```

Add to `GrainRenderPipelineTest.kt`:

```kotlin
    @Test
    fun `Nostalgic Neg Pixel gets grain applied, changing at least one pixel`() = runTest {
        val source = solidImage(20, 20, 0xFF808080.toInt())
        val inner = PassthroughPipeline
        val pipeline = GrainRenderPipeline(inner)

        val result = pipeline.render(source, RenderSelection.Simulation(FilmSimulation.NOSTALGIC_NEG_PIXEL))

        assertNotEquals(source, result)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :render:test --tests "com.fujivibe.render.ResourceLutLoaderWiringTest" --tests "com.fujivibe.render.GrainRenderPipelineTest"`
Expected: FAIL (compile error — `FilmSimulation.NOSTALGIC_NEG_PIXEL` doesn't exist yet)

- [ ] **Step 3: Write the minimal implementation**

Update `FilmSimulation.kt`:

```kotlin
package com.fujivibe.render

/**
 * The ordered Film Simulation registry, each wired to a `.cube` resource on the classpath.
 * Nostalgic Neg. is wired to its `provia conversion luts/` sRGB `.cube` file — not the
 * same-named file under `cube lut/`, which requires scene-linear input and isn't safe to apply
 * directly to a gamma-encoded JPEG (v1 launch set per ADR 0004). Classic Neg. (Pixel) and
 * Nostalgic Neg. (Pixel) are FujiVibe's own derived variants, not stock Fuji-named looks: each
 * compensates for how a Pixel phone's JPEG differs from the Provia baseline the conversion LUTs
 * assume (see ADR 0005/0007), superseding ADR 0004's "exactly two" framing. The original,
 * unmodified Classic Neg. was removed once Classic Neg. (Pixel) replaced it (see ADR 0006);
 * plain Nostalgic Neg. is kept alongside its Pixel variant for now, same sequencing.
 */
enum class FilmSimulation(val displayName: String, val cubeResourceName: String) {
    CLASSIC_NEG_PIXEL("Classic Neg. (Pixel)", "Classic Neg Pixel sRGB.cube"),
    NOSTALGIC_NEG("Nostalgic Neg.", "Provia to Nostalgic Neg sRGB.cube"),
    NOSTALGIC_NEG_PIXEL("Nostalgic Neg. (Pixel)", "Nostalgic Neg Pixel sRGB.cube"),
}
```

Update `GrainRenderPipeline.kt`'s companion object:

```kotlin
        /** Which Film Simulations get grain. Deliberately a set, not one hardcoded check. */
        val GRAIN_ENABLED_SIMULATIONS = setOf(FilmSimulation.CLASSIC_NEG_PIXEL, FilmSimulation.NOSTALGIC_NEG_PIXEL)
```

Update `PreviewFilmSimulation.kt`'s `LUT_DIRECTORIES` map:

```kotlin
    private val LUT_DIRECTORIES = mapOf(
        FilmSimulation.NOSTALGIC_NEG to File("raw-assets/abpy-fujifilm-camera-profiles/provia conversion luts"),
        FilmSimulation.CLASSIC_NEG_PIXEL to File("derived-luts"),
        FilmSimulation.NOSTALGIC_NEG_PIXEL to File("derived-luts"),
    )
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :render:test --tests "com.fujivibe.render.ResourceLutLoaderWiringTest" --tests "com.fujivibe.render.GrainRenderPipelineTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add render/src/main/kotlin/com/fujivibe/render/FilmSimulation.kt render/src/main/kotlin/com/fujivibe/render/GrainRenderPipeline.kt render/src/main/kotlin/com/fujivibe/render/tools/PreviewFilmSimulation.kt render/build.gradle.kts render/src/test/kotlin/com/fujivibe/render/ResourceLutLoaderWiringTest.kt render/src/test/kotlin/com/fujivibe/render/GrainRenderPipelineTest.kt
git commit -m "Wire Nostalgic Neg (Pixel) into the Film Simulation registry and grain"
```

---

### Task 6: `ReviewCycleTest` for the new 5-entry order, then full verification

**Files:**
- Modify: `app/src/test/java/com/fujivibe/review/ReviewCycleTest.kt`

**Interfaces:**
- Consumes: `FilmSimulation.entries` order from Task 5 (`CLASSIC_NEG_PIXEL, NOSTALGIC_NEG, NOSTALGIC_NEG_PIXEL`).

- [ ] **Step 1: Update the test to expect the new 5-entry cycle**

Replace `ReviewCycleTest.kt`'s contents:

```kotlin
package com.fujivibe.review

import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.RenderSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReviewCycleTest {

    @Test
    fun `starts at Original`() {
        val cycle = ReviewCycle.start()

        assertEquals(RenderSelection.Original, cycle.current)
        assertEquals("Original", cycle.label)
    }

    @Test
    fun `next steps through the launch Film Simulations in registry order`() {
        val cycle = ReviewCycle.start()

        val afterFirstNext = cycle.next()
        assertEquals(RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG_PIXEL), afterFirstNext.current)
        assertEquals("Classic Neg. (Pixel)", afterFirstNext.label)

        val afterSecondNext = afterFirstNext.next()
        assertEquals(RenderSelection.Simulation(FilmSimulation.NOSTALGIC_NEG), afterSecondNext.current)
        assertEquals("Nostalgic Neg.", afterSecondNext.label)

        val afterThirdNext = afterSecondNext.next()
        assertEquals(RenderSelection.Simulation(FilmSimulation.NOSTALGIC_NEG_PIXEL), afterThirdNext.current)
        assertEquals("Nostalgic Neg. (Pixel)", afterThirdNext.label)
    }

    @Test
    fun `next wraps from the last Film Simulation back to Original`() {
        val lastEntry = ReviewCycle.start().next().next().next()

        val wrapped = lastEntry.next()

        assertEquals(RenderSelection.Original, wrapped.current)
    }

    @Test
    fun `previous wraps from Original back to the last Film Simulation`() {
        val cycle = ReviewCycle.start()

        val wrapped = cycle.previous()

        assertEquals(RenderSelection.Simulation(FilmSimulation.NOSTALGIC_NEG_PIXEL), wrapped.current)
    }

    @Test
    fun `previous is the exact inverse of next`() {
        val cycle = ReviewCycle.start().next()

        assertEquals(cycle, cycle.next().previous())
        assertEquals(cycle, cycle.previous().next())
    }
}
```

- [ ] **Step 2: Run the test to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.fujivibe.review.ReviewCycleTest"`
Expected: PASS. No separate production-code change is needed in this task — `ReviewCycle` already
derives its swipe order generically from `FilmSimulation.entries`, so Task 5's registry change is
what actually makes the new order correct; this test only needs its expectations updated to match.

- [ ] **Step 3: Run the full verification suite**

Run, in order:
```bash
./gradlew :render:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: all three succeed. This is the same gate the Classic Neg. (Pixel) ticket used before merging.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/fujivibe/review/ReviewCycleTest.kt
git commit -m "Update ReviewCycleTest for the 5-entry Film Simulation cycle"
```

---

## After this plan (not part of it — manual, iterative, same as Classic Neg. (Pixel))

1. Run `./gradlew :render:previewFilmSimulation --args="<input> <output> NOSTALGIC_NEG,NOSTALGIC_NEG_PIXEL"` against the user's reference photo (or its original, pre-simulation source if available) and compare by eye against the supplied reference image.
2. Iterate: tweak a `NostalgicNegCorrection` constant → `./gradlew :render:bakeNostalgicNegPixelLut` → re-run the preview command → compare → repeat.
3. Once it reads right, `./gradlew :app:assembleDebug` and `adb -s 57181FDCH0037T install -r app/build/outputs/apk/debug/app-debug.apk` for an on-device pass.
4. Check off the remaining boxes in `.scratch/nostalgic-neg-pixel-lut/issues/01-bake-nostalgic-neg-pixel-lut.md` (visual verification, on-device verification) as they're confirmed.
