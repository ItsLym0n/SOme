package dev.attendance.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** "I've checked in" action: silences the rest of that class meeting's reminders. */
class AttendedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val classId = intent.getStringExtra(EXTRA_CLASS_ID) ?: return
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return
        if (classId != "test") ClassStore(context).setAttended(classId, date, true)
        Notifier.cancel(context, classId)
        Scheduler.scheduleNext(context)
    }

    companion object {
        private const val EXTRA_CLASS_ID = "class_id"
        private const val EXTRA_DATE = "date"

        fun intent(context: Context, classId: String, date: LocalDate) =
            Intent(context, AttendedReceiver::class.java)
                .putExtra(EXTRA_CLASS_ID, classId)
                .putExtra(EXTRA_DATE, date.toString())
    }
}
