package dev.attendance.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var store: ClassStore
    private lateinit var classList: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var nextText: TextView
    private lateinit var warnings: LinearLayout

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = ClassStore(this)
        classList = findViewById(R.id.class_list)
        emptyText = findViewById(R.id.empty_text)
        nextText = findViewById(R.id.next_text)
        warnings = findViewById(R.id.warnings)

        Notifier.createChannel(this)

        findViewById<View>(R.id.add_button).setOnClickListener { editClass(null) }
        findViewById<View>(R.id.open_union_button).setOnClickListener {
            startActivity(Notifier.unionIntent(this))
        }
        findViewById<View>(R.id.test_button).setOnClickListener {
            if (!Notifier.canPost(this)) {
                Toast.makeText(this, R.string.warn_notifications, Toast.LENGTH_LONG).show()
            } else {
                Notifier.showTest(this)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        Scheduler.scheduleNext(this)
        renderWarnings()
        renderClasses()
        renderNext()
    }

    private fun renderNext() {
        val next = Scheduler.nextReminder(this)
        nextText.text = if (next == null) {
            getString(R.string.next_none)
        } else {
            val at = Instant.ofEpochMilli(next.at).atZone(ZoneId.systemDefault())
            getString(
                R.string.next_reminder,
                at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                TIME.format(at),
                next.session.name,
            )
        }
    }

    private fun renderClasses() {
        classList.removeAllViews()
        val classes = store.classes()
        emptyText.visibility = if (classes.isEmpty()) View.VISIBLE else View.GONE
        val today = LocalDate.now()
        val inflater = LayoutInflater.from(this)
        for (c in classes) {
            val row = inflater.inflate(R.layout.item_class, classList, false)
            row.findViewById<TextView>(R.id.class_name).text = c.name
            row.findViewById<TextView>(R.id.class_time).text = getString(
                R.string.class_time,
                c.day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                TIME.format(c.start),
                TIME.format(c.end),
            )
            val attendBtn = row.findViewById<MaterialButton>(R.id.attended_button)
            if (c.day == today.dayOfWeek) {
                val attended = store.isAttended(c.id, today)
                attendBtn.visibility = View.VISIBLE
                attendBtn.text = getString(if (attended) R.string.attended_today else R.string.mark_attended)
                attendBtn.isChecked = attended
                attendBtn.setOnClickListener {
                    store.setAttended(c.id, today, !attended)
                    if (!attended) Notifier.cancel(this, c.id)
                    refresh()
                }
            } else {
                attendBtn.visibility = View.GONE
            }
            row.setOnClickListener { editClass(c) }
            classList.addView(row)
        }
    }

    @SuppressLint("BatteryLife")
    private fun renderWarnings() {
        warnings.removeAllViews()
        if (!Notifier.canPost(this)) {
            addWarning(R.string.warn_notifications) {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            }
        }
        if (!Scheduler.canScheduleExact(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addWarning(R.string.warn_exact_alarm) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                )
            }
        }
        val power = getSystemService(PowerManager::class.java)
        if (!power.isIgnoringBatteryOptimizations(packageName)) {
            addWarning(R.string.warn_battery) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                )
            }
        }
        if (!Notifier.isUnionInstalled(this)) {
            addWarning(R.string.warn_union_missing) { startActivity(Notifier.unionIntent(this)) }
        }
        warnings.visibility = if (warnings.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun addWarning(text: Int, onClick: () -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_warning, warnings, false) as TextView
        view.setText(text)
        view.setOnClickListener {
            runCatching(onClick).onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        warnings.addView(view)
    }

    private fun editClass(existing: ClassSession?) {
        val view = layoutInflater.inflate(R.layout.dialog_class, null)
        val nameInput = view.findViewById<EditText>(R.id.name_input)
        val daySpinner = view.findViewById<Spinner>(R.id.day_spinner)
        val startBtn = view.findViewById<Button>(R.id.start_button)
        val endBtn = view.findViewById<Button>(R.id.end_button)

        val days = DayOfWeek.entries
        daySpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            days.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
        )

        var start = existing?.start ?: LocalTime.of(8, 0)
        var end = existing?.end ?: LocalTime.of(10, 30)
        nameInput.setText(existing?.name.orEmpty())
        daySpinner.setSelection(days.indexOf(existing?.day ?: LocalDate.now().dayOfWeek))

        fun updateButtons() {
            startBtn.text = getString(R.string.starts_at, TIME.format(start))
            endBtn.text = getString(R.string.ends_at, TIME.format(end))
        }
        updateButtons()
        startBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                val length = java.time.Duration.between(start, end)
                start = LocalTime.of(h, m)
                if (!length.isNegative && !length.isZero) end = start.plus(length)
                updateButtons()
            }, start.hour, start.minute, true).show()
        }
        endBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                end = LocalTime.of(h, m)
                updateButtons()
            }, end.hour, end.minute, true).show()
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_class else R.string.edit_class)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                store.delete(existing.id)
                Notifier.cancel(this, existing.id)
                refresh()
            }
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            when {
                name.isEmpty() -> nameInput.error = getString(R.string.error_name)
                start == end -> Toast.makeText(this, R.string.error_time, Toast.LENGTH_SHORT).show()
                else -> {
                    store.save(
                        ClassSession(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name,
                            day = days[daySpinner.selectedItemPosition],
                            start = start,
                            end = end,
                        ),
                    )
                    dialog.dismiss()
                    refresh()
                }
            }
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
