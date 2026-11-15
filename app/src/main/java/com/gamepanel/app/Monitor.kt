package com.gamepanel.app

import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class Widgets(private val g: GPAccess) {
    private class W(val key: String, val label: String, val fn: (Stats) -> String) {
        var box: LinearLayout? = null
        var v: TextView? = null
    }

    private val ws = listOf(
        W("hz", "Screen Hz") { "${it.hz}" },
        W("ping", "Ping ms") { if (it.ping < 0) "--" else "${it.ping}" },
        W("ram", "RAM used") { "${it.ramPct}%" },
        W("temp", "Temperature") { "${it.temp.toInt()}\u00B0" },
        W("net", "Net KB/s") { "${it.down}/${it.up}" },
        W("batt", "Battery") { "${it.batt}%" }
    )

    fun names() = ws.map { it.label }

    fun isOn(i: Int) = ws[i].box != null

    fun restore() {
        ws.forEachIndexed { i, w ->
            if (g.prefs.getBoolean("w_" + w.key, false)) show(w, i)
        }
    }

    fun update() {
        for (w in ws) w.v?.text = w.fn(g.st)
    }

    fun set(i: Int, on: Boolean) {
        val w = ws[i]
        g.prefs.edit().putBoolean("w_" + w.key, on).apply()
        if (on && w.box == null) {
            show(w, i)
        } else if (!on && w.box != null) {
            g.delWin(w.box)
            w.box = null
            w.v = null
        }
    }

    private fun show(w: W, i: Int) {
        val u = g.ui
        val v = u.tv("--", 20f, TXT, true)
        val box = u.vbox()
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.background = u.bg(0xD9101018.toInt(), 12, 0x33FFFFFF)
        box.setPadding(u.dp(12), u.dp(6), u.dp(12), u.dp(6))
        box.addView(u.tv(w.label, 9f, DIM))
        box.addView(v)
        val lp = g.wlp(WRAP, WRAP)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = g.prefs.getInt("x_" + w.key, u.dp(10))
        lp.y = g.prefs.getInt("y_" + w.key, u.dp(50) + i * u.dp(64))
        g.drag(box, lp) {
            g.prefs.edit().putInt("x_" + w.key, lp.x).putInt("y_" + w.key, lp.y).apply()
        }
        g.addWin(box, lp)
        w.box = box
        w.v = v
    }
}

class MonitorTab(g: GPAccess) : Tab(g, "Monitor") {
    private val rows = HashMap<String, TextView>()

    override fun build(body: LinearLayout) {
        rows.clear()
        body.addView(u.sec("Live stats"))
        val c = u.card()
        val items = listOf(
            "hz" to "Screen refresh",
            "ping" to "Ping",
            "ram" to "RAM",
            "batt" to "Battery",
            "temp" to "Temperature",
            "net" to "Network",
            "free" to "Storage free",
            "time" to "Session"
        )
        for ((k, label) in items) {
            val r = u.hbox()
            r.setPadding(u.dp(2), u.dp(6), u.dp(2), u.dp(6))
            r.addView(u.tv(label, 12f, DIM), u.lp(0, WRAP, 1f))
            val v = u.tv("-", 13f, TXT, true)
            rows[k] = v
            r.addView(v)
            c.addView(r)
        }
        body.addView(c)
        body.addView(u.note("In-game FPS cannot be read without root or ADB. Screen refresh shows your display rate."))

        body.addView(u.sec("Ping server"))
        val servers = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")
        body.addView(u.chips(servers, 3, servers.indexOf(g.host).coerceAtLeast(0)) { g.host = servers[it] })

        body.addView(u.sec("Floating widgets"))
        val c2 = u.card()
        g.widgets.names().forEachIndexed { i, n ->
            c2.addView(u.swRow(n, "", g.widgets.isOn(i)) { g.widgets.set(i, it) })
        }
        body.addView(c2)
        body.addView(u.note("Widgets can be dragged anywhere on the screen."))
        tick()
    }

    override fun tick() {
        val s = g.st
        rows["hz"]?.text = "${s.hz} Hz"
        rows["ping"]?.text = if (s.ping < 0) "-- ms" else "${s.ping} ms"
        rows["ram"]?.text = String.format("%.1f / %.1f GB  (%d%%)", s.ramUsed, s.ramTotal, s.ramPct)
        rows["batt"]?.text = "${s.batt}%" + if (s.charging) "  charging" else ""
        rows["temp"]?.text = String.format("%.1f C", s.temp)
        rows["net"]?.text = "down ${s.down} / up ${s.up} KB/s"
        rows["free"]?.text = String.format("%.1f GB", s.free)
        val t = (SystemClock.elapsedRealtime() - g.start) / 1000
        rows["time"]?.text = String.format("%d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
    }
}
