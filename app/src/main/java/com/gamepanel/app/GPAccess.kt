package com.gamepanel.app

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.TrafficStats
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

abstract class Tab(val g: GPAccess, val title: String) {
    val u: Ui get() = g.ui
    abstract fun build(body: LinearLayout)
    open fun tick() {}
}

class Stats {
    @Volatile var ping = -1
    var hz = 0
    var ramPct = 0
    var ramUsed = 0.0
    var ramTotal = 0.0
    var temp = 0f
    var batt = 0
    var charging = false
    var down = 0L
    var up = 0L
    var free = 0.0
}

class GPAccess : AccessibilityService() {
    companion object {
        @Volatile var inst: GPAccess? = null
        const val FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    lateinit var ui: Ui
    lateinit var wm: WindowManager
    lateinit var focus: Focus
    lateinit var dim: Dimmer
    lateinit var widgets: Widgets
    lateinit var alerts: Alerts
    lateinit var tabs: List<Tab>
    val handler = Handler(Looper.getMainLooper())
    val st = Stats()
    val prefs by lazy { getSharedPreferences("gp", MODE_PRIVATE) }
    var left = false
    var hy = 40
    var host = "1.1.1.1"
    var start = 0L
    var cur = 0
    private val layers = ArrayList<View>()
    private val games = HashMap<String, Boolean>()
    private lateinit var handle: LinearLayout
    private lateinit var bar: View
    private lateinit var panel: LinearLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var hlp: WindowManager.LayoutParams
    private lateinit var plp: WindowManager.LayoutParams
    private var shown = false
    private var added = false
    private var hAdded = false
    private var ready = false
    private var alive = false
    private var frames = 0
    private var lastRx = 0L
    private var lastTx = 0L
    private var fg = ""

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            frames++
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, 1000)
        }
    }

    fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    fun nav(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    fun open(action: String) {
        try {
            nav(Intent(action))
        } catch (e: Exception) {
            toast("Not available on this phone")
        }
    }

    fun keepAlive() {
        try {
            nav(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            toast("Not available on this phone")
        }
    }

    fun wlp(w: Int, h: Int, f: Int = FLAGS): WindowManager.LayoutParams =
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, f, PixelFormat.TRANSLUCENT)

    fun addWin(v: View, lp: WindowManager.LayoutParams) {
        try {
            wm.addView(v, lp)
            layers.add(v)
        } catch (e: Exception) { }
    }

    fun delWin(v: View?) {
        if (v == null) return
        try { wm.removeView(v) } catch (e: Exception) { }
        layers.remove(v)
    }

    fun drag(v: View, lp: WindowManager.LayoutParams, done: () -> Unit) {
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = sx + (e.rawX - tx).toInt()
                        lp.y = sy + (e.rawY - ty).toInt()
                        wm.updateViewLayout(v, lp)
                    }
                    MotionEvent.ACTION_UP -> done()
                }
                return true
            }
        })
    }

    fun keepOn(on: Boolean) {
        val f = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        hlp.flags = if (on) hlp.flags or f else hlp.flags and f.inv()
        if (hAdded) wm.updateViewLayout(handle, hlp)
    }

    fun keepOnState() = (hlp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0

    fun dndOn() = getSystemService(NotificationManager::class.java).currentInterruptionFilter > 1

    fun dnd(on: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        } else {
            toast("Allow Do Not Disturb access")
            open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    }

    fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        nav(i)
        closePanel()
    }

    fun isGame(pkg: String): Boolean {
        val cat = games.getOrPut(pkg) {
            try {
                packageManager.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_GAME
            } catch (e: Exception) {
                false
            }
        }
        return cat || (prefs.getStringSet("apps", emptySet()) ?: emptySet()).contains(pkg)
    }

    fun shot() {
        if (Build.VERSION.SDK_INT < 28) {
            toast("Needs Android 9 or newer")
            return
        }
        closePanel()
        val vs = ArrayList<View>(layers)
        vs.forEach { it.alpha = 0f }
        handler.postDelayed({
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        }, 450)
        handler.postDelayed({ vs.forEach { it.alpha = 1f } }, 1800)
    }

    private fun off(): Float = ui.dp(if (left) -320 else 320).toFloat()

    fun openPanel() {
        shown = true
        if (!added) {
            selectTab(cur)
            panel.alpha = 0f
            panel.translationX = off()
            addWin(panel, plp)
            added = true
        }
        panel.animate().translationX(0f).alpha(1f).setDuration(200)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun closePanel() {
        if (!shown) return
        shown = false
        panel.animate().translationX(off()).alpha(0f).setDuration(150).withEndAction {
            if (!shown) {
                delWin(panel)
                added = false
            }
        }.start()
    }

    fun selectTab(i: Int) {
        cur = i
        for (k in 0 until tabRow.childCount) ui.look(tabRow.getChildAt(k) as TextView, k == i)
        body.removeAllViews()
        tabs[i].build(body)
        body.alpha = 0f
        body.animate().alpha(1f).setDuration(150).start()
    }

    fun rebuild() = selectTab(cur)

    fun place() {
        val u = ui
        val m = resources.displayMetrics
        val barH = u.dp(prefs.getInt("hh", 80).coerceAtLeast(24))
        val winH = barH + u.dp(40)
        val side = if (left) Gravity.START else Gravity.END
        hlp.height = winH
        hlp.gravity = side or Gravity.TOP
        hlp.y = (m.heightPixels - winH) * hy / 100
        handle.gravity = side or Gravity.CENTER_VERTICAL
        bar.layoutParams = LinearLayout.LayoutParams(u.dp(4), barH)
        bar.alpha = prefs.getInt("ha", 90).coerceAtLeast(20) / 100f
        plp.gravity = side or Gravity.TOP
        val r = u.dp(22).toFloat()
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xF4161620.toInt(), 0xF40A0A10.toInt())
        )
        bg.cornerRadii = if (left) floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        else floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        bg.setStroke(u.dp(1), 0x26FFFFFF)
        panel.background = bg
        if (hAdded) wm.updateViewLayout(handle, hlp)
        if (added) wm.updateViewLayout(panel, plp)
    }

    fun applyVis() {
        val only = prefs.getInt("hmode", 0) == 1
        handle.visibility = if (!only || isGame(fg)) View.VISIBLE else View.GONE
    }

    private fun buildHandle() {
        handle = LinearLayout(this)
        bar = View(this)
        bar.background = ui.bg(ACC, 3)
        handle.addView(bar, LinearLayout.LayoutParams(ui.dp(4), ui.dp(80)))
        handle.setOnTouchListener(object : View.OnTouchListener {
            var x0 = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> x0 = e.rawX
                    MotionEvent.ACTION_MOVE -> {
                        val d = if (left) e.rawX - x0 else x0 - e.rawX
                        if (d > ui.dp(18)) openPanel()
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(e.rawX - x0) < ui.dp(6)) openPanel()
                    }
                }
                return true
            }
        })
    }

    private fun buildPanel() {
        val u = ui
        panel = u.vbox()
        panel.setPadding(u.dp(10), u.dp(28), u.dp(10), u.dp(10))
        panel.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) closePanel()
            false
        }
        val head = u.hbox()
        val tt = u.vbox()
        tt.addView(u.tv("Game Panel", 17f, TXT, true))
        tt.addView(u.tv("Control center", 10f, DIM))
        head.addView(tt, u.lp(0, WRAP, 1f))
        val x = u.tv("\u00D7", 24f, DIM)
        x.setPadding(u.dp(12), 0, u.dp(4), 0)
        x.setOnClickListener { closePanel() }
        head.addView(x)
        panel.addView(head)
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        tabRow = u.hbox()
        hs.addView(tabRow)
        panel.addView(hs, u.lp(MATCH, WRAP, 0f, 0, 8, 0, 4))
        body = u.vbox()
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(body)
        panel.addView(sv, u.lp(MATCH, 0, 1f))
        tabs.forEachIndexed { i, t ->
            val v = u.btn(t.title) { selectTab(i) }
            v.setPadding(u.dp(16), u.dp(8), u.dp(16), u.dp(8))
            tabRow.addView(v, u.lp(WRAP, WRAP, 0f, 0, 0, 6, 0))
        }
    }

    private fun sample() {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        st.ramTotal = mi.totalMem / 1e9
        st.ramUsed = (mi.totalMem - mi.availMem) / 1e9
        st.ramPct = (100 - mi.availMem * 100 / mi.totalMem).toInt()
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        st.temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val lv = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val sc = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        st.batt = lv * 100 / sc
        val ch = b?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
        st.charging = ch == BatteryManager.BATTERY_STATUS_CHARGING || ch == BatteryManager.BATTERY_STATUS_FULL
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        st.down = (rx - lastRx) / 1024
        st.up = (tx - lastTx) / 1024
        lastRx = rx
        lastTx = tx
        st.hz = frames
        frames = 0
        st.free = StatFs(Environment.getDataDirectory().path).availableBytes / 1e9
        alerts.check(st)
        widgets.update()
        if (shown) tabs[cur].tick()
    }

    private fun startPinger() {
        alive = true
        Thread {
            while (alive) {
                st.ping = try {
                    val s = Socket()
                    val t0 = System.nanoTime()
                    s.connect(InetSocketAddress(host, 53), 1500)
                    val ms = ((System.nanoTime() - t0) / 1000000).toInt()
                    s.close()
                    ms
                } catch (e: Exception) {
                    -1
                }
                try { Thread.sleep(1500) } catch (e: InterruptedException) { break }
            }
        }.start()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        inst = this
        if (ready) return
        ready = true
        ui = Ui(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        left = prefs.getBoolean("left", false)
        hy = prefs.getInt("hy", 40)
        start = SystemClock.elapsedRealtime()
        focus = Focus(this)
        dim = Dimmer(this)
        widgets = Widgets(this)
        alerts = Alerts(this)
        tabs = makeTabs(this)
        buildHandle()
        buildPanel()
        hlp = wlp(ui.dp(28), ui.dp(120))
        plp = wlp(ui.dp(310), MATCH, FLAGS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        place()
        addWin(handle, hlp)
        hAdded = true
        widgets.restore()
        dim.update()
        focus.restore()
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        handler.post(tick)
        Choreographer.getInstance().postFrameCallback(frameCb)
        startPinger()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (!ready || e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = e.packageName?.toString() ?: return
        if (p == packageName || p.startsWith("com.android.systemui")) return
        if (p != fg) {
            fg = p
            applyVis()
        }
    }

    override fun onInterrupt() {}

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        if (ready) {
            place()
            focus.refresh()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        alive = false
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(frameCb)
        for (v in ArrayList<View>(layers)) delWin(v)
        added = false
        hAdded = false
        shown = false
        ready = false
        inst = null
        return super.onUnbind(intent)
    }
}
