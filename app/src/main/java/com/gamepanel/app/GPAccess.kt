package com.gamepanel.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class GPAccess : AccessibilityService() {
    companion object {
        @Volatile var inst: GPAccess? = null
    }

    override fun onServiceConnected() {
        inst = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        inst = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
