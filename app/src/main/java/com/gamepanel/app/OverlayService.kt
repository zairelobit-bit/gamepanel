package com.gamepanel.app

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.TrafficStats
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var am: ActivityManager
    private lateinit var root: LinearLayout
    private lateinit var info: TextView
    private lateinit var lp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var ping = -1
    @Volatile private var host = "8.8.8.8"
    @Volatile private var alive = true
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
            info.text = "⚡ ${hz}Hz   📶 ${p}ms\n🧠 $u/${t}GB ($pct%)\n🌡 ${tp}°C   🔋$lvl%\n⬇${down}K  ⬆${up}K/s"
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(c: Int, r: Int, s: Int = 0) = GradientDrawable().apply {
        setColor(c)
        cornerRadius = dp(r).toFloat()
        if (s != 0) setStroke(dp(1), s)
    }

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
        lp.flags = lp.flags xor WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        wm.updateViewLayout(root, lp)
        val on = (lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        toast(if (on) "Layar tetap nyala" else "Layar normal")
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(0xDD0B0B10.toInt(), 14, 0xFFFF2D4A.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        info = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(android.graphics.Typeface.MONOSPACE)
            text = "Memuat..."
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        var row: LinearLayout? = null
        var n = 0
        fun tool(label: String, f: () -> Unit) {
            if (n++ % 3 == 0) row = LinearLayout(this).also { panel.addView(it) }
            val t = TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, dp(9))
                background = bg(0x33FFFFFF, 8)
                setOnClickListener { f() }
            }
            val q = LinearLayout.LayoutParams(dp(74), LinearLayout.LayoutParams.WRAP_CONTENT)
            q.setMargins(dp(2), dp(3), dp(2), dp(3))
            row!!.addView(t, q)
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
        tool("👁 Transp") {
            root.alpha = if (root.alpha > 0.9f) 0.6f else if (root.alpha > 0.5f) 0.35f else 1f
        }
        tool("✖ Tutup") { stopSelf() }
        root.addView(info)
        root.addView(panel)

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(80)
        }

        info.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - tx
                        val dy = e.rawY - ty
                        if (abs(dx) > 12 || abs(dy) > 12) moved = true
                        if (moved) {
                            lp.x = sx + dx.toInt()
                            lp.y = sy + dy.toInt()
                            wm.updateViewLayout(root, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            panel.visibility = if (panel.visibility == View.GONE) View.VISIBLE else View.GONE
                        }
                    }
                }
                return true
            }
        })
        wm.addView(root, lp)
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        am = getSystemService(ActivityManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gp", "Game Panel", NotificationManager.IMPORTANCE_LOW))
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
        buildUi()
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
        if (::root.isInitialized) wm.removeView(root)
        super.onDestroy()
    }
}
