package com.fujivibe.render

import java.util.concurrent.ConcurrentHashMap

/** Loads each [FilmSimulation]'s `.cube` file from the classpath, by [FilmSimulation.cubeResourceName]. */
class ResourceLutLoader(
    private val classLoader: ClassLoader = ResourceLutLoader::class.java.classLoader,
) : LutLoader {

    private val cache = ConcurrentHashMap<FilmSimulation, Cube3DLut>()

    override fun load(filmSimulation: FilmSimulation): Cube3DLut =
        cache.getOrPut(filmSimulation) {
            val resourceName = filmSimulation.cubeResourceName
            val stream = classLoader.getResourceAsStream(resourceName)
                ?: error("Missing .cube resource for ${filmSimulation.displayName}: $resourceName")
            val text = stream.bufferedReader().use { it.readText() }
            CubeLutParser.parse(text)
        }
}
