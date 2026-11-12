package com.fujivibe.ui.camera

/** How far the connection to the camera has got. */
enum class ConnectionStage {
    NOT_TRIED,
    LOOKING,
    NO_CAMERA,
    ASKING_PERMISSION,
    PERMISSION_REFUSED,
    OPENING,
    NOT_ANSWERING,
    CONNECTED,
}

enum class CheckState { TODO, WORKING, OK, PROBLEM }

/** One step of getting connected; [help] says what to do about it when it is a [CheckState.PROBLEM]. */
class CheckItem(val title: String, val state: CheckState, val help: String? = null)

/**
 * The three things the app can tell about the connection, from how far it got. It cannot read the camera's
 * USB mode, but the camera only answers in RAW CONV./BACKUP RESTORE, so no answer points there.
 */
fun checklist(stage: ConnectionStage): List<CheckItem> {
    val found = when (stage) {
        ConnectionStage.NOT_TRIED -> CheckState.TODO
        ConnectionStage.LOOKING -> CheckState.WORKING
        ConnectionStage.NO_CAMERA -> CheckState.PROBLEM
        else -> CheckState.OK
    }
    val permission = when (stage) {
        ConnectionStage.ASKING_PERMISSION -> CheckState.WORKING
        ConnectionStage.PERMISSION_REFUSED -> CheckState.PROBLEM
        ConnectionStage.OPENING, ConnectionStage.NOT_ANSWERING, ConnectionStage.CONNECTED -> CheckState.OK
        else -> CheckState.TODO
    }
    val answers = when (stage) {
        ConnectionStage.OPENING -> CheckState.WORKING
        ConnectionStage.NOT_ANSWERING -> CheckState.PROBLEM
        ConnectionStage.CONNECTED -> CheckState.OK
        else -> CheckState.TODO
    }
    return listOf(
        CheckItem(
            "Camera found on USB", found,
            if (found == CheckState.PROBLEM) {
                "Turn the camera on and wake its screen, and charge it if it is low. Plug the cable into the phone. " +
                    "Then in Settings, Connected devices, USB, set \"USB controlled by\" to \"This device\"."
            } else null,
        ),
        CheckItem(
            "Permission to use it", permission,
            if (permission == CheckState.PROBLEM) "Tap Connect again and choose Allow." else null,
        ),
        CheckItem(
            "Camera answers in RAW CONV./BACKUP RESTORE mode", answers,
            if (answers == CheckState.PROBLEM) {
                "On the camera, set the USB mode to USB RAW CONV./BACKUP RESTORE and keep its screen awake. " +
                    "If it still does not answer, unplug the cable at the phone end, plug it back in, and tap Try again."
            } else null,
        ),
    )
}
