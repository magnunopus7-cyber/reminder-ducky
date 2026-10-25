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
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Your reminders when they are due, with a quack"
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
        // first version's channel (default sound) is replaced.
        nm.deleteNotificationChannel("reminders")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DUCK, "Floating duck", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Android requires this while the duck is on screen. You can hide it."
                setShowBadge(false)
            }
        )
    }

    companion object {
        fun quackUri(context: android.content.Context): Uri =
            Uri.parse("android.resource://${context.packageName}/${R.raw.quack}")

        const val CHANNEL_REMINDERS = "reminders_quack"
        const val CHANNEL_DUCK = "duck"
    }
}
