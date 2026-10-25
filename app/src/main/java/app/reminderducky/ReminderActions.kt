package app.reminderducky

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** What happens when a reminder goes off, is done, or is snoozed. */
object ReminderActions {
    const val SNOOZE_MINUTES = 10

    fun fire(context: Context, id: Long) {
        val store = ReminderStore(context)
        val r = store.get(id) ?: return
        showNotification(context, r)
        if (r.repeat == Repeat.NONE) {
            store.save(r.copy(fired = true))
        } else {
            val next = r.copy(timeMillis = r.repeat.nextAfter(r.timeMillis, System.currentTimeMillis()))
            store.save(next)
            Alarms.schedule(context, next)
        }
        if (Prefs(context).showDuck && DuckService.canShow(context)) {
            DuckService.alert(context, r.id)
        }
    }

    fun done(context: Context, id: Long) {
        val store = ReminderStore(context)
        val r = store.get(id)
        if (r != null) {
            NotificationManagerCompat.from(context).cancel(r.notificationId)
            if (r.repeat == Repeat.NONE) {
                store.delete(id)
                Alarms.cancel(context, id)
            }
        }
        DuckService.instance?.dismissAlert(id, Mood.HAPPY)
    }

    fun snooze(context: Context, id: Long) {
        val store = ReminderStore(context)
        val r = store.get(id)
        if (r != null) {
            NotificationManagerCompat.from(context).cancel(r.notificationId)
            val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
            // A repeating reminder keeps its schedule; the snooze is a one-off copy.
            val snoozed = if (r.repeat == Repeat.NONE) {
                r.copy(timeMillis = at, fired = false)
            } else {
                Reminder(Reminder.newId(), r.text, at, icon = r.icon)
            }
            store.save(snoozed)
            Alarms.schedule(context, snoozed)
        }
        DuckService.instance?.dismissAlert(id, Mood.SLEEPY)
    }

    private fun showNotification(context: Context, r: Reminder) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, DuckApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_duck)
            .setContentTitle("${r.icon} Ducky reminder")
            .setContentText(r.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Snooze $SNOOZE_MINUTES min", actionIntent(context, NotificationActionReceiver.ACTION_SNOOZE, r.id))
            .addAction(0, "Done", actionIntent(context, NotificationActionReceiver.ACTION_DONE, r.id))
            .build()
        NotificationManagerCompat.from(context).notify(r.notificationId, n)
    }

    private fun actionIntent(context: Context, action: String, id: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("reminder://$id"))
            .putExtra(Alarms.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
