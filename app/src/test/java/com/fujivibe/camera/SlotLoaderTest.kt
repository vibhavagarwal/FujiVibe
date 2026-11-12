package com.fujivibe.camera

import com.fujivibe.recipe.DynamicRange
import com.fujivibe.recipe.Grain
import com.fujivibe.recipe.GrainSize
import com.fujivibe.recipe.GrainStrength
import com.fujivibe.recipe.Recipe
import com.fujivibe.recipe.RecipeKind
import com.fujivibe.recipe.RecipeSettings
import com.fujivibe.recipe.slotName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SlotLoaderTest {

    @TempDir
    lateinit var dir: File

    private fun u16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())

    private fun slot(n: Int, name: String) = SlotDump(
        n,
        linkedMapOf(
            FujiProp.SLOT_NAME to packPtpString(name),
            0xD18E to u16(7), 0xD18F to u16(4), 0xD190 to u16(100), 0xD192 to u16(1), 0xD195 to u16(6),
        ),
    )

    private class FakeCamera(val slots: List<SlotDump>, val failOn: Int? = null, val refuse: List<String> = emptyList()) : SlotCamera {
        val writes = mutableListOf<Int>()
        override fun readSlots() = slots
        override fun writeSlot(number: Int, values: Map<Int, ByteArray>, current: Map<Int, ByteArray>): SlotWriteResult {
            if (number == failOn) throw PtpException("USB write failed")
            writes += number
            return SlotWriteResult(values.size, refuse, emptyList())
        }
    }

    private fun recipe(name: String) = Recipe(
        id = name, name = name, kind = RecipeKind.OWN, baseSimulation = FujiFilmSim.CLASSIC_CHROME,
        settings = RecipeSettings(dynamicRange = DynamicRange.DR200, grain = Grain(GrainStrength.WEAK, GrainSize.SMALL)),
    )

    private val slots = listOf(slot(1, "DEFAULT"), slot(2, "PORTRA"), slot(3, ""), slot(4, ""), slot(5, ""), slot(6, ""), slot(7, ""))

    @Test
    fun `only the planned slots are written, in order, and C1 never`() {
        val camera = FakeCamera(slots)

        val report = SlotLoader(camera, dir).load(mapOf(5 to recipe("Five"), 2 to recipe("Two")))

        assertEquals(listOf(2, 5), camera.writes)
        assertEquals(listOf(2, 5), report.outcomes.map { it.slot })
        assertTrue(report.allLoaded)
    }

    @Test
    fun `C1 cannot be planned`() {
        assertThrows(IllegalArgumentException::class.java) { SlotLoader(FakeCamera(slots), dir).load(mapOf(1 to recipe("X"))) }
    }

    @Test
    fun `all seven slots are backed up before anything is written, and the file can be read back`() {
        val report = SlotLoader(FakeCamera(slots), dir).load(mapOf(3 to recipe("Three")))

        val lines = report.backup.readLines().mapNotNull { SlotDump.fromBackupLine(it) }
        assertEquals((1..7).toList(), lines.map { it.number })
        assertEquals("PORTRA", lines[1].name)
    }

    @Test
    fun `two loads in the same second keep separate backups`() {
        val loader = SlotLoader(FakeCamera(slots), dir)

        val a = loader.load(mapOf(3 to recipe("A")))
        val b = loader.load(mapOf(3 to recipe("A")))

        assertFalse(a.backup == b.backup)
    }

    @Test
    fun `a failure is reported, and stops the slots after it`() {
        val camera = FakeCamera(slots, failOn = 3)

        val report = SlotLoader(camera, dir).load(mapOf(2 to recipe("Two"), 3 to recipe("Three"), 4 to recipe("Four")))

        assertEquals(listOf(SlotStatus.LOADED, SlotStatus.FAILED, SlotStatus.NOT_ATTEMPTED), report.outcomes.map { it.status })
        assertEquals("USB write failed", report.outcomes[1].detail)
        assertEquals(listOf(2), camera.writes)
        assertFalse(report.allLoaded)
    }

    @Test
    fun `settings the camera refuses, and settings that cannot be written, come back as notes`() {
        val camera = FakeCamera(slots, refuse = listOf("0xD193 (wanted 0000)"))
        val r = recipe("Mono").copy(settings = RecipeSettings(highIsoNr = -2))

        val outcome = SlotLoader(camera, dir).load(mapOf(4 to r)).outcomes.single()

        assertEquals(SlotStatus.LOADED_WITH_NOTES, outcome.status)
        assertTrue(outcome.notes.any { it.contains("High ISO NR -2") })
        assertTrue(outcome.notes.any { it.contains("refused 0xD193") })
    }

    @Test
    fun `restore puts C2 to C7 back from a backup and leaves C1`() {
        val backup = SlotLoader(FakeCamera(slots), dir).load(mapOf(2 to recipe("Two"))).backup
        val camera = FakeCamera(slots)

        val report = SlotLoader(camera, dir).restore(backup)

        assertEquals(listOf(2, 3, 4, 5, 6, 7), camera.writes)
        assertEquals("PORTRA", report.outcomes.first().recipeName)
        assertEquals("(empty)", report.outcomes[1].recipeName)
    }

    @Test
    fun `the newest backup is found, and none gives null`() {
        val loader = SlotLoader(FakeCamera(slots), dir)
        assertNull(loader.latestBackup())

        File(dir, "slots-backup-20260101-000000.txt").writeText("x")
        val newer = File(dir, "slots-backup-20261018-120000.txt").also { it.writeText("x") }

        assertEquals(newer, loader.latestBackup())
    }

    @Test
    fun `a name that would replace another is reported, but not one that is empty or the same recipe`() {
        val portra = recipe("Portra")
        assertEquals("", replacedName(slot(3, ""), portra))
        assertEquals("", replacedName(slot(3, portra.slotName()), portra))
        assertEquals("OLD", replacedName(slot(3, "OLD"), portra))
    }
}
