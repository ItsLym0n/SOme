package dev.attendance.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Works out every reminder for the coming week and keeps exactly one alarm armed: the next one.
 *
 * For each class meeting:
 *  - 15 minutes before it starts: a "class starts soon" reminder.
 *  - From the start time, every 15 minutes until the class ends: a "you haven't checked in"
 *    reminder, skipped once that meeting is marked as attended.
 */
object Scheduler {
    const val LEAD_MINUTES = 15L
    const val REPEAT_MINUTES = 15L
    const val EXTRA_FIRE_AT = "fire_at"

    enum class Kind { BEFORE_CLASS, NOT_ATTENDED }

    data class Reminder(
        val at: Long,
        val session: ClassSession,
        val date: LocalDate,
        val kind: Kind,
        val classStart: Long,
        val classEnd: Long,
    )

    /** All reminders that haven't been silenced by an attendance mark, from yesterday to a week out. */
    fun reminders(context: Context, zone: ZoneId = ZoneId.systemDefault()): List<Reminder> {
        val store = ClassStore(context)
        return reminders(store.classes(), LocalDate.now(zone), zone, store::isAttended)
    }

    fun reminders(
        classes: List<ClassSession>,
        today: LocalDate,
        zone: ZoneId,
        isAttended: (classId: String, date: LocalDate) -> Boolean,
    ): List<Reminder> {
        val out = mutableListOf<Reminder>()
        for (session in classes) {
            for (offset in -1L..7L) {
                val date = today.plusDays(offset)
                if (date.dayOfWeek != session.day) continue
                if (isAttended(session.id, date)) continue

                val start = ZonedDateTime.of(date, session.start, zone)
                var end = ZonedDateTime.of(date, session.end, zone)
                if (!end.isAfter(start)) end = end.plusDays(1) // class that runs past midnight
                val startMs = start.toInstant().toEpochMilli()
                val endMs = end.toInstant().toEpochMilli()

                out += Reminder(
                    start.minusMinutes(LEAD_MINUTES).toInstant().toEpochMilli(),
                    session, date, Kind.BEFORE_CLASS, startMs, endMs,
                )
                var t = start
                while (t.isBefore(end)) {
                    out += Reminder(t.toInstant().toEpochMilli(), session, date, Kind.NOT_ATTENDED, startMs, endMs)
                    t = t.plusMinutes(REPEAT_MINUTES)
                }
            }
        }
        return out.sortedBy { it.at }
    }

    /** Reminders due at [fireAt] (several classes can share a slot). */
    fun dueAt(context: Context, fireAt: Long): List<Reminder> =
        reminders(context).filter { it.at == fireAt }

    fun nextReminder(context: Context, after: Long = System.currentTimeMillis()): Reminder? =
        reminders(context).firstOrNull { it.at > after }

    /** Arms the alarm for the next reminder after [after], replacing any previous one. */
    fun scheduleNext(context: Context, after: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val next = nextReminder(context, after)
        val intent = Intent(context, AlarmReceiver::class.java)
        if (next == null) {
            alarms.cancel(pendingIntent(context, intent))
            return
        }
        intent.putExtra(EXTRA_FIRE_AT, next.at)
        val pi = pendingIntent(context, intent)
        if (canScheduleExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi)
        }
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pendingIntent(context: Context, intent: Intent) = PendingIntent.getBroadcast(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
