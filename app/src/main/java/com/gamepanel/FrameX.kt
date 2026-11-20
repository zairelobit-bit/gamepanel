package com.gamepanel.app

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class FrameXTab(g: GPAccess) : Tab(g, "Frame X • Live Performance") {
    private var fps: TextView? = null
    private var refresh: TextView? = null
    private var cpu: TextView? = null
    private var ram: TextView? = null
    private var temp: TextView? = null
    private var battery: TextView? = null

    private fun metric(title: String, value: String, tint: Int): Pair<LinearLayout, TextView> {
        val card = u.card().apply { background = u.grad(0xFF202945.toInt(), 0xFF12182B.toInt(), 15, 0x5548DDF4); setPadding(u.dp(10), u.dp(9), u.dp(10), u.dp(9)) }
        card.addView(u.tv(title.uppercase(), 9f, DIM, true))
        val v = u.tv(value, 21f, tint, true)
        card.addView(v)
        return card to v
    }
    override fun build(body: LinearLayout) {
        body.addView(u.note("FRAME X estimates compositor callback FPS from the panel's frame loop. It is not an in-game engine counter."))
        val row1 = u.hbox()
        val m1 = metric("Panel FPS", "--", 0xFF70F0B0.toInt()); fps = m1.second
        val m2 = metric("Display Hz", "--", 0xFF65DFFF.toInt()); refresh = m2.second
        row1.addView(m1.first, u.lp(0, WRAP, 1f, 2, 2, 4, 2)); row1.addView(m2.first, u.lp(0, WRAP, 1f, 4, 2, 2, 2)); body.addView(row1)
        val row2 = u.hbox()
        val m3 = metric("CPU", "--", ACC); cpu = m3.second
        val m4 = metric("RAM", "--", 0xFF65DFFF.toInt()); ram = m4.second
        row2.addView(m3.first, u.lp(0, WRAP, 1f, 2, 2, 4, 2)); row2.addView(m4.first, u.lp(0, WRAP, 1f, 4, 2, 2, 2)); body.addView(row2)
        val row3 = u.hbox()
        val m5 = metric("Battery temp", "--", 0xFFFFC27A.toInt()); temp = m5.second
        val m6 = metric("Battery", "--", 0xFF70F0B0.toInt()); battery = m6.second
        row3.addView(m5.first, u.lp(0, WRAP, 1f, 2, 2, 4, 2)); row3.addView(m6.first, u.lp(0, WRAP, 1f, 4, 2, 2, 2)); body.addView(row3)
        body.addView(u.sec("Frame pacing guide"))
        val guide = u.card()
        guide.addView(u.tv("60 FPS  •  16.7 ms per frame", 12f, TXT, true))
        guide.addView(u.tv("90 FPS  •  11.1 ms per frame", 12f, TXT, true))
        guide.addView(u.tv("120 FPS •  8.3 ms per frame", 12f, TXT, true))
        body.addView(guide)
        body.addView(u.note("Actual game FPS can differ from the panel estimate. Android may limit refresh rate by device, app, thermal state, and power mode."))
        tick()
    }
    override fun tick() {
        val d = try { g.wm.defaultDisplay.refreshRate } catch (_: Throwable) { 0f }
        fps?.text = if (g.st.hz > 0) "${g.st.hz}" else "--"
        refresh?.text = if (d > 0f) "${d.toInt()} Hz" else "--"
        cpu?.text = if (g.st.cpu >= 0) "${g.st.cpu}%" else "--"
        ram?.text = "${g.st.ramPct}%"
        temp?.text = if (g.st.temp > 0f) "${g.st.temp}°C" else "--"
        battery?.text = "${g.st.batt}%"
    }
}

class FeatureIndexTab(g: GPAccess) : Tab(g, "50 Feature Loadout") {
    private val features = listOf(
        "Frame X live FPS estimate", "Display refresh-rate readout", "Frame pacing reference", "CPU usage monitor", "RAM usage monitor",
        "Battery percentage monitor", "Battery temperature monitor", "Free storage monitor", "Network download counter", "Network upload counter",
        "Connection ping monitor", "CPU history graph", "RAM history graph", "One-tap Game Mode", "Do Not Disturb toggle",
        "Keep-screen-awake switch", "Screenshot shortcut", "Brightness slider", "Media volume control", "Ringtone volume control",
        "Notification volume control", "Alarm volume control", "Ringer mode presets", "Wi-Fi settings shortcut", "Bluetooth shortcut",
        "Flashlight shortcut", "Eye comfort filter", "Screen dimmer", "Touch lock", "Six crosshair styles",
        "Six crosshair color presets", "Crosshair size control", "Crosshair thickness control", "Crosshair opacity control", "Crosshair gap control",
        "Crosshair position control", "Draggable edge handle", "Left/right edge placement", "Game-only handle visibility", "Panel width control",
        "Panel opacity control", "Theme presets", "Favorite app launcher", "Installed game detection", "App picker",
        "Floating window manager", "Floating browser", "Floating notes", "Floating calculator", "Shizuku optional bridge"
    )
    override fun build(body: LinearLayout) {
        body.addView(u.note("50 integrated tools and controls • core functions remain available without Shizuku."))
        features.forEachIndexed { i, name ->
            val r = u.hbox().apply { setPadding(u.dp(7), u.dp(7), u.dp(7), u.dp(7)); background = u.rip(0x0FFFFFFF, 9) }
            val num = u.tv("%02d".format(i + 1), 11f, 0xFF6CDCF1.toInt(), true)
            num.gravity = Gravity.CENTER
            num.background = u.bg(0x222DDBEE, 8)
            r.addView(num, u.lp(u.dp(34), u.dp(30)))
            r.addView(u.tv(name, 11f, TXT), u.lp(0, WRAP, 1f, 9, 0, 4, 0))
            r.addView(u.tv("READY", 9f, 0xFF70F0B0.toInt(), true))
            body.addView(r, u.lp(MATCH, WRAP, 0f, 0, 2, 0, 2))
        }
    }
}
