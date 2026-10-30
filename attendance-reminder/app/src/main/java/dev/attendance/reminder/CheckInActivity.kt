package dev.attendance.reminder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.time.LocalDate

/**
 * Invisible step between the notification and UNION: tapping a reminder counts as checking in,
 * so this marks the class meeting as attended, clears the notification and opens UNION.
 */
class CheckInActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val classId = intent.getStringExtra(EXTRA_CLASS_ID)
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse)
        if (classId != null && date != null) {
            if (classId != TEST_ID) ClassStore(this).setAttended(classId, date, true)
            Notifier.cancel(this, classId)
            Scheduler.scheduleNext(this)
        }
        startActivity(Notifier.unionIntent(this))
        finish()
    }

    companion object {
        const val TEST_ID = "test"
        private const val EXTRA_CLASS_ID = "class_id"
        private const val EXTRA_DATE = "date"

        fun intent(context: Context, classId: String, date: LocalDate) =
            Intent(context, CheckInActivity::class.java)
                .putExtra(EXTRA_CLASS_ID, classId)
                .putExtra(EXTRA_DATE, date.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
    }
}
