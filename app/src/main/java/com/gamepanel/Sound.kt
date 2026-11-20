package com.gamepanel.app

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.media.AudioManager
import android.widget.LinearLayout

fun dndOk(g: GPAccess): Boolean {
    val nm = g.getSystemService(NotificationManager::class.java)
    if (nm.isNotificationPolicyAccessGranted) return true
    g.toast("Grant DND access in the Game Panel app first")
    return false
}

fun btOn(): Boolean = try {
    BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
} catch (e: Exception) {
    false
}

fun btSet(g: GPAccess, on: Boolean) {
    try {
        val a = BluetoothAdapter.getDefaultAdapter()
        if (a == null) {
            g.toast("This phone has no Bluetooth")
        } else if (on) {
            a.enable()
        } else {
            a.disable()
        }
    } catch (e: Exception) {
        g.toast("Bluetooth toggle is not allowed here")
    }
}

class SoundTab(g: GPAccess) : Tab(g, "Sound") {
    private fun vol(c: LinearLayout, au: AudioManager, name: String, stream: Int) {
        val mx = maxOf(1, au.getStreamMaxVolume(stream))
        c.addView(u.slider(name, mx, au.getStreamVolume(stream), { "${it * 100 / mx}%" }) {
            try {
                au.setStreamVolume(stream, it, 0)
            } catch (e: Exception) {
                g.toast("Grant DND access to change this volume")
            }
        })
    }

    override fun build(body: LinearLayout) {
        val au = g.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        body.addView(u.sec("Volume"))
        val c = u.card()
        vol(c, au, "Media", AudioManager.STREAM_MUSIC)
        vol(c, au, "Ringtone", AudioManager.STREAM_RING)
        vol(c, au, "Notifications", AudioManager.STREAM_NOTIFICATION)
        vol(c, au, "Alarm", AudioManager.STREAM_ALARM)
        body.addView(c)
        body.addView(u.sec("Ring mode"))
        val modes = intArrayOf(
            AudioManager.RINGER_MODE_NORMAL,
            AudioManager.RINGER_MODE_VIBRATE,
            AudioManager.RINGER_MODE_SILENT
        )
        val cur = modes.indexOf(au.ringerMode).coerceAtLeast(0)
        body.addView(u.chips(listOf("Normal", "Vibrate", "Silent"), 3, cur) {
            try {
                au.ringerMode = modes[it]
            } catch (e: Exception) {
                g.toast("Grant DND access to change the ring mode")
            }
        })
        body.addView(u.note("This page stays inside the game. Silent mode needs DND access."))
    }
}
