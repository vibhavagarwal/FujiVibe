package com.fujivibe.camera

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PtpConnectionTest {

    /** A pipe that records what is written and serves queued bytes, split into transfers of at most the size asked for. */
    private class FakePipe(private val incoming: List<ByteArray> = emptyList()) : UsbPipe {
        val writes = mutableListOf<ByteArray>()
        private val queue = ArrayDeque(incoming)

        override fun write(buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
            writes += buffer.copyOfRange(offset, offset + length)
            return length
        }

        override fun read(buffer: ByteArray, length: Int, timeoutMs: Int): Int {
            val next = queue.removeFirstOrNull() ?: return -1
            val n = minOf(length, next.size)
            next.copyInto(buffer, 0, 0, n)
            if (n < next.size) queue.addFirst(next.copyOfRange(n, next.size))
            return n
        }
    }

    private fun response(code: Int, id: Int, vararg params: Int) =
        containerHead(PtpType.RESPONSE, code, id, params, 0)

    private fun data(op: Int, id: Int, payload: ByteArray) =
        containerHead(PtpType.DATA, op, id, IntArray(0), payload.size) + payload

    @Test
    fun `a command is sent and its response read`() {
        val pipe = FakePipe(listOf(response(PtpResp.OK, 0)))

        val reply = PtpConnection(pipe).command(PtpOp.OPEN_SESSION, 1)

        assertTrue(reply.ok)
        assertArrayEquals(containerHead(PtpType.COMMAND, PtpOp.OPEN_SESSION, 0, intArrayOf(1), 0), pipe.writes.single())
    }

    @Test
    fun `transaction ids count up, and restart after a reset`() {
        val pipe = FakePipe(listOf(response(PtpResp.OK, 0), response(PtpResp.OK, 1), response(PtpResp.OK, 0)))
        val ptp = PtpConnection(pipe)

        ptp.command(PtpOp.OPEN_SESSION, 1)
        ptp.command(PtpOp.GET_DEVICE_INFO)
        ptp.resetTransactions()
        ptp.command(PtpOp.OPEN_SESSION, 1)

        assertEquals(listOf(0, 1, 0), pipe.writes.map { it.u32(8) })
    }

    @Test
    fun `a data phase before the response is returned with it`() {
        val payload = ByteArray(20) { it.toByte() }
        val pipe = FakePipe(listOf(data(PtpOp.GET_DEVICE_INFO, 0, payload), response(PtpResp.OK, 0)))

        val reply = PtpConnection(pipe).command(PtpOp.GET_DEVICE_INFO)

        assertArrayEquals(payload, reply.data)
    }

    @Test
    fun `a data phase larger than one transfer is reassembled`() {
        val payload = ByteArray(100_000) { (it * 7).toByte() }
        val pipe = FakePipe(listOf(data(PtpOp.GET_OBJECT, 0, payload), response(PtpResp.OK, 0)))

        val reply = PtpConnection(pipe).command(PtpOp.GET_OBJECT, 1)

        assertArrayEquals(payload, reply.data)
    }

    @Test
    fun `sending data puts the header and the start of the data in the first write`() {
        val payload = ByteArray(40_000) { it.toByte() }
        val pipe = FakePipe(listOf(response(PtpResp.OK, 0)))

        PtpConnection(pipe).commandWithData(FujiOp.SEND_OBJECT2, IntArray(0), payload)

        // Command, then the data container: header joined to the first chunk, not sent alone.
        val dataWrites = pipe.writes.drop(1)
        assertEquals(PtpConnection.CHUNK, dataWrites.first().size)
        assertEquals(PtpType.DATA, dataWrites.first().u16(4))
        assertEquals(40_000 + PTP_HEADER_SIZE, dataWrites.sumOf { it.size })
        assertArrayEquals(payload, dataWrites.reduce { a, b -> a + b }.copyOfRange(PTP_HEADER_SIZE, 40_000 + PTP_HEADER_SIZE))
    }

    @Test
    fun `a container that is an exact multiple of the packet size is followed by an empty packet`() {
        val pipe = FakePipe(listOf(response(PtpResp.OK, 0)))
        // 512 bytes in all: 12 of header and 500 of data.
        PtpConnection(pipe, maxPacketSize = 512).commandWithData(FujiOp.SEND_OBJECT2, IntArray(0), ByteArray(500))

        assertEquals(0, pipe.writes.last().size)
    }

    @Test
    fun `a failed read is reported, not mistaken for an empty response`() {
        val pipe = FakePipe(emptyList())

        assertThrows(PtpException::class.java) { PtpConnection(pipe).command(PtpOp.GET_DEVICE_INFO) }
    }
}
