package top.deepcommunity.app.core.common

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String) {
    FOLLOW_SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

object ThemeManager {
    private const val PREF_NAME = "deep_theme_prefs"
    private const val KEY_THEME_MODE = "key_theme_mode"
    private const val KEY_HAPTIC_ENABLED = "key_haptic_enabled"

    private val _themeMode = MutableStateFlow(AppThemeMode.FOLLOW_SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val modeOrdinal = prefs.getInt(KEY_THEME_MODE, AppThemeMode.FOLLOW_SYSTEM.ordinal)
        _themeMode.value = AppThemeMode.entries.getOrElse(modeOrdinal) { AppThemeMode.FOLLOW_SYSTEM }
        _hapticEnabled.value = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE, mode.ordinal)
            .apply()
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        _hapticEnabled.value = enabled
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAPTIC_ENABLED, enabled)
            .apply()
    }
}
