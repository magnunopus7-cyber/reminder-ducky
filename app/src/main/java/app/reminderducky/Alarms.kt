package app.reminderducky

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object Alarms {
    const val EXTRA_ID = "reminder_id"

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, r: Reminder) {
        if (r.fired) return
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, r.id)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.timeMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.timeMillis, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, id))
    }

    /** Alarms are lost on reboot, so put them all back. */
    fun rescheduleAll(context: Context) {
        val store = ReminderStore(context)
        val now = System.currentTimeMillis()
        for (r in store.all()) {
            if (r.fired) continue
            var current = r
            if (r.repeat != Repeat.NONE && r.timeMillis <= now) {
                current = r.copy(timeMillis = r.repeat.nextAfter(r.timeMillis, now))
                store.save(current)
            }
            // A one-off reminder missed while the phone was off fires right away.
            schedule(context, current)
        }
    }

    private fun pendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("reminder://$id"))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
