package com.alpwarestudio.chargefreeze.data

import android.content.Context

/** Lightweight local preferences for user-facing app settings. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.fromValue(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.value))
        set(value) = prefs.edit().putString(KEY_THEME, value.value).apply()

    var languageMode: LanguageMode
        get() = LanguageMode.fromValue(prefs.getString(KEY_LANGUAGE, LanguageMode.SYSTEM.value))
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.value).apply()

    var startOnUsbConnect: Boolean
        get() = prefs.getBoolean(KEY_USB_START, false)
        set(value) = prefs.edit().putBoolean(KEY_USB_START, value).apply()

    var showPersistentNotification: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_NOTIFICATION, value).apply()

    var freezeMargin: Int
        get() = prefs.getInt(KEY_FREEZE_MARGIN, 2)
        set(value) = prefs.edit().putInt(KEY_FREEZE_MARGIN, value.coerceIn(1, 5)).apply()

    var strategy: FreezeStrategy
        get() = FreezeStrategy.fromValue(prefs.getString(KEY_STRATEGY, FreezeStrategy.AUTO.value))
        set(value) = prefs.edit().putString(KEY_STRATEGY, value.value).apply()

    companion object {
        private const val FILE_NAME = "chargefreeze_preferences"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANGUAGE = "language_mode"
        private const val KEY_USB_START = "start_on_usb_connect"
        private const val KEY_PERSISTENT_NOTIFICATION = "show_persistent_notification"
        private const val KEY_FREEZE_MARGIN = "freeze_margin"
        private const val KEY_STRATEGY = "freeze_strategy"
    }
}

enum class ThemeMode(val value: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromValue(value: String?): ThemeMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

enum class LanguageMode(val value: String) {
    SYSTEM("system"), ENGLISH("en"), TURKISH("tr");

    companion object {
        fun fromValue(value: String?): LanguageMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

enum class FreezeStrategy(val value: String) {
    AUTO("auto"), NATIVE_HOLD("native_hold"), MOVING_THRESHOLD("moving_threshold");

    companion object {
        fun fromValue(value: String?): FreezeStrategy = entries.firstOrNull { it.value == value } ?: AUTO
    }
}
