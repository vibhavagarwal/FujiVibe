package com.fujivibe.render

/**
 * What to render a Capture as. Original is the absence of a Film Simulation, not itself an
 * entry in the [FilmSimulation] registry — see CONTEXT.md.
 */
sealed interface RenderSelection {
    data object Original : RenderSelection
    data class Simulation(val filmSimulation: FilmSimulation) : RenderSelection
}
