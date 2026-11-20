package com.gamepanel.app

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.widget.LinearLayout

class KeepService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gp_keep", "Game Panel", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "gp_keep")
            .setContentTitle("Game Panel is running")
            .setContentText("Edge handle is active")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        return START_STICKY
    }
}

private var floatHinted = false

fun GPAccess.freeformOk(): Boolean {
    val feat = packageManager.hasSystemFeature("android.software.freeform_window_management")
    val on = Settings.Global.getInt(contentResolver, "enable_freeform_support", 0) == 1
    return feat || on
}

// Satu-satunya fungsi launch. Dulu ada versi anggota di GPAccess yang selalu menang atas versi ini,
// jadi mode jendela mengambang tidak pernah jalan dan aplikasi selalu terbuka layar penuh.
fun GPAccess.launch(pkg: String) {
    val i = packageManager.getLaunchIntentForPackage(pkg)
    if (i == null) {
        toast("App not found")
        return
    }
    closePanel()
    if (!prefs.getBoolean("float", false)) {
        nav(i)
        return
    }
    if (!freeformOk() && !floatHinted) {
        floatHinted = true
        toast("No freeform support detected. If the app opens full screen, use Float Tools or Split screen.")
    }
    val m = DisplayMetrics()
    wm.defaultDisplay.getRealMetrics(m)
    val w = m.widthPixels * prefs.getInt("fw", 55).coerceIn(30, 90) / 100
    val h = m.heightPixels * prefs.getInt("fh", 70).coerceIn(30, 90) / 100
    val l = (m.widthPixels - w) / 2
    val t = (m.heightPixels - h) / 2
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    val o = ActivityOptions.makeBasic()
    o.setLaunchBounds(Rect(l, t, l + w, t + h))
    try {
        val mt = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
        mt.invoke(o, 5)
    } catch (e: Throwable) { }
    try {
        startActivity(i, o.toBundle())
    } catch (e: Exception) {
        nav(i)
    }
}

class FloatTab(g: GPAccess) : Tab(g, "Floating Apps") {
    private fun row(c: LinearLayout, label: String, value: String, ok: Boolean) {
        val r = u.hbox()
        r.setPadding(u.dp(2), u.dp(6), u.dp(2), u.dp(6))
        r.addView(u.tv(label, 12f, DIM), u.lp(0, WRAP, 1f))
        r.addView(u.tv(value, 12f, if (ok) 0xFF4ADE80.toInt() else ACC, true))
        c.addView(r)
    }

    private fun gint(k: String) = Settings.Global.getInt(g.contentResolver, k, 0)

    override fun build(body: LinearLayout) {
        val force = gint("force_resizable_activities") == 1
        val free = gint("enable_freeform_support") == 1
        val feat = g.packageManager.hasSystemFeature("android.software.freeform_window_management")
        val c = u.card()
        c.addView(u.swRow("Open apps as floating windows", "Experimental", g.prefs.getBoolean("float", false)) {
            g.prefs.edit().putBoolean("float", it).apply()
        })
        body.addView(c)
        body.addView(u.btn("Float Tools: browser, notes, calc") { g.go(g.pages.ft) })
        body.addView(u.btn("Split screen: current app + another") {
            g.closePanel()
            g.handler.postDelayed({
                g.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            }, 300)
        })
        body.addView(u.sec("Window size"))
        val c3 = u.card()
        c3.addView(u.slider("Width", 90, g.prefs.getInt("fw", 55), { "${it.coerceAtLeast(30)}%" }) {
            g.prefs.edit().putInt("fw", it.coerceAtLeast(30)).apply()
        })
        c3.addView(u.slider("Height", 90, g.prefs.getInt("fh", 70), { "${it.coerceAtLeast(30)}%" }) {
            g.prefs.edit().putInt("fh", it.coerceAtLeast(30)).apply()
        })
        body.addView(c3)
        body.addView(u.sec("Phone check"))
        val c2 = u.card()
        row(c2, "Android version", "API " + Build.VERSION.SDK_INT, Build.VERSION.SDK_INT >= 29)
        row(c2, "Force activities resizable", if (force) "ON" else "OFF", force)
        row(c2, "Enable freeform windows", if (free) "ON" else "OFF", free)
        row(c2, "Freeform system feature", if (feat) "YES" else "NO", feat)
        body.addView(c2)
        body.addView(u.sec("Setup"))
        body.addView(u.note("Floating third-party apps only work if the phone ROM supports freeform windows.\n1. Open Developer options. Turn on Force activities to be resizable and Enable freeform windows.\n2. Restart the phone.\n3. Clear all recent apps, then open an app from this panel.\nIf apps still open full screen, this ROM does not allow floating windows. Use Float Tools (works everywhere) or Split screen instead."))
        body.addView(u.btn("Open Developer options") { g.open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) })
    }
}
