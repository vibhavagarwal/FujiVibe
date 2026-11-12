package com.fujivibe.ui.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConnectionChecklistTest {
    private fun states(stage: ConnectionStage) = checklist(stage).map { it.state }

    @Test
    fun `nothing is checked before the first try`() {
        assertEquals(List(3) { CheckState.TODO }, states(ConnectionStage.NOT_TRIED))
    }

    @Test
    fun `no camera on USB is a problem at the first step, with help`() {
        assertEquals(listOf(CheckState.PROBLEM, CheckState.TODO, CheckState.TODO), states(ConnectionStage.NO_CAMERA))
        assertNotNull(checklist(ConnectionStage.NO_CAMERA)[0].help)
    }

    @Test
    fun `a refused permission is a problem at the second step`() {
        assertEquals(listOf(CheckState.OK, CheckState.PROBLEM, CheckState.TODO), states(ConnectionStage.PERMISSION_REFUSED))
    }

    @Test
    fun `a camera that does not answer points at its USB mode`() {
        assertEquals(listOf(CheckState.OK, CheckState.OK, CheckState.PROBLEM), states(ConnectionStage.NOT_ANSWERING))
        assertNotNull(checklist(ConnectionStage.NOT_ANSWERING)[2].help)
    }

    @Test
    fun `connected is all ok and needs no help`() {
        assertEquals(List(3) { CheckState.OK }, states(ConnectionStage.CONNECTED))
        checklist(ConnectionStage.CONNECTED).forEach { assertNull(it.help) }
    }

    @Test
    fun `steps in progress show as working`() {
        assertEquals(CheckState.WORKING, checklist(ConnectionStage.LOOKING)[0].state)
        assertEquals(CheckState.WORKING, checklist(ConnectionStage.ASKING_PERMISSION)[1].state)
        assertEquals(CheckState.WORKING, checklist(ConnectionStage.OPENING)[2].state)
    }
}
