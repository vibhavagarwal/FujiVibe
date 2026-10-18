package com.fujivibe.camera

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * PTP (Picture Transfer Protocol) pieces the camera conversation needs, with the Fuji vendor
 * codes. The vendor operations and properties were worked out by other people; the layout here
 * follows FilmKit (github.com/eggricesoy/filmkit, MIT) and libfuji (github.com/petabyt/libfuji, MIT).
 */

const val FUJI_VENDOR_ID = 0x04CB

object PtpType {
    const val COMMAND = 1
    const val DATA = 2
    const val RESPONSE = 3
}

object PtpOp {
    const val GET_DEVICE_INFO = 0x1001
    const val OPEN_SESSION = 0x1002
    const val CLOSE_SESSION = 0x1003
    const val GET_OBJECT_HANDLES = 0x1007
    const val GET_OBJECT = 0x1009
    const val DELETE_OBJECT = 0x100B
    const val GET_DEVICE_PROP_VALUE = 0x1015
    const val SET_DEVICE_PROP_VALUE = 0x1016
}

/** Fuji's own versions of send-object, used to hand the camera a RAF. */
object FujiOp {
    const val SEND_OBJECT_INFO = 0x900C
    const val SEND_OBJECT2 = 0x900D
}

object PtpResp {
    const val OK = 0x2001
    const val INVALID_DEVICE_PROP_VALUE = 0x201C
    const val SESSION_ALREADY_OPEN = 0x201E
}

object FujiProp {
    /** Writing 0 here makes the camera render the RAF it was given. */
    const val START_RAW_CONVERSION = 0xD183

    /** The recipe the camera renders with, as one binary block (see [RawConvProfile]). */
    const val RAW_CONV_PROFILE = 0xD185

    /** Writing 1 to 7 here makes that custom slot (C1 to C7) the one the properties below read and write. */
    const val SLOT_SELECT = 0xD18C
    const val SLOT_NAME = 0xD18D

    /** The 24 settings of the selected slot, one property each (names in FilmKit's notes). */
    const val SLOT_FIRST_SETTING = 0xD18E
    const val SLOT_LAST_SETTING = 0xD1A5
}

/** The ObjectFormat the camera insists on for a RAF; anything else makes it fail silently. */
const val FUJI_RAF_FORMAT = 0xF802

const val PTP_HEADER_SIZE = 12

/** A container's header and parameters, ready to send ahead of [payloadSize] bytes of data. */
fun containerHead(type: Int, code: Int, transactionId: Int, params: IntArray, payloadSize: Int): ByteArray {
    require(params.size <= 5) { "A PTP command takes at most 5 parameters" }
    val head = ByteBuffer.allocate(PTP_HEADER_SIZE + params.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    head.putInt(PTP_HEADER_SIZE + params.size * 4 + payloadSize)
    head.putShort(type.toShort())
    head.putShort(code.toShort())
    head.putInt(transactionId)
    params.forEach { head.putInt(it) }
    return head.array()
}

fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

fun ByteArray.u32(at: Int): Int = u16(at) or (u16(at + 2) shl 16)

/** A PTP string: a character count (terminator included), then UTF-16LE text and a terminator; empty is one 0 byte. */
fun packPtpString(text: String): ByteArray {
    if (text.isEmpty()) return byteArrayOf(0)
    val out = ByteBuffer.allocate(1 + (text.length + 1) * 2).order(ByteOrder.LITTLE_ENDIAN)
    out.put((text.length + 1).toByte())
    text.forEach { out.putShort(it.code.toShort()) }
    out.putShort(0)
    return out.array()
}

/** The ObjectInfo that announces a RAF of [size] bytes; the camera ignores everything but format, size and name. */
fun rafObjectInfo(size: Int, fileName: String = "FUP_FILE.dat"): ByteArray {
    val fixed = ByteBuffer.allocate(52).order(ByteOrder.LITTLE_ENDIAN)
    fixed.putInt(0) // StorageID
    fixed.putShort(FUJI_RAF_FORMAT.toShort())
    fixed.putShort(0) // ProtectionStatus
    fixed.putInt(size) // ObjectCompressedSize
    // ThumbFormat, thumb size/width/height, image width/height/depth, parent, association type and
    // description, sequence number: all zero.
    return fixed.array() + packPtpString(fileName) +
        packPtpString("") + packPtpString("") + packPtpString("") // capture date, modification date, keywords
}

/** Reads the little-endian values and strings of a PTP data phase in order. */
class PtpReader(private val bytes: ByteArray, private var pos: Int = 0) {
    fun u8(): Int = bytes[pos++].toInt() and 0xFF

    fun u16(): Int = bytes.u16(pos).also { pos += 2 }

    fun u32(): Int = bytes.u32(pos).also { pos += 4 }

    fun string(): String {
        val count = u8()
        if (count == 0) return ""
        val text = String(CharArray(count - 1) { u16().toChar() })
        u16() // terminator
        return text
    }

    fun u16Array(): IntArray = IntArray(u32()) { u16() }
}

class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val deviceVersion: String,
    val serialNumber: String,
    val operations: Set<Int>,
    val properties: Set<Int>,
)

fun parseDeviceInfo(data: ByteArray): DeviceInfo {
    val r = PtpReader(data)
    r.u16() // StandardVersion
    r.u32() // VendorExtensionID
    r.u16() // VendorExtensionVersion
    r.string() // VendorExtensionDesc
    r.u16() // FunctionalMode
    val operations = r.u16Array().toSet()
    r.u16Array() // EventsSupported
    val properties = r.u16Array().toSet()
    r.u16Array() // CaptureFormats
    r.u16Array() // ImageFormats
    return DeviceInfo(
        manufacturer = r.string(),
        model = r.string(),
        deviceVersion = r.string(),
        serialNumber = r.string(),
        operations = operations,
        properties = properties,
    )
}
