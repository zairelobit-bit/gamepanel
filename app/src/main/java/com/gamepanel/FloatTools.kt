package com.gamepanel.app

import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout

object Theme {
    val names = listOf("Red", "Cyan", "Green", "Violet", "Amber", "Pink")
    val cols = intArrayOf(
        0xFFFF2D4A.toInt(), 0xFF00D0FF.toInt(), 0xFF2EE59D.toInt(),
        0xFF9B7BFF.toInt(), 0xFFFFB020.toInt(), 0xFFFF5CC8.toInt()
    )

    fun load(c: Context) {
        val i = c.getSharedPreferences("gp", Context.MODE_PRIVATE).getInt("theme", 0)
        ACC = cols[i.coerceIn(0, cols.size - 1)]
    }
}

private class Frame(c: Context, val hit: (MotionEvent) -> Unit) : LinearLayout(c) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        hit(ev)
        return super.dispatchTouchEvent(ev)
    }
}

// Jendela mengambang sungguhan: ditarik lewat judul, diubah ukurannya lewat sudut berwarna
class FWin(
    private val g: GPAccess,
    title: String,
    content: View,
    w: Int,
    h: Int,
    private val typing: Boolean,
    private val onClose: () -> Unit
) {
    companion object {
        const val FLAG_BASE = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }

    private val u = g.ui
    private val lp = g.wlp(g.ui.dp(w), g.ui.dp(h), FLAG_BASE)
    private val root = Frame(g) { e -> hit(e) }
    private var open = false

    init {
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = u.dp(48)
        lp.y = u.dp(56)
        root.orientation = LinearLayout.VERTICAL
        root.background = u.bg(0xF2101018.toInt(), 14, 0x33FFFFFF)

        val bar = u.hbox()
        bar.setPadding(u.dp(10), u.dp(4), u.dp(4), u.dp(4))
        bar.background = u.bg(0x22FFFFFF, 14)
        bar.addView(u.tv(title, 12f, TXT, true), u.lp(0, WRAP, 1f))
        val x = Ico(g, 24)
        x.col = DIM
        x.setOnClickListener { close() }
        bar.addView(x, u.lp(u.dp(26), u.dp(26)))
        bar.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = lp.x
                        sy = lp.y
                        tx = e.rawX
                        ty = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = sx + (e.rawX - tx).toInt()
                        lp.y = sy + (e.rawY - ty).toInt()
                        upd()
                    }
                }
                return true
            }
        })
        root.addView(bar, u.lp(MATCH, WRAP))
        root.addView(content, u.lp(MATCH, 0, 1f, 6, 6, 6, 0))

        val foot = u.hbox()
        foot.gravity = Gravity.END
        val grip = View(g)
        grip.background = u.bg(ACC, 5)
        grip.setOnTouchListener(object : View.OnTouchListener {
            var sw = 0
            var sh = 0
            var tx = 0f
            var ty = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sw = lp.width
                        sh = lp.height
                        tx = e.rawX
                        ty = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val m = g.resources.displayMetrics
                        lp.width = (sw + (e.rawX - tx).toInt()).coerceIn(u.dp(190), m.widthPixels)
                        lp.height = (sh + (e.rawY - ty).toInt()).coerceIn(u.dp(170), m.heightPixels)
                        upd()
                    }
                }
                return true
            }
        })
        foot.addView(grip, u.lp(u.dp(24), u.dp(14), 0f, 0, 4, 8, 4))
        root.addView(foot, u.lp(MATCH, WRAP))
    }

    private fun hit(e: MotionEvent) {
        if (e.action == MotionEvent.ACTION_DOWN) focus(true)
        if (e.action == MotionEvent.ACTION_OUTSIDE) focus(false)
    }

    private fun focus(on: Boolean) {
        if (!typing || !open) return
        val nf = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val tm = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        val now = (lp.flags and nf) == 0
        if (now == on) return
        lp.flags = if (on) ((lp.flags and nf.inv()) or tm) else ((lp.flags or nf) and tm.inv())
        upd()
        if (!on) {
            try {
                val im = g.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                im.hideSoftInputFromWindow(root.windowToken, 0)
            } catch (e: Exception) { }
        }
    }

    private fun upd() {
        if (!open) return
        try {
            g.wm.updateViewLayout(root, lp)
        } catch (e: Exception) { }
    }

    fun show() {
        g.addWin(root, lp)
        open = true
    }

    fun close() {
        if (!open) return
        open = false
        g.delWin(root)
        onClose()
    }
}

object Floats {
    private val wins = ArrayList<FWin>()

    fun add(g: GPAccess, title: String, content: View, w: Int, h: Int, typing: Boolean, cleanup: () -> Unit = {}) {
        lateinit var win: FWin
        win = FWin(g, title, content, w, h, typing) {
            cleanup()
            wins.remove(win)
        }
        wins.add(win)
        win.show()
    }

    fun closeAll() {
        for (w in ArrayList(wins)) w.close()
    }

    fun reset() {
        wins.clear()
    }
}

object FloatTools {
    fun browser(g: GPAccess) {
        val u = g.ui
        val wv = try {
            WebView(g)
        } catch (e: Throwable) {
            null
        }
        if (wv == null) {
            g.toast("WebView is not available on this phone")
            return
        }
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()
        val box = u.vbox()
        val row = u.hbox()
        val url = EditText(g)
        url.setSingleLine(true)
        url.hint = "Search or type address"
        url.setHintTextColor(DIM)
        url.setTextColor(TXT)
        url.textSize = 12f
        url.imeOptions = EditorInfo.IME_ACTION_GO
        url.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        url.background = u.bg(0x1FFFFFFF, 10)
        url.setPadding(u.dp(10), u.dp(8), u.dp(10), u.dp(8))
        fun go() {
            val q = url.text.toString().trim()
            if (q.isEmpty()) return
            val dest = if (q.contains(" ") || !q.contains(".")) {
                "https://www.google.com/search?q=" + Uri.encode(q)
            } else if (q.startsWith("http")) {
                q
            } else {
                "https://$q"
            }
            wv.loadUrl(dest)
        }
        url.setOnEditorActionListener { _, _, _ ->
            go()
            true
        }
        row.addView(url, u.lp(0, WRAP, 1f, 0, 0, 6, 0))
        row.addView(u.btn("Go") { go() }, u.lp(u.dp(52), WRAP))
        box.addView(row, u.lp(MATCH, WRAP, 0f, 0, 0, 0, 6))
        box.addView(wv, u.lp(MATCH, 0, 1f))
        wv.loadUrl("https://www.google.com")
        Floats.add(g, "Browser", box, 320, 270, true) { wv.destroy() }
        g.closePanel()
    }

    fun notes(g: GPAccess) {
        val u = g.ui
        val et = EditText(g)
        et.setText(g.prefs.getString("note", "") ?: "")
        et.hint = "Write here"
        et.setHintTextColor(DIM)
        et.setTextColor(TXT)
        et.textSize = 13f
        et.gravity = Gravity.TOP or Gravity.START
        et.background = u.bg(0x14FFFFFF, 10)
        et.setPadding(u.dp(10), u.dp(8), u.dp(10), u.dp(8))
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                g.prefs.edit().putString("note", s?.toString() ?: "").apply()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        Floats.add(g, "Notes", et, 260, 200, true)
        g.closePanel()
    }

    fun calc(g: GPAccess) {
        val u = g.ui
        val box = u.vbox()
        val disp = u.tv("0", 26f, TXT, true)
        disp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        disp.setPadding(u.dp(8), u.dp(4), u.dp(8), u.dp(4))
        disp.maxLines = 1
        box.addView(disp, u.lp(MATCH, u.dp(48)))
        var acc = 0.0
        var op = ""
        var cur = "0"
        var fresh = true
        fun fmt(d: Double): String {
            if (d.isNaN() || d.isInfinite()) return "Error"
            val s = d.toString()
            return if (s.endsWith(".0")) s.dropLast(2) else s
        }
        fun calcNow() {
            val b = cur.toDoubleOrNull() ?: 0.0
            acc = when (op) {
                "+" -> acc + b
                "-" -> acc - b
                "x" -> acc * b
                "/" -> if (b == 0.0) Double.NaN else acc / b
                else -> b
            }
        }
        fun press(k: String) {
            when (k) {
                "C" -> {
                    acc = 0.0
                    op = ""
                    cur = "0"
                    fresh = true
                }
                "+/-" -> {
                    cur = if (cur.startsWith("-")) cur.substring(1) else if (cur != "0") "-$cur" else cur
                }
                "%" -> {
                    cur = fmt((cur.toDoubleOrNull() ?: 0.0) / 100.0)
                }
                "+", "-", "x", "/" -> {
                    if (!(fresh && op != "")) {
                        calcNow()
                        cur = fmt(acc)
                    }
                    op = k
                    fresh = true
                }
                "=" -> {
                    calcNow()
                    cur = fmt(acc)
                    op = ""
                    fresh = true
                }
                "." -> {
                    if (fresh) {
                        cur = "0."
                        fresh = false
                    } else if (!cur.contains(".")) {
                        cur = "$cur."
                    }
                }
                else -> {
                    cur = if (fresh || cur == "0") k else cur + k
                    fresh = false
                }
            }
            disp.text = cur
        }
        val keys = listOf(
            listOf("C", "+/-", "%", "/"),
            listOf("7", "8", "9", "x"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=")
        )
        for (r in keys) {
            val row = u.hbox()
            for (k in r) {
                row.addView(u.btn(k, k == "=") { press(k) }, u.lp(0, WRAP, if (k == "0") 2f else 1f, 2, 2, 2, 2))
            }
            box.addView(row, u.lp(MATCH, 0, 1f))
        }
        Floats.add(g, "Calculator", box, 250, 340, false)
        g.closePanel()
    }
}

class FloatToolsTab(g: GPAccess) : Tab(g, "Float Tools") {
    override fun build(body: LinearLayout) {
        body.addView(u.note("Real floating windows. They stay on top of any game and work on every phone."))
        val box = u.vbox()
        body.addView(box)
        val add = u.grid(box, 3)
        add(u.orb(18, "Browser", false) { FloatTools.browser(g) })
        add(u.orb(9, "Notes", false) { FloatTools.notes(g) })
        add(u.orb(8, "Calc", false) { FloatTools.calc(g) })
        body.addView(u.btn("Close all windows") { Floats.closeAll() })
        body.addView(u.note("Drag the title bar to move. Drag the colored corner to resize. Tap a text box to type, tap outside to give control back to the game."))
    }
}
