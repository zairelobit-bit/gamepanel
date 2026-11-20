package com.gamepanel.app

import android.content.Intent

class Pages(g: GPAccess) {
    val home = HomeTab(g)
    val more = MoreTab(g)
    val monitor = MonitorTab(g)
    val aim = AimTab(g)
    val edge = EdgeTab(g)
    val apps = AppsTab(g)
    val fl = FloatTab(g)
    val ft = FloatToolsTab(g)
    val snd = SoundTab(g)

    init {
        if (g.prefs.getInt("ver", 0) < 6) {
            g.prefs.edit().putBoolean("left", true).putInt("ver", 6).apply()
            g.left = true
        }
        try {
            g.startForegroundService(Intent(g, KeepService::class.java))
        } catch (e: Exception) { }
    }
}
