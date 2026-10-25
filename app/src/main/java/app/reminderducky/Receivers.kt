package app.reminderducky

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Alarms.EXTRA_ID, -1)
        if (id != -1L) ReminderActions.fire(context, id)
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Alarms.EXTRA_ID, -1)
        if (id == -1L) return
        when (intent.action) {
            ACTION_DONE -> ReminderActions.done(context, id)
            ACTION_SNOOZE -> ReminderActions.snooze(context, id)
        }
    }

    companion object {
        const val ACTION_DONE = "app.reminderducky.DONE"
        const val ACTION_SNOOZE = "app.reminderducky.SNOOZE"
    }
}

/** Restores alarms and the duck after a reboot, an app update or a clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.rescheduleAll(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (Prefs(context).showDuck && DuckService.canShow(context)) DuckService.start(context)
        }
    }
}
