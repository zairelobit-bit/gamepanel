package com.sidetools.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sidetools.app.utils.FeatureList

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Mulai Floating Service (FPS Counter) saat aplikasi dibuka
        startService(Intent(this, FloatingService::class.java))
    }

    fun openFloatingTools(view: android.view.View) {
        // Mengganti tombol "+" dengan pemanggilan Floating Calculator/Browser
        val intent = Intent(this, FloatingService::class.java).apply {
            action = "TOGGLE_TOOLS"
        }
        startService(intent)
        Toast.makeText(this, "Floating Tools Aktif", Toast.LENGTH_SHORT).show()
    }

    fun openMusicPlayer(view: android.view.View) {
        startService(Intent(this, MusicPlayerService::class.java))
        Toast.makeText(this, "Music Player with Lyrics Active", Toast.LENGTH_SHORT).show()
    }

    fun runOptimization(view: android.view.View) {
        // Konsep Moha Lab: Simulasi pembersihan & tweak
        Toast.makeText(this, "Optimizing System... (Moha Lab Engine)", Toast.LENGTH_SHORT).show()
    }
}
