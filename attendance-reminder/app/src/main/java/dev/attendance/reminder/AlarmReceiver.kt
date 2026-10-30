package dev.attendance.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at each reminder time: posts what's due, then arms the following alarm. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fireAt = intent.getLongExtra(Scheduler.EXTRA_FIRE_AT, -1L)
        if (fireAt > 0) {
            Notifier.createChannel(context)
            Scheduler.dueAt(context, fireAt).forEach { Notifier.show(context, it) }
        }
        Scheduler.scheduleNext(context, maxOf(fireAt, System.currentTimeMillis()))
    }
}
