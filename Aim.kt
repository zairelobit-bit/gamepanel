package com.gamepanel.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout

class CrossView(c: Context) : View(c) {
    var style = 1
    var sz = 40
    var th = 2
    var gap = 4
    var col = Color.GREEN
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = sz / 2f
        p.color = col
        p.strokeWidth = th.toFloat()
        p.style = Paint.Style.STROKE
        when (style) {
            0 -> {
                p.style = Paint.Style.FILL
                cv.drawCircle(cx, cy, th * 1.8f, p)
            }
            1 -> {
                cv.drawLine(cx - r, cy, cx + r, cy, p)
                cv.drawLine(cx, cy - r, cx, cy + r, p)
            }
            2 -> {
                cv.drawLine(cx - r, cy, cx - gap, cy, p)
                cv.drawLine(cx + gap, cy, cx + r, cy, p)
                cv.drawLine(cx, cy - r, cx, cy - gap, p)
                cv.drawLine(cx, cy + gap, cx, cy + r, p)
            }
            3 -> cv.drawCircle(cx, cy, r, p)
            4 -> {
                cv.drawCircle(cx, cy, r, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(cx, cy, th * 1.5f, p)
            }
            else -> {
                cv.drawLine(cx - r, cy, cx + r, cy, p)
                cv.drawLine(cx, cy, cx, cy + r, p)
            }
        }
    }
}

class Cross(private val g: GPAccess) {
    private var v: CrossView? = null
    private var lp: WindowManager.LayoutParams? = null
    private val cols = intArrayOf(
        0xFFFF2D4A.toInt(), 0xFF39FF14.toInt(), 0xFF00E5FF.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFE600.toInt(), 0xFFFF4FD8.toInt()
    )

    val on: Boolean get() = g.prefs.getBoolean("c_on", false)

    fun enable(b: Boolean) {
        g.prefs.edit().putBoolean("c_on", b).apply()
        update()
    }

    fun update() {
        if (!on) {
            g.delWin(v)
            v = null
            lp = null
            return
        }
        val p = g.prefs
        val d = g.ui.d
        var view = v
        var params = lp
        if (view == null || params == null) {
            view = CrossView(g)
            val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params = g.wlp(WRAP, WRAP, f)
            params.gravity = Gravity.CENTER
            g.addWin(view, params)
            v = view
            lp = params
        }
        view.style = p.getInt("c_style", 1)
        view.sz = (p.getInt("c_size", 36).coerceAtLeast(8) * d).toInt()
        view.th = (p.getInt("c_th", 2).coerceAtLeast(1) * d).toInt().coerceAtLeast(1)
        view.gap = (p.getInt("c_gap", 4) * d).toInt()
        val a = p.getInt("c_alpha", 100).coerceIn(20, 100) * 255 / 100
        view.col = (cols[p.getInt("c_col", 1).coerceIn(0, 5)] and 0x00FFFFFF) or (a shl 24)
        val box = view.sz + g.ui.dp(24)
        params.width = box
        params.height = box
        params.x = ((p.getInt("c_x", 150) - 150) * d).toInt()
        params.y = ((p.getInt("c_y", 150) - 150) * d).toInt()
        g.wm.updateViewLayout(view, params)
        view.invalidate()
    }
}

class AimTab(g: GPAccess) : Tab(g, "Crosshair") {
    private fun save(k: String, v: Int) {
        g.prefs.edit().putInt(k, v).apply()
        g.cross.update()
    }

    override fun build(body: LinearLayout) {
        val p = g.prefs
        val c1 = u.card()
        c1.addView(u.swRow("Crosshair", "Always on top of the game", g.cross.on) { g.cross.enable(it) })
        body.addView(c1)
        body.addView(u.sec("Style"))
        body.addView(u.chips(listOf("Dot", "Cross", "Gap", "Circle", "Ring", "T shape"), 3, p.getInt("c_style", 1)) { save("c_style", it) })
        body.addView(u.sec("Color"))
        body.addView(u.chips(listOf("Red", "Green", "Cyan", "White", "Yellow", "Pink"), 3, p.getInt("c_col", 1)) { save("c_col", it) })
        body.addView(u.sec("Shape"))
        val c2 = u.card()
        c2.addView(u.slider("Size", 120, p.getInt("c_size", 36), { "$it dp" }) { save("c_size", it) })
        c2.addView(u.slider("Thickness", 8, p.getInt("c_th", 2), { "$it" }) { save("c_th", it) })
        c2.addView(u.slider("Gap", 30, p.getInt("c_gap", 4), { "$it" }) { save("c_gap", it) })
        c2.addView(u.slider("Opacity", 100, p.getInt("c_alpha", 100), { "$it%" }) { save("c_alpha", it) })
        body.addView(c2)
        body.addView(u.sec("Position"))
        val c3 = u.card()
        c3.addView(u.slider("Offset X", 300, p.getInt("c_x", 150), { "${it - 150}" }) { save("c_x", it) })
        c3.addView(u.slider("Offset Y", 300, p.getInt("c_y", 150), { "${it - 150}" }) { save("c_y", it) })
        body.addView(c3)
        body.addView(u.btn("Reset crosshair") {
            val e = p.edit()
            e.putInt("c_style", 1).putInt("c_col", 1).putInt("c_size", 36).putInt("c_th", 2)
            e.putInt("c_gap", 4).putInt("c_alpha", 100).putInt("c_x", 150).putInt("c_y", 150).apply()
            g.cross.update()
            g.rebuild()
        })
    }
}
