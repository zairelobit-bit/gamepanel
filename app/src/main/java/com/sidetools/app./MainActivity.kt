package com.sidetools.app

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : Activity() {
    private val pageBackground = Color.rgb(16, 17, 26)
    private val cardBackground = Color.rgb(29, 31, 45)
    private val accent = Color.rgb(139, 124, 255)
    private val secondary = Color.rgb(39, 42, 59)
    private lateinit var content: LinearLayout
    private lateinit var shizukuStatus: TextView

    private data class Feature(
        val name: String,
        val detail: String,
        val action: String? = null,
        val special: String? = null
    )

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST) {
            updateShizukuStatus()
            toast(if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) "Shizuku access granted" else "Shizuku permission not granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::shizukuStatus.isInitialized) updateShizukuStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(pageBackground)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = column()
        heading.addView(label("GAMEPANEL", 12, accent, true).apply { letterSpacing = .18f })
        heading.addView(label("Your game, your setup.", 25, Color.WHITE, true).apply {
            setPadding(0, dp(5), 0, 0)
        })
        heading.addView(label("A clean hub for useful Android tools", 13, 0xFFAAAEC0.toInt()).apply {
            setPadding(0, dp(5), 0, 0)
        })
        top.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label("GP", 17, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(accent, 18)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(top)

        val summary = column().apply {
            background = rounded(cardBackground, 20)
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        summary.addView(label("DEVICE SNAPSHOT", 11, 0xFFAAAEC0.toInt(), true))
        val details = label(deviceSummary(), 14, Color.WHITE).apply { setPadding(0, dp(8), 0, 0) }
        summary.addView(details)
        content.addView(summary, margin(top = 20, bottom = 10))

        val shizukuCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF25233B.toInt(), 18)
            setPadding(dp(15), dp(13), dp(12), dp(13))
        }
        val shizukuCopy = column()
        shizukuCopy.addView(label("Shizuku support", 15, Color.WHITE, true))
        shizukuStatus = label("Checking service…", 12, 0xFFB7B5D0.toInt()).apply { setPadding(0, dp(4), 0, 0) }
        shizukuCopy.addView(shizukuStatus)
        shizukuCard.addView(shizukuCopy, LinearLayout.LayoutParams(0, -2, 1f))
        shizukuCard.addView(Button(this).apply {
            text = "Connect"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(accent, 14)
            setOnClickListener { connectShizuku() }
        }, LinearLayout.LayoutParams(dp(98), dp(44)))
        content.addView(shizukuCard, margin(top = 8, bottom = 22))
        updateShizukuStatus()

        val groups = featureGroups()
        groups.forEach { (groupName, features) ->
            content.addView(label(groupName, 17, Color.WHITE, true).apply {
                setPadding(dp(2), dp(8), 0, dp(4))
            })
            content.addView(label("Shortcuts open Android's relevant settings or run a local action.", 12, 0xFF969AAF.toInt()).apply {
                setPadding(dp(2), 0, 0, dp(10))
            })
            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            features.chunked(2).forEach { rowFeatures ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowFeatures.forEachIndexed { index, feature ->
                    row.addView(featureCard(feature), LinearLayout.LayoutParams(0, dp(98), 1f).apply {
                        if (index == 0) rightMargin = dp(8) else leftMargin = dp(8)
                        bottomMargin = dp(10)
                    })
                }
                if (rowFeatures.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, dp(98), 1f))
                grid.addView(row)
            }
            content.addView(grid)
        }

        content.addView(label("No fake ‘FPS boost’ or hidden system tweaks. Android controls remain in charge.", 12, 0xFF969AAF.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(8))
        })
    }

    private fun featureCard(feature: Feature): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(cardBackground, 16)
        setPadding(dp(13), dp(10), dp(10), dp(10))
        isClickable = true
        isFocusable = true
        addView(label("›  ${feature.name}", 14, Color.WHITE, true))
        addView(label(feature.detail, 11, 0xFFAAAEC0.toInt()).apply { setPadding(0, dp(5), 0, 0) })
        setOnClickListener { runFeature(feature) }
    }

    private fun runFeature(feature: Feature) {
        when (feature.special) {
            "snapshot" -> toast(deviceSummary())
            "shizuku" -> connectShizuku()
            "share" -> shareApp()
            "app-info" -> openIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:$packageName")))
            else -> {
                val action = feature.action
                if (action == null) {
                    toast("This shortcut is unavailable on this Android version")
                } else {
                    val intent = Intent(action)
                    if (action == "android.settings.APP_NOTIFICATION_SETTINGS") {
                        intent.putExtra("android.provider.extra.APP_PACKAGE", packageName)
                        intent.putExtra("app_package", packageName)
                        intent.putExtra("app_uid", applicationInfo.uid)
                    }
                    openIntent(intent)
                }
            }
        }
    }

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("This setting isn't available on this device")
        }
    }

    private fun connectShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                toast("Start the Shizuku service first, then tap Connect")
                openIntent(Intent("moe.shizuku.manager.action.MANAGER"))
                return
            }
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                updateShizukuStatus()
                toast("Shizuku is connected and permission is granted")
            } else {
                Shizuku.requestPermission(SHIZUKU_REQUEST)
            }
        } catch (_: Exception) {
            toast("Shizuku is not running. Start it in the Shizuku app first.")
        }
    }

    private fun updateShizukuStatus() {
        if (!::shizukuStatus.isInitialized) return
        shizukuStatus.text = try {
            when {
                !Shizuku.pingBinder() -> "Service not running — start Shizuku first"
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED -> "Connected · permission granted"
                else -> "Service ready · permission not granted"
            }
        } catch (_: Exception) {
            "Install and start Shizuku to connect"
        }
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Try GamePanel")
        }
        openIntent(Intent.createChooser(intent, "Share GamePanel"))
    }

    private fun deviceSummary(): String {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val memory = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        memory.getMemoryInfo(info)
        val freeRam = info.availMem / (1024L * 1024L)
        val batteryText = percent?.let { "$it% battery" } ?: "Battery unavailable"
        return "${Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }} ${Build.MODEL}  ·  Android ${Build.VERSION.RELEASE}\n$batteryText  ·  ${freeRam} MB RAM available"
    }

    private fun featureGroups(): List<Pair<String, List<Feature>>> {
        fun f(name: String, detail: String, action: String? = null, special: String? = null) = Feature(name, detail, action, special)
        return listOf(
            "DEVICE & GAME" to listOf(
                f("Device snapshot", "Battery, Android & RAM", special = "snapshot"),
                f("Shizuku access", "Connect with user approval", special = "shizuku"),
                f("App details", "Storage, version & controls", special = "app-info"),
                f("Developer options", "Android developer settings", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                f("Accessibility", "Accessibility services", Settings.ACTION_ACCESSIBILITY_SETTINGS),
                f("System update", "Check available updates", "android.settings.SYSTEM_UPDATE_SETTINGS"),
                f("Storage", "Review device storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                f("Battery settings", "Battery & power usage", Settings.ACTION_BATTERY_SAVER_SETTINGS),
                f("Battery optimization", "Background usage controls", Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                f("Share GamePanel", "Send app link or name", special = "share")
            ),
            "DISPLAY & COMFORT" to listOf(
                f("Display", "Screen settings", Settings.ACTION_DISPLAY_SETTINGS),
                f("Brightness", "Brightness & adaptive display", Settings.ACTION_DISPLAY_SETTINGS),
                f("Dark mode", "Theme and dark appearance", Settings.ACTION_DISPLAY_SETTINGS),
                f("Screen timeout", "Auto screen-off duration", Settings.ACTION_DISPLAY_SETTINGS),
                f("Font size", "Text and display size", Settings.ACTION_DISPLAY_SETTINGS),
                f("Wallpaper", "Choose a wallpaper", Intent.ACTION_SET_WALLPAPER),
                f("Screen saver", "Screen saver settings", "android.settings.DREAM_SETTINGS"),
                f("Auto-rotate", "Rotation and orientation", Settings.ACTION_DISPLAY_SETTINGS),
                f("Color correction", "Color & visibility controls", Settings.ACTION_ACCESSIBILITY_SETTINGS),
                f("Live captions", "Captions and accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS)
            ),
            "SOUND & FOCUS" to listOf(
                f("Sound settings", "Ringtone, media & alarms", Settings.ACTION_SOUND_SETTINGS),
                f("Media volume", "Adjust sound levels", Settings.ACTION_SOUND_SETTINGS),
                f("Ringtone", "Choose a phone ringtone", Settings.ACTION_SOUND_SETTINGS),
                f("Notification sound", "Notification sound options", Settings.ACTION_SOUND_SETTINGS),
                f("Alarm sound", "Alarm volume & options", Settings.ACTION_SOUND_SETTINGS),
                f("Do Not Disturb", "Silence interruptions", Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                f("Notifications", "App notification controls", "android.settings.APP_NOTIFICATION_SETTINGS"),
                f("Vibration", "Haptics and vibration", Settings.ACTION_SOUND_SETTINGS),
                f("Audio accessibility", "Hearing assistance", Settings.ACTION_ACCESSIBILITY_SETTINGS),
                f("Media output", "Connected audio devices", Settings.ACTION_BLUETOOTH_SETTINGS)
            ),
            "NETWORK & CONNECTIONS" to listOf(
                f("Wi-Fi", "Networks and connections", Settings.ACTION_WIFI_SETTINGS),
                f("Bluetooth", "Pair audio & game devices", Settings.ACTION_BLUETOOTH_SETTINGS),
                f("Internet", "Network and internet", Settings.ACTION_WIRELESS_SETTINGS),
                f("Data usage", "Mobile data usage", Settings.ACTION_DATA_USAGE_SETTINGS),
                f("Airplane mode", "Wireless connection settings", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                f("VPN", "Manage VPN connections", Settings.ACTION_VPN_SETTINGS),
                f("Hotspot", "Tethering and hotspot", Settings.ACTION_WIRELESS_SETTINGS),
                f("Location", "Location permission & access", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                f("NFC", "Contactless connection settings", Settings.ACTION_NFC_SETTINGS),
                f("Cast", "Cast and connected devices", Settings.ACTION_WIRELESS_SETTINGS)
            ),
            "APPS, PRIVACY & SYSTEM" to listOf(
                f("All apps", "Installed app management", Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),
                f("Default apps", "Choose default applications", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                f("App permissions", "Review permission access", Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
                f("Special app access", "Overlay and special access", Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                f("Security", "Security and device controls", Settings.ACTION_SECURITY_SETTINGS),
                f("Privacy", "Privacy dashboard & controls", Settings.ACTION_PRIVACY_SETTINGS),
                f("Accounts", "Account sync and setup", Settings.ACTION_SYNC_SETTINGS),
                f("Date & time", "Clock and time zone", Settings.ACTION_DATE_SETTINGS),
                f("Keyboard & input", "Keyboard and input methods", Settings.ACTION_INPUT_METHOD_SETTINGS),
                f("Language", "Language and regional settings", Settings.ACTION_LOCALE_SETTINGS)
            )
        )
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val SHIZUKU_REQUEST = 1001
    }
}
