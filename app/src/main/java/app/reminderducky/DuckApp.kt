package app.reminderducky

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri

class DuckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_reminders_desc)
                enableVibration(true)
                setSound(
                    quackUri(this@DuckApp),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
        // Android never changes a channel's sound after creation, so the
        // earlier channels with older sounds are replaced.
        nm.deleteNotificationChannel("reminders")
        nm.deleteNotificationChannel("reminders_quack")
        nm.deleteNotificationChannel("reminders_duck_sound")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DUCK, getString(R.string.channel_duck), NotificationManager.IMPORTANCE_MIN).apply {
                description = getString(R.string.channel_duck_desc)
                setShowBadge(false)
            }
        )
    }

    companion object {
        fun quackUri(context: android.content.Context): Uri =
            Uri.parse("android.resource://${context.packageName}/${R.raw.alarm_quack}")

        const val CHANNEL_REMINDERS = "reminders_short_quacks"
        const val CHANNEL_DUCK = "duck"
    }
}
