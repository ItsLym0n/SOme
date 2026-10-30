package dev.attendance.reminder

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Turns a pasted timetable into classes. Deliberately forgiving, since it gets whatever was
 * copied from UNION, myUMN, a spreadsheet or a chat message. Understands:
 *
 *  - one class per line:   "Senin 08:00-10:30 Dasar Pemrograman"
 *  - table rows (tabs):    "Monday\tIF100 Calculus\t08.00\t10.30\tB501"
 *  - day headings:         "Selasa" on its own line, classes below it
 *  - names on their own line, above or below the time line
 */
object ScheduleParser {
    data class Result(val classes: List<ClassSession>, val skipped: List<String>)

    private val DAYS: List<Pair<Regex, DayOfWeek>> = listOf(
        "senin|monday|mon" to DayOfWeek.MONDAY,
        "selasa|tuesday|tues|tue" to DayOfWeek.TUESDAY,
        "rabu|wednesday|wed" to DayOfWeek.WEDNESDAY,
        "kamis|thursday|thurs|thur|thu" to DayOfWeek.THURSDAY,
        "jum'?at|jumat|friday|fri" to DayOfWeek.FRIDAY,
        "sabtu|saturday|sat" to DayOfWeek.SATURDAY,
        "minggu|ahad|sunday|sun" to DayOfWeek.SUNDAY,
    ).map { (words, day) -> Regex("(?<![\\p{L}\\d])(?:$words)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE) to day }

    private val TIME = Regex("(?<!\\d)([01]?\\d|2[0-3])\\s*[:.]\\s*([0-5]\\d)(?!\\d)")
    private val DATE = Regex("\\b\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}\\b")
    private val LABEL = Regex(
        "\\b(mata\\s*kuliah|matkul|course|subject|hari|day|jam|waktu|time|kelas|class|pukul)\\s*:",
        RegexOption.IGNORE_CASE,
    )
    private val NOISE = Regex("\\b(wib|wita|wit|s/d|sd|sampai|until|to)\\b", RegexOption.IGNORE_CASE)

    private data class Line(val day: DayOfWeek?, val start: LocalTime?, val end: LocalTime?, val text: String)

    fun parse(input: String): Result {
        val classes = mutableListOf<ClassSession>()
        val skipped = mutableListOf<String>()

        var currentDay: DayOfWeek? = null
        var pendingName: String? = null // text seen before a time line
        var awaitingName: Int? = null   // index of a class whose name comes on the next line
        var namesAfterTimes: Boolean? = null

        for (raw in input.lines()) {
            if (raw.isBlank()) continue
            val line = analyse(raw)

            if (line.start == null) {
                if (line.day != null) {
                    // A day heading; any text alongside it is treated like a name line.
                    currentDay = line.day
                    pendingName = null
                    awaitingName = null
                }
                if (line.text.isEmpty()) continue
                val waiting = awaitingName
                if (waiting != null) {
                    classes[waiting] = classes[waiting].copy(name = line.text)
                    awaitingName = null
                } else if (pendingName == null && namesAfterTimes != true) {
                    pendingName = line.text
                }
                continue
            }

            val day = line.day ?: currentDay
            if (day == null || line.end == null) {
                skipped += raw.trim()
                continue
            }
            if (line.day != null) currentDay = line.day

            val name = line.text.ifEmpty { pendingName.orEmpty() }
            if (namesAfterTimes == null) namesAfterTimes = name.isEmpty()
            classes += ClassSession(name = name, day = day, start = line.start, end = line.end)
            awaitingName = if (name.isEmpty()) classes.lastIndex else null
            pendingName = null
        }

        val named = classes.map { if (it.name.isBlank()) it.copy(name = "Class") else it }
        return Result(named.distinctBy { Triple(it.name.lowercase(), it.day, it.start) }, skipped)
    }

    private fun analyse(raw: String): Line {
        var text = raw.replace('\t', ' ')
        text = DATE.replace(text, " ")

        val day = DAYS.firstOrNull { (re, _) -> re.containsMatchIn(text) }?.second
        DAYS.forEach { (re, _) -> text = re.replace(text, " ") }

        val times = TIME.findAll(text).map { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.toList()
        text = TIME.replace(text, " ")

        text = LABEL.replace(text, " ")
        text = NOISE.replace(text, " ")
        text = text.replace(Regex("\\s+"), " ")
            .trim { it.isWhitespace() || it in "-–—|,;:()[]•*." }

        return Line(day, times.getOrNull(0), times.getOrNull(1), text)
    }
}
