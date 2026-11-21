package com.sidetools.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.TextView
import kotlin.random.Random

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var fpsView: View
    private lateinit var toolsView: View
    private var isToolsVisible = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFPSOverlay()
        createToolsOverlay()
    }

    private fun createFPSOverlay() {
        fpsView = LayoutInflater.from(this).inflate(R.layout.floating_fps, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 50; y = 150 }
        
        windowManager.addView(fpsView, params)
        
        val fpsText = fpsView.findViewById<TextView>(R.id.fps_text)
        Thread {
            while (true) {
                Thread.sleep(1000)
                // Simulasi FPS (Ganti dengan Choreographer.FrameCallback untuk FPS asli)
                fpsText.text = "${Random.nextInt(55, 61)} FPS"
            }
        }.start()
    }

    private fun createToolsOverlay() {
        toolsView = LayoutInflater.from(this).inflate(R.layout.floating_calculator, null)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "TOGGLE_TOOLS") toggleTools()
        return START_STICKY
    }

    private fun toggleTools() {
        if (isToolsVisible) {
            windowManager.removeView(toolsView)
        } else {
            val params = WindowManager.LayoutParams(
                600, 800, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            windowManager.addView(toolsView, params)
            
            val webView = toolsView.findViewById<WebView>(R.id.web_view)
            webView.settings.javaScriptEnabled = true
            webView.loadUrl("https://www.google.com/search?q=calculator") // Browser Calculator
        }
        isToolsVisible = !isToolsVisible
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(fpsView)
        if (isToolsVisible) windowManager.removeView(toolsView)
    }
}
