package com.fujivibe.recipe

import com.fujivibe.camera.FujiFilmSim

/**
 * A **Recipe** (see CONTEXT.md): a named set of camera settings the camera applies when it
 * develops a RAF. A setting that is null is not part of the recipe, so the camera keeps what the
 * RAF recorded for it.
 *
 * Everything past [settings] is for people and for a future recommendation engine to read: where
 * the recipe came from, and what it is good for, in the shared vocabulary of [BestFor].
 */
data class Recipe(
    val id: String,
    val name: String,
    val kind: RecipeKind,
    val baseSimulation: FujiFilmSim,
    val settings: RecipeSettings,
    val shootingNotes: ShootingNotes = ShootingNotes(),
    val source: RecipeSource = RecipeSource(),
    /** The owner's own one-line description of the look, if they gave one. */
    val userDescription: String? = null,
    val bestFor: BestFor = BestFor(),
    val avoid: List<String> = emptyList(),
    /** A paragraph about the look and when to use it, for reading by a person or a recommender. */
    val recommendationText: String = "",
    val confidence: String = "",
    val notes: String = "",
)

enum class RecipeKind { STOCK, COMMUNITY, OWN }

enum class DynamicRange { AUTO, DR100, DR200, DR400 }

enum class DRangePriority { OFF, AUTO, WEAK, STRONG }

enum class Level { OFF, WEAK, STRONG }

enum class GrainStrength { OFF, WEAK, STRONG }

enum class GrainSize { SMALL, LARGE }

data class Grain(val strength: GrainStrength, val size: GrainSize? = null)

enum class WhiteBalanceMode {
    AUTO, AUTO_WHITE_PRIORITY, AUTO_AMBIENCE_PRIORITY, DAYLIGHT, SHADE, INCANDESCENT,
    FLUORESCENT_1, FLUORESCENT_2, FLUORESCENT_3, KELVIN,
}

/** A null [mode] leaves the white balance the RAF was shot with, with any shifts applied to it. */
data class WhiteBalance(
    val mode: WhiteBalanceMode? = null,
    val kelvin: Int? = null,
    val shiftR: Int? = null,
    val shiftB: Int? = null,
)

/** The warm-cool and magenta-green shifts of a black-and-white film simulation. */
data class MonochromaticColor(val wc: Int, val mg: Int)

data class RecipeSettings(
    val dynamicRange: DynamicRange? = null,
    val dRangePriority: DRangePriority? = null,
    val grain: Grain? = null,
    val colorChrome: Level? = null,
    val colorChromeFxBlue: Level? = null,
    val whiteBalance: WhiteBalance? = null,
    /** Tone and colour steps may be halves, as in Shadow +0.5. */
    val highlight: Double? = null,
    val shadow: Double? = null,
    val color: Double? = null,
    val sharpness: Double? = null,
    val highIsoNr: Int? = null,
    val clarity: Double? = null,
    val monochromaticColor: MonochromaticColor? = null,
)

/** What the author shoots with. Not part of developing a RAF, which is already shot. */
data class ShootingNotes(val iso: String = "", val exposureCompensation: String = "")

data class RecipeSource(
    val author: String = "",
    val publisher: String = "",
    val url: String? = null,
)

/** Tags in a small shared vocabulary (see the draft in .scratch/raw-develop/recipes). */
data class BestFor(
    val subjects: List<String> = emptyList(),
    val light: List<String> = emptyList(),
    val mood: List<String> = emptyList(),
)
