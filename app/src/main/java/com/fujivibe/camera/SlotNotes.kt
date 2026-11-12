package com.fujivibe.camera

/**
 * The loader's notes are written for logs ("The camera refused 0xD190 (wanted ffff)"); this turns them
 * into plain sentences for the screen. A note it does not know is passed through unchanged.
 */
object SlotNotes {
    private val names = mapOf(
        0xD18D to "Slot name",
        0xD190 to "Dynamic Range",
        0xD191 to "D Range Priority",
        0xD192 to "Film Simulation",
        0xD193 to "B&W warm-cool tint",
        0xD194 to "B&W magenta-green tint",
        0xD195 to "Grain",
        0xD196 to "Color Chrome Effect",
        0xD197 to "Color Chrome FX Blue",
        0xD198 to "Smooth Skin",
        0xD199 to "White balance",
        0xD19A to "White balance red shift",
        0xD19B to "White balance blue shift",
        0xD19C to "White balance temperature",
        0xD19D to "Highlight tone",
        0xD19E to "Shadow tone",
        0xD19F to "Color",
        0xD1A0 to "Sharpness",
        0xD1A1 to "High ISO noise reduction",
        0xD1A2 to "Clarity",
    )

    private val refused = Regex("""^The camera refused (?:0x)?([0-9A-Fa-f]{4})\b.*""")
    private val notKept = Regex("""^Did not read back: (?:0x)?([0-9A-Fa-f]{4})\b.*""")

    fun plain(note: String): String {
        refused.matchEntire(note)?.let { return "The camera would not accept ${nameOf(it.groupValues[1])}." }
        notKept.matchEntire(note)?.let { return "${nameOf(it.groupValues[1])} did not stay as written." }
        return when {
            note == "Monochromatic colour position is unconfirmed" ->
                "The black-and-white tint was written but has not been checked on a real camera yet."
            note == "Monochromatic colour only applies to a black-and-white simulation" ->
                "The recipe has a black-and-white tint, which a colour simulation cannot use. Skipped."
            note == "White balance mode not given; Auto used" ->
                "The recipe gives no white balance mode, so Auto is used."
            note.startsWith("High ISO NR ") && note.endsWith(" is not written (left at 0)") ->
                "High ISO noise reduction ${note.removePrefix("High ISO NR ").removeSuffix(" is not written (left at 0)")} is not a value the app can write. It is left at 0; set it on the camera if you want it."
            else -> note
        }
    }

    private fun nameOf(hexCode: String): String = names[hexCode.toInt(16)] ?: "setting 0x${hexCode.uppercase()}"
}
