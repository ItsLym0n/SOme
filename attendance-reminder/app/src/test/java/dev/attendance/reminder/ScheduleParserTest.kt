package dev.attendance.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

class ScheduleParserTest {
    private fun summary(text: String) = ScheduleParser.parse(text).classes
        .map { "${it.day} ${it.start}-${it.end} ${it.name}" }

    @Test
    fun oneClassPerLine() = assertEquals(
        listOf(
            "MONDAY 08:00-10:30 Dasar Pemrograman",
            "WEDNESDAY 13:00-15:30 Kalkulus",
            "FRIDAY 07:30-09:00 English",
        ),
        summary(
            """
            Senin 08:00-10:30 Dasar Pemrograman
            Rabu, 13.00 - 15.30 WIB Kalkulus
            Jum'at 07:30 s/d 09:00 English
            """.trimIndent(),
        ),
    )

    @Test
    fun dayHeadingsWithClassesBelow() = assertEquals(
        listOf(
            "MONDAY 08:00-10:30 IF100 Algorithms",
            "MONDAY 13:00-14:40 Physics",
            "TUESDAY 10:00-12:30 Statistics",
        ),
        summary(
            """
            Monday
            08:00 - 10:30  IF100 Algorithms
            13:00 - 14:40  Physics

            Tuesday
            10:00 - 12:30  Statistics
            """.trimIndent(),
        ),
    )

    @Test
    fun tabSeparatedTableRows() = assertEquals(
        listOf("TUESDAY 08:00-10:30 IF120 Discrete Math B501"),
        summary("Selasa\tIF120 Discrete Math\t08.00\t10.30\tB501"),
    )

    @Test
    fun nameAboveTimeIgnoresExtraLines() = assertEquals(
        listOf("MONDAY 08:00-10:30 Basis Data", "MONDAY 11:00-12:00 Etika"),
        summary(
            """
            Senin
            Basis Data
            Ruang C305
            08:00 - 10:30
            Etika
            11:00 - 12:00
            """.trimIndent(),
        ),
    )

    @Test
    fun nameBelowTime() = assertEquals(
        listOf("MONDAY 08:00-10:30 Basis Data", "MONDAY 11:00-12:00 Etika"),
        summary(
            """
            Senin
            08:00 - 10:30
            Basis Data
            11:00 - 12:00
            Etika
            """.trimIndent(),
        ),
    )

    @Test
    fun labelledFields() = assertEquals(
        listOf("FRIDAY 09:00-11:30 Pancasila"),
        summary(
            """
            Hari: Jumat
            Mata Kuliah: Pancasila
            Jam: 09:00 - 11:30
            """.trimIndent(),
        ),
    )

    @Test
    fun linesWithoutDayAreSkipped() {
        val r = ScheduleParser.parse("08:00 - 10:30 Orphan\nRabu 10:00-11:00 Kept")
        assertEquals(listOf("Kept"), r.classes.map { it.name })
        assertEquals(listOf("08:00 - 10:30 Orphan"), r.skipped)
    }
}
