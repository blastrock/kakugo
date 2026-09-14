package org.kaqui.theme

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.preference.PreferenceManager

private const val THEME_MODE_KEY = "theme_mode"
private const val LEGACY_DARK_THEME_KEY = "dark_theme"

enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: SYSTEM
    }
}

fun getThemeMode(context: Context): ThemeMode {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    migrateDarkThemePreference(context, prefs)
    return ThemeMode.fromId(prefs.getString(THEME_MODE_KEY, null))
}

fun isDarkTheme(context: Context): Boolean =
        when (getThemeMode(context)) {
            ThemeMode.SYSTEM -> isSystemInDarkMode(context)
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

@Composable
fun rememberThemeMode(): ThemeMode {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var themeMode by remember(context) { mutableStateOf(getThemeMode(context)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == THEME_MODE_KEY)
                themeMode = getThemeMode(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return themeMode
}

// Resolving SYSTEM through isSystemInDarkTheme() is what makes @PreviewLightDark
// work: previews have no stored preference, so they fall back to the uiMode of
// the preview configuration.
@Composable
fun isDarkTheme(): Boolean =
        when (rememberThemeMode()) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

fun isSystemInDarkMode(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

// Migrate from the old dark_theme boolean preference. Users whose choice already
// matched the system are moved to SYSTEM so that the app starts following the
// system for them, the others keep the appearance they picked.
private fun migrateDarkThemePreference(context: Context, prefs: SharedPreferences) {
    if (!prefs.contains(LEGACY_DARK_THEME_KEY))
        return

    val dark = prefs.getBoolean(LEGACY_DARK_THEME_KEY, false)
    val mode = when {
        dark == isSystemInDarkMode(context) -> ThemeMode.SYSTEM
        dark -> ThemeMode.DARK
        else -> ThemeMode.LIGHT
    }

    prefs.edit {
        if (!prefs.contains(THEME_MODE_KEY))
            putString(THEME_MODE_KEY, mode.id)
        remove(LEGACY_DARK_THEME_KEY)
    }
}
