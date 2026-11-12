package com.fujivibe.camera

/**
 * Has a Fuji camera on the other end of [ptp] develop a RAF into a JPEG: the RAF goes up once,
 * then each render is a recipe written to the camera and a trigger, so trying another look does
 * not send the file again. The camera does the rendering.
 *
 * Blocking; call it off the main thread. [log] gets a line per step for the spike screen.
 */
class FujiRawSession(
    private val ptp: PtpConnection,
    private val log: (String) -> Unit = {},
) : SlotCamera {
    /** The recipe the camera read from the RAF just uploaded, or null before an upload. */
    var baseProfile: RawConvProfile? = null
        private set

    fun open(): DeviceInfo {
        var response = ptp.command(PtpOp.OPEN_SESSION, SESSION_ID)
        if (response.code == PtpResp.SESSION_ALREADY_OPEN) {
            log("Session was already open; closing it and reopening")
            ptp.command(PtpOp.CLOSE_SESSION)
            ptp.resetTransactions()
            response = ptp.command(PtpOp.OPEN_SESSION, SESSION_ID)
        }
        expectOk(response, "OpenSession")
        val info = parseDeviceInfo(expectOk(ptp.command(PtpOp.GET_DEVICE_INFO), "GetDeviceInfo").data)
        log("${info.manufacturer} ${info.model} firmware ${info.deviceVersion}")
        log(
            "Raw conversion: SendObject2 ${yesNo(FujiOp.SEND_OBJECT2 in info.operations)}, " +
                "profile ${yesNo(FujiProp.RAW_CONV_PROFILE in info.properties)}, " +
                "start ${yesNo(FujiProp.START_RAW_CONVERSION in info.properties)}",
        )
        return info
    }

    /** Hands the camera [raf] and reads back the recipe it took from the file. */
    fun upload(raf: ByteArray) {
        baseProfile = null
        expectOk(
            ptp.commandWithData(FujiOp.SEND_OBJECT_INFO, intArrayOf(0, 0, 0), rafObjectInfo(raf.size)),
            "SendObjectInfo",
        )
        expectOk(ptp.commandWithData(FujiOp.SEND_OBJECT2, IntArray(0), raf, UPLOAD_TIMEOUT_MS), "SendObject2")
        val profile = RawConvProfile(
            expectOk(ptp.command(PtpOp.GET_DEVICE_PROP_VALUE, FujiProp.RAW_CONV_PROFILE), "Get profile").data,
        )
        baseProfile = profile
        log("Recipe in the RAF: ${profile.describe()}")
        log("All params: ${profile.dumpAll()}")
    }

    /** Renders the uploaded RAF with [edit] applied to the recipe it came with, and returns the JPEG. */
    fun render(edit: (RawConvProfile) -> RawConvProfile): ByteArray {
        val base = baseProfile ?: throw PtpException("Upload a RAF before rendering")
        val profile = edit(base)
        expectOk(
            ptp.commandWithData(PtpOp.SET_DEVICE_PROP_VALUE, intArrayOf(FujiProp.RAW_CONV_PROFILE), profile.bytes),
            "Set profile",
        )
        expectOk(
            ptp.commandWithData(
                PtpOp.SET_DEVICE_PROP_VALUE,
                intArrayOf(FujiProp.START_RAW_CONVERSION),
                byteArrayOf(0, 0),
            ),
            "Start conversion",
        )
        return waitForResult()
    }

    /** The raw bytes of one device property, for finding out what a property the camera lists holds. */
    fun readProperty(code: Int): ByteArray =
        expectOk(ptp.command(PtpOp.GET_DEVICE_PROP_VALUE, code), "Get property ${hex(code)}").data

    fun writeProperty(code: Int, data: ByteArray) {
        expectOk(
            ptp.commandWithData(PtpOp.SET_DEVICE_PROP_VALUE, intArrayOf(code), data),
            "Set property ${hex(code)}",
        )
    }

    /**
     * Reads all seven custom slots: selects each, reads its name and settings, then selects the slot
     * that was selected before. The only thing written is the slot selector.
     */
    override fun readSlots(): List<SlotDump> {
        val before = readProperty(FujiProp.SLOT_SELECT).u16(0)
        try {
            return (1..SLOT_COUNT).map { number ->
                writeProperty(FujiProp.SLOT_SELECT, byteArrayOf(number.toByte(), 0))
                val props = linkedMapOf<Int, ByteArray>()
                props[FujiProp.SLOT_NAME] = readProperty(FujiProp.SLOT_NAME)
                for (code in FujiProp.SLOT_FIRST_SETTING..FujiProp.SLOT_LAST_SETTING) {
                    props[code] = try {
                        readProperty(code)
                    } catch (e: PtpException) {
                        ByteArray(0) // a property this camera does not answer for
                    }
                }
                SlotDump(number, props)
            }
        } finally {
            runCatching { writeProperty(FujiProp.SLOT_SELECT, byteArrayOf(before.toByte(), (before shr 8).toByte())) }
        }
    }

    /**
     * Writes [values] (property code to bytes) into custom slot [number], skipping any that already
     * equal what [current] holds (an unchanged setting need not be written, and some, such as
     * monochrome colour on a colour simulation, are refused even with their own value). A setting the
     * camera refuses with "invalid value" is reported and the rest carry on. Everything written is read
     * back. The slot selected before is selected again.
     */
    override fun writeSlot(number: Int, values: Map<Int, ByteArray>, current: Map<Int, ByteArray>): SlotWriteResult {
        val before = readProperty(FujiProp.SLOT_SELECT).u16(0)
        try {
            writeProperty(FujiProp.SLOT_SELECT, byteArrayOf(number.toByte(), 0))
            val rejected = mutableListOf<String>()
            var written = 0
            val refused = mutableSetOf<Int>()
            var modeWritten = false
            for ((code, data) in values) {
                // Changing the white balance mode resets its shifts (and its temperature, in kelvin mode), so
                // once the mode has been written they are written too, whether or not they matched before.
                val resets = modeWritten && (code in WB_SHIFTS || (code == WB_TEMPERATURE && values[WB_MODE]?.u16(0) == WB_MODE_KELVIN))
                if (!resets && current[code]?.contentEquals(data) == true) continue
                if (code == WB_MODE) modeWritten = true
                try {
                    writeProperty(code, data)
                    written++
                } catch (e: PtpException) {
                    if (e.responseCode != PtpResp.INVALID_DEVICE_PROP_VALUE) throw e
                    rejected += "${hex(code)} (wanted ${data.toHex()})"
                    refused += code
                }
            }
            // Read everything back only once all the writing is done: writing one setting can change another
            // (a white balance temperature resets the shifts), which a read straight after each write misses.
            val mismatches = values.mapNotNull { (code, data) ->
                // Image size and quality are only kept, not set, and the camera reports its own quality.
                if (code in refused || code in KEPT_SETTINGS) return@mapNotNull null
                val now = readProperty(code)
                if (now.contentEquals(data)) null else "${hex(code)}: wrote ${data.toHex()}, read ${now.toHex()}"
            }
            return SlotWriteResult(written, rejected, mismatches)
        } finally {
            runCatching { writeProperty(FujiProp.SLOT_SELECT, byteArrayOf(before.toByte(), (before shr 8).toByte())) }
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    fun close() {
        runCatching { ptp.command(PtpOp.CLOSE_SESSION) }
    }

    /** The finished JPEG shows up as an object on the camera; take it, then delete it from there. */
    private fun waitForResult(): ByteArray {
        val deadline = System.currentTimeMillis() + RENDER_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val handles = expectOk(
                ptp.command(PtpOp.GET_OBJECT_HANDLES, ALL_STORAGES, 0, 0),
                "GetObjectHandles",
            ).data
            if (handles.size >= 8 && handles.u32(0) > 0) {
                val handle = handles.u32(4)
                val jpeg = expectOk(ptp.command(PtpOp.GET_OBJECT, handle, timeoutMs = UPLOAD_TIMEOUT_MS), "GetObject").data
                runCatching { ptp.command(PtpOp.DELETE_OBJECT, handle) }
                return jpeg
            }
            Thread.sleep(POLL_MS)
        }
        throw PtpException("The camera produced no JPEG within ${RENDER_TIMEOUT_MS / 1000}s")
    }

    private fun expectOk(response: PtpResponse, step: String): PtpResponse {
        if (!response.ok) throw PtpException("$step failed with response ${hex(response.code)}", response.code)
        return response
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "NO"

    private companion object {
        const val WB_MODE = 0xD199
        const val WB_TEMPERATURE = 0xD19C
        const val WB_MODE_KELVIN = 0x8007
        val WB_SHIFTS = setOf(0xD19A, 0xD19B)
        val KEPT_SETTINGS = setOf(0xD18E, 0xD18F)
        const val SLOT_COUNT = 7
        const val SESSION_ID = 1
        const val ALL_STORAGES = -1 // 0xFFFFFFFF
        const val UPLOAD_TIMEOUT_MS = 60_000
        const val RENDER_TIMEOUT_MS = 60_000L
        const val POLL_MS = 300L
    }
}
