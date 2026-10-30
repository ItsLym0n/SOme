package dev.attendance.reminder

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Schedule export/import: a JSON file for backups, plain text for sharing. */
object ScheduleFile {
    private const val FORMAT = "attendance-reminder-schedule"
    private const val VERSION = 1
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun toJson(classes: List<ClassSession>): String {
        val arr = JSONArray()
        sorted(classes).forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("day", it.day.name)
                    .put("start", TIME.format(it.start))
                    .put("end", TIME.format(it.end)),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("classes", arr)
            .toString(2)
    }

    /** One class per line, in a form [ScheduleParser] reads back. */
    fun toText(classes: List<ClassSession>): String = sorted(classes).joinToString("\n") {
        "${it.day.name.lowercase().replaceFirstChar(Char::uppercase)} " +
            "${TIME.format(it.start)}-${TIME.format(it.end)} ${it.name}"
    }

    /**
     * Reads an exported JSON file, or failing that any timetable text. Imported classes get
     * fresh ids so they never clash with what's already saved.
     */
    fun read(content: String): ScheduleParser.Result {
        fromJson(content)?.let { return ScheduleParser.Result(it, emptyList()) }
        return ScheduleParser.parse(content)
    }

    private fun fromJson(content: String): List<ClassSession>? {
        val trimmed = content.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        return try {
            val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("classes")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ClassSession(
                    name = o.getString("name").trim().ifEmpty { "Class" },
                    day = parseDay(o.get("day")),
                    start = LocalTime.parse(o.getString("start")),
                    end = LocalTime.parse(o.getString("end")),
                )
            }
        } catch (_: JSONException) {
            null
        } catch (_: RuntimeException) {
            null // bad day or time value
        }
    }

    private fun parseDay(value: Any): DayOfWeek =
        if (value is Number) DayOfWeek.of(value.toInt()) else DayOfWeek.valueOf(value.toString().uppercase())

    private fun sorted(classes: List<ClassSession>) = classes.sortedWith(compareBy({ it.day }, { it.start }))
}
