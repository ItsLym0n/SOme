package dev.attendance.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** UNION-styled home: header with stats, tile grid, today's classes; plus Schedule and Profile tabs. */
class MainActivity : AppCompatActivity() {
    private lateinit var store: ClassStore
    private lateinit var pages: List<View>
    private lateinit var tabs: List<FrameLayout>
    private var currentTab = TAB_HOME

    private data class Tile(val icon: Int, val label: Int, val onClick: () -> Unit)

    private data class SetupItem(
        val title: Int,
        val ok: Boolean,
        val okText: Int,
        val problemText: Int,
        val fix: () -> Unit,
    )

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private val exportFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) exportTo(uri)
        }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importFrom(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = ClassStore(this)
        Notifier.createChannel(this)

        pages = listOf(R.id.page_home, R.id.page_schedule, R.id.page_profile).map { findViewById(it) }
        tabs = listOf(R.id.tab_home, R.id.tab_schedule, R.id.tab_profile).map { findViewById(it) }
        tabs.forEachIndexed { i, tab -> tab.setOnClickListener { selectTab(i) } }
        currentTab = savedInstanceState?.getInt(STATE_TAB) ?: TAB_HOME
        selectTab(currentTab)
        applyInsets()

        findViewById<View>(R.id.greeting).setOnClickListener { editName() }
        findViewById<View>(R.id.setup_warning).setOnClickListener { selectTab(TAB_PROFILE) }
        findViewById<View>(R.id.add_button).setOnClickListener { editClass(null) }
        findViewById<View>(R.id.import_button).setOnClickListener(::showScheduleMenu)
        buildTiles()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, currentTab)
    }

    @Deprecated("Back goes to the Home tab first")
    override fun onBackPressed() {
        if (currentTab != TAB_HOME) selectTab(TAB_HOME) else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        Scheduler.scheduleNext(this)
        val setup = setupItems()
        renderHeader()
        renderReminderCard(setup)
        renderToday()
        renderWeek()
        renderProfile(setup)
    }

    // ---- Layout plumbing ----

    /** Header runs under the status bar like UNION's; the tab bar sits above the gesture bar. */
    private fun applyInsets() {
        val headers = listOf(R.id.header_home, R.id.header_schedule, R.id.header_profile)
            .map { findViewById<View>(it) }
        val baseTop = headers.map { it.paddingTop }
        val bar = findViewById<View>(R.id.bottom_bar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headers.forEachIndexed { i, h -> h.updatePadding(top = baseTop[i] + bars.top) }
            bar.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        pages.forEachIndexed { i, page -> page.visibility = if (i == index) View.VISIBLE else View.GONE }
        val active = ContextCompat.getColor(this, R.color.union_blue)
        val inactive = ContextCompat.getColor(this, R.color.tab_inactive)
        tabs.forEachIndexed { i, tab ->
            val selected = i == index
            tab.getChildAt(0).visibility = if (selected) View.VISIBLE else View.INVISIBLE
            (tab.getChildAt(1) as ImageView).imageTintList =
                ColorStateList.valueOf(if (selected) active else inactive)
            tab.isSelected = selected
        }
    }

    private fun buildTiles() {
        val tiles = listOf(
            Tile(R.drawable.tile_union, R.string.tile_open_union) { startActivity(Notifier.unionIntent(this)) },
            Tile(R.drawable.tile_add, R.string.tile_add) { editClass(null) },
            Tile(R.drawable.tile_paste, R.string.tile_paste) { importTimetable() },
            Tile(R.drawable.tile_import, R.string.tile_import) { launchImport() },
            Tile(R.drawable.tile_export, R.string.tile_export) { launchExport() },
            Tile(R.drawable.tile_share, R.string.tile_share) { shareSchedule() },
            Tile(R.drawable.tile_test, R.string.tile_test) { sendTest() },
            Tile(R.drawable.tile_profile, R.string.tile_setup) { selectTab(TAB_PROFILE) },
        )
        val grid = findViewById<LinearLayout>(R.id.tile_grid)
        tiles.chunked(4).forEach { rowTiles ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = -dp(5); marginEnd = -dp(5) }
            }
            rowTiles.forEach { tile ->
                val view = layoutInflater.inflate(R.layout.item_tile, row, false)
                view.findViewById<ImageView>(R.id.tile_icon).setImageResource(tile.icon)
                val label = getString(tile.label)
                view.findViewById<TextView>(R.id.tile_label).text = label
                view.findViewById<View>(R.id.tile_card).apply {
                    contentDescription = label.replace('\n', ' ')
                    setOnClickListener { tile.onClick() }
                }
                row.addView(view)
            }
            grid.addView(row)
        }
    }

    // ---- Home ----

    private fun renderHeader() {
        val name = store.userName
        findViewById<TextView>(R.id.greeting).text =
            if (name.isEmpty()) getString(R.string.greeting_no_name) else getString(R.string.greeting, name.substringBefore(' '))

        val today = LocalDate.now()
        val todays = store.classes().filter { it.day == today.dayOfWeek }
        val checked = todays.count { store.isAttended(it.id, today) }
        findViewById<TextView>(R.id.stat_today_value).text = todays.size.toString()
        findViewById<TextView>(R.id.stat_checked_value).text =
            if (todays.isEmpty()) getString(R.string.stat_none) else getString(R.string.stat_checked_value, checked, todays.size)
        val next = Scheduler.nextReminder(this)
        findViewById<TextView>(R.id.stat_next_value).text = next?.let {
            val at = Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault())
            if (at.toLocalDate() == today) TIME.format(at)
            else at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        } ?: getString(R.string.stat_none)
    }

    private fun renderReminderCard(setup: List<SetupItem>) {
        val next = Scheduler.nextReminder(this)
        findViewById<TextView>(R.id.next_text).text = if (next == null) {
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
        val problems = setup.count { !it.ok }
        findViewById<TextView>(R.id.setup_warning).apply {
            visibility = if (problems > 0) View.VISIBLE else View.GONE
            text = resources.getQuantityString(R.plurals.setup_issues, problems, problems)
        }
    }

    private fun renderToday() {
        val today = LocalDate.now()
        val list = findViewById<LinearLayout>(R.id.today_list)
        list.removeAllViews()
        val todays = store.classes().filter { it.day == today.dayOfWeek }
        findViewById<View>(R.id.today_empty).visibility = if (todays.isEmpty()) View.VISIBLE else View.GONE
        todays.forEach { list.addView(scheduleCard(list, it, today)) }
    }

    // ---- Schedule tab ----

    private fun renderWeek() {
        val classes = store.classes()
        findViewById<TextView>(R.id.week_summary).text =
            resources.getQuantityString(R.plurals.week_summary, classes.size, classes.size)
        findViewById<View>(R.id.week_empty).visibility = if (classes.isEmpty()) View.VISIBLE else View.GONE
        val list = findViewById<LinearLayout>(R.id.week_list)
        list.removeAllViews()
        classes.groupBy { it.day }.forEach { (day, dayClasses) ->
            val title = TextView(this, null, 0, R.style.Union_SectionTitle).apply {
                text = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(20); bottomMargin = dp(10) }
            }
            list.addView(title)
            dayClasses.forEach { list.addView(scheduleCard(list, it, null)) }
        }
    }

    /** A UNION-style class card. With [today] set it shows check-in status and the toggle. */
    private fun scheduleCard(parent: ViewGroup, c: ClassSession, today: LocalDate?): View {
        val view = layoutInflater.inflate(R.layout.item_schedule, parent, false)
        view.findViewById<TextView>(R.id.class_name).text = c.name
        view.findViewById<TextView>(R.id.class_time).text =
            getString(R.string.time_range, TIME.format(c.start), TIME.format(c.end))
        val status = view.findViewById<TextView>(R.id.class_status)
        val button = view.findViewById<MaterialButton>(R.id.attended_button)

        if (today == null) {
            status.text = getString(R.string.reminder_at, TIME.format(c.start.minusMinutes(Scheduler.LEAD_MINUTES)))
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(status, R.drawable.ic_alarm, 0, 0, 0)
        } else {
            val attended = store.isAttended(c.id, today)
            val now = LocalTime.now()
            val ended = c.end > c.start && now >= c.end
            val (text, icon, color) = when {
                attended -> Triple(R.string.status_checked_in, R.drawable.ic_check_circle, R.color.ok_green)
                ended -> Triple(R.string.status_ended, R.drawable.ic_pending, R.color.text_label)
                now < c.start.minusMinutes(Scheduler.LEAD_MINUTES) ->
                    Triple(R.string.status_upcoming, R.drawable.ic_pending, R.color.union_blue)
                else -> Triple(R.string.status_not_yet, R.drawable.ic_warning, R.color.union_orange)
            }
            status.setText(text)
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(status, icon, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(status, ContextCompat.getColorStateList(this, color))
            button.visibility = View.VISIBLE
            button.setText(if (attended) R.string.undo_attended else R.string.mark_attended)
            button.setOnClickListener {
                store.setAttended(c.id, today, !attended)
                if (!attended) Notifier.cancel(this, c.id)
                refresh()
            }
        }
        view.setOnClickListener { editClass(c) }
        return view
    }

    // ---- Profile tab ----

    private fun renderProfile(setup: List<SetupItem>) {
        val name = store.userName
        findViewById<TextView>(R.id.profile_name).text = name.ifEmpty { getString(R.string.tab_profile) }

        val setupList = findViewById<LinearLayout>(R.id.setup_list)
        setupList.removeAllViews()
        addSettingRow(setupList, R.drawable.ic_person, R.color.union_blue, getString(R.string.your_name),
            name.ifEmpty { getString(R.string.your_name_hint) }) { editName() }
        setup.forEach { item ->
            addSettingRow(
                setupList,
                if (item.ok) R.drawable.ic_check_circle else R.drawable.ic_warning,
                if (item.ok) R.color.ok_green else R.color.union_orange,
                getString(item.title),
                getString(if (item.ok) item.okText else item.problemText),
            ) { runCatching(item.fix).onFailure { openAppSettings() } }
        }

        val more = findViewById<LinearLayout>(R.id.more_list)
        more.removeAllViews()
        addSettingRow(more, R.drawable.ic_alarm, R.color.union_blue,
            getString(R.string.more_test), getString(R.string.more_test_sub)) { sendTest() }
        addSettingRow(more, R.drawable.ic_edit, R.color.union_blue,
            getString(R.string.more_channel), getString(R.string.more_channel_sub)) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private fun addSettingRow(
        parent: LinearLayout, icon: Int, tint: Int, title: String, subtitle: String, onClick: () -> Unit,
    ) {
        if (parent.childCount > 0) {
            parent.addView(View(this).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    .apply { marginStart = dp(58) }
            })
        }
        val row = layoutInflater.inflate(R.layout.item_setting, parent, false)
        row.findViewById<ImageView>(R.id.setting_icon).apply {
            setImageResource(icon)
            imageTintList = ContextCompat.getColorStateList(context, tint)
        }
        row.findViewById<TextView>(R.id.setting_title).text = title
        row.findViewById<TextView>(R.id.setting_subtitle).text = subtitle
        row.setOnClickListener { onClick() }
        parent.addView(row)
    }

    @SuppressLint("BatteryLife")
    private fun setupItems(): List<SetupItem> {
        val items = mutableListOf(
            SetupItem(R.string.setup_notifications, Notifier.canPost(this), R.string.setup_ok, R.string.warn_notifications) {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items += SetupItem(R.string.setup_exact, Scheduler.canScheduleExact(this), R.string.setup_ok, R.string.warn_exact_alarm) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        val power = getSystemService(PowerManager::class.java)
        items += SetupItem(
            R.string.setup_battery, power.isIgnoringBatteryOptimizations(packageName),
            R.string.setup_battery_ok, R.string.warn_battery,
        ) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        items += SetupItem(
            R.string.setup_union, Notifier.isUnionInstalled(this),
            R.string.setup_union_ok, R.string.warn_union_missing,
        ) { startActivity(Notifier.unionIntent(this)) }
        return items
    }

    private fun openAppSettings() =
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    private fun editName() {
        val input = EditText(this).apply {
            setText(store.userName)
            hint = getString(R.string.your_name)
            setSingleLine()
            inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS or android.text.InputType.TYPE_CLASS_TEXT
        }
        val frame = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.your_name)
            .setView(frame)
            .setPositiveButton(R.string.save) { _, _ ->
                store.userName = input.text.toString()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendTest() {
        if (Notifier.canPost(this)) Notifier.showTest(this)
        else Toast.makeText(this, R.string.warn_notifications, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

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

    private fun showScheduleMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, MENU_PASTE, 0, R.string.import_timetable)
        menu.menu.add(0, MENU_IMPORT_FILE, 1, R.string.import_file)
        val hasClasses = store.classes().isNotEmpty()
        menu.menu.add(0, MENU_EXPORT_FILE, 2, R.string.export_file).isEnabled = hasClasses
        menu.menu.add(0, MENU_SHARE, 3, R.string.share_schedule).isEnabled = hasClasses
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_PASTE -> importTimetable()
                MENU_IMPORT_FILE -> launchImport()
                MENU_EXPORT_FILE -> launchExport()
                MENU_SHARE -> shareSchedule()
            }
            true
        }
        menu.show()
    }

    private fun launchImport() =
        importFile.launch(arrayOf("application/json", "text/*", "application/octet-stream"))

    private fun launchExport() {
        if (store.classes().isEmpty()) Toast.makeText(this, R.string.empty, Toast.LENGTH_SHORT).show()
        else exportFile.launch("class-schedule.json")
    }

    private fun exportTo(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri, "wt")!!.use {
                it.write(ScheduleFile.toJson(store.classes()).toByteArray())
            }
            Toast.makeText(this, R.string.export_done, Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.file_error, e.message), Toast.LENGTH_LONG).show()
        } catch (e: NullPointerException) {
            Toast.makeText(this, getString(R.string.file_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun importFrom(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.file_error, e.message), Toast.LENGTH_LONG).show()
            return
        } catch (e: NullPointerException) {
            Toast.makeText(this, getString(R.string.file_error, e.message), Toast.LENGTH_LONG).show()
            return
        }
        val result = ScheduleFile.read(text)
        if (result.classes.isEmpty()) {
            Toast.makeText(this, R.string.import_none, Toast.LENGTH_LONG).show()
        } else {
            confirmImport(result)
        }
    }

    private fun shareSchedule() {
        if (store.classes().isEmpty()) {
            Toast.makeText(this, R.string.empty, Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            .putExtra(Intent.EXTRA_TEXT, ScheduleFile.toText(store.classes()))
        startActivity(Intent.createChooser(send, getString(R.string.share_schedule)))
    }

    private fun importTimetable() {
        val view = layoutInflater.inflate(R.layout.dialog_import, null)
        val input = view.findViewById<EditText>(R.id.import_input)
        view.findViewById<View>(R.id.paste_button).setOnClickListener {
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
            if (!text.isNullOrBlank()) input.setText(text)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.import_timetable)
            .setView(view)
            .setPositiveButton(R.string.import_read, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val result = ScheduleParser.parse(input.text.toString())
            if (result.classes.isEmpty()) {
                Toast.makeText(this, R.string.import_none, Toast.LENGTH_LONG).show()
            } else {
                dialog.dismiss()
                confirmImport(result)
            }
        }
    }

    private fun confirmImport(result: ScheduleParser.Result) {
        val preview = buildString {
            result.classes.sortedWith(compareBy({ it.day }, { it.start })).forEach {
                append(it.day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                append("  ").append(TIME.format(it.start)).append("–").append(TIME.format(it.end))
                append("  ").append(it.name).append('\n')
            }
            if (result.skipped.isNotEmpty()) {
                append(getString(R.string.import_skipped, result.skipped.joinToString("\n")))
            }
        }.trimEnd()

        fun save(replace: Boolean) {
            if (replace) store.classes().forEach {
                store.delete(it.id)
                Notifier.cancel(this, it.id)
            }
            result.classes.forEach(store::save)
            Toast.makeText(this, resources.getQuantityString(R.plurals.import_done, result.classes.size, result.classes.size), Toast.LENGTH_SHORT).show()
            refresh()
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.import_preview_title, result.classes.size, result.classes.size))
            .setMessage(preview)
            .setPositiveButton(R.string.import_replace) { _, _ -> save(replace = true) }
            .setNeutralButton(R.string.cancel, null)
        if (store.classes().isNotEmpty()) {
            builder.setNegativeButton(R.string.import_add) { _, _ -> save(replace = false) }
        }
        builder.show()
    }

    private companion object {
        const val TAB_HOME = 0
        const val TAB_PROFILE = 2
        const val STATE_TAB = "tab"
        const val MENU_PASTE = 1
        const val MENU_IMPORT_FILE = 2
        const val MENU_EXPORT_FILE = 3
        const val MENU_SHARE = 4
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
