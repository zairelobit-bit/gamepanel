package com.gamepanel.app

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class HomeTab(g: GPAccess) : Tab(g, "Game Panel") {
    private var cpuP: TextView? = null
    private var ramP: TextView? = null
    private var batP: TextView? = null
    private var graph: Graph? = null

    private fun pill(t: String, col: Int): TextView {
        val v = u.tv(t, 12f, col, true)
        v.gravity = Gravity.CENTER
        v.setPadding(u.dp(4), u.dp(7), u.dp(4), u.dp(7))
        v.background = u.grad(0x2CFFFFFF, 0x10FFFFFF, 14, 0x26FFFFFF)
        return v
    }

    override fun build(body: LinearLayout) {
        val row = u.hbox()
        val a = pill("CPU --", ACC)
        val b = pill("RAM --", 0xFF00D0FF.toInt())
        val c = pill("BAT --", 0xFF4ADE80.toInt())
        for (v in listOf(a, b, c)) row.addView(v, u.lp(0, WRAP, 1f, 2, 0, 2, 0))
        body.addView(row)
        val gr = Graph(g)
        body.addView(gr, u.lp(MATCH, u.dp(64), 0f, 0, 8, 0, 4))
        cpuP = a
        ramP = b
        batP = c
        graph = gr

        val box = u.vbox()
        body.addView(box)
        val sl = u.vbox()
        val au = g.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val add = u.grid(box, 4)
        add(u.orb(0, "Brightness", false) {
            sl.removeAllViews()
            val br = Settings.System.getInt(g.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            sl.addView(u.slider("Brightness", 255, br, { "${it * 100 / 255}%" }) { Act.bright(g, it) })
        })
        add(u.orb(1, "Volume", false) {
            sl.removeAllViews()
            val mx = maxOf(1, au.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            sl.addView(u.slider("Volume", mx, au.getStreamVolume(AudioManager.STREAM_MUSIC), { "${it * 100 / mx}%" }) {
                au.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
            })
        })
        add(u.orb(2, "DND", g.dndOn()) { ring ->
            if (dndOk(g)) {
                val v = !g.dndOn()
                g.dnd(v)
                u.orbSet(ring, v)
            }
        })
        add(u.orb(4, "Meter", g.sbar.isOn()) { ring ->
            g.sbar.set(!g.sbar.isOn())
            u.orbSet(ring, g.sbar.isOn())
        })
        add(u.orb(5, "Crosshair", g.cross.on) { ring ->
            g.cross.enable(!g.cross.on)
            u.orbSet(ring, g.cross.on)
        })
        add(u.orb(6, "Screenshot", false) { g.shot() })
        add(u.orb(7, "Rotation", Act.rotOn(g)) { ring ->
            Act.rot(g, !Act.rotOn(g))
            u.orbSet(ring, Act.rotOn(g))
        })
        add(u.orb(8, "More", false) { g.go(g.pages.more) })
        body.addView(sl)
        tick()
    }

    override fun tick() {
        val s = g.st
        cpuP?.text = if (s.cpu < 0) "CPU --" else "CPU ${s.cpu}%"
        ramP?.text = "RAM ${s.ramPct}%"
        batP?.text = "BAT ${s.batt}%"
        val gr = graph ?: return
        gr.a = ArrayList(s.cpuH)
        gr.b = ArrayList(s.ramH)
        gr.invalidate()
    }
}

class MoreTab(g: GPAccess) : Tab(g, "Gaming Tools") {
    private fun wifiPanel(): String =
        if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS

    override fun build(body: LinearLayout) {
        val p = g.prefs
        val box = u.vbox()
        body.addView(box)
        val add = u.grid(box, 3)
        add(u.orb(9, "Touch Lock", false) { g.touch.lock() })
        add(u.orb(10, "Eye Safe", p.getBoolean("warm", false)) { ring ->
            val v = !p.getBoolean("warm", false)
            p.edit().putBoolean("warm", v).apply()
            g.dim.update()
            u.orbSet(ring, v)
        })
        add(u.orb(20, "G-Stats", false) { g.go(g.pages.monitor) })
        add(u.orb(22, "Net Speed", g.widgets.isOn(4)) { ring ->
            g.widgets.set(4, !g.widgets.isOn(4))
            u.orbSet(ring, g.widgets.isOn(4))
        })
        add(u.orb(11, "Clean RAM", false) { Act.cleanRam(g) })
        add(u.orb(12, "Flashlight", g.torch) { ring ->
            Act.flash(g, !g.torch)
            u.orbSet(ring, g.torch)
        })
        add(u.orb(13, "Keep Awake", g.keepOnState()) { ring ->
            g.keepOn(!g.keepOnState())
            u.orbSet(ring, g.keepOnState())
        })
        add(u.orb(3, "Game Mode", g.gameModeOn()) { ring ->
            val v = !g.gameModeOn()
            g.prefs.edit().putBoolean("gm", v).apply()
            g.keepOn(v)
            if (dndOk(g)) g.dnd(v)
            u.orbSet(ring, v)
        })
        add(u.orb(5, "Crosshair", false) { g.go(g.pages.aim) })
        add(u.orb(14, "Wi-Fi", false) { g.open(wifiPanel()) })
        add(u.orb(15, "Bluetooth", btOn()) { ring ->
            val v = !btOn()
            btSet(g, v)
            u.orbSet(ring, v)
        })
        add(u.orb(16, "Keep Alive", false) { g.keepAlive() })
        add(u.orb(18, "Apps", false) { g.go(g.pages.apps) })
        add(u.orb(8, "Float Tools", false) { g.go(g.pages.ft) })
        add(u.orb(19, "Edge", false) { g.go(g.pages.edge) })
        add(u.orb(1, "Sound", false) { g.go(g.pages.snd) })

        body.addView(u.sec("Screen"))
        val c1 = u.card()
        c1.addView(u.slider("Screen dimmer", 80, p.getInt("dim", 0), { "$it%" }) {
            p.edit().putInt("dim", it).apply()
            g.dim.update()
        })
        body.addView(c1)
        body.addView(u.sec("Alerts"))
        val c2 = u.card()
        c2.addView(u.swRow("Overheat alert", "Vibrate at 42 C or more", p.getBoolean("heat", false)) {
            p.edit().putBoolean("heat", it).apply()
        })
        c2.addView(u.swRow("Low battery alert", "Vibrate at 15% or less", p.getBoolean("lowb", false)) {
            p.edit().putBoolean("lowb", it).apply()
        })
        body.addView(c2)
        body.addView(u.note("Apps cannot hide or block the navigation bar. For a hidden bar, switch the phone to gesture navigation in system settings."))
    }
}
