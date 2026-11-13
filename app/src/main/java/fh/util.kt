package fh

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.core.view.WindowCompat
import com.fuck.iab.R
import com.fuck.iab.ScriptType
import com.fuck.iab.ThemeHelper

/**
 * The window background follows the theme, and the system bar icons sit on top of it, so light
 * icons on a light background are invisible. Ask for dark icons in the light theme, and for
 * light icons in the dark one.
 */
fun applySystemBarIcons(activity: Activity, root: View) {
    val darkIcons = !ThemeHelper.isNight(activity)

    WindowCompat.getInsetsController(activity.window, root).apply {
        isAppearanceLightStatusBars = darkIcons
        isAppearanceLightNavigationBars = darkIcons
    }
}

