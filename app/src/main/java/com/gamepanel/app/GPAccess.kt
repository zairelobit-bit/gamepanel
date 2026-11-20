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
import android.util.DisplayMetrics
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
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
    var cpu = -1
    var ramPct = 0
    var ramUsed = 0.0
    var ramTotal = 0.0
    var temp = 0f
    var batt = 0
    var charging = false
    var down = 0L
    var up = 0L
    var free = 0.0
    val cpuH = ArrayList<Int>()
    val ramH = ArrayList<Int>()
}

class GPAccess : AccessibilityService() {
    companion object {
        @Volatile var inst: GPAccess? = null
        const val FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    lateinit var ui: Ui
    lateinit var wm: WindowManager
    lateinit var dim: Dimmer
    lateinit var widgets: Widgets
    lateinit var sbar: StatsBar
    lateinit var cross: Cross
    lateinit var touch: TouchLock
    lateinit var alerts: Alerts
    lateinit var pages: Pages
    val handler = Handler(Looper.getMainLooper())
    val st = Stats()
    val prefs by lazy { getSharedPreferences("gp", MODE_PRIVATE) }
    var left = false
    var hy = 40
    var host = "1.1.1.1"
    var start = 0L
    var torch = false
    private val stack = ArrayList<Tab>()
    private val layers = ArrayList<View>()
    private val games = HashMap<String, Boolean>()
    private lateinit var handle: LinearLayout
    private lateinit var bar: View
    private lateinit var panel: LinearLayout
    private lateinit var strip: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var mainV: LinearLayout
    private lateinit var titleV: TextView
    private lateinit var backV: View
    private lateinit var hlp: WindowManager.LayoutParams
    private lateinit var plp: WindowManager.LayoutParams
    private var shown = false
    private var added = false
    private var hAdded = false
    private var ready = false
    private var alive = false
    private var gen = 0
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

    fun gameModeOn() = prefs.getBoolean("gm", false)

    fun gameMode(on: Boolean) {
        prefs.edit().putBoolean("gm", on).apply()
        keepOn(on)
        dnd(on)
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

    private fun off(): Float = (if (left) -plp.width else plp.width).toFloat()

    fun openPanel() {
        shown = true
        if (!added) {
            stack.clear()
            stack.add(pages.home)
            render()
            panel.alpha = 0f
            panel.translationX = off()
            addWin(panel, plp)
            added = true
        }
        panel.animate().translationX(0f).alpha(panelA()).setDuration(200)
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

    fun render() {
        val t = stack.last()
        titleV.text = t.title
        backV.visibility = if (stack.size > 1) View.VISIBLE else View.GONE
        body.removeAllViews()
        t.build(body)
        body.alpha = 0f
        body.animate().alpha(1f).setDuration(140).start()
        buildStrip()
    }

    fun rebuild() = render()

    fun go(t: Tab) {
        stack.add(t)
        render()
    }

    fun back() {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            render()
        } else {
            closePanel()
        }
    }

    private fun buildStrip() {
        val u = ui
        strip.removeAllViews()
        strip.addView(u.orb(23, "", false) { go(pages.apps) })
        val pm = packageManager
        for (pkg in (prefs.getStringSet("apps", emptySet()) ?: emptySet()).take(8)) {
            try {
                val im = ImageView(this)
                im.setImageDrawable(pm.getApplicationIcon(pkg))
                im.setOnClickListener { launch(pkg) }
                strip.addView(im, u.lp(u.dp(34), u.dp(34), 0f, 0, 8, 0, 0))
            } catch (e: Exception) { }
        }
    }

    private fun safe(): IntArray {
        val r = resources
        val id = r.getIdentifier("status_bar_height", "dimen", "android")
        val top = if (id > 0) r.getDimensionPixelSize(id) else ui.dp(26)
        val nid = r.getIdentifier("navigation_bar_height", "dimen", "android")
        val nav = if (nid > 0) r.getDimensionPixelSize(nid) else ui.dp(48)
        val m = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        val land = m.widthPixels > m.heightPixels
        return intArrayOf(m.widthPixels, m.heightPixels, top, if (land) 0 else nav)
    }
        private fun placeHandle() {
        val u = ui
        val barH = u.dp(prefs.getInt("hh", 80).coerceAtLeast(24))
        val winH = barH + u.dp(40)
        val side = if (left) Gravity.START else Gravity.END
        val s = safe()
        val lo = s[2] + u.dp(8)
        val hi = s[1] - s[3] - winH - u.dp(8)
        hlp.height = winH
        hlp.gravity = side or Gravity.TOP
        hlp.y = lo + (hi - lo).coerceAtLeast(0) * hy / 100
        handle.gravity = side or Gravity.CENTER_VERTICAL
        bar.layoutParams = LinearLayout.LayoutParams(u.dp(4), barH)
        bar.alpha = prefs.getInt("ha", 90).coerceAtLeast(20) / 100f
        if (hAdded) wm.updateViewLayout(handle, hlp)
    }

    private fun moveHandle(rx: Float, ry: Float) {
        val s = safe()
        val lo = s[2] + ui.dp(8)
        val hi = s[1] - s[3] - hlp.height - ui.dp(8)
        val span = (hi - lo).coerceAtLeast(1)
        hy = ((ry - hlp.height / 2f - lo) * 100f / span).toInt().coerceIn(0, 100)
        left = rx < s[0] / 2f
        placeHandle()
    }

    fun place() {
        placeHandle()
                val s = safe()
        val land = s[0] > s[1]
        val side = if (left) Gravity.START else Gravity.END
        plp.width = ui.dp(prefs.getInt("pw", 340).coerceAtLeast(260))
        plp.gravity = side or (if (land) Gravity.TOP else Gravity.CENTER_VERTICAL)
        plp.height = if (land) MATCH else minOf(ui.dp(400), s[1] - s[2] - s[3] - ui.dp(24))
        if (added) wm.updateViewLayout(panel, plp)
    }

    fun panelA(): Float = prefs.getInt("pa", 100).coerceIn(50, 100) / 100f

    fun panelOpacity() {
        if (added) panel.alpha = panelA()
    }

    fun applyTheme() {
        if (!ready) return
        bar.background = ui.bg(ACC, 3)
        strip.background = cardBg()
        mainV.background = cardBg()
        render()
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
            var dragging = false
            val longPress = Runnable {
                dragging = true
                handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                handle.animate().scaleX(1.8f).setDuration(120).start()
            }
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        x0 = e.rawX
                        dragging = false
                        handler.postDelayed(longPress, 350)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (dragging) {
                            moveHandle(e.rawX, e.rawY)
                        } else {
                            val d = if (left) e.rawX - x0 else x0 - e.rawX
                            if (d > ui.dp(18)) {
                                handler.removeCallbacks(longPress)
                                if (!shown) openPanel()
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPress)
                        handle.animate().scaleX(1f).setDuration(120).start()
                        if (dragging) {
                            prefs.edit().putInt("hy", hy).putBoolean("left", left).apply()
                            place()
                        } else if (e.action == MotionEvent.ACTION_UP && abs(e.rawX - x0) < ui.dp(6) && !shown) {
                            openPanel()
                        }
                        dragging = false
                    }
                }
                return true
            }
        })
    }

    private fun cardBg(): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF171720.toInt(), 0xFF0C0C12.toInt())
        )
        g.cornerRadius = ui.dp(20).toFloat()
        g.setStroke(ui.dp(1), (ACC and 0x00FFFFFF) or 0x66000000)
        return g
    }

    private fun buildPanel() {
        val u = ui
        panel = u.hbox()
        panel.gravity = Gravity.TOP
        panel.setPadding(u.dp(8), u.dp(22), u.dp(8), u.dp(22))
        panel.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) closePanel()
            false
        }
        strip = u.vbox()
        strip.gravity = Gravity.CENTER_HORIZONTAL
        strip.setPadding(u.dp(6), u.dp(8), u.dp(6), u.dp(8))
        strip.background = cardBg()
        panel.addView(strip, u.lp(u.dp(52), MATCH, 0f, 0, 0, 8, 0))
        val main = u.vbox()
        mainV = main
        main.setPadding(u.dp(12), u.dp(10), u.dp(12), u.dp(10))
        main.background = cardBg()
        panel.addView(main, u.lp(0, MATCH, 1f))
        val head = u.hbox()
        val back = Ico(this, 21)
        back.col = TXT
        back.setOnClickListener { back() }
        backV = back
        head.addView(back, u.lp(u.dp(28), u.dp(28), 0f, 0, 0, 4, 0))
        titleV = u.tv("", 17f, TXT, true)
        head.addView(titleV, u.lp(0, WRAP, 1f))
        val x = Ico(this, 24)
        x.col = DIM
        x.setOnClickListener { closePanel() }
        head.addView(x, u.lp(u.dp(28), u.dp(28)))
        main.addView(head)
        body = u.vbox()
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(body)
        main.addView(sv, u.lp(MATCH, 0, 1f, 0, 6, 0, 0))
    }

    private fun sample() {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        st.ramTotal = mi.totalMem / 1e9
        st.ramUsed = (mi.totalMem - mi.availMem) / 1e9
        st.ramPct = (100 - mi.availMem * 100 / mi.totalMem).toInt()
        st.cpu = Cpu.pct()
        st.cpuH.add(maxOf(st.cpu, 0))
        st.ramH.add(st.ramPct)
        if (st.cpuH.size > 40) st.cpuH.removeAt(0)
        if (st.ramH.size > 40) st.ramH.removeAt(0)
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
        sbar.update()
        if (shown && stack.isNotEmpty()) stack.last().tick()
    }

    private fun startPinger() {
        alive = true
        val my = ++gen
        Thread {
            while (alive && my == gen) {
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
        Theme.load(this)
        ui = Ui(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (prefs.getInt("ver", 0) < 5) {
            prefs.edit().putInt("hy", 40).putInt("hh", 80).putInt("ha", 90).putInt("ver", 5).apply()
        }
        left = prefs.getBoolean("left", false)
        hy = prefs.getInt("hy", 40)
        host = prefs.getString("hostsel", "1.1.1.1") ?: "1.1.1.1"
        start = SystemClock.elapsedRealtime()
        dim = Dimmer(this)
        widgets = Widgets(this)
        sbar = StatsBar(this)
        cross = Cross(this)
        touch = TouchLock(this)
        alerts = Alerts(this)
        pages = Pages(this)
        buildHandle()
        buildPanel()
        hlp = wlp(ui.dp(28), ui.dp(120), FLAGS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        plp = wlp(ui.dp(340), MATCH, FLAGS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        place()
        addWin(handle, hlp)
        hAdded = true
        widgets.restore()
        sbar.restore()
        cross.update()
        dim.update()
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
        if (ready) place()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        alive = false
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(frameCb)
        for (v in ArrayList<View>(layers)) delWin(v)
        Floats.reset()
        added = false
        hAdded = false
        shown = false
        ready = false
        inst = null
        return super.onUnbind(intent)
    }
}
