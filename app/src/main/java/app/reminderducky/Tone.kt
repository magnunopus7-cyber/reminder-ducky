package app.reminderducky

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes

/** How Ducky talks about your to-dos: lines for one item, several, none, and a cheer when one is ticked. */
enum class Tone(
    @StringRes val label: Int,
    @ArrayRes val one: Int,
    @ArrayRes val many: Int,
    @ArrayRes val none: Int,
    @ArrayRes val praise: Int,
) {
    FRIENDLY(R.string.tone_friendly, R.array.talk_one, R.array.talk_many, R.array.talk_none, R.array.talk_praise),
    ZEN(R.string.tone_zen, R.array.talk_one_zen, R.array.talk_many_zen, R.array.talk_none_zen, R.array.talk_praise_zen),
    MEAN(R.string.tone_mean, R.array.talk_one_mean, R.array.talk_many_mean, R.array.talk_none_mean, R.array.talk_praise_mean),
    BOSS(R.string.tone_boss, R.array.talk_one_boss, R.array.talk_many_boss, R.array.talk_none_boss, R.array.talk_praise_boss),
}
