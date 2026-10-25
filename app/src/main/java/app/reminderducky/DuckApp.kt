package app.reminderducky

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class DuckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Your reminders when they are due"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DUCK, "Floating duck", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Android requires this while the duck is on screen. You can hide it."
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_DUCK = "duck"
    }
}
