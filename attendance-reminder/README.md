# Attendance Reminder

A small Android app that makes sure you never forget to check in on
**UNION – UMN Companion** (`umn.union`).

## What it does

For every class in your weekly timetable:

| When | Notification |
| --- | --- |
| 15 minutes before class starts | "**Calculus starts in 15 minutes**" |
| At the start, then every 15 minutes while the class is running | "**You haven't checked in to Calculus!**" |

- Tapping the notification (or **Open UNION**) opens the UNION app. If UNION isn't
  installed, it opens its Play Store page.
- Press **I've checked in** on the notification (or the button next to the class in the
  app) to stop the reminders for that class today. Opening UNION alone doesn't count,
  because the app has no way to see whether your check-in in UNION went through.
- If you never check in, the reminders keep coming every 15 minutes until the class ends,
  then the notification disappears by itself.

### Priority notifications

- High-importance channel: pops up as a heads-up banner, with sound and vibration, and is
  shown in full on the lock screen.
- Marked as an alarm-category, max-priority notification that is pinned (ongoing) until
  you check in or the class ends. Swiping it away only hides it until the next 15-minute
  reminder.
- Exact alarms are used so reminders arrive on the minute, also in Doze, and are
  re-armed after a reboot, app update or time zone change.

## Install

1. Download [`dist/attendance-reminder-1.0.0.apk`](dist/attendance-reminder-1.0.0.apk) to
   your Android phone (Android 8.0 or newer).
2. Open it and allow "Install unknown apps" when asked.
3. Open **Attendance Reminder**, allow notifications, and tap any red warning cards at the
   top. They take you to the right setting:
   - **Battery optimisation**: exempt the app. On Xiaomi/Oppo/Vivo/Samsung phones, also
     allow *Autostart* / set battery to *Unrestricted*, or the system may kill reminders.
   - **Exact alarms** (Android 12 only): allow.
4. Tap **Add class** for each class: name, day, start time, end time. Tap a class to edit
   or delete it.
5. Tap **Test notification** to see what a reminder looks like.

Optional: to get reminders even in Do Not Disturb, go to
*Settings → Notifications → Do Not Disturb → Apps* and add Attendance Reminder.

## Build from source

Requires JDK 17+ and the Android SDK (platform 35).

```sh
./gradlew assembleRelease   # APK at app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest
```

The release build is signed with the debug key so it can be sideloaded directly.

## Code layout

- `Scheduler.kt`: works out every reminder time for the coming week and keeps one exact
  alarm armed for the next one.
- `AlarmReceiver.kt`: fires at each reminder time, posts the notification(s), arms the
  next alarm.
- `Notifier.kt`: notification channel, notification content, launching UNION.
- `AttendedReceiver.kt`: the "I've checked in" action.
- `BootReceiver.kt`: re-arms alarms after reboot, update or clock change.
- `ClassStore.kt`: timetable and check-in marks (stored on the device).
- `MainActivity.kt`: timetable editor and setup warnings.

Not affiliated with Universitas Multimedia Nusantara.
