package app.reminderducky

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes

/**
 * How Ducky talks: lines for one to-do, several, none, a cheer when one is ticked,
 * and the line it says when a reminder goes off.
 */
enum class Tone(
    @StringRes val label: Int,
    @ArrayRes val one: Int,
    @ArrayRes val many: Int,
    @ArrayRes val none: Int,
    @ArrayRes val praise: Int,
    @ArrayRes val alert: Int,
) {
    FRIENDLY(R.string.tone_friendly, R.array.talk_one, R.array.talk_many, R.array.talk_none, R.array.talk_praise, R.array.alert_friendly),
    ZEN(R.string.tone_zen, R.array.talk_one_zen, R.array.talk_many_zen, R.array.talk_none_zen, R.array.talk_praise_zen, R.array.alert_zen),
    MEAN(R.string.tone_mean, R.array.talk_one_mean, R.array.talk_many_mean, R.array.talk_none_mean, R.array.talk_praise_mean, R.array.alert_mean),
    BOSS(R.string.tone_boss, R.array.talk_one_boss, R.array.talk_many_boss, R.array.talk_none_boss, R.array.talk_praise_boss, R.array.alert_boss);

    /** The reminder line for [text]; the same reminder always gets the same line. */
    fun alertLine(context: android.content.Context, id: Long, text: String): String {
        val lines = context.resources.getStringArray(alert)
        return lines[Math.floorMod(id, lines.size.toLong()).toInt()].format(text)
    }
}
