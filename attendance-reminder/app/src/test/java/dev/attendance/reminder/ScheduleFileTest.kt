package dev.attendance.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ScheduleFileTest {
    private val classes = listOf(
        ClassSession(name = "Kalkulus", day = DayOfWeek.WEDNESDAY, start = LocalTime.of(13, 0), end = LocalTime.of(15, 30)),
        ClassSession(name = "Dasar Pemrograman", day = DayOfWeek.MONDAY, start = LocalTime.of(8, 0), end = LocalTime.of(10, 30)),
    )

    private fun summary(list: List<ClassSession>) = list.map { "${it.day} ${it.start}-${it.end} ${it.name}" }

    @Test
    fun jsonRoundTripWithFreshIds() {
        val back = ScheduleFile.read(ScheduleFile.toJson(classes)).classes
        assertEquals(summary(classes.sortedBy { it.day }), summary(back))
        assertNotEquals(classes.map { it.id }.toSet(), back.map { it.id }.toSet())
    }

    @Test
    fun sharedTextReadsBack() {
        val text = ScheduleFile.toText(classes)
        assertEquals("Monday 08:00-10:30 Dasar Pemrograman\nWednesday 13:00-15:30 Kalkulus", text)
        assertEquals(summary(classes.sortedBy { it.day }), summary(ScheduleFile.read(text).classes))
    }

    @Test
    fun plainTextFileFallsBackToParser() {
        val r = ScheduleFile.read("Senin 08:00-10:30 Basis Data")
        assertEquals(listOf("MONDAY 08:00-10:30 Basis Data"), summary(r.classes))
    }

    @Test
    fun brokenJsonFindsNothing() {
        assertEquals(0, ScheduleFile.read("""{"classes":[{"name":"X","day":"FUNDAY"}]}""").classes.size)
    }
}
