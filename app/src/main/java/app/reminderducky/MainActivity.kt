package app.reminderducky

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
            renderer.sleepsWhenIdle = false
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
            ACTION_CUSTOMIZE -> {
                unfold("customizeCard")
                scrollTo(R.id.customizeCard)
            }
            ACTION_TODO -> {
                unfold("todoCard")
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
        val input = findViewById<android.widget.EditText>(R.id.todoInput)
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
        showChosenDuck(Mood.HAPPY)
    }

    private fun showChosenDuck(mood: Mood) {
        preview.renderer.look = prefs.look
        preview.renderer.squash()
        preview.renderer.react(mood, 1.5f)
        DuckService.instance?.refreshAppearance()
        syncCustomizer()
    }

    // ---- Folding sections ----

    /** Tapping [key]Head shows or hides [key]Body; the choice is remembered. */
    private fun foldable(key: String, openByDefault: Boolean) {
        val head = findViewById<View>(resources.getIdentifier("${key}Head", "id", packageName))
        val body = findViewById<View>(resources.getIdentifier("${key}Body", "id", packageName))
        val chevron = findViewById<View>(resources.getIdentifier("${key}Chevron", "id", packageName))
        fun show(open: Boolean) {
            body.visibility = if (open) View.VISIBLE else View.GONE
            chevron.rotation = if (open) 180f else 0f
        }
        show(prefs.isOpen(key, openByDefault))
        head.contentDescription = getString(R.string.collapse_toggle)
        head.setOnClickListener {
            val open = body.visibility != View.VISIBLE
            prefs.setOpen(key, open)
            show(open)
        }
    }

    /** Opens a section, for example when the duck's menu sends you straight to it. */
    private fun unfold(key: String) {
        prefs.setOpen(key, true)
        findViewById<View>(resources.getIdentifier("${key}Body", "id", packageName)).visibility = View.VISIBLE
        findViewById<View>(resources.getIdentifier("${key}Chevron", "id", packageName)).rotation = 180f
    }

    private fun setUpFolds() {
        foldable("setupCard", true)
        foldable("remindersCard", true)
        foldable("todoCard", true)
        foldable("skinsCard", false)
        foldable("customizeCard", false)
        for (key in listOf("material", "accessory", "tone", "size", "language")) foldable(key, false)
    }

    // ---- Picture options ----

    private val materialCards = mutableMapOf<DuckMaterial, MaterialCardView>()
    private val colorCards = mutableMapOf<DuckColor, MaterialCardView>()
    private val accessoryCards = mutableMapOf<DuckAccessory, MaterialCardView>()
    private val sizeCards = mutableMapOf<DuckSize, MaterialCardView>()

    /** A small card with a picture and a name, outlined when it is the current choice. */
    private fun optionCard(row: ViewGroup, image: Int, label: Int, imageDp: Int = 64, onPick: () -> Unit): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            strokeWidth = dp(2)
            cardElevation = dp(1).toFloat()
            setOnClickListener { onPick() }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        val box = FrameLayout(this)
        box.addView(ImageView(this).apply {
            setImageResource(image)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(imageDp), dp(imageDp), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM))
        col.addView(box, LinearLayout.LayoutParams(dp(72), dp(72)))
        col.addView(TextView(this).apply {
            setText(label)
            setTextColor(ContextCompat.getColor(context, R.color.ink))
            textSize = 13f
            gravity = Gravity.CENTER
            // Never cut a name off: wrap onto as many lines as it needs, breaking Korean between words.
            if (Build.VERSION.SDK_INT >= 33) lineBreakWordStyle = android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
            setPadding(0, dp(4), 0, 0)
        }, LinearLayout.LayoutParams(dp(84), LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(col)
        row.addView(card, ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginEnd = dp(8); topMargin = dp(4); bottomMargin = dp(4); marginStart = dp(2) })
        return card
    }

    /** Puts a round + at the right end of a sideways row while more options are hidden there. */
    private fun addMoreHint(row: View) {
        val scroll = row.parent as android.widget.HorizontalScrollView
        val holder = scroll.parent as ViewGroup
        val index = holder.indexOfChild(scroll)
        holder.removeView(scroll)
        val frame = FrameLayout(this)
        frame.addView(scroll)
        val plus = ImageView(this).apply {
            setImageResource(R.drawable.ic_more_plus)
            contentDescription = getString(R.string.more_options)
            setOnClickListener { scroll.smoothScrollBy(dp(200), 0) }
        }
        frame.addView(plus, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.END or Gravity.CENTER_VERTICAL))
        holder.addView(frame, index, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        fun update() {
            plus.visibility = if (scroll.canScrollHorizontally(1)) View.VISIBLE else View.GONE
        }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> update() }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
    }

    /**
     * Shows only one line of a wrapping row of cards, keeping the chosen one in view,
     * with a round + at the right end that opens the rest (and a − to close them again).
     */
    private fun showFew(row: ViewGroup, cards: List<MaterialCardView>, chosen: () -> MaterialCardView?) {
        var open = false
        val holder = FrameLayout(this)
        val plus = ImageView(this)
        holder.addView(plus, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        row.addView(holder, ViewGroup.MarginLayoutParams(dp(44), dp(44)).apply { topMargin = dp(4); marginStart = dp(2) })
        var step = 0
        var cardHeight = 0
        fun apply() {
            val first = cards.firstOrNull() ?: return
            if (step == 0 && first.width > 0) {
                val lp = first.layoutParams as ViewGroup.MarginLayoutParams
                step = first.width + lp.marginStart + lp.marginEnd + dp(8)
                cardHeight = first.height
            }
            if (row.width == 0 || step == 0) return
            val fit = ((row.width - row.paddingStart - row.paddingEnd - dp(52)) / step).coerceAtLeast(1)
            holder.visibility = if (cards.size > fit || open) View.VISIBLE else View.GONE
            (holder.layoutParams as ViewGroup.MarginLayoutParams).height = cardHeight
            plus.setImageResource(if (open) R.drawable.ic_more_minus else R.drawable.ic_more_plus)
            plus.contentDescription = getString(if (open) R.string.fewer_options else R.string.more_options)
            val pick = chosen()
            val pickAt = cards.indexOf(pick)
            for ((i, card) in cards.withIndex()) {
                val shown = open || cards.size <= fit ||
                    (if (pickAt >= fit) (i < fit - 1 || card == pick) else i < fit)
                card.visibility = if (shown) View.VISIBLE else View.GONE
            }
            holder.requestLayout()
        }
        holder.setOnClickListener { open = !open; apply() }
        plus.setOnClickListener { open = !open; apply() }
        row.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ -> if (r - l != or_ - ol) row.post { apply() } }
        row.post { apply() }
    }

    private fun buildCustomizer() {
        setUpFolds()
        addMoreHint(findViewById(R.id.materialRow))
        addMoreHint(findViewById(R.id.sizeRow))
        val materialRow = findViewById<LinearLayout>(R.id.materialRow)
        for (m in DuckMaterial.entries) {
            materialCards[m] = optionCard(materialRow, m.thumb, m.label) { applyLook(prefs.look.copy(material = m)) }
        }
        val colorRow = findViewById<ViewGroup>(R.id.colorRow)
        for (c in DuckColor.entries) {
            colorCards[c] = optionCard(colorRow, c.thumb, c.label) { applyLook(prefs.look.copy(color = c)) }
        }
        val accessoryRow = findViewById<ViewGroup>(R.id.accessoryRow)
        for (a in DuckAccessory.entries) {
            accessoryCards[a] = optionCard(accessoryRow, a.thumb, a.label) { applyLook(prefs.look.copy(accessory = a)) }
        }
        showFew(colorRow, DuckColor.entries.map { colorCards.getValue(it) }) { colorCards[prefs.look.color] }
        showFew(accessoryRow, DuckAccessory.entries.map { accessoryCards.getValue(it) }) { accessoryCards[prefs.look.accessory] }
        // Sizes: the same duck drawn bigger and bigger.
        val sizeRow = findViewById<LinearLayout>(R.id.sizeRow)
        val sizes = DuckSize.entries
        for ((i, s) in sizes.withIndex()) {
            sizeCards[s] = optionCard(sizeRow, R.drawable.thumb_col_sunny, s.label, imageDp = 30 + i * 42 / (sizes.size - 1)) {
                prefs.size = s
                DuckService.instance?.refreshAppearance()
                preview.renderer.squash()
                syncCustomizer()
            }
        }

        // Tone: how Ducky talks about your to-dos in its bubble.
        val toneGroup = findViewById<ChipGroup>(R.id.toneGroup)
        for (t in Tone.entries) {
            val chip = filterChip(getString(t.label)).also { toneGroup.addView(it) }
            if (t == prefs.tone) toneGroup.check(chip.id)
            chip.setOnClickListener {
                prefs.tone = t
                if (prefs.tapQuack) Quack.play(this, 0.5f)
                preview.renderer.react(Mood.HAPPY, 1.2f)
                syncCustomizer()
            }
        }

        // Language: English or Korean just for Ducky. Until one is picked, the phone's language is used.
        val languageGroup = findViewById<ChipGroup>(R.id.languageGroup)
        val languages = listOf("en" to "English", "ko" to "한국어")
        val current = (AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()).language
            .let { if (it == "ko") "ko" else "en" }
        for ((tag, name) in languages) {
            val chip = filterChip(name).also { languageGroup.addView(it) }
            if (tag == current) {
                languageGroup.check(chip.id)
                findViewById<TextView>(R.id.languageValue).text = name
            }
            chip.setOnClickListener {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        }
        syncCustomizer()
    }

    private fun filterChip(label: String) =
        (LayoutInflater.from(this).inflate(R.layout.chip_filter, null) as Chip).apply {
            id = View.generateViewId()
            text = label
        }

    /** Outlines the current choices and shows them next to each folded title. */
    private fun syncCustomizer() {
        val look = prefs.look
        val accent = ContextCompat.getColor(this, R.color.duck_yellow)
        fun <K> mark(cards: Map<K, MaterialCardView>, chosen: K?) {
            for ((k, card) in cards) card.strokeColor = if (k == chosen) accent else Color.TRANSPARENT
        }
        mark(materialCards, look.material)
        mark(colorCards, look.color)
        mark(accessoryCards, look.accessory)
        mark(sizeCards, prefs.size)
        findViewById<TextView>(R.id.materialValue).setText(look.material.label)
        findViewById<TextView>(R.id.accessoryValue).setText(look.accessory.label)
        findViewById<TextView>(R.id.sizeValue).setText(prefs.size.label)
        val tone = prefs.tone
        findViewById<TextView>(R.id.toneValue).setText(tone.label)
        findViewById<TextView>(R.id.toneSample).text =
            resources.getStringArray(tone.many)[0].format(3, getString(R.string.tone_sample_task))
    }

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
    /** Opens the battery list, where Ducky can be set to "not optimised" so reminders aren't delayed. */
    private fun askIgnoreBatteryOptimizations() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    companion object {
        const val ACTION_ADD = "app.reminderducky.ADD"
        const val ACTION_CUSTOMIZE = "app.reminderducky.CUSTOMIZE"
        const val ACTION_TODO = "app.reminderducky.TODO"
    }
}
