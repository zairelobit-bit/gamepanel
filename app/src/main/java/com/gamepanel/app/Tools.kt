package com.gamepanel.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout

class Focus(private val g: GPAccess) {
    private val views = ArrayList<View>()
    var on = false
        private set

    fun restore() {
        if (g.prefs.getBoolean("focus", false)) enable(true)
    }

    fun enable(v: Boolean) {
        on = v
        g.prefs.edit().putBoolean("focus", v).apply()
        g.keepOn(v)
        g.dnd(v)
        refresh()
    }

    fun refresh() {
        for (v in views) g.delWin(v)
        views.clear()
        if (!on) return
        val r = g.resources
        val m = r.displayMetrics
        val land = m.widthPixels > m.heightPixels
        fun dm(n: String, f: Int): Int {
            val id = r.getIdentifier(n, "dimen", "android")
            return if (id > 0) r.getDimensionPixelSize(id) else g.ui.dp(f)
        }
        add(MATCH, dm("status_bar_height", 26), Gravity.TOP)
        if (land) {
            val rot = g.wm.defaultDisplay.rotation
            add(dm("navigation_bar_width", 48), MATCH, if (rot == 3) Gravity.START else Gravity.END)
        } else {
            add(MATCH, dm("navigation_bar_height", 48), Gravity.BOTTOM)
        }
    }

    private fun add(w: Int, h: Int, gr: Int) {
        val v = View(g)
        v.setOnTouchListener { _, _ -> true }
        val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val lp = g.wlp(w, h, f)
        lp.gravity = gr
        g.addWin(v, lp)
        views.add(v)
    }
}

class Dimmer(private val g: GPAccess) {
    private var v: View? = null

    fun update() {
        val lvl = g.prefs.getInt("dim", 0)
        val warm = g.prefs.getBoolean("warm", false)
        if (lvl == 0 && !warm) {
            g.delWin(v)
            v = null
            return
        }
        var view = v
        if (view == null) {
            view = View(g)
            val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            g.addWin(view, g.wlp(MATCH, MATCH, f))
            v = view
        }
        val a = lvl * 255 / 100
        val alpha = if (warm) maxOf(a, 45) else a
        val rgb = if (warm) 0x3A1800 else 0x000000
        view.setBackgroundColor((alpha shl 24) or rgb)
    }
}

class Alerts(private val g: GPAccess) {
    private var last = 0L

    fun check(s: Stats) {
        val now = SystemClock.elapsedRealtime()
        if (now - last < 90000) return
        val p = g.prefs
        if (p.getBoolean("heat", false) && s.temp >= 42f) {
            last = now
            val t = s.temp.toInt()
            buzz("Phone is hot: $t C")
        } else if (p.getBoolean("lowb", false) && s.batt <= 15 && !s.charging) {
            last = now
            buzz("Battery low: ${s.batt}%")
        }
    }

    private fun buzz(m: String) {
        g.toast(m)
        val vb = g.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vb.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

class ToolsTab(g: GPAccess) : Tab(g, "Tools") {
    private var torch = false

    private fun needWrite() {
        g.toast("Allow Modify system settings")
        g.nav(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + g.packageName)))
    }

    private fun rotOn() =
        Settings.System.getInt(g.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun rot(on: Boolean) {
        if (Settings.System.canWrite(g)) {
            Settings.System.putInt(g.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        } else {
            needWrite()
        }
    }

    private fun bright(v: Int) {
        if (Settings.System.canWrite(g)) {
            Settings.System.putInt(g.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(g.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceAtLeast(5))
        } else {
            needWrite()
        }
    }

    private fun flash(on: Boolean) {
        try {
            val cm = g.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList.first(), on)
            torch = on
        } catch (e: Exception) {
            g.toast("Flashlight not available")
        }
    }

    private fun cleanRam() {
        val am = g.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val before = mi.availMem
        for (a in g.packageManager.getInstalledApplications(0)) {
            if (a.packageName != g.packageName) am.killBackgroundProcesses(a.packageName)
        }
        g.handler.postDelayed({
            val m2 = ActivityManager.MemoryInfo()
            am.getMemoryInfo(m2)
            val mb = maxOf(0L, (m2.availMem - before) / 1048576)
            g.toast("Freed $mb MB")
        }, 800)
    }

    override fun build(body: LinearLayout) {
        val au = g.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val p = g.prefs
        val vmax = maxOf(1, au.getStreamMaxVolume(AudioManager.STREAM_MUSIC))

        body.addView(u.sec("Game modes"))
        val c1 = u.card()
        c1.addView(u.swRow("Focus mode", "Blocks pull-down and nav strip", g.focus.on) { g.focus.enable(it) })
        c1.addView(u.swRow("Do Not Disturb", "Silence calls and popups", g.dndOn()) { g.dnd(it) })
        c1.addView(u.swRow("Keep screen on", "", g.keepOnState()) { g.keepOn(it) })
        c1.addView(u.swRow("Flashlight", "", torch) { flash(it) })
        c1.addView(u.swRow("Auto-rotate", "", rotOn()) { rot(it) })
        body.addView(c1)

        body.addView(u.sec("Sliders"))
        val c2 = u.card()
        c2.addView(u.slider("Media volume", vmax, au.getStreamVolume(AudioManager.STREAM_MUSIC), { "${it * 100 / vmax}%" }) {
            au.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
        })
        val br = Settings.System.getInt(g.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        c2.addView(u.slider("Brightness", 255, br, { "${it * 100 / 255}%" }) { bright(it) })
        c2.addView(u.slider("Screen dimmer", 80, p.getInt("dim", 0), { "$it%" }) {
            p.edit().putInt("dim", it).apply()
            g.dim.update()
        })
        c2.addView(u.swRow("Warm tint", "Eye comfort filter", p.getBoolean("warm", false)) {
            p.edit().putBoolean("warm", it).apply()
            g.dim.update()
        })
        body.addView(c2)

        body.addView(u.sec("Alerts"))
        val c3 = u.card()
        c3.addView(u.swRow("Overheat alert", "Vibrate at 42 C or more", p.getBoolean("heat", false)) {
            p.edit().putBoolean("heat", it).apply()
        })
        c3.addView(u.swRow("Low battery alert", "Vibrate at 15% or less", p.getBoolean("lowb", false)) {
            p.edit().putBoolean("lowb", it).apply()
        })
        body.addView(c3)

        body.addView(u.sec("Quick actions"))
        val box = u.vbox()
        body.addView(box)
        val add = u.grid(box, 3)
        add(u.btn("Screenshot") { g.shot() })
        add(u.btn("Clean RAM") { cleanRam() })
        add(u.btn("Keep alive") { g.keepAlive() })
        add(u.btn("Wi-Fi") { g.open(Settings.ACTION_WIFI_SETTINGS) })
        add(u.btn("Bluetooth") { g.open(Settings.ACTION_BLUETOOTH_SETTINGS) })
        add(u.btn("Network") { g.open(Settings.ACTION_WIRELESS_SETTINGS) })
        add(u.btn("Sound") { g.open(Settings.ACTION_SOUND_SETTINGS) })
        add(u.btn("Battery saver") { g.open(Settings.ACTION_BATTERY_SAVER_SETTINGS) })
        add(u.btn("Developer") { g.open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) })
    }
}
