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

    init {
        try {
            g.startForegroundService(Intent(g, KeepService::class.java))
        } catch (e: Exception) { }
    }
}
