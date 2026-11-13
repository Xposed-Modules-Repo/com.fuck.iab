package com.fuck.iab

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object ThemeHelper {
    private const val SETTINGS_GROUP = "ui"
    private const val THEME_KEY = "theme"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    // ---- theme -----------------------------------------------------------------

    /** The theme the user picked with the toolbar button, system until then. */
    fun theme(context: Context): Int {
        return context.getSharedPreferences(SETTINGS_GROUP, Context.MODE_PRIVATE).getInt(THEME_KEY, THEME_SYSTEM)
    }

    fun setTheme(context: Context, mode: Int) {
        context.getSharedPreferences(SETTINGS_GROUP, Context.MODE_PRIVATE).edit { putInt(THEME_KEY, mode) }
        applyTheme(context)
    }

    @JvmStatic
    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme(context)) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** What the app is showing right now, whether from the system or from the pick. */
    fun isNight(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}