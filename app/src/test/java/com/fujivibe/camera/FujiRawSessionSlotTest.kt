package com.fujivibe.camera

import com.fujivibe.recipe.DynamicRange
import com.fujivibe.recipe.Grain
import com.fujivibe.recipe.GrainSize
import com.fujivibe.recipe.GrainStrength
import com.fujivibe.recipe.Level
import com.fujivibe.recipe.MonochromaticColor
import com.fujivibe.recipe.Recipe
import com.fujivibe.recipe.RecipeKind
import com.fujivibe.recipe.RecipeSettings
import com.fujivibe.recipe.WhiteBalance
import com.fujivibe.recipe.WhiteBalanceMode
import com.fujivibe.recipe.toSlotValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Writes recipes into a fake X-T5 that has the quirks found on the real one (2026-10-18): the
 * temperature is only accepted in kelvin mode, a colour simulation refuses the monochrome colour
 * settings and a black-and-white one refuses Color, and changing the white balance mode resets the shifts.
 */
class FujiRawSessionSlotTest {

    private fun u16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())

    private class FakeFuji : UsbPipe {
        val slots = Array(8) { mutableMapOf<Int, ByteArray>() }
        var selected = 7
        val setLog = mutableListOf<Int>()
        private val out = ArrayDeque<ByteArray>()
        private var pendingSet: Pair<Int, Int>? = null

        private fun u16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        private fun slot() = slots[selected]
        fun value(code: Int): Int = slot()[code]?.u16(0) ?: 0

        init {
            for (n in 1..7) slots[n].putAll(
                mapOf(
                    0xD18D to byteArrayOf(1, 0, 0), 0xD18E to u16(7), 0xD18F to u16(4), 0xD190 to u16(100), 0xD191 to u16(0),
                    0xD192 to u16(1), 0xD193 to u16(0), 0xD194 to u16(0), 0xD195 to u16(6), 0xD196 to u16(1), 0xD197 to u16(1),
                    0xD198 to u16(1), 0xD199 to u16(2), 0xD19A to u16(0), 0xD19B to u16(0), 0xD19C to u16(10000),
                    0xD19D to u16(0), 0xD19E to u16(0), 0xD19F to u16(0), 0xD1A0 to u16(0), 0xD1A1 to u16(8192),
                    0xD1A2 to u16(0), 0xD1A3 to u16(1), 0xD1A4 to u16(1), 0xD1A5 to u16(7),
                ),
            )
        }

        private fun isMono(film: Int) = film in 0x06..0x0A || film in 0x0C..0x0F

        /** Applies a write the way the camera does; returns the response code. */
        private fun set(code: Int, data: ByteArray): Int {
            setLog += code
            if (code == 0xD18C) { selected = data.u16(0); return PtpResp.OK }
            val value = if (data.size == 2) data.u16(0) else -1
            when (code) {
                0xD19C -> if (value(0xD199) != 0x8007) return PtpResp.INVALID_DEVICE_PROP_VALUE
                0xD19F -> if (isMono(value(0xD192))) return PtpResp.INVALID_DEVICE_PROP_VALUE
                0xD193, 0xD194 -> if (!isMono(value(0xD192))) return PtpResp.INVALID_DEVICE_PROP_VALUE
                0xD199 -> if (value != value(0xD199)) { slot()[0xD19A] = u16(0); slot()[0xD19B] = u16(0) }
            }
            slot()[code] = data
            return PtpResp.OK
        }

        private fun value(code: Int, fromSlot: Boolean): Int = value(code)

        override fun write(buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
            if (length == 0) return 0
            val b = buffer.copyOfRange(offset, offset + length)
            val type = b.u16(4)
            val op = b.u16(6)
            val tid = b.u32(8)
            if (type == PtpType.COMMAND) {
                val param = if (b.size >= 16) b.u32(12) else 0
                when (op) {
                    PtpOp.GET_DEVICE_PROP_VALUE -> {
                        val data = if (param == 0xD18C) u16(selected) else slot()[param] ?: u16(0)
                        out += containerHead(PtpType.DATA, op, tid, IntArray(0), data.size) + data
                        out += containerHead(PtpType.RESPONSE, PtpResp.OK, tid, IntArray(0), 0)
                    }
                    PtpOp.SET_DEVICE_PROP_VALUE -> pendingSet = param to tid
                    else -> out += containerHead(PtpType.RESPONSE, PtpResp.OK, tid, IntArray(0), 0)
                }
            } else if (type == PtpType.DATA) {
                val (code, setTid) = pendingSet!!
                pendingSet = null
                val response = set(code, b.copyOfRange(PTP_HEADER_SIZE, b.size))
                out += containerHead(PtpType.RESPONSE, response, setTid, IntArray(0), 0)
            }
            return length
        }

        override fun read(buffer: ByteArray, length: Int, timeoutMs: Int): Int {
            val next = out.removeFirstOrNull() ?: return -1
            next.copyInto(buffer)
            return next.size
        }
    }

    private fun session(camera: FakeFuji) = FujiRawSession(PtpConnection(camera))

    private fun recipe(sim: FujiFilmSim, s: RecipeSettings) =
        Recipe(id = "r", name = "R", kind = RecipeKind.OWN, baseSimulation = sim, settings = s)

    private val pacific = recipe(
        FujiFilmSim.CLASSIC_NEG,
        RecipeSettings(
            dynamicRange = DynamicRange.DR400, grain = Grain(GrainStrength.STRONG, GrainSize.LARGE), colorChrome = Level.STRONG,
            colorChromeFxBlue = Level.WEAK, whiteBalance = WhiteBalance(WhiteBalanceMode.KELVIN, 5800, 1, -3),
            highlight = -2.0, shadow = 3.0, color = 4.0, sharpness = -2.0, highIsoNr = -4, clarity = -3.0,
        ),
    )

    private val newspaper = recipe(
        FujiFilmSim.ACROS_YE,
        RecipeSettings(
            grain = Grain(GrainStrength.STRONG, GrainSize.SMALL), whiteBalance = WhiteBalance(null, null, -4, -3),
            highlight = 4.0, shadow = 4.0, sharpness = 2.0, highIsoNr = 0, clarity = 0.0,
        ),
    )

    private fun write(s: FujiRawSession, camera: FakeFuji, r: Recipe, slot: Int): SlotWriteResult {
        val current = s.readSlots()[slot - 1]
        return s.writeSlot(slot, r.toSlotValues(current).values, current.props)
    }

    @Test
    fun `a kelvin recipe lands in a fresh slot with its temperature and shifts`() {
        val camera = FakeFuji()
        val s = session(camera)

        val result = write(s, camera, pacific, 7)

        assertTrue(result.ok, "rejected ${result.rejected} mismatches ${result.mismatches}")
        camera.selected = 7
        assertEquals(0x8007, camera.value(0xD199))
        assertEquals(5800, camera.value(0xD19C))
        assertEquals(1, camera.value(0xD19A))
        assertEquals(-3 and 0xFFFF, camera.value(0xD19B))
    }

    @Test
    fun `changing the white balance mode does not lose a shift that matched before`() {
        val camera = FakeFuji()
        val s = session(camera)
        write(s, camera, pacific, 7) // kelvin, R+1 B-3

        // Newspaper keeps B at -3 but moves to Auto, which resets the shifts on the camera.
        val result = write(s, camera, newspaper, 7)

        assertTrue(result.ok, "rejected ${result.rejected} mismatches ${result.mismatches}")
        camera.selected = 7
        assertEquals(0x0002, camera.value(0xD199))
        assertEquals(-4 and 0xFFFF, camera.value(0xD19A))
        assertEquals(-3 and 0xFFFF, camera.value(0xD19B))
    }

    @Test
    fun `a black-and-white recipe over a colour one is not refused`() {
        val camera = FakeFuji()
        val s = session(camera)
        write(s, camera, pacific, 6)

        val result = write(s, camera, newspaper.copy(settings = newspaper.settings.copy(monochromaticColor = MonochromaticColor(-1, 0))), 6)

        assertEquals(emptyList<String>(), result.rejected)
        assertTrue(result.mismatches.isEmpty(), result.mismatches.toString())
    }

    @Test
    fun `a colour recipe over a black-and-white one is not refused either`() {
        val camera = FakeFuji()
        val s = session(camera)
        write(s, camera, newspaper, 5)

        val result = write(s, camera, pacific, 5)

        assertEquals(emptyList<String>(), result.rejected)
        assertTrue(result.ok, result.mismatches.toString())
    }

    @Test
    fun `the temperature is not written outside the kelvin mode`() {
        val camera = FakeFuji()
        val s = session(camera)

        write(s, camera, newspaper, 4)

        assertTrue(0xD19C !in camera.setLog)
        assertTrue(0xD19F !in camera.setLog)
    }

    @Test
    fun `the slot that was selected before is selected again`() {
        val camera = FakeFuji().also { it.selected = 3 }
        val s = session(camera)

        write(s, camera, pacific, 6)

        assertEquals(3, camera.selected)
    }
}
