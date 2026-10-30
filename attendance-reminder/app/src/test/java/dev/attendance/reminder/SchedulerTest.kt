package dev.attendance.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SchedulerTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val monday = LocalDate.of(2026, 11, 2)
    private val calculus = ClassSession(
        id = "calc", name = "Calculus",
        day = DayOfWeek.MONDAY, start = LocalTime.of(8, 0), end = LocalTime.of(9, 0),
    )

    private fun times(rs: List<Scheduler.Reminder>, date: LocalDate) = rs
        .filter { it.date == date }
        .map { "${it.kind.name[0]} " + Instant.ofEpochMilli(it.at).atZone(zone).toLocalTime() }

    @Test
    fun remindsBeforeClassThenEvery15MinutesUntilItEnds() {
        val rs = Scheduler.reminders(listOf(calculus), monday, zone) { _, _ -> false }
        assertEquals(
            listOf("B 07:45", "N 08:00", "N 08:15", "N 08:30", "N 08:45"),
            times(rs, monday),
        )
    }

    @Test
    fun attendingSilencesThatMeetingOnly() {
        val rs = Scheduler.reminders(listOf(calculus), monday, zone) { _, d -> d == monday }
        assertTrue(times(rs, monday).isEmpty())
        assertEquals(5, times(rs, monday.plusWeeks(1)).size)
    }

    @Test
    fun classPastMidnightKeepsNagging() {
        val late = calculus.copy(start = LocalTime.of(23, 30), end = LocalTime.of(0, 15))
        val rs = Scheduler.reminders(listOf(late), monday, zone) { _, _ -> false }
        assertEquals(listOf("B 23:15", "N 23:30", "N 23:45", "N 00:00"), times(rs, monday))
    }

    @Test
    fun overlappingClassesShareASlot() {
        val other = calculus.copy(id = "phys", name = "Physics")
        val rs = Scheduler.reminders(listOf(calculus, other), monday, zone) { _, _ -> false }
        val first = rs.first { it.date == monday }.at
        assertEquals(setOf("calc", "phys"), rs.filter { it.at == first }.map { it.session.id }.toSet())
    }
}
