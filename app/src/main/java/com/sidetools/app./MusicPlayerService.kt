package com.sidetools.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class MusicPlayerService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var lyricsView: View

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createLyricsOverlay()
    }

    private fun createLyricsOverlay() {
        lyricsView = LayoutInflater.from(this).inflate(R.layout.music_player_lyrics, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 200,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM; y = 100 }
        
        windowManager.addView(lyricsView, params)
        
        val lyricsText = lyricsView.findViewById<TextView>(R.id.lyrics_text)
        Thread {
            val sampleLyrics = listOf(
                "♪ Playing: Epic Anime OST...",
                "♪ sidetools active - No lag...",
                "♪ Enjoy the 4K experience...",
                "♪ DroidBubble style lyrics ♪"
            )
            var index = 0
            while (true) {
                Thread.sleep(3000)
                lyricsText.text = sampleLyrics[index % sampleLyrics.size]
                index++
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(lyricsView)
    }
}
