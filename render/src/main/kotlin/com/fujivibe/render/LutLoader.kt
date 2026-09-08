package com.fujivibe.render

/** Resolves a [FilmSimulation] to its parsed [Cube3DLut]. */
fun interface LutLoader {
    fun load(filmSimulation: FilmSimulation): Cube3DLut
}
