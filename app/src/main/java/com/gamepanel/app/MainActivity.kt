package com.gamepanel.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
            setBackgroundColor(0xFF101014.toInt())
        }
        root.addView(TextView(this).apply {
            text = "GAME PANEL"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF2D4A.toInt())
        })
        fun btn(t: String, f: () -> Unit) {
            root.addView(Button(this).apply {
                text = t
                setOnClickListener { f() }
            })
        }
        btn("1. Izinkan Overlay") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("2. Mulai Panel") {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
        }
        btn("Stop Panel") { stopService(Intent(this, OverlayService::class.java)) }
        setContentView(root)
    }
}
