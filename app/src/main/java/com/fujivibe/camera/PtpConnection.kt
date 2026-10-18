package com.fujivibe.camera

/** The two bulk endpoints of a camera's PTP interface; [write] and [read] return bytes moved, or a negative number on failure or timeout. */
interface UsbPipe {
    fun write(buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int

    /** Reads into the start of [buffer]; a transfer ends on a short packet, so it may return less than [length]. */
    fun read(buffer: ByteArray, length: Int, timeoutMs: Int): Int
}

class PtpException(message: String) : Exception(message)

class PtpResponse(val code: Int, val params: IntArray, val data: ByteArray) {
    val ok get() = code == PtpResp.OK
}

/**
 * One PTP conversation over a [UsbPipe]: a command, an optional data phase either way, then the
 * camera's response. Everything is blocking, so call it off the main thread.
 *
 * [maxPacketSize] is the bulk endpoint's packet size. A container whose length is an exact
 * multiple of it is followed by an empty packet, or the other side cannot tell it has ended.
 */
class PtpConnection(
    private val pipe: UsbPipe,
    private val maxPacketSize: Int = 512,
) {
    private var transactionId = 0

    /** A new session numbers its transactions from 0 again. */
    fun resetTransactions() {
        transactionId = 0
    }

    fun command(op: Int, vararg params: Int, timeoutMs: Int = DEFAULT_TIMEOUT_MS): PtpResponse {
        val id = transactionId++
        send(PtpType.COMMAND, op, id, params, EMPTY, timeoutMs)
        var data = EMPTY
        var reply = receive(timeoutMs)
        if (reply.type == PtpType.DATA) {
            data = reply.payload
            reply = receive(timeoutMs)
        }
        if (reply.type != PtpType.RESPONSE) throw PtpException("Expected a response to ${hex(op)}, got container type ${reply.type}")
        return PtpResponse(reply.code, reply.params, data)
    }

    fun commandWithData(op: Int, params: IntArray, data: ByteArray, timeoutMs: Int = DEFAULT_TIMEOUT_MS): PtpResponse {
        val id = transactionId++
        send(PtpType.COMMAND, op, id, params, EMPTY, timeoutMs)
        send(PtpType.DATA, op, id, IntArray(0), data, timeoutMs)
        val reply = receive(timeoutMs)
        if (reply.type != PtpType.RESPONSE) throw PtpException("Expected a response to ${hex(op)}, got container type ${reply.type}")
        return PtpResponse(reply.code, reply.params, EMPTY)
    }

    private fun send(type: Int, code: Int, id: Int, params: IntArray, payload: ByteArray, timeoutMs: Int) {
        val head = containerHead(type, code, id, params, payload.size)
        val total = head.size + payload.size
        // The header goes out with the start of the data, not alone: a short packet by itself
        // would read as the end of the container.
        val firstLength = minOf(total, CHUNK)
        val first = ByteArray(firstLength)
        head.copyInto(first)
        payload.copyInto(first, head.size, 0, firstLength - head.size)
        writeAll(first, 0, firstLength, timeoutMs)
        var sent = firstLength - head.size
        while (sent < payload.size) {
            val length = minOf(CHUNK, payload.size - sent)
            writeAll(payload, sent, length, timeoutMs)
            sent += length
        }
        if (total % maxPacketSize == 0) writeAll(EMPTY, 0, 0, timeoutMs)
    }

    private fun writeAll(buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int) {
        var done = 0
        do {
            val moved = pipe.write(buffer, offset + done, length - done, timeoutMs)
            if (moved < 0) throw PtpException("USB write failed after $done of $length bytes")
            done += moved
        } while (done < length)
    }

    private class Container(val type: Int, val code: Int, val params: IntArray, val payload: ByteArray)

    private fun receive(timeoutMs: Int): Container {
        val chunk = ByteArray(CHUNK)
        var moved = pipe.read(chunk, CHUNK, timeoutMs)
        if (moved < PTP_HEADER_SIZE) throw PtpException("USB read failed or timed out (got $moved bytes)")
        val total = chunk.u32(0)
        if (total < PTP_HEADER_SIZE || total > MAX_CONTAINER) throw PtpException("Implausible container length $total")
        val bytes = ByteArray(total)
        var have = minOf(moved, total)
        chunk.copyInto(bytes, 0, 0, have)
        var lastWasFull = moved == CHUNK
        while (have < total) {
            moved = pipe.read(chunk, CHUNK, timeoutMs)
            if (moved <= 0) throw PtpException("USB read stalled at $have of $total bytes")
            val take = minOf(moved, total - have)
            chunk.copyInto(bytes, have, 0, take)
            have += take
            lastWasFull = moved == CHUNK
        }
        // A read ends on a short packet. If the last one filled the buffer exactly, the empty
        // packet that closes an exact-multiple container is still waiting, so clear it.
        if (total % maxPacketSize == 0 && lastWasFull) pipe.read(chunk, CHUNK, ZLP_DRAIN_MS)

        val type = bytes.u16(4)
        val code = bytes.u16(6)
        return if (type == PtpType.RESPONSE) {
            val count = minOf(5, (total - PTP_HEADER_SIZE) / 4)
            Container(type, code, IntArray(count) { bytes.u32(PTP_HEADER_SIZE + it * 4) }, EMPTY)
        } else {
            Container(type, code, IntArray(0), bytes.copyOfRange(PTP_HEADER_SIZE, total))
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000

        /** Android's bulkTransfer is happiest well under a megabyte; this is a size every device takes. */
        const val CHUNK = 16 * 1024
        private const val MAX_CONTAINER = 256 * 1024 * 1024
        private const val ZLP_DRAIN_MS = 50
        private val EMPTY = ByteArray(0)
    }
}

fun hex(value: Int): String = "0x" + value.toString(16).uppercase()
