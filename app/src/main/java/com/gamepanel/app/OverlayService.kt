package com.gamepanel.app

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.TrafficStats
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

class OverlayService : Service() {
    private class W(val key: String, val label: String, val fn: () -> String) {
        var box: LinearLayout? = null
        var v: TextView? = null
    }

    private val ACC = 0xFFFF2D4A.toInt()
    private val DIM = 0xFFC9C9D2.toInt()
    private val GRAY = 0xFF8E8E9A.toInt()
    private lateinit var wm: WindowManager
    private lateinit var am: ActivityManager
    private lateinit var handle: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var hlp: WindowManager.LayoutParams
    private lateinit var plp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("gp", MODE_PRIVATE) }
    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    @Volatile private var ping = -1
    @Volatile private var host = "8.8.8.8"
    @Volatile private var alive = true
    private var left = false
    private var hy = 40
    private var shown = false
    private var picking = false
    private var askedWrite = false
    private var frames = 0
    private var hzv = 0
    private var ramPct = 0
    private var temp = 0f
    private var net = "0/0"
    private var torch = false
    private var lastRx = 0L
    private var lastTx = 0L

    private val ws = listOf(
        W("hz", "Hz") { "$hzv" },
        W("ping", "Ping ms") { if (ping < 0) "--" else "$ping" },
        W("ram", "RAM") { "$ramPct%" },
        W("temp", "Suhu") { "%.0f°".format(temp) },
        W("net", "Net KB/s") { net }
    )

    private val off: Float get() = (if (left) -dp(300) else dp(300)).toFloat()

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            frames++
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val pinger = Thread {
        while (alive) {
            ping = try {
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
    }

    private val tick = object : Runnable {
        override fun run() {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            ramPct = (100 - mi.availMem * 100 / mi.totalMem).toInt()
            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            net = "${(rx - lastRx) / 1024}/${(tx - lastTx) / 1024}"
            lastRx = rx
            lastTx = tx
            hzv = frames
            frames = 0
            ws.forEach { it.v?.text = it.fn() }
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(c: Int, r: Int, s: Int = 0) = GradientDrawable().apply {
        setColor(c)
        cornerRadius = dp(r).toFloat()
        if (s != 0) setStroke(dp(1), s)
    }

    private fun wlp(w: Int, h: Int, f: Int = baseFlags) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, f, PixelFormat.TRANSLUCENT
    )

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun nav(i: Intent) {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun cleanRam() {
        packageManager.getInstalledApplications(0).forEach {
            if (it.packageName != packageName) am.killBackgroundProcesses(it.packageName)
        }
        toast("RAM dibersihkan")
    }

    private fun dndOn() =
        getSystemService(NotificationManager::class.java).currentInterruptionFilter > 1

    private fun dnd() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                if (dndOn()) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        } else {
            nav(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun flash() {
        try {
            val cm = getSystemService(CameraManager::class.java)
            torch = !torch
            cm.setTorchMode(cm.cameraIdList.first(), torch)
        } catch (e: Exception) {
            torch = false
            toast("Senter gagal")
        }
    }

    private fun keepOnState() = (hlp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0

    private fun keepOn() {
        hlp.flags = hlp.flags xor WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        wm.updateViewLayout(handle, hlp)
    }

    private fun bright(v: Int) {
        if (Settings.System.canWrite(this)) {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceAtLeast(5))
        } else if (!askedWrite) {
            askedWrite = true
            toast("Izinkan ubah pengaturan sistem")
            nav(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun shot() {
        if (Build.VERSION.SDK_INT < 28) {
            toast("Butuh Android 9 ke atas")
            return
        }
        val a = GPAccess.inst
        if (a == null) {
            toast("Aktifkan Game Panel di Aksesibilitas")
            nav(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        closePanel()
        val all = ws.mapNotNull { it.box } + handle
        all.forEach { it.alpha = 0f }
        handler.postDelayed({ a.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 450)
        handler.postDelayed({ all.forEach { it.alpha = 1f } }, 1800)
    }

    private fun launchApp(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            nav(it)
            closePanel()
        }
    }

    private fun apps() = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    ).filter { it.activityInfo.packageName != packageName }
        .sortedWith(compareBy(
            { it.activityInfo.applicationInfo.category != ApplicationInfo.CATEGORY_GAME },
            { it.loadLabel(packageManager).toString().lowercase() }
        ))

    private fun openPanel() {
        shown = true
        if (!panel.isAttachedToWindow) {
            render()
            panel.translationX = off
            wm.addView(panel, plp)
        }
        panel.animate().translationX(0f).setDuration(160).start()
    }

    private fun closePanel() {
        if (!shown) return
        shown = false
        picking = false
        panel.animate().translationX(off).setDuration(140).withEndAction {
            if (!shown) {
                try { wm.removeView(panel) } catch (e: Exception) { }
            }
        }.start()
    }

    private fun placeHandle() {
        val g = if (left) Gravity.START else Gravity.END
        hlp.gravity = g or Gravity.TOP
        hlp.y = (resources.displayMetrics.heightPixels - dp(120)) * hy / 100
        handle.gravity = g or Gravity.CENTER_VERTICAL
        plp.gravity = g or Gravity.TOP
        val r = dp(22).toFloat()
        panel.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xF4141419.toInt(), 0xF40A0A0E.toInt())
        ).apply {
            cornerRadii = if (left) floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            else floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            setStroke(dp(1), 0x26FFFFFF)
        }
        if (handle.isAttachedToWindow) wm.updateViewLayout(handle, hlp)
        if (panel.isAttachedToWindow) wm.updateViewLayout(panel, plp)
    }

    private fun side(l: Boolean) {
        left = l
        prefs.edit().putBoolean("left", l).apply()
        placeHandle()
        render()
    }

    private fun drag(v: View, lp: WindowManager.LayoutParams, done: () -> Unit) {
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

    private fun showW(w: W, i: Int) {
        val v = TextView(this).apply {
            text = "--"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = bg(0xD9101014.toInt(), 12, 0x33FFFFFF)
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }
        box.addView(TextView(this).apply {
            text = w.label
            textSize = 9f
            setTextColor(GRAY)
        })
        box.addView(v)
        val lp = wlp(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x_" + w.key, dp(10))
            y = prefs.getInt("y_" + w.key, dp(50) + i * dp(68))
        }
        drag(box, lp) { prefs.edit().putInt("x_" + w.key, lp.x).putInt("y_" + w.key, lp.y).apply() }
        wm.addView(box, lp)
        w.box = box
        w.v = v
    }

    private fun toggleW(w: W) {
        val b = w.box
        if (b != null) {
            try { wm.removeView(b) } catch (e: Exception) { }
            w.box = null
            w.v = null
            prefs.edit().putBoolean("w_" + w.key, false).apply()
        } else {
            showW(w, ws.indexOf(w))
            prefs.edit().putBoolean("w_" + w.key, true).apply()
        }
    }

    private fun gridAdder(parent: LinearLayout, cols: Int): (View) -> Unit {
        var row: LinearLayout? = null
        var n = 0
        return { v ->
            if (n++ % cols == 0) {
                row = LinearLayout(this).also { parent.addView(it) }
            }
            row!!.addView(v)
        }
    }

    private fun sec(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 11f
        v.setTextColor(GRAY)
        v.letterSpacing = 0.06f
        v.setPadding(dp(3), dp(16), 0, dp(6))
        return v
    }

    private fun tileBg(on: Boolean) = bg(if (on) 0x33FF2D4A else 0x14FFFFFF, 10, if (on) ACC else 0x1AFFFFFF)

    private fun setOn(v: TextView, on: Boolean) {
        v.background = tileBg(on)
        v.setTextColor(if (on) Color.WHITE else DIM)
    }

    private fun tile(t: String, on: Boolean, f: (TextView) -> Unit): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 12f
        v.gravity = Gravity.CENTER
        v.setPadding(0, dp(13), 0, dp(13))
        setOn(v, on)
        val q = LinearLayout.LayoutParams(dp(84), -2)
        q.setMargins(dp(3), dp(3), dp(3), dp(3))
        v.layoutParams = q
        v.setOnClickListener { f(v) }
        return v
    }

    private fun appCell(ic: Drawable?, t: String, f: () -> Unit): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { f() }
        }
        if (ic != null) {
            c.addView(ImageView(this).apply { setImageDrawable(ic) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        } else {
            c.addView(
                TextView(this).apply {
                    text = "+"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(ACC)
                    background = bg(0x14FFFFFF, 12, ACC)
                },
                LinearLayout.LayoutParams(dp(40), dp(40))
            )
        }
        c.addView(
            TextView(this).apply {
                text = t
                textSize = 10f
                setTextColor(DIM)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(62), -2)
        )
        c.layoutParams = LinearLayout.LayoutParams(dp(66), -2)
        return c
    }

    private fun slider(title: String, mx: Int, cur: Int, onChange: (Int) -> Unit): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val tv = TextView(this).apply {
            setTextColor(DIM)
            textSize = 12f
        }
        fun upd(p: Int) {
            tv.text = "$title   ${p * 100 / mx}%"
        }
        upd(cur)
        col.addView(tv)
        col.addView(SeekBar(this).apply {
            max = mx
            progress = cur
            progressTintList = ColorStateList.valueOf(ACC)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {
                    if (user) {
                        upd(p)
                        onChange(p)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })
        return col
    }

    private fun renderPicker() {
        val sel = (prefs.getStringSet("apps", emptySet()) ?: emptySet()).toMutableSet()
        val pm = packageManager
        body.addView(sec("Pilih aplikasi untuk panel"))
        body.addView(tile("Selesai", true) {
            picking = false
            render()
        })
        apps().forEach { ri ->
            val pkg = ri.activityInfo.packageName
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            row.addView(ImageView(this).apply { setImageDrawable(ri.loadIcon(pm)) }, LinearLayout.LayoutParams(dp(34), dp(34)))
            row.addView(
                TextView(this).apply {
                    text = ri.loadLabel(pm)
                    textSize = 13f
                    setTextColor(Color.WHITE)
                },
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(10), 0, dp(10), 0) }
            )
            val mark = TextView(this).apply {
                text = if (pkg in sel) "Dipilih" else ""
                textSize = 11f
                setTextColor(ACC)
            }
            row.addView(mark)
            row.setOnClickListener {
                if (!sel.remove(pkg)) sel.add(pkg)
                prefs.edit().putStringSet("apps", HashSet(sel)).apply()
                mark.text = if (pkg in sel) "Dipilih" else ""
            }
            body.addView(row)
        }
    }

    private fun render() {
        body.removeAllViews()
        if (picking) {
            renderPicker()
            return
        }
        val au = getSystemService(AudioManager::class.java)
        body.addView(sec("Kontrol"))
        body.addView(slider("Volume", au.getStreamMaxVolume(AudioManager.STREAM_MUSIC), au.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            au.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
        })
        body.addView(slider("Kecerahan", 255, Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)) {
            bright(it)
        })

        body.addView(sec("Aplikasi"))
        val ga = gridAdder(body, 4)
        val pm = packageManager
        (prefs.getStringSet("apps", emptySet()) ?: emptySet()).forEach { pkg ->
            try {
                val lb = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                ga(appCell(pm.getApplicationIcon(pkg), lb) { launchApp(pkg) })
            } catch (e: Exception) { }
        }
        ga(appCell(null, "Tambah") {
            picking = true
            render()
        })

        body.addView(sec("Alat"))
        val g = gridAdder(body, 3)
        g(tile("Screenshot", false) { shot() })
        g(tile("Bersihkan RAM", false) { cleanRam() })
        g(tile("Jangan ganggu", dndOn()) { v ->
            dnd()
            setOn(v, dndOn())
        })
        g(tile("Senter", torch) { v ->
            flash()
            setOn(v, torch)
        })
        g(tile("Layar nyala", keepOnState()) { v ->
            keepOn()
            setOn(v, keepOnState())
        })
        g(tile("Ping $host", false) { v ->
            host = if (host == "8.8.8.8") "1.1.1.1" else "8.8.8.8"
            v.text = "Ping $host"
        })
        g(tile("Anti mati", false) {
            nav(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })
        g(tile("Stop", false) { stopSelf() })

        body.addView(sec("Widget melayang"))
        val gw = gridAdder(body, 3)
        ws.forEach { w ->
            gw(tile(w.label, w.box != null) { v ->
                toggleW(w)
                setOn(v, w.box != null)
            })
        }

        body.addView(sec("Tepi layar"))
        body.addView(slider("Posisi tepi", 100, hy) {
            hy = it
            prefs.edit().putInt("hy", it).apply()
            placeHandle()
        })
        val gs = gridAdder(body, 3)
        gs(tile("Kiri", left) { side(true) })
        gs(tile("Kanan", !left) { side(false) })
    }

    private fun buildHandle() {
        handle = LinearLayout(this)
        handle.alpha = 0.9f
        handle.addView(
            View(this).apply { background = bg(ACC, 3) },
            LinearLayout.LayoutParams(dp(4), dp(72))
        )
        handle.setOnTouchListener(object : View.OnTouchListener {
            var x0 = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> x0 = e.rawX
                    MotionEvent.ACTION_MOVE -> {
                        val .addView(
            TextView(this).apply {
                text = "GAME PANEL"
                textSize = 16f
                setTextColor(0xFFFF2D4A.toInt())
                setTypeface(null, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        head.addView(TextView(this).apply {
            text = "✖"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(4), 0)
            setOnClickListener { closePanel() }
        })
        panel.addView(head)
        stats = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, dp(8), 0, dp(8))
        }
        panel.addView(stats)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val add = gridAdder(body, 3)
        fun tool(t: String, f: () -> Unit) {
            val v = TextView(this).apply {
                text = t
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(10))
                background = bg(0x33FFFFFF, 8)
                setOnClickListener { f() }
            }
            val q = LinearLayout.LayoutParams(dp(76), -2)
            q.setMargins(dp(2), dp(3), dp(2), dp(3))
            v.layoutParams = q
            add(v)
        }
        tool("🧹 RAM") { cleanRam() }
        tool("🔕 DND") { dnd() }
        tool("🔦 Senter") { flash() }
        tool("🔊 Vol+") { vol(AudioManager.ADJUST_RAISE) }
        tool("🔉 Vol-") { vol(AudioManager.ADJUST_LOWER) }
        tool("💡 Layar") { keepOn() }
        tool("🌐 Server") {
            host = if (host == "8.8.8.8") "1.1.1.1" else "8.8.8.8"
            toast("Ping ke $host")
        }
        tool("📊 Monitor") {
            miniBox.visibility = if (miniBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        tool("⏹ Stop") { stopSelf() }

        body.addView(TextView(this).apply {
            text = "Aplikasi (game di atas)"
            textSize = 12f
            setTextColor(0xFFFF2D4A.toInt())
            setPadding(0, dp(12), 0, dp(4))
        })
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedWith(compareBy(
                { it.activityInfo.applicationInfo.category != ApplicationInfo.CATEGORY_GAME },
                { it.loadLabel(pm).toString().lowercase() }
            ))
        val addApp = gridAdder(body, 4)
        apps.forEach { ri ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener { launchApp(ri.activityInfo.packageName) }
            }
            cell.addView(
                ImageView(this).apply { setImageDrawable(ri.loadIcon(pm)) },
                LinearLayout.LayoutParams(dp(38), dp(38))
            )
            cell.addView(
                TextView(this).apply {
                    text = ri.loadLabel(pm)
                    textSize = 9f
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(dp(56), -2)
            )
            cell.layoutParams = LinearLayout.LayoutParams(dp(60), -2)
            addApp(cell)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        am = getSystemService(ActivityManager::class.java)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("gp", "Game Panel", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "gp")
            .setContentTitle("Game Panel aktif")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        mlp = wlp(-2, -2).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(60) }
        hlp = wlp(dp(26), dp(120)).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        plp = wlp(dp(260), -1, baseFlags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            .apply { gravity = Gravity.END or Gravity.TOP }
        buildMini()
        buildHandle()
        buildPanel()
        wm.addView(miniBox, mlp)
        wm.addView(handle, hlp)
        handler.post(tick)
        Choreographer.getInstance().postFrameCallback(frameCb)
        pinger.start()
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        alive = false
        pinger.interrupt()
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(frameCb)
        listOf(miniBox, handle, panel).forEach {
            try { wm.removeView(it) } catch (e: Exception) { }
        }
        super.onDestroy()
    }
}
