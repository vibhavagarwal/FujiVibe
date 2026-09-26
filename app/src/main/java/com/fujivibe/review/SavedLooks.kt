package com.fujivibe.review

import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.RenderSelection

/**
 * The looks already Exported from the current Capture during this Review. A saved look can't be
 * Exported again (no duplicate gallery files), and once anything is saved the closing action reads
 * "Done" rather than "Discard". Immutable, like [ReviewCycle].
 */
class SavedLooks private constructor(private val looks: Set<RenderSelection>) {

    operator fun contains(selection: RenderSelection): Boolean = selection in looks

    fun canExport(selection: RenderSelection): Boolean = selection !in looks

    operator fun plus(selection: RenderSelection): SavedLooks = SavedLooks(looks + selection)

    val closeActionLabel: String get() = if (looks.isEmpty()) "Discard" else "Done"

    /** Stable string keys for saved-instance state; see [fromKeys]. */
    fun toKeys(): ArrayList<String> = ArrayList(looks.map { it.key() })

    override fun equals(other: Any?): Boolean = other is SavedLooks && looks == other.looks

    override fun hashCode(): Int = looks.hashCode()

    companion object {
        private const val ORIGINAL_KEY = "Original"

        val NONE = SavedLooks(emptySet())

        /** Unknown keys (e.g. a Film Simulation removed since the state was saved) are dropped. */
        fun fromKeys(keys: List<String>): SavedLooks = SavedLooks(
            keys.mapNotNull { key ->
                if (key == ORIGINAL_KEY) {
                    RenderSelection.Original
                } else {
                    FilmSimulation.entries.firstOrNull { it.name == key }?.let { RenderSelection.Simulation(it) }
                }
            }.toSet()
        )

        private fun RenderSelection.key(): String = when (this) {
            RenderSelection.Original -> ORIGINAL_KEY
            is RenderSelection.Simulation -> filmSimulation.name
        }
    }
}
