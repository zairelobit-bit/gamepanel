package com.gamepanel.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Lightweight procedural pixel-art scene; no network images or extra assets required. */
class PixelLobbyView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun block(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        p.color = color
        p.style = Paint.Style.FILL
        c.drawRect(x, y, x + w, y + h, p)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        block(canvas, 0f, 0f, w, h, 0xFF111326.toInt())
        // Neon dusk sky and distant pixel skyline.
        block(canvas, 0f, h * .58f, w, h * .42f, 0xFF171B38.toInt())
        val unit = (w / 44f).coerceAtLeast(5f)
        for (i in 0..13) {
            val x = i * unit * 3.3f
            val bh = h * (.13f + ((i * 17) % 9) / 50f)
            block(canvas, x, h * .62f - bh, unit * 2.5f, bh, if (i % 2 == 0) 0xFF242B50.toInt() else 0xFF202642.toInt())
            for (r in 0..2) for (q in 0..1) {
                if ((i + r + q) % 3 != 0) block(canvas, x + unit * (.45f + q), h * .65f - bh + r * unit * 1.5f, unit * .45f, unit * .55f, 0xFF47DDF2.toInt())
            }
        }
        // Moon and pixel stars.
        block(canvas, w * .76f, h * .13f, unit * 3f, unit * 3f, 0xFFFFD98A.toInt())
        block(canvas, w * .79f, h * .10f, unit * 2f, unit * 1f, 0xFF111326.toInt())
        val stars = arrayOf(.08f to .18f, .19f to .31f, .39f to .12f, .58f to .24f, .9f to .34f, .68f to .43f)
        stars.forEachIndexed { i, v -> block(canvas, w * v.first, h * v.second, unit * (if (i % 2 == 0) .65f else .4f), unit * .65f, 0xFFB8F7FF.toInt()) }
        // Pixel hero: original chibi-style gamer silhouette, deliberately abstract.
        val cx = w * .42f; val y = h * .39f; val u = unit * 1.12f
        block(canvas, cx - u * 2f, y, u * 4f, u * 3f, 0xFFFFD2B0.toInt())
        block(canvas, cx - u * 2.4f, y - u * .7f, u * 4.8f, u * 1.4f, 0xFF25213D.toInt())
        block(canvas, cx - u * 2.8f, y + u * .2f, u * .8f, u * 2.1f, 0xFF25213D.toInt())
        block(canvas, cx + u * 2f, y + u * .2f, u * .8f, u * 2.1f, 0xFF25213D.toInt())
        block(canvas, cx - u * 1.3f, y + u * 1.25f, u * .55f, u * .35f, 0xFF25213D.toInt())
        block(canvas, cx + u * .8f, y + u * 1.25f, u * .55f, u * .35f, 0xFF25213D.toInt())
        block(canvas, cx - u * 2.6f, y + u * 3f, u * 5.2f, u * 3.7f, 0xFF45C7E8.toInt())
        block(canvas, cx - u * 3.4f, y + u * 3.5f, u * 1.1f, u * 2.4f, 0xFF45C7E8.toInt())
        block(canvas, cx + u * 2.3f, y + u * 3.5f, u * 1.1f, u * 2.4f, 0xFF45C7E8.toInt())
        block(canvas, cx - u * 1.7f, y + u * 2.8f, u * 3.4f, u * .45f, 0xFFFF75C8.toInt())
        // Ground glow and pixel floor.
        block(canvas, 0f, h * .91f, w, h * .09f, 0xFF0B1024.toInt())
        for (i in 0..18) block(canvas, i * w / 18f, h * .92f, 1.5f, h * .08f, 0xFF26385B.toInt())
        p.color = 0x6648DDF4
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        canvas.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), 18f, 18f, p)
    }
}
