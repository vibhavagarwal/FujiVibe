package com.fujivibe.camera

/**
 * What one custom slot (C1 to C7) held when it was read: its name and the raw bytes of each setting.
 * Kept raw so a backup can put back exactly what was there, whatever the encoding turns out to be.
 */
class SlotDump(val number: Int, val props: Map<Int, ByteArray>) {

    val name: String
        get() = props[FujiProp.SLOT_NAME]?.takeIf { it.isNotEmpty() }?.let { runCatching { PtpReader(it).string() }.getOrDefault("?") }.orEmpty()

    /** A setting as the number it holds, when it is a 16-bit value; null when it is something else. */
    fun value(code: Int): Int? = props[code]?.takeIf { it.size == 2 }?.u16(0)

    /** One line for a log: the slot, its name, and each setting as a number (or hex if it is not 2 bytes). */
    fun describe(): String = buildString {
        append("C$number \"$name\":")
        for (code in FujiProp.SLOT_FIRST_SETTING..FujiProp.SLOT_LAST_SETTING) {
            append(' ').append(Integer.toHexString(code).uppercase()).append('=')
            val bytes = props[code]
            append(value(code)?.toString() ?: bytes?.joinToString("") { "%02x".format(it) } ?: "-")
        }
    }

    /** All properties as `code=hex` pairs on one line, for a backup file. */
    fun backupLine(): String = "slot $number " + props.entries.joinToString(" ") {
        "${Integer.toHexString(it.key).uppercase()}=${it.value.joinToString("") { b -> "%02x".format(b) }}"
    }

    companion object {
        /** Reads a line written by [backupLine]; null if it is not one. */
        fun fromBackupLine(line: String): SlotDump? {
            val parts = line.trim().split(" ")
            if (parts.size < 3 || parts[0] != "slot") return null
            val number = parts[1].toIntOrNull() ?: return null
            val props = linkedMapOf<Int, ByteArray>()
            for (pair in parts.drop(2)) {
                val (code, hex) = pair.split("=", limit = 2).takeIf { it.size == 2 } ?: return null
                val c = code.toIntOrNull(16) ?: return null
                if (hex.length % 2 != 0) return null
                props[c] = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
            }
            return SlotDump(number, props)
        }
    }
}


/** What a slot write did: settings the camera refused, and settings that did not read back as written. */
class SlotWriteResult(val written: Int, val rejected: List<String>, val mismatches: List<String>) {
    val ok get() = rejected.isEmpty() && mismatches.isEmpty()
}

/** The part of the camera the slot loader needs, so it can be tested without one. */
interface SlotCamera {
    fun readSlots(): List<SlotDump>

    /** Writes [values] into slot [number], skipping what [current] already holds; see [FujiRawSession.writeSlot]. */
    fun writeSlot(number: Int, values: Map<Int, ByteArray>, current: Map<Int, ByteArray> = emptyMap()): SlotWriteResult
}
