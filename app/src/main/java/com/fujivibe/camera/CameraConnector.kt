package com.fujivibe.camera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat

/** An open conversation with the camera: the USB link and the PTP session on it. Close it when done. */
class CameraConnection(val link: UsbPtpLink, val session: FujiRawSession, val info: DeviceInfo) : AutoCloseable {
    override fun close() {
        session.close()
        link.close()
    }
}

/** Finds the X-T5 on USB, asks for permission to use it, and opens it. */
class CameraConnector(context: Context) {
    private val app = context.applicationContext
    private val usb = app.getSystemService(UsbManager::class.java)

    sealed interface Found {
        data object NoCamera : Found
        class NeedsPermission(val device: UsbDevice) : Found
        class Ready(val device: UsbDevice) : Found
    }

    fun find(): Found {
        val device = UsbPtpLink.findFuji(usb) ?: return Found.NoCamera
        return if (usb.hasPermission(device)) Found.Ready(device) else Found.NeedsPermission(device)
    }

    /** Asks the system to show its USB permission prompt; [onResult] gets the answer, once. */
    fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                app.unregisterReceiver(this)
                onResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Mutable because the system fills in the device and the answer; explicit, as Android requires.
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(app.packageName)
        usb.requestPermission(device, PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_MUTABLE))
    }

    /** Blocking: opens the link and the PTP session. Throws [PtpException] if the camera does not answer. */
    fun open(device: UsbDevice): CameraConnection {
        val link = UsbPtpLink.open(usb, device)
        try {
            val session = FujiRawSession(link.ptp)
            return CameraConnection(link, session, session.open())
        } catch (e: Exception) {
            link.close()
            throw e
        }
    }

    private companion object {
        const val ACTION_USB_PERMISSION = "com.fujivibe.USB_PERMISSION"
    }
}
