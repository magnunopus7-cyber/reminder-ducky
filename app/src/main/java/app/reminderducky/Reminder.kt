package app.reminderducky

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class Repeat(@androidx.annotation.StringRes val label: Int) {
    NONE(R.string.repeat_once), DAILY(R.string.repeat_daily), WEEKLY(R.string.repeat_weekly);

    /** The first occurrence strictly after [now], starting from [time]. */
    fun nextAfter(time: Long, now: Long): Long {
        if (this == NONE) return time
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        while (cal.timeInMillis <= now) {
            cal.add(if (this == DAILY) Calendar.DAY_OF_YEAR else Calendar.WEEK_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}

data class Reminder(
    val id: Long,
    val text: String,
    val timeMillis: Long,
    val repeat: Repeat = Repeat.NONE,
    val icon: String = DEFAULT_ICON,
    /** A one-off reminder that has gone off but has not been marked done yet. */
    val fired: Boolean = false,
) {
    val notificationId: Int get() = (id % Int.MAX_VALUE).toInt()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("text", text)
        .put("time", timeMillis)
        .put("repeat", repeat.name)
        .put("fired", fired)
        .put("icon", icon)

    companion object {
        fun fromJson(o: JSONObject) = Reminder(
            id = o.getLong("id"),
            text = o.getString("text"),
            timeMillis = o.getLong("time"),
            repeat = runCatching { Repeat.valueOf(o.getString("repeat")) }.getOrDefault(Repeat.NONE),
            fired = o.optBoolean("fired", false),
            icon = o.optString("icon", DEFAULT_ICON),
        )

        const val DEFAULT_ICON = "🔔"
        val ICONS = listOf("🔔", "💊", "💧", "📞", "📅", "🏃", "🍽️", "📚", "⭐")

        fun newId(): Long = System.currentTimeMillis()
    }
}

/** Keeps reminders on the phone as a small JSON list. */
class ReminderStore(context: Context) {
    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun all(): List<Reminder> = synchronized(LOCK) {
        val arr = JSONArray(sp.getString("list", "[]"))
        (0 until arr.length()).map { Reminder.fromJson(arr.getJSONObject(it)) }
    }

    fun get(id: Long): Reminder? = all().firstOrNull { it.id == id }

    fun save(r: Reminder) = synchronized(LOCK) {
        write(all().filter { it.id != r.id } + r)
    }

    fun delete(id: Long) = synchronized(LOCK) {
        write(all().filter { it.id != id })
    }

    private fun write(list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("list", arr.toString()).apply()
        CalendarSync.sync(app)
    }

    companion object {
        private val LOCK = Any()
    }
}
