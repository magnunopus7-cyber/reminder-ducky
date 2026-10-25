package app.reminderducky

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
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

        preview = DuckGLView(this).apply {
            renderer.look = prefs.look
            renderer.facingRight = false
            renderer.sleepsAtNight = false
            contentDescription = getString(R.string.preview_desc)
        }
        findViewById<FrameLayout>(R.id.previewHolder)
            .addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        preview.setOnClickListener {
            preview.renderer.squash()
            if (prefs.tapQuack) Quack.play(this, 0.6f)
            preview.renderer.react(listOf(Mood.HAPPY, Mood.EXCITED, Mood.SURPRISED, Mood.SLEEPY).random(), 2f)
        }

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

        findViewById<MaterialSwitch>(R.id.switchQuack).apply {
            isChecked = prefs.tapQuack
            setOnCheckedChangeListener { _, checked ->
                prefs.tapQuack = checked
                if (checked) Quack.play(this@MainActivity, 0.6f)
            }
        }
        findViewById<MaterialSwitch>(R.id.switchDuck).setOnCheckedChangeListener { v, checked ->
            if (!v.isPressed) return@setOnCheckedChangeListener
            prefs.showDuck = checked
            if (checked && DuckService.canShow(this)) DuckService.start(this) else DuckService.stop(this)
        }

        findViewById<View>(R.id.btnAdd).setOnClickListener { showEditDialog(null) }
        findViewById<View>(R.id.btnDone).setOnClickListener { finish() }
        findViewById<View>(R.id.btnTest).setOnClickListener {
            val r = Reminder(Reminder.newId(), getString(R.string.test_text), System.currentTimeMillis() + 60_000L, icon = "🦆")
            store.save(r)
            Alarms.schedule(this, r)
            renderList()
            Toast.makeText(this, R.string.test_toast, Toast.LENGTH_LONG).show()
        }

        buildCustomizer()
        setUpTodos()
        handleAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAction(intent)
    }

    private fun handleAction(intent: Intent?) {
        when (intent?.action) {
            ACTION_ADD -> findViewById<View>(R.id.btnAdd).post { showEditDialog(null) }
            ACTION_CUSTOMIZE -> scrollTo(R.id.customizeCard)
            ACTION_TODO -> {
                scrollTo(R.id.todoCard)
                findViewById<View>(R.id.todoInput).let { input ->
                    input.post {
                        input.requestFocus()
                        getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                            .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        }
        intent?.action = null
    }

    private fun scrollTo(id: Int) {
        val sv = findViewById<ScrollView>(R.id.scroll)
        val target = findViewById<View>(id)
        sv.post {
            val r = android.graphics.Rect()
            target.getDrawingRect(r)
            sv.offsetDescendantRectToMyCoords(target, r)
            sv.smoothScrollTo(0, r.top - (16 * resources.displayMetrics.density).roundToInt())
        }
    }

    // ---- To-do list ----

    private lateinit var todos: TodoStore

    private fun setUpTodos() {
        todos = TodoStore(this)
        val input = findViewById<TextInputEditText>(R.id.todoInput)
        fun add() {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return
            todos.save(Todo(System.currentTimeMillis(), text))
            input.setText("")
            preview.renderer.react(Mood.HAPPY, 1.2f)
            renderTodos()
        }
        findViewById<View>(R.id.todoAdd).setOnClickListener { add() }
        input.setOnEditorActionListener { _, _, _ -> add(); true }
        findViewById<View>(R.id.todoClear).setOnClickListener {
            todos.clearDone()
            renderTodos()
        }
        renderTodos()
    }

    private fun renderTodos() {
        val container = findViewById<LinearLayout>(R.id.todoList)
        container.removeAllViews()
        val list = todos.all().sortedBy { it.done }
        findViewById<View>(R.id.todoEmpty).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.todoClear).visibility = if (list.any { it.done }) View.VISIBLE else View.GONE
        for (t in list) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_todo, container, false)
            val check = row.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.todoCheck)
            check.text = t.text
            check.isChecked = t.done
            check.paintFlags = if (t.done) check.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            else check.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            check.setOnCheckedChangeListener { _, checked ->
                todos.save(t.copy(done = checked))
                if (checked) {
                    preview.renderer.react(Mood.HAPPY, 1.5f)
                    if (prefs.tapQuack) Quack.play(this, 0.5f)
                }
                container.post { renderTodos() }
            }
            row.findViewById<View>(R.id.todoDelete).setOnClickListener {
                todos.delete(t.id)
                renderTodos()
            }
            container.addView(row)
        }
    }

    override fun onResume() {
        super.onResume()
        preview.onResume()
        refresh()
        if (prefs.showDuck && DuckService.canShow(this) && DuckService.instance == null) DuckService.start(this)
        // The floating duck would cover this screen's buttons; it comes back when you leave.
        DuckService.setAppOpen(true)
    }

    override fun onPause() {
        DuckService.setAppOpen(false)
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

    // ---- Look ----

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun applyLook(look: DuckLook) {
        prefs.look = look
        preview.renderer.look = look
        preview.renderer.squash()
        preview.renderer.react(Mood.HAPPY, 1.5f)
        DuckService.instance?.refreshAppearance()
        syncCustomizer()
    }

    private val skinCards = mutableMapOf<DuckSkin, MaterialCardView>()
    private val colorDots = mutableMapOf<DuckColor, View>()
    private val materialChips = mutableMapOf<DuckMaterial, Chip>()
    private val accessoryChips = mutableMapOf<DuckAccessory, Chip>()
    private var syncing = false

    private fun buildCustomizer() {
        val skinRow = findViewById<LinearLayout>(R.id.skinRow)
        for (skin in DuckSkin.entries) {
            val card = MaterialCardView(this).apply {
                radius = dp(20).toFloat()
                strokeWidth = dp(2)
                setOnClickListener { applyLook(skin.look) }
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(14), dp(14), dp(14), dp(12))
            }
            col.addView(View(this).apply { background = swatch(skin.look.material, skin.look.color, dp(56)) },
                LinearLayout.LayoutParams(dp(56), dp(56)))
            col.addView(TextView(this).apply {
                setText(skin.label)
                setTextColor(ContextCompat.getColor(context, R.color.ink))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            })
            col.addView(TextView(this).apply {
                setText(skin.mood)
                setTextColor(ContextCompat.getColor(context, R.color.ink_soft))
                textSize = 12f
            })
            card.addView(col, FrameLayout.LayoutParams(dp(112), FrameLayout.LayoutParams.WRAP_CONTENT))
            skinRow.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = dp(10); topMargin = dp(4); bottomMargin = dp(4) })
            skinCards[skin] = card
        }

        val materialGroup = findViewById<ChipGroup>(R.id.materialGroup)
        for (m in DuckMaterial.entries) {
            materialChips[m] = filterChip(getString(m.label)).also { materialGroup.addView(it) }
        }
        materialGroup.setOnCheckedStateChangeListener { _, ids ->
            if (syncing) return@setOnCheckedStateChangeListener
            val m = materialChips.entries.firstOrNull { it.value.id == ids.firstOrNull() }?.key ?: return@setOnCheckedStateChangeListener
            applyLook(prefs.look.copy(material = m))
        }

        val colorRow = findViewById<LinearLayout>(R.id.colorRow)
        for (c in DuckColor.entries) {
            val dot = View(this).apply {
                contentDescription = getString(c.label)
                setOnClickListener { applyLook(prefs.look.copy(color = c)) }
            }
            colorRow.addView(dot, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
            colorDots[c] = dot
        }

        val accessoryGroup = findViewById<ChipGroup>(R.id.accessoryGroup)
        for (a in DuckAccessory.entries) {
            accessoryChips[a] = filterChip(getString(a.label)).also { accessoryGroup.addView(it) }
        }
        accessoryGroup.setOnCheckedStateChangeListener { _, ids ->
            if (syncing) return@setOnCheckedStateChangeListener
            val a = accessoryChips.entries.firstOrNull { it.value.id == ids.firstOrNull() }?.key ?: return@setOnCheckedStateChangeListener
            applyLook(prefs.look.copy(accessory = a))
        }

        val sizeGroup = findViewById<ChipGroup>(R.id.sizeGroup)
        val sizeChips = DuckSize.entries.associateWith { s -> filterChip(getString(s.label)).also { sizeGroup.addView(it) } }
        sizeGroup.check(sizeChips.getValue(prefs.size).id)
        sizeGroup.setOnCheckedStateChangeListener { _, ids ->
            prefs.size = sizeChips.entries.firstOrNull { it.value.id == ids.firstOrNull() }?.key ?: return@setOnCheckedStateChangeListener
            DuckService.instance?.refreshAppearance()
        }

        // Language: follow the phone, or pick English or Korean just for Ducky.
        val languageGroup = findViewById<ChipGroup>(R.id.languageGroup)
        val languages = listOf("" to getString(R.string.lang_auto), "en" to "English", "ko" to "한국어")
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
        for ((tag, name) in languages) {
            val chip = filterChip(name).also { languageGroup.addView(it) }
            if (tag == current) languageGroup.check(chip.id)
            chip.setOnClickListener {
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                )
            }
        }
        syncCustomizer()
    }

    private fun filterChip(label: String) =
        (LayoutInflater.from(this).inflate(R.layout.chip_filter, null) as Chip).apply {
            id = View.generateViewId()
            text = label
        }

    /** Marks the current skin, material, colour and accessory as selected. */
    private fun syncCustomizer() {
        syncing = true
        val look = prefs.look
        val accent = ContextCompat.getColor(this, R.color.duck_yellow)
        for ((skin, card) in skinCards) {
            card.strokeColor = if (skin.look == look) accent else Color.TRANSPARENT
        }
        materialChips[look.material]?.isChecked = true
        accessoryChips[look.accessory]?.isChecked = true
        for ((c, dot) in colorDots) {
            dot.background = swatch(look.material, c, dp(40), selected = c == look.color)
        }
        syncing = false
    }

    /** A round swatch that hints at the material: shine for metal, rainbow for crystal. */
    private fun swatch(material: DuckMaterial, color: DuckColor, size: Int, selected: Boolean = false): GradientDrawable {
        val body = 0xFF000000.toInt() or color.body
        val head = 0xFF000000.toInt() or color.head
        val colors = when (material) {
            DuckMaterial.METALLIC -> intArrayOf(Color.WHITE, body, darken(body))
            DuckMaterial.CRYSTAL -> intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6D9FF.toInt(), 0xFFCDEBFF.toInt(), 0xFFFFE3F1.toInt())
            else -> intArrayOf(lighten(head), body)
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = size * 0.75f
            setGradientCenter(0.35f, 0.3f)
            if (selected) setStroke(dp(3), ContextCompat.getColor(this@MainActivity, R.color.ink))
            else setStroke(dp(1), 0x22000000)
        }
    }

    private fun lighten(c: Int) = Color.rgb(
        (Color.red(c) + 255) / 2, (Color.green(c) + 255) / 2, (Color.blue(c) + 255) / 2
    )

    private fun darken(c: Int) = Color.rgb(Color.red(c) * 6 / 10, Color.green(c) * 6 / 10, Color.blue(c) * 6 / 10)

    // ---- Reminders ----

    private fun renderList() {
        val container = findViewById<LinearLayout>(R.id.listContainer)
        container.removeAllViews()
        val list = store.all().sortedWith(compareByDescending<Reminder> { it.fired }.thenBy { it.timeMillis })
        findViewById<View>(R.id.emptyText).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        for (r in list) {
            val row = inflater.inflate(R.layout.item_reminder, container, false)
            row.findViewById<TextView>(R.id.itemIcon).text = r.icon
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
        if (r.fired) return getString(R.string.due_now)
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE d MMM " + if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a")
        val time = DateFormat.format(pattern, r.timeMillis).toString()
        return if (r.repeat == Repeat.NONE) time else "$time · ${getString(r.repeat.label)}"
    }

    @SuppressLint("InflateParams")
    private fun showEditDialog(existing: Reminder?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add, null)
        val input = view.findViewById<TextInputEditText>(R.id.inputText)
        val btnDate = view.findViewById<MaterialButton>(R.id.btnDate)
        val timeWheel = view.findViewById<android.widget.TimePicker>(R.id.timeWheel)
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
        timeWheel.setIs24HourView(DateFormat.is24HourFormat(this))
        timeWheel.hour = cal.get(Calendar.HOUR_OF_DAY)
        timeWheel.minute = cal.get(Calendar.MINUTE)
        timeWheel.setOnTimeChangedListener { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, min)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.new_reminder else R.string.edit_reminder)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        // The 3D preview sits on its own layer above dialogs, so hide it meanwhile.
        dialog.setOnShowListener {
            preview.visibility = View.INVISIBLE
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = view.findViewById<TextView>(R.id.dialogError)
                fun fail(msg: String) {
                    error.text = msg
                    error.visibility = View.VISIBLE
                }
                try {
                    val text = input.text?.toString()?.trim().orEmpty()
                    if (text.isEmpty()) {
                        fail(getString(R.string.err_empty))
                        return@setOnClickListener
                    }
                    val repeat = repeatChips[repeatGroup.checkedChipId] ?: Repeat.NONE
                    val icon = existing?.icon ?: Reminder.DEFAULT_ICON
                    val now = System.currentTimeMillis()
                    var time = cal.timeInMillis
                    if (time <= now) {
                        if (repeat == Repeat.NONE) {
                            fail(getString(R.string.err_past))
                            return@setOnClickListener
                        }
                        time = repeat.nextAfter(time, now)
                    }
                    val r = Reminder(existing?.id ?: Reminder.newId(), text, time, repeat, icon = icon)
                    store.save(r)
                    existing?.let { Alarms.cancel(this, it.id) }
                    Alarms.schedule(this, r)
                    renderList()
                    preview.renderer.react(Mood.HAPPY, 1.5f)
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    fail(getString(R.string.err_save, "${e.javaClass.simpleName}: ${e.message}"))
                }
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

    companion object {
        const val ACTION_ADD = "app.reminderducky.ADD"
        const val ACTION_CUSTOMIZE = "app.reminderducky.CUSTOMIZE"
        const val ACTION_TODO = "app.reminderducky.TODO"
    }
}
