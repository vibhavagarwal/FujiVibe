package com.fujivibe.recipe

import com.fujivibe.camera.FujiProp
import com.fujivibe.camera.SlotDump
import com.fujivibe.camera.packPtpString

/** What to write into a custom slot for a recipe, and what could not be written. */
class SlotWrite(val values: Map<Int, ByteArray>, val notes: List<String>)

/** The longest name a slot is known to hold; the camera's own are up to 7 capitals and digits. */
const val SLOT_NAME_MAX = 7

/** A slot name for this recipe: capitals and digits only, cut to [SLOT_NAME_MAX]. */
fun Recipe.slotName(): String =
    name.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(SLOT_NAME_MAX).ifEmpty { "RECIPE" }

private fun u16(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte())

private fun Int.signed16() = this and 0xFFFF

/**
 * The slot values for this recipe. The image size and quality, and settings no recipe here touches,
 * stay as [current] has them; every other setting is the recipe's, or the camera's default (an empty
 * slot's) when the recipe does not set it, so nothing is left over from whatever the slot held.
 *
 * Positions and encodings were read from an X-T5's slots (grain Off is 6 here, 1 in the RAW block;
 * everything else matches), apart from monochrome colour, which is unconfirmed.
 */
fun Recipe.toSlotValues(current: SlotDump): SlotWrite {
    val notes = mutableListOf<String>()
    val v = linkedMapOf<Int, ByteArray>()
    val s = settings

    fun keep(code: Int, fallback: Int) { v[code] = current.props[code]?.takeIf { it.size == 2 } ?: u16(fallback) }

    v[FujiProp.SLOT_NAME] = packPtpString(slotName())
    keep(0xD18E, 7) // image size
    keep(0xD18F, 2) // image quality
    v[0xD190] = u16(s.dynamicRange?.percent() ?: 100)
    if (s.dynamicRange == DynamicRange.AUTO) notes += "Dynamic range auto is not written (left at DR100)"
    if (s.dRangePriority != null) notes += "D Range Priority is not written yet"
    v[0xD191] = u16(0)
    v[0xD192] = u16(baseSimulation.code)
    v[0xD193] = u16((s.monochromaticColor?.wc?.times(10) ?: 0).signed16())
    v[0xD194] = u16((s.monochromaticColor?.mg?.times(10) ?: 0).signed16())
    if (s.monochromaticColor != null) notes += "Monochromatic colour position is unconfirmed"
    v[0xD195] = u16(
        s.grain?.let { if (it.strength == GrainStrength.OFF) 6 else it.nativeValue() } ?: 6,
    )
    v[0xD196] = u16(s.colorChrome?.nativeValue() ?: 1)
    v[0xD197] = u16(s.colorChromeFxBlue?.nativeValue() ?: 1)
    keep(0xD198, 1) // smooth skin
    val wb = s.whiteBalance
    v[0xD199] = u16(wb?.mode?.nativeValue() ?: 2)
    if (wb != null && wb.mode == null) notes += "White balance mode not given; Auto used"
    v[0xD19A] = u16((wb?.shiftR ?: 0).signed16())
    v[0xD19B] = u16((wb?.shiftB ?: 0).signed16())
    v[0xD19C] = u16(wb?.kelvin ?: 10000)
    v[0xD19D] = u16((s.highlight?.times10() ?: 0).signed16())
    v[0xD19E] = u16((s.shadow?.times10() ?: 0).signed16())
    v[0xD19F] = u16((s.color?.times10() ?: 0).signed16())
    v[0xD1A0] = u16((s.sharpness?.times10() ?: 0).signed16())
    val nr = s.highIsoNr ?: 0
    v[0xD1A1] = u16(NoiseReductionCodes.encode(nr) ?: 0x2000)
    if (NoiseReductionCodes.encode(nr) == null) notes += "High ISO NR $nr is not written (left at 0)"
    v[0xD1A2] = u16((s.clarity?.times10() ?: 0).signed16())
    keep(0xD1A3, 1)
    keep(0xD1A4, 1)
    keep(0xD1A5, 7)
    return SlotWrite(v, notes)
}
