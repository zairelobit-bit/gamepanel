package com.gamepanel.app

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

fun GPAccess.launch(pkg: String) {
    val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
    if (prefs.getBoolean("float", false)) {
        val m = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        val w = (m.widthPixels * 0.5f).toInt()
        val h = (m.heightPixels * 0.7f).toInt()
        val l = (m.widthPixels - w) / 2
        val t = (m.heightPixels - h) / 2
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val o = ActivityOptions.makeBasic()
        o.setLaunchBounds(Rect(l, t, l + w, t + h))
        try {
            startActivity(i, o.toBundle())
        } catch (e: Exception) {
            nav(i)
        }
    } else {
        nav(i)
    }
    closePanel()
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
        body.addView(u.sec("Phone check"))
        val c2 = u.card()
        row(c2, "Android version", "API " + Build.VERSION.SDK_INT, Build.VERSION.SDK_INT >= 29)
        row(c2, "Force activities resizable", if (force) "ON" else "OFF", force)
        row(c2, "Enable freeform windows", if (free) "ON" else "OFF", free)
        row(c2, "Freeform system feature", if (feat) "YES" else "NO", feat)
        body.addView(c2)
        body.addView(u.sec("Setup"))
        body.addView(u.note("1. Open Developer options. Turn on Force activities to be resizable and Enable freeform windows.\n2. Restart the phone.\n3. Clear all recent apps, then open an app from this panel.\nAndroid 9 or older also needs an ADB command from a PC. If apps still open full screen, this ROM does not allow floating windows."))
        body.addView(u.btn("Open Developer options") { g.open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) })
    }
}
