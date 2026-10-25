package app.reminderducky

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: ReminderStore
    private lateinit var preview: DuckGLView

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private val finishChips = mapOf(
        R.id.finishGold to DuckFinish.GOLD,
        R.id.finishChrome to DuckFinish.CHROME,
        R.id.finishRose to DuckFinish.ROSE,
        R.id.finishMidnight to DuckFinish.MIDNIGHT,
    )
    private val sizeChips = mapOf(
        R.id.sizeSmall to DuckSize.SMALL,
        R.id.sizeMedium to DuckSize.MEDIUM,
        R.id.sizeLarge to DuckSize.LARGE,
    )
    private val repeatChips = mapOf(
        R.id.repeatNone to Repeat.NONE,
        R.id.repeatDaily to Repeat.DAILY,
        R.id.repeatWeekly to Repeat.WEEKLY,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        store = ReminderStore(this)

        preview = DuckGLView(this).apply { renderer.finish = prefs.finish; renderer.facingRight = true }
        val previewSize = (150 * resources.displayMetrics.density).roundToInt()
        findViewById<FrameLayout>(R.id.previewHolder)
            .addView(preview, FrameLayout.LayoutParams(previewSize, previewSize, Gravity.CENTER))
        preview.setOnClickListener { preview.renderer.squash() }

        findViewById<View>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<View>(R.id.btnNotif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        findViewById<View>(R.id.btnExact).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        findViewById<View>(R.id.btnBattery).setOnClickListener { askIgnoreBatteryOptimizations() }

        findViewById<MaterialSwitch>(R.id.switchDuck).setOnCheckedChangeListener { _, checked ->
            prefs.showDuck = checked
            if (checked && DuckService.canShow(this)) DuckService.start(this) else DuckService.stop(this)
        }

        findViewById<ChipGroup>(R.id.finishGroup).apply {
            check(finishChips.entries.first { it.value == prefs.finish }.key)
            setOnCheckedStateChangeListener { _, ids ->
                val f = finishChips[ids.firstOrNull()] ?: return@setOnCheckedStateChangeListener
                prefs.finish = f
                preview.renderer.finish = f
                preview.renderer.squash()
                DuckService.instance?.refreshAppearance()
            }
        }
        findViewById<ChipGroup>(R.id.sizeGroup).apply {
            check(sizeChips.entries.first { it.value == prefs.size }.key)
            setOnCheckedStateChangeListener { _, ids ->
                prefs.size = sizeChips[ids.firstOrNull()] ?: return@setOnCheckedStateChangeListener
                DuckService.instance?.refreshAppearance()
            }
        }

        findViewById<View>(R.id.btnAdd).setOnClickListener { showEditDialog(null) }
        findViewById<View>(R.id.btnTest).setOnClickListener {
            val r = Reminder(Reminder.newId(), "This is your test reminder. Quack!", System.currentTimeMillis() + 60_000L)
            store.save(r)
            Alarms.schedule(this, r)
            refresh()
            Toast.makeText(this, "The duck will quack in about a minute. You can leave the app.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        preview.onResume()
        refresh()
        if (prefs.showDuck && DuckService.canShow(this) && DuckService.instance == null) DuckService.start(this)
    }

    override fun onPause() {
        preview.onPause()
        super.onPause()
    }

    private fun refresh() {
        val overlayOk = DuckService.canShow(this)
        val notifOk = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val exactOk = Alarms.canScheduleExact(this)
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        findViewById<View>(R.id.btnOverlay).visibility = if (overlayOk) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnNotif).visibility = if (notifOk) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnExact).visibility = if (exactOk) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnBattery).visibility = if (batteryOk) View.GONE else View.VISIBLE
        findViewById<View>(R.id.setupCard).visibility =
            if (overlayOk && notifOk && exactOk && batteryOk) View.GONE else View.VISIBLE

        findViewById<MaterialSwitch>(R.id.switchDuck).apply {
            isEnabled = overlayOk
            isChecked = prefs.showDuck && overlayOk
        }
        renderList()
    }

    private fun renderList() {
        val container = findViewById<LinearLayout>(R.id.listContainer)
        container.removeAllViews()
        val list = store.all().sortedWith(compareByDescending<Reminder> { it.fired }.thenBy { it.timeMillis })
        findViewById<View>(R.id.emptyText).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        for (r in list) {
            val row = inflater.inflate(R.layout.item_reminder, container, false)
            row.findViewById<TextView>(R.id.itemText).text = r.text
            row.findViewById<TextView>(R.id.itemWhen).text = describeTime(r)
            row.findViewById<View>(R.id.itemDelete).setOnClickListener {
                Alarms.cancel(this, r.id)
                ReminderActions.done(this, r.id)
                store.delete(r.id)
                renderList()
            }
            row.setOnClickListener { showEditDialog(r) }
            container.addView(row)
        }
    }

    private fun describeTime(r: Reminder): String {
        if (r.fired) return "Due now. Tap Delete when it's done."
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE d MMM " + if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a")
        val time = DateFormat.format(pattern, r.timeMillis).toString()
        return if (r.repeat == Repeat.NONE) time else "$time · ${r.repeat.label.lowercase()}"
    }

    @SuppressLint("InflateParams")
    private fun showEditDialog(existing: Reminder?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add, null)
        val input = view.findViewById<TextInputEditText>(R.id.inputText)
        val btnDate = view.findViewById<MaterialButton>(R.id.btnDate)
        val btnTime = view.findViewById<MaterialButton>(R.id.btnTime)
        val repeatGroup = view.findViewById<ChipGroup>(R.id.repeatGroup)

        val cal = Calendar.getInstance().apply {
            if (existing != null) {
                timeInMillis = existing.timeMillis
            } else {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        fun updateButtons() {
            btnDate.text = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE d MMM"), cal)
            btnTime.text = DateFormat.getTimeFormat(this).format(cal.time)
        }
        updateButtons()
        input.setText(existing?.text ?: "")
        repeatGroup.check(repeatChips.entries.first { it.value == (existing?.repeat ?: Repeat.NONE) }.key)

        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                updateButtons()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                updateButtons()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), DateFormat.is24HourFormat(this)).show()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "New reminder" else "Edit reminder")
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        // The 3D preview sits on its own layer above dialogs, so hide it meanwhile.
        dialog.setOnShowListener {
            preview.visibility = View.INVISIBLE
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) {
                    input.error = "Type what to remind you about"
                    return@setOnClickListener
                }
                val repeat = repeatChips[repeatGroup.checkedChipId] ?: Repeat.NONE
                val now = System.currentTimeMillis()
                var time = cal.timeInMillis
                if (time <= now) {
                    if (repeat == Repeat.NONE) {
                        Toast.makeText(this, "Pick a time in the future", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    time = repeat.nextAfter(time, now)
                }
                existing?.let {
                    Alarms.cancel(this, it.id)
                    ReminderActions.done(this, it.id)
                }
                val r = Reminder(existing?.id ?: Reminder.newId(), text, time, repeat)
                store.save(r)
                Alarms.schedule(this, r)
                renderList()
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { preview.visibility = View.VISIBLE }
        dialog.show()
    }

    @SuppressLint("BatteryLife")
    private fun askIgnoreBatteryOptimizations() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        try {
            startActivity(direct)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
