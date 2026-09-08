package com.fujivibe.render

/** Parses the Adobe `.cube` 3D LUT text format into an in-memory [Cube3DLut]. */
object CubeLutParser {

    private val dataLineSplitter = Regex("\\s+")

    fun parse(text: String): Cube3DLut {
        var size = -1
        val values = mutableListOf<Float>()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("LUT_3D_SIZE")) {
                val sizeText = line.substringAfter("LUT_3D_SIZE").trim()
                size = sizeText.toIntOrNull() ?: error("Invalid LUT_3D_SIZE value: \"$sizeText\"")
                continue
            }

            // Other metadata keywords (TITLE, DOMAIN_MIN/MAX, LUT_1D_SIZE, ...) aren't
            // present in this pack's files; anything starting with a letter is ignored
            // as unsupported metadata rather than treated as a data line.
            if (line.first().isLetter()) continue

            val parts = line.split(dataLineSplitter)
            check(parts.size == 3) { "Malformed .cube data line: \"$rawLine\"" }
            values += parts[0].toFloat()
            values += parts[1].toFloat()
            values += parts[2].toFloat()
        }

        check(size > 0) { "Missing or invalid LUT_3D_SIZE" }
        val expectedValueCount = size * size * size * 3
        check(values.size == expectedValueCount) {
            "Expected $expectedValueCount values for LUT_3D_SIZE $size, found ${values.size}"
        }

        return Cube3DLut(size, values.toFloatArray())
    }
}
