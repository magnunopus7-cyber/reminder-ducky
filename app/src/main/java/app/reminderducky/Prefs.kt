package app.reminderducky

import android.content.Context

/** Small settings stored on the phone. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("duck_prefs", Context.MODE_PRIVATE)

    var showDuck: Boolean
        get() = sp.getBoolean("show_duck", true)
        set(v) = sp.edit().putBoolean("show_duck", v).apply()

    /** Quack when Ducky is tapped. */
    var tapQuack: Boolean
        get() = sp.getBoolean("tap_quack", true)
        set(v) = sp.edit().putBoolean("tap_quack", v).apply()

    var look: DuckLook
        get() = DuckLook(
            enumOr(sp.getString("material", null), DuckMaterial.GLOSSY),
            enumOr(sp.getString("color", null), DuckColor.SUNNY),
            enumOr(sp.getString("accessory", null), DuckAccessory.NONE),
        )
        set(v) = sp.edit()
            .putString("material", v.material.name)
            .putString("color", v.color.name)
            .putString("accessory", v.accessory.name)
            .apply()

    /** Which sections of the app page are folded open. */
    fun isOpen(section: String, default: Boolean) = sp.getBoolean("open_$section", default)
    fun setOpen(section: String, open: Boolean) = sp.edit().putBoolean("open_$section", open).apply()

    /** Copy reminders and to-dos into a phone calendar (see CalendarSync), and which one. */
    var calendarSync: Boolean
        get() = sp.getBoolean("calendar_sync", false)
        set(v) = sp.edit().putBoolean("calendar_sync", v).apply()
    var calendarId: Long
        get() = sp.getLong("calendar_id", -1)
        set(v) = sp.edit().putLong("calendar_id", v).apply()
    var calendarName: String
        get() = sp.getString("calendar_name", "") ?: ""
        set(v) = sp.edit().putString("calendar_name", v).apply()

    var tone: Tone
        get() = enumOr(sp.getString("tone", null), Tone.FRIENDLY)
        set(v) = sp.edit().putString("tone", v.name).apply()

    var size: DuckSize
        get() = enumOr(sp.getString("size", null), DuckSize.MEDIUM)
        set(v) = sp.edit().putString("size", v.name).apply()

    /** Which screen edge the duck last rested on. */
    var onRight: Boolean
        get() = sp.getBoolean("on_right", true)
        set(v) = sp.edit().putBoolean("on_right", v).apply()

    /** Vertical position as a fraction of the screen height. */
    var yFraction: Float
        get() = sp.getFloat("y_fraction", 0.3f)
        set(v) = sp.edit().putFloat("y_fraction", v).apply()

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        runCatching { enumValueOf<T>(name!!) }.getOrDefault(default)
}
