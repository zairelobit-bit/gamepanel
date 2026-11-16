package com.gamepanel.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.cos
import kotlin.math.sin

class Ico(c: Context, val k: Int) : View(c) {
    var col = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density

    private fun ray(cv: Canvas, cx: Float, cy: Float, a: Double, r1: Float, r2: Float) {
        cv.drawLine(
            cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat(),
            cx + (cos(a) * r2).toFloat(), cy + (sin(a) * r2).toFloat(), p
        )
    }

    private fun poly(cv: Canvas, cx: Float, cy: Float, s: Float, vararg pts: Float) {
        val a = Path()
        var i = 0
        while (i < pts.size) {
            val x = cx + pts[i] * s
            val y = cy + pts[i + 1] * s
            if (i == 0) a.moveTo(x, y) else a.lineTo(x, y)
            i += 2
        }
        cv.drawPath(a, p)
    }

    private fun dot(cv: Canvas, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        cv.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) / 2f * 0.5f
        p.color = col
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f * d
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        when (k) {
            0 -> {
                cv.drawCircle(cx, cy, s * 0.5f, p)
                for (i in 0 until 8) ray(cv, cx, cy, Math.PI * i / 4, s * 0.8f, s * 1.1f)
            }
            1 -> {
                poly(cv, cx, cy, s, -1f, -0.35f, -0.4f, -0.35f, 0.2f, -0.9f, 0.2f, 0.9f, -0.4f, 0.35f, -1f, 0.35f, -1f, -0.35f)
                cv.drawArc(RectF(cx - s * 0.2f, cy - s * 0.5f, cx + s * 0.6f, cy + s * 0.5f), -50f, 100f, false, p)
                cv.drawArc(RectF(cx - s * 0.2f, cy - s * 0.95f, cx + s * 1.1f, cy + s * 0.95f), -50f, 100f, false, p)
            }
            2, 3 -> {
                cv.drawArc(RectF(cx - s * 0.7f, cy - s * 0.9f, cx + s * 0.7f, cy + s * 0.5f), 180f, 180f, false, p)
                poly(cv, cx, cy, s, -0.7f, -0.2f, -0.9f, 0.6f, 0.9f, 0.6f, 0.7f, -0.2f)
                dot(cv, cx, cy + s * 0.95f, s * 0.15f)
                if (k == 2) cv.drawLine(cx - s, cy - s, cx + s, cy + s, p)
            }
            4 -> {
                cv.drawArc(RectF(cx - s, cy - s, cx + s, cy + s), 150f, 240f, false, p)
                cv.drawLine(cx, cy, cx + s * 0.6f, cy - s * 0.5f, p)
                dot(cv, cx, cy, s * 0.12f)
            }
            5 -> {
                cv.drawCircle(cx, cy, s * 0.7f, p)
                for (i in 0 until 4) ray(cv, cx, cy, Math.PI * i / 2, s * 0.45f, s * 1.1f)
                dot(cv, cx, cy, s * 0.1f)
            }
            6 -> {
                cv.drawRoundRect(RectF(cx - s, cy - s * 0.6f, cx + s, cy + s * 0.75f), s * 0.2f, s * 0.2f, p)
                cv.drawCircle(cx, cy + s * 0.07f, s * 0.38f, p)
                poly(cv, cx, cy, s, -0.4f, -0.6f, -0.25f, -0.95f, 0.25f, -0.95f, 0.4f, -0.6f)
            }
            7 -> {
                cv.drawArc(RectF(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f), 200f, 250f, false, p)
                poly(cv, cx, cy, s, 0.1f, 0.5f, -0.1f, 0.8f, 0.2f, 1.0f)
            }
            8 -> {
                cv.drawCircle(cx, cy, s, p)
                for (i in -1..1) dot(cv, cx + i * s * 0.5f, cy, s * 0.13f)
            }
            9 -> {
                cv.drawRoundRect(RectF(cx - s * 0.7f, cy - s * 0.1f, cx + s * 0.7f, cy + s * 0.9f), s * 0.15f, s * 0.15f, p)
                cv.drawArc(RectF(cx - s * 0.45f, cy - s * 0.9f, cx + s * 0.45f, cy + s * 0.2f), 180f, 180f, false, p)
                dot(cv, cx, cy + s * 0.4f, s * 0.1f)
            }
            10 -> {
                cv.drawOval(RectF(cx - s, cy - s * 0.55f, cx + s, cy + s * 0.55f), p)
                dot(cv, cx, cy, s * 0.28f)
            }
            11 -> {
                cv.drawRect(RectF(cx - s * 0.55f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f), p)
                for (i in -1..1) {
                    cv.drawLine(cx + i * s * 0.3f, cy - s * 0.55f, cx + i * s * 0.3f, cy - s, p)
                    cv.drawLine(cx + i * s * 0.3f, cy + s * 0.55f, cx + i * s * 0.3f, cy + s, p)
                    cv.drawLine(cx - s * 0.55f, cy + i * s * 0.3f, cx - s, cy + i * s * 0.3f, p)
                    cv.drawLine(cx + s * 0.55f, cy + i * s * 0.3f, cx + s, cy + i * s * 0.3f, p)
                }
            }
            12 -> poly(cv, cx, cy, s, 0.2f, -1f, -0.5f, 0.1f, 0f, 0.1f, -0.2f, 1f, 0.5f, -0.1f, 0f, -0.1f, 0.2f, -1f)
            13 -> {
                cv.drawCircle(cx, cy - s * 0.2f, s * 0.6f, p)
                cv.drawLine(cx - s * 0.3f, cy + s * 0.55f, cx + s * 0.3f, cy + s * 0.55f, p)
                cv.drawLine(cx - s * 0.2f, cy + s * 0.85f, cx + s * 0.2f, cy + s * 0.85f, p)
            }
            14 -> {
                for (r in listOf(0.5f, 1.0f, 1.5f)) {
                    cv.drawArc(RectF(cx - s * r, cy + s * 0.7f - s * r, cx + s * r, cy + s * 0.7f + s * r), 225f, 90f, false, p)
                }
                dot(cv, cx, cy + s * 0.7f, s * 0.1f)
            }
            15 -> poly(cv, cx, cy, s, -0.5f, -0.5f, 0.5f, 0.5f, 0f, 0.9f, 0f, -0.9f, 0.5f, -0.5f, -0.5f, 0.5f)
            16 -> {
                cv.drawRoundRect(RectF(cx - s, cy - s * 0.55f, cx + s * 0.8f, cy + s * 0.55f), s * 0.15f, s * 0.15f, p)
                cv.drawLine(cx + s * 0.95f, cy - s * 0.2f, cx + s * 0.95f, cy + s * 0.2f, p)
                p.style = Paint.Style.FILL
                cv.drawRect(RectF(cx - s * 0.75f, cy - s * 0.3f, cx + s * 0.1f, cy + s * 0.3f), p)
                p.style = Paint.Style.STROKE
            }
            17 -> {
                poly(cv, cx, cy, s, 0f, -0.95f, 0.95f, 0.8f, -0.95f, 0.8f, 0f, -0.95f)
                cv.drawLine(cx, cy - s * 0.3f, cx, cy + s * 0.25f, p)
                dot(cv, cx, cy + s * 0.55f, s * 0.08f)
            }
            18 -> {
                for (i in 0..1) for (j in 0..1) {
                    val l = cx - s + i * s * 1.1f
                    val t = cy - s + j * s * 1.1f
                    cv.drawRoundRect(RectF(l, t, l + s * 0.9f, t + s * 0.9f), s * 0.12f, s * 0.12f, p)
                }
            }
            19 -> {
                for (i in -1..1) cv.drawLine(cx - s, cy + i * s * 0.6f, cx + s, cy + i * s * 0.6f, p)
                dot(cv, cx - s * 0.4f, cy - s * 0.6f, s * 0.2f)
                dot(cv, cx + s * 0.4f, cy, s * 0.2f)
                dot(cv, cx - s * 0.1f, cy + s * 0.6f, s * 0.2f)
            }
            20 -> {
                poly(cv, cx, cy, s, -1f, -0.9f, -1f, 0.9f, 1f, 0.9f)
                poly(cv, cx, cy, s, -0.7f, 0.4f, -0.2f, -0.2f, 0.2f, 0.2f, 0.8f, -0.6f)
            }
            21 -> poly(cv, cx, cy, s, 0.4f, -0.8f, -0.4f, 0f, 0.4f, 0.8f)
            22 -> {
                poly(cv, cx, cy, s, -0.4f, 0.9f, -0.4f, -0.9f)
                poly(cv, cx, cy, s, -0.8f, -0.5f, -0.4f, -0.9f, 0f, -0.5f)
                poly(cv, cx, cy, s, 0.4f, -0.9f, 0.4f, 0.9f)
                poly(cv, cx, cy, s, 0f, 0.5f, 0.4f, 0.9f, 0.8f, 0.5f)
            }
            23 -> {
                cv.drawLine(cx - s * 0.8f, cy, cx + s * 0.8f, cy, p)
                cv.drawLine(cx, cy - s * 0.8f, cx, cy + s * 0.8f, p)
            }
            else -> {
                cv.drawLine(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f, p)
                cv.drawLine(cx + s * 0.7f, cy - s * 0.7f, cx - s * 0.7f, cy + s * 0.7f, p)
            }
        }
    }
}

class Graph(c: Context) : View(c) {
    var a: List<Int> = emptyList()
    var b: List<Int> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = 0x22FFFFFF
        for (i in 0..2) cv.drawLine(0f, h * i / 2, w, h * i / 2, p)
        line(cv, a, ACC, w, h)
        line(cv, b, 0xFF4FC3F7.toInt(), w, h)
    }

    private fun line(cv: Canvas, v: List<Int>, col: Int, w: Float, h: Float) {
        if (v.size < 2) return
        p.color = col
        p.strokeWidth = 2f * d
        val path = Path()
        for (i in v.indices) {
            val x = w * (40 - v.size + i) / 39f
            val y = h - h * v[i].coerceIn(0, 100) / 100f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        cv.drawPath(path, p)
    }
}

fun Ui.orbBg(on: Boolean): RippleDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(if (on) 0x40FF2D4A else 0x22FFFFFF)
    g.setStroke(dp(1), if (on) ACC else 0x33FFFFFF)
    val m = GradientDrawable()
    m.shape = GradientDrawable.OVAL
    m.setColor(Color.WHITE)
    return RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), g, m)
}

fun Ui.orb(kind: Int, label: String, on: Boolean, f: (FrameLayout) -> Unit): LinearLayout {
    val box = vbox()
    box.gravity = Gravity.CENTER_HORIZONTAL
    box.setPadding(0, dp(6), 0, dp(4))
    val ring = FrameLayout(c)
    ring.background = orbBg(on)
    val ic = Ico(c, kind)
    ic.col = if (on) ACC else TXT
    ring.addView(ic, FrameLayout.LayoutParams(dp(46), dp(46)))
    ring.setOnClickListener { f(ring) }
    box.addView(ring, LinearLayout.LayoutParams(dp(46), dp(46)))
    if (label.isNotEmpty()) {
        val t = tv(label, 10f, DIM)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        box.addView(t, lp(WRAP, WRAP, 0f, 0, 4, 0, 0))
    }
    return box
}

fun Ui.orbSet(ring: FrameLayout, on: Boolean) {
    ring.background = orbBg(on)
    val ic = ring.getChildAt(0) as Ico
    ic.col = if (on) ACC else TXT
    ic.invalidate()
}
