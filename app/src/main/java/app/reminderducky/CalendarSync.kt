package app.reminderducky

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Copies reminders and to-dos into a calendar on the phone (usually the Google account's),
 * which Google syncs on to Google Calendar. No sign-in or internet access of our own:
 * Android's calendar does the syncing.
 *
 * Reminders become events at their time; open to-dos become all-day events for today.
 * Each item's event id is remembered so later changes update or remove the same event.
 */
object CalendarSync {
    data class Cal(val id: Long, val name: String, val account: String, val primary: Boolean)

    private val worker = Executors.newSingleThreadExecutor()

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Calendars Ducky can write to, Google ones first. */
    fun calendars(context: Context): List<Cal> {
        if (!hasPermission(context)) return emptyList()
        val list = mutableListOf<Cal>()
        context.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.IS_PRIMARY),
            "${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${Calendars.VISIBLE} = 1",
            null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val google = c.getString(3) == "com.google"
                list += Cal(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", google && c.getInt(4) == 1)
            }
        }
        return list.sortedByDescending { it.primary }
    }

    /** Brings the calendar up to date in the background. Does nothing while the option is off. */
    fun sync(context: Context) {
        val app = context.applicationContext
        if (!Prefs(app).calendarSync) return
        worker.execute { runCatching { syncNow(app) } }
    }

    /** Removes every event Ducky added, for when the option is turned off. */
    fun clear(context: Context) {
        val app = context.applicationContext
        worker.execute {
            runCatching {
                val state = load(app)
                for (key in state.keys()) deleteEvent(app, state.getJSONObject(key).getLong("event"))
                save(app, JSONObject())
            }
        }
    }

    private fun syncNow(context: Context) {
        if (!hasPermission(context)) return
        val calId = Prefs(context).calendarId.takeIf { it >= 0 } ?: return
        val wanted = mutableMapOf<String, ContentValues>()

        for (r in ReminderStore(context).all()) {
            wanted["r${r.id}"] = ContentValues().apply {
                put(Events.TITLE, "${r.icon} ${r.text}")
                put(Events.DTSTART, r.timeMillis)
                put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                when (r.repeat) {
                    Repeat.NONE -> { put(Events.DTEND, r.timeMillis + HALF_HOUR); putNull(Events.RRULE); putNull(Events.DURATION) }
                    Repeat.DAILY -> { putNull(Events.DTEND); put(Events.DURATION, "PT30M"); put(Events.RRULE, "FREQ=DAILY") }
                    Repeat.WEEKLY -> { putNull(Events.DTEND); put(Events.DURATION, "PT30M"); put(Events.RRULE, "FREQ=WEEKLY") }
                }
                put(Events.ALL_DAY, 0)
            }
        }
        // All-day events are stored as midnight UTC of the day.
        val now = Calendar.getInstance()
        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val todoTitle = context.getString(R.string.calendar_todo_title)
        for (t in TodoStore(context).all()) {
            if (t.done) continue
            wanted["t${t.id}"] = ContentValues().apply {
                put(Events.TITLE, "$todoTitle ${t.text}")
                put(Events.DTSTART, today)
                put(Events.DTEND, today + DAY)
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.ALL_DAY, 1)
                putNull(Events.RRULE)
                putNull(Events.DURATION)
            }
        }

        val state = load(context)
        val next = JSONObject()
        // Remove events for items that are gone, done, or in another calendar.
        for (key in state.keys()) {
            val old = state.getJSONObject(key)
            if (key !in wanted || old.optLong("cal") != calId) deleteEvent(context, old.getLong("event"))
        }
        for ((key, values) in wanted) {
            val old = state.optJSONObject(key)?.takeIf { it.optLong("cal") == calId }
            val print = values.toString()
            var eventId = old?.getLong("event") ?: -1L
            if (old != null && old.optString("print") == print && exists(context, eventId)) {
                next.put(key, old)
                continue
            }
            values.put(Events.DESCRIPTION, context.getString(R.string.calendar_note))
            val updated = eventId >= 0 &&
                context.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null) > 0
            if (!updated) {
                values.put(Events.CALENDAR_ID, calId)
                eventId = context.contentResolver.insert(Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: continue
            }
            next.put(key, JSONObject().put("event", eventId).put("cal", calId).put("print", print))
        }
        save(context, next)
    }

    private fun exists(context: Context, eventId: Long): Boolean =
        context.contentResolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.DELETED), null, null, null,
        )?.use { it.moveToFirst() && it.getInt(0) == 0 } ?: false

    private fun deleteEvent(context: Context, eventId: Long) {
        runCatching { context.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null) }
    }

    private fun load(context: Context) =
        JSONObject(context.getSharedPreferences("calendar_sync", Context.MODE_PRIVATE).getString("events", "{}") ?: "{}")

    private fun save(context: Context, state: JSONObject) =
        context.getSharedPreferences("calendar_sync", Context.MODE_PRIVATE).edit().putString("events", state.toString()).apply()

    private const val HALF_HOUR = 30 * 60 * 1000L
    private const val DAY = 24 * 60 * 60 * 1000L
}
