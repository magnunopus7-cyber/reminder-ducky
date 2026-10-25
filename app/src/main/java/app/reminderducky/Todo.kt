package app.reminderducky

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A to-do item: just text to tick off, no time or alarm. */
data class Todo(val id: Long, val text: String, val done: Boolean = false)

class TodoStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("todos", Context.MODE_PRIVATE)

    fun all(): List<Todo> = synchronized(LOCK) {
        val arr = JSONArray(sp.getString("list", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Todo(o.getLong("id"), o.getString("text"), o.optBoolean("done", false))
        }
    }

    fun save(t: Todo) = synchronized(LOCK) {
        val list = all()
        val i = list.indexOfFirst { it.id == t.id }
        write(if (i >= 0) list.toMutableList().also { it[i] = t } else list + t)
    }

    fun delete(id: Long) = synchronized(LOCK) { write(all().filter { it.id != id }) }

    fun clearDone() = synchronized(LOCK) { write(all().filter { !it.done }) }

    private fun write(list: List<Todo>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
        sp.edit().putString("list", arr.toString()).apply()
    }

    companion object {
        private val LOCK = Any()
    }
}
