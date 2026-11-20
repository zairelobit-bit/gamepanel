package com.gamepanel.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

object Act {
    fun needWrite(g: GPAccess) {
        g.toast("Allow Modify system settings")
        g.nav(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + g.packageName)))
    }

    fun rotOn(g: GPAccess) =
        Settings.System.getInt(g.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    fun rot(g: GPAccess, on: Boolean) {
        if (Settings.System.canWrite(g)) {
            Settings.System.putInt(g.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        } else {
            needWrite(g)
        }
    }

    fun bright(g: GPAccess, v: Int) {
        if (Settings.System.canWrite(g)) {
            Settings.System.putInt(g.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(g.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceAtLeast(5))
        } else {
            needWrite(g)
        }
    }

    fun flash(g: GPAccess, on: Boolean) {
        try {
            val cm = g.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList.first(), on)
            g.torch = on
        } catch (e: Exception) {
            g.toast("Flashlight not available")
        }
    }

    fun cleanRam(g: GPAccess) {
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

class TouchLock(private val g: GPAccess) {
    private var root: FrameLayout? = null

    fun lock() {
        if (root != null) return
        val u = g.ui
        val fl = FrameLayout(g)
        fl.setOnTouchListener { _, _ -> true }
        val pill = u.tv("Touch locked - hold here to unlock", 12f, TXT, true)
        pill.setPadding(u.dp(14), u.dp(8), u.dp(14), u.dp(8))
        pill.background = u.bg(0xCC101018.toInt(), 16, 0x33FFFFFF)
        pill.setOnLongClickListener {
            unlock()
            true
        }
        val lp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = u.dp(36)
        fl.addView(pill, lp)
        val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        g.addWin(fl, g.wlp(MATCH, MATCH, f))
        root = fl
        g.closePanel()
    }

    fun unlock() {
        g.delWin(root)
        root = null
    }
}
