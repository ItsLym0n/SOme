package dev.attendance.reminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ClassSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("day", day.value)
        .put("start", start.toString())
        .put("end", end.toString())

    companion object {
        fun fromJson(o: JSONObject) = ClassSession(
            id = o.getString("id"),
            name = o.getString("name"),
            day = DayOfWeek.of(o.getInt("day")),
            start = LocalTime.parse(o.getString("start")),
            end = LocalTime.parse(o.getString("end")),
        )
    }
}

/** Weekly class schedule plus which class meetings you've already marked as attended. */
class ClassStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("schedule", Context.MODE_PRIVATE)

    fun classes(): List<ClassSession> {
        val raw = prefs.getString(KEY_CLASSES, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length())
            .map { ClassSession.fromJson(arr.getJSONObject(it)) }
            .sortedWith(compareBy({ it.day }, { it.start }))
    }

    fun find(id: String): ClassSession? = classes().firstOrNull { it.id == id }

    fun save(session: ClassSession) {
        val list = classes().filterNot { it.id == session.id } + session
        write(list)
    }

    fun delete(id: String) = write(classes().filterNot { it.id == id })

    private fun write(list: List<ClassSession>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_CLASSES, arr.toString()).apply()
    }

    var userName: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NAME, value.trim()).apply()

    fun isAttended(classId: String, date: LocalDate): Boolean =
        prefs.getBoolean(attendedKey(classId, date), false)

    fun setAttended(classId: String, date: LocalDate, attended: Boolean) {
        val edit = prefs.edit()
        // Drop marks older than a week so prefs don't grow forever.
        val cutoff = LocalDate.now().minusDays(7)
        prefs.all.keys
            .filter { it.startsWith(ATTENDED_PREFIX) }
            .filter { key -> runCatching { LocalDate.parse(key.substringAfterLast('|')) < cutoff }.getOrDefault(true) }
            .forEach { edit.remove(it) }
        if (attended) edit.putBoolean(attendedKey(classId, date), true)
        else edit.remove(attendedKey(classId, date))
        edit.apply()
    }

    private fun attendedKey(classId: String, date: LocalDate) = "$ATTENDED_PREFIX$classId|$date"

    private companion object {
        const val KEY_CLASSES = "classes"
        const val KEY_NAME = "user_name"
        const val ATTENDED_PREFIX = "attended|"
    }
}
