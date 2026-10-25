package app.reminderducky

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/** Plays Ducky's quack from inside the app (the reminder notification has its own copy). */
object Quack {
    private var pool: SoundPool? = null
    private var soundId = 0
    private var loaded = false

    /** The full reminder alarm sound, for when notifications (which carry it) are off. */
    fun playAlarm(context: Context) {
        MediaPlayer.create(context.applicationContext, R.raw.alarm_quack)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }

    fun play(context: Context, volume: Float = 0.8f) {
        val p = pool ?: SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build().also { newPool ->
                pool = newPool
                newPool.setOnLoadCompleteListener { sp, id, status ->
                    if (status == 0) {
                        loaded = true
                        sp.play(id, volume, volume, 1, 0, 1f)
                    }
                }
                soundId = newPool.load(context.applicationContext, R.raw.quack, 1)
            }
        if (loaded) p.play(soundId, volume, volume, 1, 0, 1f)
    }
}
