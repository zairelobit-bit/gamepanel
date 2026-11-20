package com.gamepanel.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

var ACC = 0xFFFF2D4A.toInt()
val TXT = 0xFFF2F2F7.toInt()
val DIM = 0xFFA3A3B0.toInt()
const val MATCH = -1
const val WRAP = -2

class Sw(c: Context, start: Boolean, private val f: (Boolean) -> Unit) : View(c) {
    var on = start
        private set
    private var p = if (start) 1f else 0f
    private val d = c.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setOnClickListener {
            slide(!on)
            f(on)
        }
    }

    fun slide(v: Boolean) {
        on = v
        val a = ValueAnimator.ofFloat(p, if (v) 1f else 0f)
        a.setDuration(170)
        a.addUpdateListener {
            p = it.animatedValue as Float
            invalidate()
        }
        a.start()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((56 * d).toInt(), (28 * d).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = ArgbEvaluator().evaluate(p, 0xFF30303A.toInt(), ACC) as Int
        cv.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, paint)
        paint.color = 0x55000000
        cv.drawCircle(h / 2 + (w - h) * p, h / 2 + 1.5f * d, h / 2 - 3 * d, paint)
        paint.color = Color.WHITE
        cv.drawCircle(h / 2 + (w - h) * p, h / 2, h / 2 - 3 * d, paint)
        paint.textSize = 9 * d
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.alpha = (255 * (if (on) p else 1 - p)).toInt()
        val ty = h / 2 + 3.2f * d
        if (on) cv.drawText("ON", w * 0.30f, ty, paint) else cv.drawText("OFF", w * 0.70f, ty, paint)
    }
}

class Ui(val c: Context) {
    val d = c.resources.displayMetrics.density

    fun dp(v: Int) = (v * d).toInt()

    fun accA(a: Int): Int = (ACC and 0x00FFFFFF) or (a shl 24)

    fun shade(c: Int, f: Float): Int {
        val r = (((c shr 16) and 255) * f).toInt()
        val g = (((c shr 8) and 255) * f).toInt()
        val b = ((c and 255) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun grad(c0: Int, c1: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c0, c1))
        g.cornerRadius = dp(r).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    fun press(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
            false
        }
    }

    fun bg(color: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    fun rip(color: Int, r: Int, stroke: Int = 0) =
        RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), bg(color, r, stroke), bg(Color.WHITE, r))

    fun lp(w: Int, h: Int, wt: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
        val q = LinearLayout.LayoutParams(w, h, wt)
        q.setMargins(dp(l), dp(t), dp(r), dp(b))
        return q
    }

    fun tv(t: String, sp: Float = 13f, col: Int = TXT, bold: Boolean = false): TextView {
        val v = TextView(c)
        v.text = t
        v.textSize = sp
        v.setTextColor(col)
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD)
        return v
    }

    fun vbox(): LinearLayout {
        val v = LinearLayout(c)
        v.orientation = LinearLayout.VERTICAL
        return v
    }

    fun hbox(): LinearLayout {
        val v = LinearLayout(c)
        v.orientation = LinearLayout.HORIZONTAL
        v.gravity = Gravity.CENTER_VERTICAL
        return v
    }

    fun sec(t: String): TextView {
        val v = tv(t.uppercase(), 10f, accA(0xCC), true)
        v.letterSpacing = 0.14f
        v.setPadding(dp(2), dp(12), 0, dp(5))
        val m = GradientDrawable()
        m.setColor(ACC)
        m.cornerRadius = dp(2).toFloat()
        m.setSize(dp(3), dp(11))
        v.setCompoundDrawablesWithIntrinsicBounds(m, null, null, null)
        v.compoundDrawablePadding = dp(7)
        return v
    }

    fun note(t: String): TextView {
        val v = tv(t, 10f, DIM)
        v.setPadding(dp(2), dp(4), dp(2), dp(4))
        return v
    }

    fun card(): LinearLayout {
        val v = vbox()
        v.background = grad(0x22FFFFFF, 0x0DFFFFFF, 16, 0x24FFFFFF)
        v.setPadding(dp(12), dp(6), dp(12), dp(6))
        return v
    }

    fun look(v: TextView, on: Boolean) {
        val d = if (on) grad(accA(0x70), accA(0x30), 12, ACC) else grad(0x26FFFFFF, 0x12FFFFFF, 12, 0x22FFFFFF)
        v.background = RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), d, bg(Color.WHITE, 12))
        v.setTextColor(if (on) Color.WHITE else TXT)
    }

    fun btn(t: String, on: Boolean = false, f: () -> Unit): TextView {
        val v = tv(t, 12f, TXT, true)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(4), dp(11), dp(4), dp(11))
        look(v, on)
        press(v)
        v.setOnClickListener { f() }
        return v
    }

    fun primary(t: String, f: () -> Unit): TextView {
        val v = tv(t, 14f, Color.WHITE, true)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(8), dp(14), dp(8), dp(14))
        v.background = RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), grad(ACC, shade(ACC, 0.7f), 16), bg(Color.WHITE, 16))
        press(v)
        v.setOnClickListener { f() }
        return v
    }

    fun grid(parent: LinearLayout, cols: Int): (View) -> Unit {
        var r: LinearLayout? = null
        var n = 0
        return { v ->
            if (n++ % cols == 0) {
                r = hbox().also { parent.addView(it, lp(MATCH, WRAP)) }
            }
            r!!.addView(v, lp(0, WRAP, 1f, 2, 2, 2, 2))
        }
    }

    fun chips(items: List<String>, cols: Int, sel: Int, f: (Int) -> Unit): LinearLayout {
        val box = vbox()
        val add = grid(box, cols)
        val vs = ArrayList<TextView>()
        items.forEachIndexed { i, s ->
            val v = btn(s, i == sel) {}
            v.setOnClickListener {
                vs.forEachIndexed { j, x -> look(x, j == i) }
                f(i)
            }
            vs.add(v)
            add(v)
        }
        return box
    }

    fun swRow(title: String, sub: String, on: Boolean, f: (Boolean) -> Unit): LinearLayout {
        val r = hbox()
        r.setPadding(dp(2), dp(7), dp(2), dp(7))
        val t = vbox()
        t.addView(tv(title, 13f, TXT))
        if (sub.isNotEmpty()) t.addView(tv(sub, 10f, DIM))
        r.addView(t, lp(0, WRAP, 1f))
        r.addView(Sw(c, on, f))
        return r
    }

    fun slider(title: String, mx: Int, cur: Int, fmt: (Int) -> String, f: (Int) -> Unit): LinearLayout {
        val box = vbox()
        box.setPadding(dp(2), dp(5), dp(2), dp(2))
        val head = hbox()
        val value = tv(fmt(cur), 12f, TXT, true)
        head.addView(tv(title, 12f, DIM), lp(0, WRAP, 1f))
        head.addView(value)
        box.addView(head)
        val sb = SeekBar(c)
        sb.max = mx
        sb.progress = cur
        sb.progressTintList = ColorStateList.valueOf(ACC)
        sb.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sb.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {
                value.text = fmt(p)
                if (user) f(p)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        box.addView(sb)
        return box
    }
}
