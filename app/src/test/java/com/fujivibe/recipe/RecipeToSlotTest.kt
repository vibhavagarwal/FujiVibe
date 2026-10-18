package com.fujivibe.recipe

import com.fujivibe.camera.FujiFilmSim
import com.fujivibe.camera.FujiProp
import com.fujivibe.camera.PtpReader
import com.fujivibe.camera.SlotDump
import com.fujivibe.camera.packPtpString
import com.fujivibe.camera.u16
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checked against the real custom slots of the user's X-T5 (read 2026-10-18): the slots hold recipes
 * the user had already loaded, so a recipe with the same settings must produce the same values.
 */
class RecipeToSlotTest {

    /** Builds a slot from the numbers a Read slots log line prints, e.g. "D18E=7 D18F=4 ...". */
    private fun slot(number: Int, name: String, line: String): SlotDump {
        val props = linkedMapOf<Int, ByteArray>(FujiProp.SLOT_NAME to packPtpString(name))
        line.trim().split(" ").forEach {
            val (code, value) = it.split("=")
            props[code.toInt(16)] = byteArrayOf(value.toInt().toByte(), (value.toInt() shr 8).toByte())
        }
        return SlotDump(number, props)
    }

    private val portra = slot(2, "PORTRA", "D18E=7 D18F=4 D190=400 D191=0 D192=11 D193=0 D194=0 D195=2 D196=3 D197=2 D198=1 D199=2 D19A=2 D19B=65532 D19C=10000 D19D=65526 D19E=65526 D19F=20 D1A0=65516 D1A1=32768 D1A2=0 D1A3=1 D1A4=1 D1A5=7")
    private val ccNeg = slot(3, "CC NEG", "D18E=7 D18F=4 D190=400 D191=0 D192=17 D193=0 D194=0 D195=5 D196=3 D197=3 D198=1 D199=2 D19A=4 D19B=65531 D19C=10000 D19D=65516 D19E=10 D19F=40 D1A0=0 D1A1=32768 D1A2=65496 D1A3=1 D1A4=1 D1A5=7")
    private val koda = slot(4, "KODA64", "D18E=7 D18F=4 D190=400 D191=0 D192=11 D193=0 D194=0 D195=2 D196=3 D197=2 D198=1 D199=4 D19A=2 D19B=65531 D19C=10000 D19D=65516 D19E=65526 D19F=20 D1A0=0 D1A1=32768 D1A2=0 D1A3=1 D1A4=1 D1A5=7")
    private val empty = slot(5, "", "D18E=7 D18F=2 D190=100 D191=0 D192=1 D193=0 D194=0 D195=6 D196=1 D197=1 D198=1 D199=2 D19A=0 D19B=0 D19C=10000 D19D=0 D19E=0 D19F=0 D1A0=0 D1A1=8192 D1A2=0 D1A3=1 D1A4=1 D1A5=7")

    private fun recipe(name: String, sim: FujiFilmSim, s: RecipeSettings) =
        Recipe(id = name, name = name, kind = RecipeKind.OWN, baseSimulation = sim, settings = s)

    private fun assertSameSettings(expected: SlotDump, write: SlotWrite) {
        for (code in 0xD18E..0xD1A5) {
            // Clarity is never written (it slows saving), whatever the slot held.
            val want = if (code == 0xD1A2) 0 else expected.value(code)
            assertEquals(want, write.values.getValue(code).u16(0), "property ${Integer.toHexString(code).uppercase()}")
        }
    }

    @Test
    fun `Reggie's Portra settings give the values the user's PORTRA slot holds`() {
        val r = recipe("PORTRA", FujiFilmSim.CLASSIC_CHROME, RecipeSettings(
            dynamicRange = DynamicRange.DR400, grain = Grain(GrainStrength.WEAK, GrainSize.SMALL),
            colorChrome = Level.STRONG, colorChromeFxBlue = Level.WEAK,
            whiteBalance = WhiteBalance(WhiteBalanceMode.AUTO, shiftR = 2, shiftB = -4),
            highlight = -1.0, shadow = -1.0, color = 2.0, sharpness = -2.0, highIsoNr = -4, clarity = 0.0,
        ))

        assertSameSettings(portra, r.toSlotValues(portra))
    }

    @Test
    fun `a Kodachrome-style daylight recipe gives the values of the KODA64 slot`() {
        val r = recipe("KODA64", FujiFilmSim.CLASSIC_CHROME, RecipeSettings(
            dynamicRange = DynamicRange.DR400, grain = Grain(GrainStrength.WEAK, GrainSize.SMALL),
            colorChrome = Level.STRONG, colorChromeFxBlue = Level.WEAK,
            whiteBalance = WhiteBalance(WhiteBalanceMode.DAYLIGHT, shiftR = 2, shiftB = -5),
            highlight = -2.0, shadow = -1.0, color = 2.0, sharpness = 0.0, highIsoNr = -4, clarity = 0.0,
        ))

        assertSameSettings(koda, r.toSlotValues(koda))
    }

    @Test
    fun `a Classic Negative recipe with strong large grain gives the values of the CC NEG slot`() {
        val r = recipe("CC NEG", FujiFilmSim.CLASSIC_NEG, RecipeSettings(
            dynamicRange = DynamicRange.DR400, grain = Grain(GrainStrength.STRONG, GrainSize.LARGE),
            colorChrome = Level.STRONG, colorChromeFxBlue = Level.STRONG,
            whiteBalance = WhiteBalance(WhiteBalanceMode.AUTO, shiftR = 4, shiftB = -5),
            highlight = -2.0, shadow = 1.0, color = 4.0, sharpness = 0.0, highIsoNr = -4, clarity = -4.0,
        ))

        assertSameSettings(ccNeg, r.toSlotValues(ccNeg))
    }

    @Test
    fun `a recipe that sets only a film simulation leaves every other setting at the default of an empty slot`() {
        val r = recipe("VELVIA", FujiFilmSim.VELVIA, RecipeSettings())

        val w = r.toSlotValues(portra) // written over a slot that held a recipe: nothing may be left behind

        assertEquals(FujiFilmSim.VELVIA.code, w.values.getValue(0xD192).u16(0))
        for (code in listOf(0xD190, 0xD195, 0xD196, 0xD197, 0xD199, 0xD19A, 0xD19B, 0xD19C, 0xD19D, 0xD19E, 0xD19F, 0xD1A0, 0xD1A1, 0xD1A2)) {
            assertEquals(empty.value(code), w.values.getValue(code).u16(0), "property ${Integer.toHexString(code).uppercase()}")
        }
    }

    @Test
    fun `white balance temperature is written before the shifts, which it would otherwise reset`() {
        val r = recipe("P", FujiFilmSim.CLASSIC_NEG, RecipeSettings(
            whiteBalance = WhiteBalance(WhiteBalanceMode.KELVIN, kelvin = 5800, shiftR = 1, shiftB = -3),
        ))

        val order = r.toSlotValues(empty).values.keys.toList()

        assertTrue(order.indexOf(0xD199) < order.indexOf(0xD19C))
        assertTrue(order.indexOf(0xD19C) < order.indexOf(0xD19A))
        assertTrue(order.indexOf(0xD19C) < order.indexOf(0xD19B))
    }

    @Test
    fun `image size and quality stay as the slot has them`() {
        val w = recipe("X", FujiFilmSim.PROVIA, RecipeSettings()).toSlotValues(portra)

        assertEquals(7, w.values.getValue(0xD18E).u16(0))
        assertEquals(4, w.values.getValue(0xD18F).u16(0)) // the user's RAW + JPEG
    }

    @Test
    fun `the slot name is capitals and digits, cut to seven`() {
        assertEquals("REGGIES", recipe("Reggie's Portra", FujiFilmSim.CLASSIC_CHROME, RecipeSettings()).slotName())
        assertEquals("PACIFIC", recipe("Pacific Blues", FujiFilmSim.CLASSIC_NEG, RecipeSettings()).slotName())
        assertEquals("KODA64", recipe("Koda64", FujiFilmSim.CLASSIC_CHROME, RecipeSettings()).slotName())
        assertEquals("RECIPE", recipe("!!!", FujiFilmSim.PROVIA, RecipeSettings()).slotName())
        val w = recipe("Pacific Blues", FujiFilmSim.CLASSIC_NEG, RecipeSettings()).toSlotValues(empty)
        assertEquals("PACIFIC", PtpReader(w.values.getValue(FujiProp.SLOT_NAME)).string())
    }

    @Test
    fun `D Range Priority Strong is written as 2, as on a slot the user set to Strong`() {
        // C5 "AAA" on the user's X-T5 after setting D Range Priority to Strong: D191 went from 0 to 2.
        val aaa = slot(5, "AAA", "D18E=7 D18F=4 D190=400 D191=2 D192=11 D193=0 D194=0 D195=2 D196=3 D197=2 D198=1 D199=4 D19A=2 D19B=65531 D19C=10000 D19D=65516 D19E=65526 D19F=20 D1A0=0 D1A1=32768 D1A2=0 D1A3=1 D1A4=1 D1A5=7")
        val r = recipe("AAA", FujiFilmSim.CLASSIC_CHROME, RecipeSettings(
            dynamicRange = DynamicRange.DR400, dRangePriority = DRangePriority.STRONG, grain = Grain(GrainStrength.WEAK, GrainSize.SMALL),
            colorChrome = Level.STRONG, colorChromeFxBlue = Level.WEAK,
            whiteBalance = WhiteBalance(WhiteBalanceMode.DAYLIGHT, shiftR = 2, shiftB = -5),
            highlight = -2.0, shadow = -1.0, color = 2.0, sharpness = 0.0, highIsoNr = -4, clarity = 0.0,
        ))

        val w = r.toSlotValues(aaa)

        assertSameSettings(aaa, w)
        assertEquals(emptyList<String>(), w.notes)
    }

    @Test
    fun `D Range Priority Weak is written as 1, as on a slot the user set to Weak`() {
        // C5 on the user's X-T5 after setting D Range Priority to Weak: D191 read 1.
        val w = recipe("W", FujiFilmSim.CLASSIC_CHROME, RecipeSettings(dRangePriority = DRangePriority.WEAK)).toSlotValues(empty)

        assertEquals(1, w.values.getValue(0xD191).u16(0))
        assertEquals(emptyList<String>(), w.notes)
    }

    @Test
    fun `D Range Priority Auto is written as 0x8000, as read from a slot the user set to Auto`() {
        val w = recipe("A", FujiFilmSim.CLASSIC_CHROME, RecipeSettings(dRangePriority = DRangePriority.AUTO)).toSlotValues(empty)

        assertEquals(0x8000, w.values.getValue(0xD191).u16(0))
        assertEquals(emptyList<String>(), w.notes)
    }

    @Test
    fun `D Range Priority off is 0, and Off is what a recipe without it gets`() {
        val strong = recipe("S", FujiFilmSim.CLASSIC_CHROME, RecipeSettings(dRangePriority = DRangePriority.STRONG)).toSlotValues(empty)
        val off = recipe("O", FujiFilmSim.CLASSIC_CHROME, RecipeSettings(dRangePriority = DRangePriority.OFF)).toSlotValues(empty)
        val none = recipe("N", FujiFilmSim.CLASSIC_CHROME, RecipeSettings()).toSlotValues(empty)

        assertEquals(2, strong.values.getValue(0xD191).u16(0))
        assertEquals(0, off.values.getValue(0xD191).u16(0))
        assertEquals(0, none.values.getValue(0xD191).u16(0))
    }

    @Test
    fun `Dynamic Range Auto is written as 0xFFFF, as read from a slot the user set to Auto`() {
        // C5 "WOODLAN" on the user's X-T5 after setting Dynamic Range to Auto: D190 read 65535.
        val w = recipe("A", FujiFilmSim.CLASSIC_CHROME, RecipeSettings(dynamicRange = DynamicRange.AUTO)).toSlotValues(empty)

        assertEquals(65535, w.values.getValue(0xD190).u16(0))
        assertEquals(emptyList<String>(), w.notes)
    }

    @Test
    fun `what cannot be written is reported as a note`() {
        val r = recipe("T", FujiFilmSim.ACROS_R, RecipeSettings(
            dynamicRange = DynamicRange.AUTO,
            monochromaticColor = MonochromaticColor(-1, 0), highIsoNr = -2,
        ))

        val notes = r.toSlotValues(empty).notes

        assertTrue(notes.any { it.contains("Monochromatic") })
        assertTrue(notes.any { it.contains("High ISO NR -2") })
    }

    @Test
    fun `Clarity is written as 0 whatever the recipe says, and a filter is suggested instead`() {
        val r = recipe("CL", FujiFilmSim.CLASSIC_CHROME, RecipeSettings(clarity = -3.0))

        assertEquals(0, r.toSlotValues(ccNeg).values.getValue(0xD1A2).u16(0)) // the slot held -4
        assertEquals("CineBloom 10% or Black Pro-Mist 1/4", r.diffusionFilter())
        assertTrue(r.clarityNote()!!.contains("not written"))
    }

    @Test
    fun `filter strength follows how negative the Clarity is, and positive Clarity gets no filter`() {
        fun filter(c: Double?) = recipe("F", FujiFilmSim.PROVIA, RecipeSettings(clarity = c)).diffusionFilter()
        assertEquals("CineBloom 5% or Black Pro-Mist 1/8", filter(-1.0))
        assertEquals("CineBloom 10% or Black Pro-Mist 1/4", filter(-4.0))
        assertEquals("CineBloom 20% or Black Pro-Mist 1/2", filter(-5.0))
        assertEquals(null, filter(0.0))
        assertEquals(null, filter(2.0))
        assertEquals(null, filter(null))
        assertTrue(recipe("F", FujiFilmSim.PROVIA, RecipeSettings(clarity = 2.0)).clarityNote()!!.contains("less bite"))
    }

    @Test
    fun `fluorescent, shade and auto priority modes are written with the codes read from the X-T5`() {
        fun write(mode: WhiteBalanceMode) = recipe("W", FujiFilmSim.ETERNA, RecipeSettings(whiteBalance = WhiteBalance(mode, shiftR = -6, shiftB = -4))).toSlotValues(empty)

        assertEquals(0x8003, write(WhiteBalanceMode.FLUORESCENT_3).values.getValue(0xD199).u16(0))
        assertEquals(0x8002, write(WhiteBalanceMode.FLUORESCENT_2).values.getValue(0xD199).u16(0))
        assertEquals(0x8001, write(WhiteBalanceMode.FLUORESCENT_1).values.getValue(0xD199).u16(0))
        assertEquals(0x8006, write(WhiteBalanceMode.SHADE).values.getValue(0xD199).u16(0))
        assertEquals(0x8020, write(WhiteBalanceMode.AUTO_WHITE_PRIORITY).values.getValue(0xD199).u16(0))
        assertEquals(0x8021, write(WhiteBalanceMode.AUTO_AMBIENCE_PRIORITY).values.getValue(0xD199).u16(0))
        assertEquals(emptyList<String>(), write(WhiteBalanceMode.FLUORESCENT_3).notes)
    }
}
