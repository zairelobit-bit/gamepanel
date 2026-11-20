package com.gamepanel.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

class AppsTab(g: GPAccess) : Tab(g, "Apps") {
    private var picking = false

    private fun launchables(): List<ResolveInfo> {
        val pm = g.packageManager
        return pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != g.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    private fun cell(ic: Drawable?, t: String, f: () -> Unit): LinearLayout {
        val c = u.vbox()
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setPadding(0, u.dp(6), 0, u.dp(6))
        c.background = u.rip(0x00000000, 10)
        c.setOnClickListener { f() }
        if (ic != null) {
            val im = ImageView(g)
            im.setImageDrawable(ic)
            c.addView(im, LinearLayout.LayoutParams(u.dp(40), u.dp(40)))
        } else {
            val plus = u.tv("+", 22f, ACC, true)
            plus.gravity = Gravity.CENTER
            plus.background = u.bg(0x14FFFFFF, 12, ACC)
            c.addView(plus, LinearLayout.LayoutParams(u.dp(40), u.dp(40)))
        }
        val lb = u.tv(t, 10f, DIM)
        lb.maxLines = 1
        lb.gravity = Gravity.CENTER
        c.addView(lb, LinearLayout.LayoutParams(u.dp(58), WRAP))
        return c
    }

    override fun build(body: LinearLayout) {
        if (picking) {
            pick(body)
            return
        }
        val pm = g.packageManager
        val fav = (g.prefs.getStringSet("apps", emptySet()) ?: emptySet()).toList()
        val fc = u.card()
        fc.addView(u.swRow("Floating windows", "Experimental, needs setup", g.prefs.getBoolean("float", false)) {
            g.prefs.edit().putBoolean("float", it).apply()
        })
        fc.addView(u.btn("Floating setup and phone check") { g.go(g.pages.fl) })
        fc.addView(u.btn("Float Tools: browser, notes, calc") { g.go(g.pages.ft) })
        body.addView(fc)
        body.addView(u.sec("Favorites"))
        val box = u.vbox()
        body.addView(box)
        val add = u.grid(box, 4)
        for (pkg in fav) {
            try {
                val name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                add(cell(pm.getApplicationIcon(pkg), name) { g.launch(pkg) })
            } catch (e: Exception) { }
        }
        add(cell(null, "Add") {
            picking = true
            g.rebuild()
        })

        body.addView(u.sec("Detected games"))
        val box2 = u.vbox()
        body.addView(box2)
        val add2 = u.grid(box2, 4)
        var n = 0
        for (ri in launchables()) {
            if (n >= 16) break
            if (ri.activityInfo.applicationInfo.category != ApplicationInfo.CATEGORY_GAME) continue
            val pkg = ri.activityInfo.packageName
            add2(cell(ri.loadIcon(pm), ri.loadLabel(pm).toString()) { g.launch(pkg) })
            n++
        }
        if (n == 0) body.addView(u.note("No games detected. Add your games to Favorites."))
    }

    private fun pick(body: LinearLayout) {
        val sel = (g.prefs.getStringSet("apps", emptySet()) ?: emptySet()).toMutableSet()
        val pm = g.packageManager
        body.addView(u.sec("Choose favorite apps"))
        body.addView(u.btn("Done", true) {
            picking = false
            g.rebuild()
        })
        for (ri in launchables()) {
            val pkg = ri.activityInfo.packageName
            val row = u.hbox()
            row.setPadding(u.dp(4), u.dp(6), u.dp(4), u.dp(6))
            val im = ImageView(g)
            im.setImageDrawable(ri.loadIcon(pm))
            row.addView(im, u.lp(u.dp(34), u.dp(34)))
            row.addView(u.tv(ri.loadLabel(pm).toString(), 13f, TXT), u.lp(0, WRAP, 1f, 10, 0, 10, 0))
            val mark = u.tv(if (pkg in sel) "ADDED" else "", 11f, ACC, true)
            row.addView(mark)
            row.setOnClickListener {
                if (!sel.remove(pkg)) sel.add(pkg)
                g.prefs.edit().putStringSet("apps", HashSet(sel)).apply()
                mark.text = if (pkg in sel) "ADDED" else ""
            }
            body.addView(row)
        }
    }
}

class EdgeTab(g: GPAccess) : Tab(g, "Edge") {
    override fun build(body: LinearLayout) {
        val p = g.prefs
        body.addView(u.sec("Handle side"))
        body.addView(u.chips(listOf("Left", "Right"), 2, if (g.left) 0 else 1) {
            g.left = it == 0
            p.edit().putBoolean("left", g.left).apply()
            g.place()
        })
        body.addView(u.sec("Handle visibility"))
        body.addView(u.chips(listOf("Always", "Games only"), 2, p.getInt("hmode", 0)) {
            p.edit().putInt("hmode", it).apply()
            g.applyVis()
        })
        body.addView(u.note("Games only hides the handle outside games. You can still open the panel from the app."))
        body.addView(u.sec("Handle"))
        val c = u.card()
        c.addView(u.slider("Position", 100, g.hy, { "$it%" }) {
            g.hy = it
            p.edit().putInt("hy", it).apply()
            g.place()
        })
        c.addView(u.slider("Length", 160, p.getInt("hh", 80), { "$it dp" }) {
            p.edit().putInt("hh", it).apply()
            g.place()
        })
        c.addView(u.slider("Opacity", 100, p.getInt("ha", 90), { "$it%" }) {
            p.edit().putInt("ha", it).apply()
            g.place()
        })
        body.addView(c)
        body.addView(u.sec("Theme"))
        body.addView(u.chips(Theme.names, 3, p.getInt("theme", 0)) {
            p.edit().putInt("theme", it).apply()
            Theme.load(g)
            g.applyTheme()
        })
        body.addView(u.sec("Panel"))
        val pc = u.card()
        pc.addView(u.slider("Width", 440, p.getInt("pw", 340), { "${it.coerceAtLeast(260)} dp" }) {
            p.edit().putInt("pw", it.coerceAtLeast(260)).apply()
            g.place()
        })
        pc.addView(u.slider("Opacity", 100, p.getInt("pa", 100), { "${it.coerceAtLeast(50)}%" }) {
            p.edit().putInt("pa", it.coerceAtLeast(50)).apply()
            g.panelOpacity()
        })
        body.addView(pc)
        body.addView(u.btn("Close panel") { g.closePanel() })
    }
}
