package app.reminderducky

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Keeps the duck floating over other apps, like a chat head. Drag it around,
 * tap it to open the app, drop it on the X to hide it. When a reminder is due
 * it wobbles and shows a speech bubble.
 */
class DuckService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var duckRoot: FrameLayout? = null
    private var duckView: DuckGLView? = null
    private lateinit var duckParams: WindowManager.LayoutParams
    private var trashView: View? = null
    private var bubbleView: View? = null
    private var menuView: View? = null
    private var snapAnimator: ValueAnimator? = null

    private val alertQueue = ArrayDeque<Long>()
    private val handler = Handler(Looper.getMainLooper())
    private val calmDown = Runnable { setWobbling(false) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> duckView?.stopTicking()
                Intent.ACTION_SCREEN_ON -> duckView?.startTicking()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WindowManager::class.java)
        prefs = Prefs(this)
        goForeground()
        if (!canShow(this)) {
            stopSelf()
            return
        }
        addDuck()
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ALERT) {
            val id = intent.getLongExtra(Alarms.EXTRA_ID, -1)
            if (id != -1L) showAlert(id)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        snapAnimator?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        listOf(menuView, bubbleView, trashView, duckRoot).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        menuView = null; bubbleView = null; trashView = null; duckRoot = null; duckView = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (duckRoot != null) placeFromPrefs()
    }

    private fun goForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, DuckApp.CHANNEL_DUCK)
            .setSmallIcon(R.drawable.ic_stat_duck)
            .setContentTitle("Ducky is keeping you company")
            .setContentText("Tap to open your reminders")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FG_ID, n)
        }
    }

    // ---- The duck window ----

    private fun duckSizePx() = dp(prefs.size.dp)

    @SuppressLint("ClickableViewAccessibility")
    private fun addDuck() {
        val size = duckSizePx()
        duckParams = overlayParams(size, size, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        val view = DuckGLView(this).apply { renderer.look = prefs.look }
        val root = FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            contentDescription = "Ducky. Tap for quick actions."
        }
        root.setOnTouchListener(DragListener())
        duckView = view
        duckRoot = root
        placeFromPrefs(addTo = root)
        if (appOpen) applyAppOpen(true)
    }

    /** Puts the duck on its remembered edge and height. */
    private fun placeFromPrefs(addTo: View? = null) {
        val screen = screenSize()
        val size = duckSizePx()
        duckParams.width = size
        duckParams.height = size
        duckParams.x = if (prefs.onRight) screen.x - size else 0
        duckParams.y = (prefs.yFraction * screen.y).roundToInt().coerceIn(0, screen.y - size)
        duckView?.renderer?.facingRight = !prefs.onRight
        if (addTo != null) wm.addView(addTo, duckParams) else duckRoot?.let { wm.updateViewLayout(it, duckParams) }
        positionBubble()
        positionMenu()
    }

    /** Steps aside (invisible and untouchable) while the Ducky app itself is open. */
    private fun applyAppOpen(open: Boolean) {
        val root = duckRoot ?: return
        if (open) hideMenu()
        root.visibility = if (open) View.INVISIBLE else View.VISIBLE
        duckParams.flags = if (open) {
            duckParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            duckParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        wm.updateViewLayout(root, duckParams)
        bubbleView?.visibility = if (open) View.GONE else View.VISIBLE
    }

    /** Called from the app when the finish or size changes. */
    fun refreshAppearance() {
        duckView?.renderer?.look = prefs.look
        if (duckRoot != null && duckParams.width != duckSizePx()) placeFromPrefs()
    }

    private inner class DragListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private val slop = ViewConfiguration.get(this@DuckService).scaledTouchSlop

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    hideMenu()
                    downX = e.rawX; downY = e.rawY
                    startX = duckParams.x; startY = duckParams.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) {
                        dragging = true
                        duckView?.fast = true
                        showTrash()
                    }
                    if (dragging) {
                        duckParams.x = startX + dx.roundToInt()
                        duckParams.y = startY + dy.roundToInt()
                        wm.updateViewLayout(v, duckParams)
                        duckView?.renderer?.facingRight = duckParams.x + duckParams.width / 2 < screenSize().x / 2
                        trashView?.let { it.scaleX = if (overTrash()) 1.3f else 1f; it.scaleY = it.scaleX }
                        positionBubble()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val dropped = overTrash()
                        hideTrash()
                        if (dropped) hideDuckByUser() else snapToEdge()
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        onDuckTapped()
                    }
                }
            }
            return true
        }
    }

    private fun onDuckTapped() {
        duckView?.renderer?.squash()
        if (alertQueue.isEmpty()) duckView?.renderer?.react(Mood.HAPPY, 1.6f)
        if (prefs.tapQuack) Quack.play(this, 0.6f)
        showMenu()
    }

    private fun snapToEdge() {
        val screen = screenSize()
        val size = duckParams.width
        val onRight = duckParams.x + size / 2 >= screen.x / 2
        val targetX = if (onRight) screen.x - size else 0
        duckParams.y = duckParams.y.coerceIn(0, screen.y - size)
        duckView?.renderer?.facingRight = !onRight
        prefs.onRight = onRight
        prefs.yFraction = duckParams.y.toFloat() / screen.y
        snapAnimator = ValueAnimator.ofInt(duckParams.x, targetX).apply {
            duration = 350
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                duckParams.x = it.animatedValue as Int
                duckRoot?.let { r -> wm.updateViewLayout(r, duckParams) }
                positionBubble()
                positionMenu()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (alertQueue.isEmpty()) duckView?.fast = false
                }
            })
            start()
        }
    }

    // ---- The X target for hiding the duck ----

    private fun showTrash() {
        if (trashView != null) return
        val size = dp(64)
        val view = TextView(this).apply {
            text = "✕"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.trash_bg)
        }
        val screen = screenSize()
        val p = overlayParams(
            size, size,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            x = (screen.x - size) / 2
            y = screen.y - size - dp(56)
            alpha = 0.8f
        }
        wm.addView(view, p)
        trashView = view
    }

    private fun hideTrash() {
        trashView?.let { runCatching { wm.removeView(it) } }
        trashView = null
    }

    private fun overTrash(): Boolean {
        val screen = screenSize()
        val trashCx = screen.x / 2f
        val trashCy = screen.y - dp(56) - dp(32).toFloat()
        val cx = duckParams.x + duckParams.width / 2f
        val cy = duckParams.y + duckParams.height / 2f
        return hypot(cx - trashCx, cy - trashCy) < dp(80)
    }

    private fun hideDuckByUser() {
        prefs.showDuck = false
        Toast.makeText(this, "Ducky is napping. Reminders still arrive as notifications. Open the Ducky app to bring it back.", Toast.LENGTH_LONG).show()
        stopSelf()
    }

    // ---- Quick actions menu (tap the duck) ----

    @SuppressLint("ClickableViewAccessibility")
    private fun showMenu() {
        if (menuView != null) {
            hideMenu()
            return
        }
        bubbleView?.let { runCatching { wm.removeView(it) } }
        bubbleView = null
        val v = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_ReminderDucky))
            .inflate(R.layout.quick_menu, null)
        val store = ReminderStore(this)
        val alert = alertQueue.firstOrNull()?.let { store.get(it) }
        val header = v.findViewById<TextView>(R.id.menuHeader)
        if (alert != null) {
            header.text = "${alert.icon}  ${alert.text}"
        } else {
            val next = store.all().filter { !it.fired }.minByOrNull { it.timeMillis }
            header.text = if (next == null) "Nothing coming up. Quack!" else
                "Next: ${next.icon} ${next.text} · ${DateFormat.getTimeFormat(this).format(next.timeMillis)}"
        }
        v.findViewById<View>(R.id.menuDone).apply {
            visibility = if (alert != null) View.VISIBLE else View.GONE
            setOnClickListener { alert?.let { ReminderActions.done(this@DuckService, it.id) } }
        }
        v.findViewById<View>(R.id.menuSnooze).apply {
            visibility = if (alert != null) View.VISIBLE else View.GONE
            setOnClickListener { alert?.let { ReminderActions.snooze(this@DuckService, it.id) } }
        }
        v.findViewById<View>(R.id.menuAdd).setOnClickListener { openApp(MainActivity.ACTION_ADD) }
        v.findViewById<View>(R.id.menuList).setOnClickListener { openApp(null) }
        v.findViewById<View>(R.id.menuCustomize).setOnClickListener { openApp(MainActivity.ACTION_CUSTOMIZE) }
        v.findViewById<View>(R.id.menuHide).setOnClickListener {
            hideMenu()
            hideDuckByUser()
        }
        v.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) hideMenu()
            false
        }
        val p = overlayParams(
            dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        wm.addView(v, p)
        menuView = v
        positionMenu()
    }

    private fun hideMenu() {
        val v = menuView ?: return
        runCatching { wm.removeView(v) }
        menuView = null
        updateBubble()
    }

    private fun openApp(action: String?) {
        hideMenu()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setAction(action ?: Intent.ACTION_MAIN)
        )
    }

    private fun positionMenu() {
        val v = menuView ?: return
        val p = v.layoutParams as WindowManager.LayoutParams
        val screen = screenSize()
        val duckOnLeft = duckParams.x + duckParams.width / 2 < screen.x / 2
        p.x = if (duckOnLeft) duckParams.x + duckParams.width else duckParams.x - p.width
        p.x = p.x.coerceIn(0, maxOf(0, screen.x - p.width))
        p.y = duckParams.y.coerceIn(0, maxOf(0, screen.y - dp(380)))
        runCatching { wm.updateViewLayout(v, p) }
    }

    // ---- Alerts ----

    fun showAlert(id: Long) {
        if (duckRoot == null) return
        if (!alertQueue.contains(id)) alertQueue.addLast(id)
        duckView?.renderer?.react(Mood.SURPRISED, 1.2f)
        // The notification quacks; if notifications are off, Ducky quacks itself.
        if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) Quack.play(this, 1f)
        setWobbling(true)
        handler.removeCallbacks(calmDown)
        handler.postDelayed(calmDown, WOBBLE_MILLIS)
        updateBubble()
    }

    fun dismissAlert(id: Long, mood: Mood) {
        alertQueue.remove(id)
        duckView?.renderer?.react(mood, 2.5f)
        hideMenu()
        updateBubble()
    }

    private fun setWobbling(on: Boolean) {
        duckView?.renderer?.alerting = on
        duckView?.fast = on
    }

    private fun updateBubble() {
        val store = ReminderStore(this)
        // Drop alerts whose reminder was deleted meanwhile.
        while (alertQueue.isNotEmpty() && store.get(alertQueue.first()) == null) alertQueue.removeFirst()
        val id = alertQueue.firstOrNull()
        if (id == null || menuView != null) {
            bubbleView?.let { runCatching { wm.removeView(it) } }
            bubbleView = null
            if (id == null) {
                handler.removeCallbacks(calmDown)
                setWobbling(false)
            }
            return
        }
        val r = store.get(id) ?: return
        val view = bubbleView ?: LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_ReminderDucky))
            .inflate(R.layout.bubble_alert, null).also { v ->
                val p = overlayParams(bubbleWidth(), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                wm.addView(v, p)
                bubbleView = v
            }
        val more = alertQueue.size - 1
        view.findViewById<TextView>(R.id.bubbleIcon).text = r.icon
        view.findViewById<TextView>(R.id.bubbleText).text = r.text
        view.findViewById<TextView>(R.id.bubbleTime).text =
            DateFormat.getTimeFormat(this).format(r.timeMillis) + if (more > 0) "  ·  +$more more" else ""
        view.findViewById<View>(R.id.bubbleDone).setOnClickListener { ReminderActions.done(this, id) }
        view.findViewById<View>(R.id.bubbleSnooze).setOnClickListener {
            ReminderActions.snooze(this, id)
            Toast.makeText(this, "Snoozed for ${ReminderActions.SNOOZE_MINUTES} minutes", Toast.LENGTH_SHORT).show()
        }
        positionBubble()
    }

    private fun bubbleWidth(): Int {
        val screen = screenSize()
        return min(screen.x - duckSizePx(), dp(320))
    }

    /** Keeps the speech bubble beside the duck, on the side facing the screen. */
    private fun positionBubble() {
        val v = bubbleView ?: return
        val p = v.layoutParams as WindowManager.LayoutParams
        val screen = screenSize()
        p.width = bubbleWidth()
        val duckOnLeft = duckParams.x + duckParams.width / 2 < screen.x / 2
        p.x = if (duckOnLeft) duckParams.x + duckParams.width else duckParams.x - p.width
        p.x = p.x.coerceIn(0, screen.x - p.width)
        p.y = duckParams.y.coerceIn(0, maxOf(0, screen.y - dp(160)))
        runCatching { wm.updateViewLayout(v, p) }
    }

    // ---- Helpers ----

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @Suppress("DEPRECATION")
    private fun screenSize(): Point {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            Point().also { wm.defaultDisplay.getRealSize(it) }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    companion object {
        const val ACTION_ALERT = "app.reminderducky.ALERT"
        private const val FG_ID = 1
        private const val WOBBLE_MILLIS = 20_000L

        @Volatile var instance: DuckService? = null
            private set

        /** True while the Ducky app is on screen. */
        var appOpen = false
            private set

        fun setAppOpen(open: Boolean) {
            appOpen = open
            instance?.applyAppOpen(open)
        }

        fun canShow(context: Context) = Settings.canDrawOverlays(context)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DuckService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DuckService::class.java))
        }

        fun alert(context: Context, id: Long) {
            val s = instance
            if (s != null) {
                s.showAlert(id)
            } else {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DuckService::class.java).setAction(ACTION_ALERT).putExtra(Alarms.EXTRA_ID, id)
                )
            }
        }
    }
}
