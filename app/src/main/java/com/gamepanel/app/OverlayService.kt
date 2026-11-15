package com.gamepanel.app

import android.app.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.TrafficStats
import android.os.*
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var am: ActivityManager
    private lateinit var mini: TextView
    private lateinit var stats: TextView
    private lateinit var miniBox: LinearLayout
    private lateinit var handle: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var mlp: WindowManager.LayoutParams
    private lateinit var hlp: WindowManager.LayoutParams
    private lateinit var plp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    @Volatile private var ping = -1
    @Volatile private var host = "8.8.8.8"
    @Volatile private var alive = true
    private var shown = false
    private var frames = 0
    private var torch = false
    private var lastRx = 0L
    private var lastTx = 0L

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
            val tot = mi.totalMem / 1e9
            val used = tot - mi.availMem / 1e9
            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val down = (rx - lastRx) / 1024
            val up = (tx - lastTx) / 1024
            lastRx = rx
            lastTx = tx
            val hz = frames
            frames = 0
            val p = if (ping < 0) "--" else "$ping"
            val u = "%.1f".format(used)
            val t = "%.1f".format(tot)
            val pct = (used / tot * 100).toInt()
            val tp = "%.1f".format(temp)
            val s = "⚡ ${hz}Hz   📶 ${p}ms\n🧠 $u/${t}GB ($pct%)\n🌡 ${tp}°C   🔋$lvl%\n⬇${down}K  ⬆${up}K/s"
            mini.text = s
            stats.text = s
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

    private fun cleanRam() {
        packageManager.getInstalledApplications(0).forEach {
            if (it.packageName != packageName) am.killBackgroundProcesses(it.packageName)
        }
        toast("RAM dibersihkan")
    }

    private fun dnd() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            val off = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(
                if (off) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            toast(if (off) "DND aktif" else "DND mati")
        } else {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun flash() {
        try {
            val cm = getSystemService(CameraManager::class.java)
            torch = !torch
            cm.setTorchMode(cm.cameraIdList.first(), torch)
        } catch (e: Exception) {
            toast("Senter gagal")
        }
    }

    private fun vol(dir: Int) {
        getSystemService(AudioManager::class.java)
            .adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
    }

    private fun keepOn() {
        hlp.flags = hlp.flags xor WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        wm.updateViewLayout(handle, hlp)
        val on = (hlp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        toast(if (on) "Layar tetap nyala" else "Layar normal")
    }

    private fun launchApp(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
            closePanel()
        }
    }

    private fun openPanel() {
        shown = true
        if (!panel.isAttachedToWindow) {
            panel.translationX = dp(260).toFloat()
            wm.addView(panel, plp)
        }
        panel.animate().translationX(0f).setDuration(160).start()
    }

    private fun closePanel() {
        if (!shown) return
        shown = false
        panel.animate().translationX(dp(260).toFloat()).setDuration(140).withEndAction {
            if (!shown) {
                try { wm.removeView(panel) } catch (e: Exception) { }
            }
        }.start()
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

    private fun buildMini() {
        miniBox = LinearLayout(this).apply {
            background = bg(0xCC0B0B10.toInt(), 12, 0xFFFF2D4A.toInt())
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        mini = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 10f
            setTypeface(Typeface.MONOSPACE)
            text = "..."
        }
        miniBox.addView(mini)
        miniBox.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = mlp.x; sy = mlp.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - tx
                        val dy = e.rawY - ty
                        if (abs(dx) > 12 || abs(dy) > 12) moved = true
                        if (moved) {
                            mlp.x = sx + dx.toInt()
                            mlp.y = sy + dy.toInt()
                            wm.updateViewLayout(miniBox, mlp)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            if (shown) closePanel() else openPanel()
                        }
                    }
                }
                return true
            }
        })
    }

    private fun buildHandle() {
        handle = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        handle.addView(
            View(this).apply { background = bg(0xCCFF2D4A.toInt(), 3) },
            LinearLayout.LayoutParams(dp(5), dp(80))
        )
        handle.setOnTouchListener(object : View.OnTouchListener {
            var x0 = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> x0 = e.rawX
                    MotionEvent.ACTION_MOVE -> {
                        if (x0 - e.rawX > dp(18)) openPanel()
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(e.rawX - x0) < dp(6)) openPanel()
                    }
                }
                return true
            }
        })
    }

    private fun buildPanel() {
        val r = dp(18).toFloat()
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xF2101014.toInt())
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
            setPadding(dp(10), dp(36), dp(10), dp(10))
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) closePanel()
                false
            }
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(
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
