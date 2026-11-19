package com.gamepanel.app

import android.content.res.ColorStateList
import android.net.TrafficStats
import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File

object Cpu {
    fun pct(): Int {
        var cur = 0L
        var max = 0L
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val d = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            try {
                cur += File(d + "scaling_cur_freq").readText().trim().toLong()
                max += File(d + "cpuinfo_max_freq").readText().trim().toLong()
            } catch (e: Exception) { }
        }
        return if (max > 0) (cur * 100 / max).toInt() else -1
    }
}

object NetMeter {
    var down = 0f
    var up = 0f
    private var lt = 0L
    private var lr = 0L
    private var lx = 0L

    fun tick() {
        val now = SystemClock.elapsedRealtime()
        val r = TrafficStats.getTotalRxBytes()
        val x = TrafficStats.getTotalTxBytes()
        if (lt != 0L && r >= 0 && x >= 0 && now > lt) {
            val dt = (now - lt) / 1000f
            down = ((r - lr) / 1024f / dt).coerceAtLeast(0f)
            up = ((x - lx) / 1024f / dt).coerceAtLeast(0f)
        }
        lt = now
        lr = r
        lx = x
    }

    fun fmt(kb: Float): String =
        if (kb >= 1024f) String.format("%.1f MB/s", kb / 1024f) else String.format("%.0f KB/s", kb)
}

class StatsBar(private val g: GPAccess) {
    private var box: LinearLayout? = null
    private val vals = ArrayList<TextView>()
    private val bars = ArrayList<ProgressBar>()

    fun isOn() = box != null

    fun restore() {
        if (g.prefs.getBoolean("w_bar", false)) show()
    }

    fun set(on: Boolean) {
        g.prefs.edit().putBoolean("w_bar", on).apply()
        if (on && box == null) {
            show()
        } else if (!on && box != null) {
            g.delWin(box)
            box = null
            vals.clear()
            bars.clear()
        }
    }

    private fun show() {
        val u = g.ui
        val row = u.hbox()
        row.background = u.bg(0xE6101018.toInt(), 10, 0x44FFFFFF)
        row.setPadding(u.dp(8), u.dp(5), u.dp(8), u.dp(5))
        for (n in listOf("CPU", "RAM", "BAT", "TEMP", "FPS")) {
            val cell = u.vbox()
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.addView(u.tv(n, 8f, DIM))
            val v = u.tv("--", 11f, TXT, true)
            cell.addView(v)
            val pb = ProgressBar(g, null, android.R.attr.progressBarStyleHorizontal)
            pb.max = 100
            pb.progressTintList = ColorStateList.valueOf(ACC)
            pb.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
            cell.addView(pb, u.lp(u.dp(34), u.dp(3), 0f, 0, 2, 0, 0))
            row.addView(cell, u.lp(WRAP, WRAP, 0f, 4, 0, 4, 0))
            vals.add(v)
            bars.add(pb)
        }
        val lp = g.wlp(WRAP, WRAP)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = g.prefs.getInt("x_bar", u.dp(120))
        lp.y = g.prefs.getInt("y_bar", u.dp(30))
        g.drag(row, lp) {
            g.prefs.edit().putInt("x_bar", lp.x).putInt("y_bar", lp.y).apply()
        }
        g.addWin(row, lp)
        box = row
    }

    fun update() {
        if (box == null) return
        val s = g.st
        val data = listOf(
            (if (s.cpu < 0) "--" else "${s.cpu}%") to maxOf(s.cpu, 0),
            "${s.ramPct}%" to s.ramPct,
            "${s.batt}%" to s.batt,
            "${s.temp.toInt()}\u00B0" to (s.temp * 100 / 60).toInt(),
            "${s.hz}" to (s.hz * 100 / 120)
        )
        data.forEachIndexed { i, d ->
            vals[i].text = d.first
            bars[i].progress = d.second.coerceIn(0, 100)
        }
    }
}

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
        W("net", "Net speed") { NetMeter.fmt(NetMeter.down + NetMeter.up) },
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
        NetMeter.tick()
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

class MonitorTab(g: GPAccess) : Tab(g, "Game Stats") {
    private val rows = HashMap<String, TextView>()

    override fun build(body: LinearLayout) {
        rows.clear()
        body.addView(u.sec("Live stats"))
        val c = u.card()
        val items = listOf(
            "hz" to "FPS (screen)",
            "cpu" to "CPU (by frequency)",
            "ping" to "Ping",
            "ram" to "RAM",
            "batt" to "Battery",
            "temp" to "Temperature",
            "dl" to "Download",
            "ul" to "Upload",
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
        body.addView(u.note("FPS here is the screen frame rate. In-game FPS needs root or ADB. Ping is measured to the server below, not to your game server."))

        body.addView(u.sec("Ping server"))
        val servers = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")
        body.addView(u.chips(servers, 3, servers.indexOf(g.host).coerceAtLeast(0)) {
            g.host = servers[it]
            g.prefs.edit().putString("hostsel", servers[it]).apply()
        })

        body.addView(u.sec("Floating widgets"))
        val c2 = u.card()
        c2.addView(u.swRow("Stats bar", "CPU, RAM, BAT, TEMP, FPS", g.sbar.isOn()) { g.sbar.set(it) })
        g.widgets.names().forEachIndexed { i, n ->
            c2.addView(u.swRow(n, "", g.widgets.isOn(i)) { g.widgets.set(i, it) })
        }
        body.addView(c2)
        body.addView(u.note("Widgets can be dragged anywhere on the screen."))
        tick()
    }

    override fun tick() {
        val s = g.st
        rows["hz"]?.text = "${s.hz}"
        rows["cpu"]?.text = if (s.cpu < 0) "N/A" else "${s.cpu}%"
        rows["ping"]?.text = if (s.ping < 0) "-- ms" else "${s.ping} ms"
        rows["ram"]?.text = String.format("%.1f / %.1f GB  (%d%%)", s.ramUsed, s.ramTotal, s.ramPct)
        rows["batt"]?.text = "${s.batt}%" + if (s.charging) "  charging" else ""
        rows["temp"]?.text = String.format("%.1f C", s.temp)
        rows["dl"]?.text = NetMeter.fmt(NetMeter.down)
        rows["ul"]?.text = NetMeter.fmt(NetMeter.up)
        rows["free"]?.text = String.format("%.1f GB", s.free)
        val t = (SystemClock.elapsedRealtime() - g.start) / 1000
        rows["time"]?.text = String.format("%d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
    }
}
