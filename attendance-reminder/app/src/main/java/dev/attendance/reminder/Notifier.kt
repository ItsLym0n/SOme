package dev.attendance.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Notifier {
    const val UNION_PACKAGE = "umn.union"
    private const val CHANNEL_ID = "attendance_v1"
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH, // heads-up, sound, vibration
        ).apply {
            description = context.getString(R.string.channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            enableLights(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setBypassDnd(true) // only honoured if you allow it in system settings
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, r: Scheduler.Reminder) {
        if (!canPost(context)) return
        val now = System.currentTimeMillis()
        if (now >= r.classEnd) return // fired late (phone was off): class is already over

        val start = TIME.format(Instant.ofEpochMilli(r.classStart).atZone(ZoneId.systemDefault()))
        val end = TIME.format(Instant.ofEpochMilli(r.classEnd).atZone(ZoneId.systemDefault()))
        val (title, text) = when (r.kind) {
            Scheduler.Kind.BEFORE_CLASS -> Pair(
                context.getString(R.string.notif_before_title, r.session.name),
                context.getString(R.string.notif_before_text, start, end),
            )
            Scheduler.Kind.NOT_ATTENDED -> Pair(
                context.getString(R.string.notif_nag_title, r.session.name),
                context.getString(R.string.notif_nag_text, start, end),
            )
        }

        val id = notificationId(r.session.id)
        val checkIn = PendingIntent.getActivity(
            context, id, CheckInActivity.intent(context, r.session.id, r.date),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.union_blue))
            .setLargeIcon(largeIcon(context))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // Tapping opens UNION and counts as checking in for this class meeting.
            .setContentIntent(checkIn)
            .setAutoCancel(true)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(r.classEnd - now)
            .setWhen(r.classStart)
            .setShowWhen(true)
            .addAction(0, context.getString(R.string.action_open_union), checkIn)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked between the check above and now.
        }
    }

    fun cancel(context: Context, classId: String) =
        NotificationManagerCompat.from(context).cancel(notificationId(classId))

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isUnionInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(UNION_PACKAGE) != null

    /** Launches UNION, or its Play Store page if it isn't installed. */
    fun unionIntent(context: Context): Intent {
        val launch = context.packageManager.getLaunchIntentForPackage(UNION_PACKAGE)
        val intent = launch ?: Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$UNION_PACKAGE"),
        )
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun notificationId(classId: String) = classId.hashCode()

    /** The home screen's "Open UNION" tile art, shown on the right of the notification. */
    private fun largeIcon(context: Context): android.graphics.Bitmap? {
        val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        return ContextCompat.getDrawable(context, R.drawable.tile_union)?.toBitmap(size, size)
    }

    /** Fake reminder for the "Send test notification" button. */
    fun showTest(context: Context) {
        val now = System.currentTimeMillis()
        show(
            context,
            Scheduler.Reminder(
                at = now,
                session = ClassSession(id = CheckInActivity.TEST_ID, name = context.getString(R.string.test_class_name),
                    day = LocalDate.now().dayOfWeek,
                    start = java.time.LocalTime.now().plusMinutes(15),
                    end = java.time.LocalTime.now().plusMinutes(17)),
                date = LocalDate.now(),
                kind = Scheduler.Kind.BEFORE_CLASS,
                classStart = now + 15 * 60_000,
                classEnd = now + 17 * 60_000,
            ),
        )
    }
}
