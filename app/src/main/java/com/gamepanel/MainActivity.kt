package com.gamepanel.app

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var root: LinearLayout
    private val shizukuRequest = 4102
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == shizukuRequest && !isFinishing) render()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Theme.load(this)
        ui = Ui(this)
        window.statusBarColor = 0xFF0A0B16.toInt()
        window.navigationBarColor = 0xFF0A0B16.toInt()
        try { Shizuku.addRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) { }
        root = ui.hbox()
        root.gravity = Gravity.CENTER
        root.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF171A32.toInt(), 0xFF080A14.toInt(), 0xFF101528.toInt()))
        setContentView(root)
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) { }
        super.onDestroy()
    }
    override fun onResume() { super.onResume(); render() }

    private fun go(action: String, pkgUri: Boolean = false) {
        try {
            val i = if (pkgUri) Intent(action, Uri.parse("package:$packageName")) else Intent(action)
            startActivity(i)
        } catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
    private fun label(t: String, size: Float, color: Int, bold: Boolean = false): TextView = ui.tv(t, size, color, bold).apply {
        if (bold) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    private fun card(): LinearLayout = ui.card().apply {
        background = ui.grad(0xE51B213A.toInt(), 0xE5101427.toInt(), 18, 0x5548DDF4)
        setPadding(ui.dp(13), ui.dp(10), ui.dp(13), ui.dp(10))
    }
    private fun statusPill(text: String, color: Int): TextView = label(text, 10f, color, true).apply {
        setPadding(ui.dp(9), ui.dp(5), ui.dp(9), ui.dp(5)); background = ui.bg(0x242EDEEF, 30, 0x443BD5E8)
    }
    private fun shizukuState(): Pair<String, Boolean> = try {
        val alive = Shizuku.pingBinder()
        val granted = alive && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        when { granted -> "CONNECTED • PERMISSION READY" to true; alive -> "CONNECTED • PERMISSION NEEDED" to false; else -> "OPTIONAL • NOT RUNNING" to false }
    } catch (_: Throwable) { "OPTIONAL • NOT INSTALLED / NOT RUNNING" to false }

    private fun render() {
        root.removeAllViews()
        val svc = GPAccess.inst
        val left = ui.vbox()
        left.setPadding(ui.dp(4), ui.dp(2), ui.dp(8), ui.dp(2))
        root.addView(left, ui.lp(0, MATCH, 1.12f))
        val rightScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val right = ui.vbox().apply { setPadding(ui.dp(5), ui.dp(2), ui.dp(4), ui.dp(2)) }
        rightScroll.addView(right)
        root.addView(rightScroll, ui.lp(0, MATCH, .88f))

        val top = ui.hbox()
        top.addView(label("PIXEL // GAME PANEL", 16f, TXT, true), ui.lp(0, WRAP, 1f))
        top.addView(statusPill("V2.0 • LOBBY", 0xFF63E8FF.toInt()))
        left.addView(top)
        left.addView(label("YOUR GAME. YOUR CONTROL.", 10f, 0xFF83A0C7.toInt(), true).apply { letterSpacing = .18f }, ui.lp(MATCH, WRAP, 0f, 0, 3, 0, 8))

        val art = ui.vbox().apply {
            background = ui.bg(0xFF11162A.toInt(), 18, 0x6648DDF4)
            clipToOutline = true
        }
        art.addView(PixelLobbyView(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        val caption = ui.hbox().apply { setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8)); setBackgroundColor(0xFF10172B.toInt()) }
        val heroText = ui.vbox()
        heroText.addView(label("NEON NEXUS", 15f, TXT, true))
        heroText.addView(label("PIXEL LOBBY  /  ANIME EDITION", 9f, 0xFF72DDF1.toInt(), true))
        caption.addView(heroText, ui.lp(0, WRAP, 1f))
        caption.addView(statusPill(if (svc != null) "PANEL ONLINE" else "PANEL OFFLINE", if (svc != null) 0xFF70F0B0.toInt() else 0xFFFF7898.toInt()))
        art.addView(caption)
        left.addView(art, ui.lp(MATCH, 0, 1f, 0, 0, 0, 8))

        val actions = ui.hbox()
        val open = ui.primary(if (svc != null) "OPEN GAME PANEL  ↗" else "ENABLE GAME PANEL") {
            if (svc != null) { svc.openPanel(); moveTaskToBack(true) } else go(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        actions.addView(open, ui.lp(0, WRAP, 1f, 0, 0, 5, 0))
        actions.addView(ui.btn("FRAME X", true) { if (svc != null) { svc.go(svc.pages.framex); moveTaskToBack(true) } else go(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, ui.lp(ui.dp(92), WRAP, 0f, 5, 0, 0, 0))
        left.addView(actions)
        left.addView(label("50-FEATURE LOADOUT  •  SHIZUKU OPTIONAL  •  ANDROID 8+", 9f, DIM, true), ui.lp(MATCH, WRAP, 0f, 2, 8, 0, 0))

        right.addView(label("SYSTEM CHECK", 13f, TXT, true))
        right.addView(label("Tap a row to open its Android settings.", 10f, DIM), ui.lp(MATCH, WRAP, 0f, 0, 2, 0, 7))
        val nm = getSystemService(NotificationManager::class.java)
        val pw = getSystemService(Context.POWER_SERVICE) as PowerManager
        val setup = card()
        setup.addView(perm("Accessibility service", "Required for the edge panel", svc != null) { go(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
        setup.addView(perm("Overlay access", "For floating tools and game overlay", Settings.canDrawOverlays(this)) { go(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) })
        setup.addView(perm("Modify system settings", "Brightness and rotation controls", Settings.System.canWrite(this)) { go(Settings.ACTION_MANAGE_WRITE_SETTINGS, true) })
        setup.addView(perm("Do Not Disturb", "Focus and quiet mode", nm.isNotificationPolicyAccessGranted) { go(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) })
        setup.addView(perm("Battery optimization", "Helps keep the panel available", pw.isIgnoringBatteryOptimizations(packageName)) { go(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true) })
        right.addView(setup)

        right.addView(label("SHIZUKU BRIDGE  /  OPTIONAL", 11f, 0xFF72DDF1.toInt(), true), ui.lp(MATCH, WRAP, 0f, 0, 9, 0, 4))
        val sh = card()
        val state = shizukuState()
        sh.addView(label(state.first, 10f, if (state.second) 0xFF70F0B0.toInt() else 0xFFFFD28A.toInt(), true))
        sh.addView(label("Normal Game Panel features keep working without Shizuku. Elevated actions are only enabled after an explicit permission grant.", 10f, DIM), ui.lp(MATCH, WRAP, 0f, 0, 4, 0, 6))
        sh.addView(ui.btn(if (state.second) "Shizuku permission active" else if (state.first.startsWith("CONNECTED")) "Grant Shizuku permission" else "Shizuku setup guide", state.second) {
            try {
                if (Shizuku.pingBinder()) {
                    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(shizukuRequest)
                    else render()
                } else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            } catch (_: Throwable) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }
        })
        right.addView(sh)

        right.addView(label("QUICK ACCESS", 11f, 0xFF72DDF1.toInt(), true), ui.lp(MATCH, WRAP, 0f, 0, 9, 0, 4))
        val quick = card()
        quick.addView(ui.hbox().apply {
            addView(ui.btn("FEATURE INDEX 50", true) { svc?.let { it.go(it.pages.features); moveTaskToBack(true) } ?: go(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, ui.lp(0, WRAP, 1f, 0, 0, 4, 0))
            addView(ui.btn("FRAME X", false) { svc?.let { it.go(it.pages.framex); moveTaskToBack(true) } ?: go(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, ui.lp(0, WRAP, 1f, 4, 0, 0, 0))
        })
        quick.addView(ui.btn("OPEN ACCESSIBILITY SETTINGS") { go(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
        right.addView(quick)
        right.addView(label("TIP: On ColorOS, allow auto-start and background activity.", 9f, DIM), ui.lp(MATCH, WRAP, 0f, 2, 8, 0, 0))

        for (i in 0 until left.childCount) left.getChildAt(i).apply { alpha = 0f; translationY = ui.dp(8).toFloat(); animate().alpha(1f).translationY(0f).setStartDelay(i * 35L).setDuration(220).start() }
        for (i in 0 until right.childCount) right.getChildAt(i).apply { alpha = 0f; translationY = ui.dp(8).toFloat(); animate().alpha(1f).translationY(0f).setStartDelay(i * 28L).setDuration(220).start() }
    }

    private fun perm(name: String, why: String, ok: Boolean, f: () -> Unit): LinearLayout {
        val r = ui.hbox().apply { setPadding(ui.dp(2), ui.dp(7), ui.dp(2), ui.dp(7)); background = ui.rip(0, 8); setOnClickListener { f() } }
        val t = ui.vbox()
        t.addView(label(name, 11f, TXT, true)); t.addView(label(why, 9f, DIM))
        r.addView(t, ui.lp(0, WRAP, 1f))
        r.addView(statusPill(if (ok) "READY" else "SET UP", if (ok) 0xFF70F0B0.toInt() else 0xFFFFB66F.toInt()))
        return r
    }
}
