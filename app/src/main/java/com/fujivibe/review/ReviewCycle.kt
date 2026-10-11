package com.fujivibe.review

import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.RenderSelection

/** How a look is named on screen: "Original", or the Film Simulation's display name. */
fun RenderSelection.displayLabel(): String = when (this) {
    RenderSelection.Original -> "Original"
    is RenderSelection.Simulation -> filmSimulation.displayName
}

/** The acronym for a look: "O" for Original, else the Film Simulation's short name (e.g. "CN"). */
fun RenderSelection.shortLabel(): String = when (this) {
    RenderSelection.Original -> "O"
    is RenderSelection.Simulation -> filmSimulation.shortName
}

/**
 * Tracks which entry of Review's ordered cycle — Original, then each launch [FilmSimulation] in
 * registry order — is currently shown. Immutable: [next] and [previous] return the new state
 * rather than mutating in place, so it drops straight into Compose's `remember`/state model.
 */
class ReviewCycle private constructor(private val index: Int) {

    val current: RenderSelection get() = ENTRIES[index]

    /** Zero-based place in the cycle; with [count], drives the position dots and saved state. */
    val position: Int get() = index

    val count: Int get() = ENTRIES.size

    val label: String get() = current.displayLabel()

    fun next(): ReviewCycle = ReviewCycle((index + 1) % ENTRIES.size)

    fun previous(): ReviewCycle = ReviewCycle((index - 1 + ENTRIES.size) % ENTRIES.size)

    override fun equals(other: Any?): Boolean = other is ReviewCycle && index == other.index

    override fun hashCode(): Int = index

    companion object {
        private val ENTRIES: List<RenderSelection> =
            listOf(RenderSelection.Original) + FilmSimulation.entries.map { RenderSelection.Simulation(it) }

        /** Every new Capture opens Review here, regardless of what was showing last time. */
        fun start(): ReviewCycle = ReviewCycle(0)

        /** Restores a cycle from a saved [position]; an out-of-range value falls back to [start]. */
        fun at(position: Int): ReviewCycle =
            if (position in ENTRIES.indices) ReviewCycle(position) else start()
    }
}
