package com.fujivibe.camera

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The recipe the camera renders a RAF with: the camera's own binary block, property 0xD185. Its
 * last `numParams` 4-byte little-endian slots are the settings; slot 0 of the block says how many.
 * Settings left alone keep the values the camera read from the RAF, so a patch changes only what
 * it names.
 *
 * Field positions and encodings follow FilmKit's notes on the X100VI's 625-byte block. The
 * X-T5's may differ; [describe] exists so a first run on the camera can be checked against them.
 */
class RawConvProfile(val bytes: ByteArray) {
    private val numParams: Int
    private val table: Int

    init {
        require(bytes.size >= 2) { "A profile starts with its parameter count" }
        numParams = bytes.u16(0)
        table = bytes.size - numParams * 4
        require(table >= 2) { "Profile of ${bytes.size} bytes cannot hold $numParams parameters" }
    }

    fun field(index: Int): Int {
        require(index in 0 until numParams) { "No parameter $index in a profile of $numParams" }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(table + index * 4)
    }

    fun with(index: Int, value: Int): RawConvProfile {
        require(index in 0 until numParams) { "No parameter $index in a profile of $numParams" }
        val copy = bytes.copyOf()
        ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN).putInt(table + index * 4, value)
        return RawConvProfile(copy)
    }

    fun withFilmSimulation(code: Int) = with(FILM_SIMULATION, code)

    /** Asks for the full-size JPEG (L 3:2, Fine); left alone, the camera has rendered 10 MP. */
    fun withFullSize() = with(IMAGE_SIZE, IMAGE_SIZE_L_3_2).with(IMAGE_QUALITY, IMAGE_QUALITY_FINE)

    /** Every parameter as `index=value`, for finding what an unmapped one means. */
    fun dumpAll(): String = (0 until numParams).joinToString(" ") { "$it=${field(it)}" }

    /** The settings worth a glance when checking what the camera took from the RAF. */
    fun describe(): String = buildString {
        append("${bytes.size} bytes, $numParams params; ")
        append("size=${field(IMAGE_SIZE)} quality=${field(IMAGE_QUALITY)} ")
        append("film=${hex(field(FILM_SIMULATION))} ")
        append("dr=${field(DYNAMIC_RANGE)} grain=${field(GRAIN)} chrome=${field(COLOR_CHROME)} ")
        append("wb=${hex(field(WHITE_BALANCE))} temp=${field(WB_COLOR_TEMP)} shiftR=${field(WB_SHIFT_R)} shiftB=${field(WB_SHIFT_B)} ")
        append("chromeBlue=${field(COLOR_CHROME_FX_BLUE)} ")
        append("hl=${field(HIGHLIGHT_TONE)} sh=${field(SHADOW_TONE)} color=${field(COLOR)}")
    }

    companion object {
        // The codes are from libfuji's predecessor (github.com/petabyt/fp, MIT), which documents the
        // 632-byte write format. The camera's own 625-byte block has one more field at the start, so
        // each position there is one higher (an X-T5 RAF shot at L 3:2, Fine reads size=7 at 3 and
        // quality=2 at 4, exposure bias at 5, dynamic range 200 at 6, film simulation at 8).
        const val IMAGE_SIZE = 3
        const val IMAGE_QUALITY = 4
        const val IMAGE_SIZE_L_3_2 = 7
        const val IMAGE_QUALITY_FINE = 2

        const val EXPOSURE_BIAS = 5
        const val DYNAMIC_RANGE = 6
        const val D_RANGE_PRIORITY = 7
        const val FILM_SIMULATION = 8
        const val GRAIN = 9
        const val COLOR_CHROME = 10
        const val WHITE_BALANCE = 12
        const val WB_SHIFT_R = 13
        const val WB_SHIFT_B = 14
        const val WB_COLOR_TEMP = 15
        const val HIGHLIGHT_TONE = 16
        const val SHADOW_TONE = 17
        const val COLOR = 18
        const val SHARPNESS = 19
        const val COLOR_CHROME_FX_BLUE = 25
        const val CLARITY = 27
    }
}

/**
 * The film simulation codes the camera takes in a [RawConvProfile] (FilmKit's table, from an
 * X100VI), with the [id] a recipe names it by.
 */
enum class FujiFilmSim(val code: Int, val label: String, val id: String) {
    PROVIA(0x01, "Provia", "provia"),
    VELVIA(0x02, "Velvia", "velvia"),
    ASTIA(0x03, "Astia", "astia"),
    PRO_NEG_HI(0x04, "Pro Neg. Hi", "pro-neg-hi"),
    PRO_NEG_STD(0x05, "Pro Neg. Std", "pro-neg-std"),
    MONOCHROME(0x06, "Monochrome", "monochrome"),
    MONOCHROME_YE(0x07, "Monochrome + Ye", "monochrome-ye"),
    MONOCHROME_R(0x08, "Monochrome + R", "monochrome-r"),
    MONOCHROME_G(0x09, "Monochrome + G", "monochrome-g"),
    SEPIA(0x0A, "Sepia", "sepia"),
    CLASSIC_CHROME(0x0B, "Classic Chrome", "classic-chrome"),
    ACROS(0x0C, "Acros", "acros"),
    ACROS_YE(0x0D, "Acros + Ye", "acros-ye"),
    ACROS_R(0x0E, "Acros + R", "acros-r"),
    ACROS_G(0x0F, "Acros + G", "acros-g"),
    ETERNA(0x10, "Eterna", "eterna"),
    CLASSIC_NEG(0x11, "Classic Neg.", "classic-neg"),
    ETERNA_BLEACH(0x12, "Eterna Bleach Bypass", "eterna-bleach"),
    NOSTALGIC_NEG(0x13, "Nostalgic Neg.", "nostalgic-neg"),
    REALA_ACE(0x14, "Reala Ace", "reala-ace");

    companion object {
        fun fromId(id: String): FujiFilmSim? = entries.firstOrNull { it.id == id }
    }
}
