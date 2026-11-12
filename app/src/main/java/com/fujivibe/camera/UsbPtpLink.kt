package com.fujivibe.camera

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

/** A Fuji camera opened over USB host mode, with its PTP interface claimed. Close it when done. */
class UsbPtpLink private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    val ptp: PtpConnection,
    val description: String,
) : AutoCloseable {

    override fun close() {
        runCatching { connection.releaseInterface(usbInterface) }
        connection.close()
    }

    companion object {
        fun findFuji(usb: UsbManager): UsbDevice? =
            usb.deviceList.values.firstOrNull { it.vendorId == FUJI_VENDOR_ID }

        /** Opens [device] (permission must already be granted) and claims its PTP interface. */
        fun open(usb: UsbManager, device: UsbDevice): UsbPtpLink {
            val choice = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .mapNotNull { iface ->
                    val bulkIn = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
                        .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
                    val bulkOut = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
                        .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
                    if (bulkIn != null && bulkOut != null) Triple(iface, bulkIn, bulkOut) else null
                }
                // The still-image class is PTP; take it if there are several candidates.
                .sortedBy { if (it.first.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) 0 else 1 }
                .firstOrNull() ?: throw PtpException("No interface with bulk in/out endpoints on ${device.deviceName}")
            val (iface, bulkIn, bulkOut) = choice

            val connection = usb.openDevice(device) ?: throw PtpException("Could not open the camera (permission?)")
            if (!connection.claimInterface(iface, true)) {
                connection.close()
                throw PtpException("Could not claim the camera's USB interface")
            }
            val ptp = PtpConnection(AndroidPipe(connection, bulkIn, bulkOut), maxOf(bulkIn.maxPacketSize, bulkOut.maxPacketSize))
            val description = "${device.productName ?: "camera"} " +
                "vid=${hex(device.vendorId)} pid=${hex(device.productId)} " +
                "interface ${iface.id} class ${iface.interfaceClass}, " +
                "bulk packet ${bulkIn.maxPacketSize}"
            return UsbPtpLink(connection, iface, ptp, description)
        }
    }

    private class AndroidPipe(
        private val connection: UsbDeviceConnection,
        private val bulkIn: UsbEndpoint,
        private val bulkOut: UsbEndpoint,
    ) : UsbPipe {
        override fun write(buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int) =
            connection.bulkTransfer(bulkOut, buffer, offset, length, timeoutMs)

        override fun read(buffer: ByteArray, length: Int, timeoutMs: Int) =
            connection.bulkTransfer(bulkIn, buffer, 0, length, timeoutMs)
    }
}
