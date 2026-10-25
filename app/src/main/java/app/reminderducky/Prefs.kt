package app.reminderducky

import android.content.Context

enum class DuckFinish(val body: FloatArray, val beak: FloatArray) {
    GOLD(floatArrayOf(1.0f, 0.68f, 0.14f), floatArrayOf(1.0f, 0.40f, 0.08f)),
    CHROME(floatArrayOf(0.92f, 0.92f, 0.94f), floatArrayOf(1.0f, 0.55f, 0.20f)),
    ROSE(floatArrayOf(0.97f, 0.60f, 0.52f), floatArrayOf(0.90f, 0.38f, 0.28f)),
    MIDNIGHT(floatArrayOf(0.22f, 0.32f, 0.58f), floatArrayOf(1.0f, 0.62f, 0.18f)),
}

enum class DuckSize(val dp: Int) { SMALL(72), MEDIUM(96), LARGE(128) }

/** Small settings stored on the phone. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("duck_prefs", Context.MODE_PRIVATE)

    var showDuck: Boolean
        get() = sp.getBoolean("show_duck", true)
        set(v) = sp.edit().putBoolean("show_duck", v).apply()

    var finish: DuckFinish
        get() = runCatching { DuckFinish.valueOf(sp.getString("finish", null)!!) }.getOrDefault(DuckFinish.GOLD)
        set(v) = sp.edit().putString("finish", v.name).apply()

    var size: DuckSize
        get() = runCatching { DuckSize.valueOf(sp.getString("size", null)!!) }.getOrDefault(DuckSize.MEDIUM)
        set(v) = sp.edit().putString("size", v.name).apply()

    /** Which screen edge the duck last rested on. */
    var onRight: Boolean
        get() = sp.getBoolean("on_right", true)
        set(v) = sp.edit().putBoolean("on_right", v).apply()

    /** Vertical position as a fraction of the screen height. */
    var yFraction: Float
        get() = sp.getFloat("y_fraction", 0.3f)
        set(v) = sp.edit().putFloat("y_fraction", v).apply()
}
