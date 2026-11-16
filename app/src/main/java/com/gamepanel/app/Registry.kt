package com.gamepanel.app

class Pages(g: GPAccess) {
    val home = HomeTab(g)
    val more = MoreTab(g)
    val monitor = MonitorTab(g)
    val aim = AimTab(g)
    val edge = EdgeTab(g)
    val apps = AppsTab(g)
}
