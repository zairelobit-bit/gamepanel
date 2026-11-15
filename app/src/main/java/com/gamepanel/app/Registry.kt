package com.gamepanel.app

fun makeTabs(g: GPAccess): List<Tab> =
    listOf(ToolsTab(g), MonitorTab(g), AppsTab(g), EdgeTab(g))
