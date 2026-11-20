package com.gamepanel.app

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var root: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Theme.load(this)
        ui = Ui(this)
        window.statusBarColor = 0xFF0B0B10.toInt()
        window.navigationBarColor = 0xFF0B0B10.toInt()
        root = ui.vbox()
        root.setPadding(ui.dp(18), ui.dp(26), ui.dp(18), ui.dp(26))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF16161F.toInt(), 0xFF08080B.toInt())
        )
        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun go(action: String, pkgUri: Boolean = false) {
        try {
            val i = if (pkgUri) Intent(action, Uri.parse("package:$packageName")) else Intent(action)
            startActivity(i)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun perm(name: String, why: String, ok: Boolean, f: () -> Unit): LinearLayout {
        val u = ui
        val r = u.hbox()
        r.setPadding(u.dp(2), u.dp(10), u.dp(2), u.dp(10))
        val t = u.vbox()
        t.addView(u.tv(name, 13f, TXT))
        t.addView(u.tv(why, 10f, DIM))
        r.addView(t, u.lp(0, WRAP, 1f))
        val tag = u.tv(if (ok) "READY" else "SET UP", 10f, if (ok) 0xFF4ADE80.toInt() else ACC, true)
        tag.setPadding(u.dp(10), u.dp(4), u.dp(10), u.dp(4))
        tag.background = u.bg(if (ok) 0x224ADE80 else u.accA(0x26), 10)
        r.addView(tag)
        r.background = u.rip(0x00000000, 8)
        r.setOnClickListener { f() }
        return r
    }

    private fun render() {
        val u = ui
        root.removeAllViews()
        val svc = GPAccess.inst
        val nm = getSystemService(NotificationManager::class.java)
        val pw = getSystemService(Context.POWER_SERVICE) as PowerManager

        val title = u.tv("GAME PANEL", 26f, TXT, true)
        title.letterSpacing = 0.12f
        root.addView(title)
        root.addView(u.tv("Edge control center for gaming", 12f, DIM))

        val hero = u.card()
        hero.setPadding(u.dp(16), u.dp(16), u.dp(16), u.dp(16))
        hero.background = u.grad(u.accA(0x40), 0x12FFFFFF, 22, u.accA(0x77))
        hero.addView(u.tv(if (svc != null) "Panel is active" else "Panel is off", 18f, if (svc != null) 0xFF4ADE80.toInt() else ACC, true))
        hero.addView(
            u.tv(
                if (svc != null) "Swipe the red edge handle in any game to open it."
                else "Turn on the Game Panel accessibility service to start the edge panel.",
                12f, DIM
            )
        )
        val b = if (svc != null) {
            u.primary("Open panel now") {
                GPAccess.inst?.openPanel()
                moveTaskToBack(true)
            }
        } else {
            u.primary("Enable panel") { go(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        }
        hero.addView(b, u.lp(MATCH, WRAP, 0f, 0, 12, 0, 0))
        root.addView(hero, u.lp(MATCH, WRAP, 0f, 0, 16, 0, 0))

        root.addView(u.sec("Setup"))
        val c = u.card()
        c.addView(perm("Accessibility service", "Keeps the panel alive above games", svc != null) { go(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
        c.addView(perm("Display over other apps", "Needed to open apps from the panel", Settings.canDrawOverlays(this)) { go(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) })
        c.addView(perm("Modify system settings", "Brightness and auto-rotate sliders", Settings.System.canWrite(this)) { go(Settings.ACTION_MANAGE_WRITE_SETTINGS, true) })
        c.addView(perm("Do Not Disturb access", "Focus mode and DND switch", nm.isNotificationPolicyAccessGranted) { go(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) })
        c.addView(perm("Unrestricted battery", "Stops the system from killing the panel", pw.isIgnoringBatteryOptimizations(packageName)) { go(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true) })
        root.addView(c)
        root.addView(u.note("Oppo / ColorOS: also allow Auto-start and Background activity for Game Panel, and lock the app in Recents."))

        root.addView(u.sec("Games"))
        val box = u.vbox()
        root.addView(box)
        val add = u.grid(box, 4)
        val pm = packageManager
        var n = 0
        val all = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        for (ri in all) {
            if (n >= 12) break
            if (ri.activityInfo.applicationInfo.category != ApplicationInfo.CATEGORY_GAME) continue
            val pkg = ri.activityInfo.packageName
            val cell = u.vbox()
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(0, u.dp(6), 0, u.dp(6))
            cell.background = u.rip(0x00000000, 10)
            val im = ImageView(this)
            im.setImageDrawable(ri.loadIcon(pm))
            cell.addView(im, LinearLayout.LayoutParams(u.dp(46), u.dp(46)))
            val lb = u.tv(ri.loadLabel(pm).toString(), 10f, DIM)
            lb.maxLines = 1
            lb.gravity = Gravity.CENTER
            cell.addView(lb, LinearLayout.LayoutParams(u.dp(64), WRAP))
            cell.setOnClickListener {
                val li = pm.getLaunchIntentForPackage(pkg)
                if (li != null) startActivity(li)
            }
            add(cell)
            n++
        }
        if (n == 0) root.addView(u.note("No games found. Add favorites from the Apps tab in the panel."))

        for (i in 0 until root.childCount) {
            val v: View = root.getChildAt(i)
            v.alpha = 0f
            v.translationY = u.dp(10).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 45L).setDuration(260).start()
        }
    }
}
