package org.kaqui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

private val LightColors = lightColors(
    primary = Color(0xFF3F51B5),
    primaryVariant = Color(0xFF303F9F),
    secondary = Color(0xFF99CC00),
    surface = Color(0xFFDDDDDD),
    background = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onSurface = Color.Black,
    onBackground = Color.Black
)

private val DarkColors = darkColors(
    primary = Color(0xFF3F51B5),
    primaryVariant = Color(0xFF303F9F),
    secondary = Color(0xFF99CC00),
    surface = Color(0xFF222222),
    background = Color(0xFF080808),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onSurface = Color.White,
    onBackground = Color.White
)

// Light theme colors from XML
private val LightThemeColors = ThemeAttributes(
    itemGood = Color(0xFF9CCC65),
    itemMeh = Color(0xFFC5CAE9),
    itemBad = Color(0xFFFCE4EC),
    historyBackground = Color(0xFFDDDDDD),
    wrongAnswerBackground = Color(0xFFFFDDDD),
    correctAnswerBackground = Color(0xFFDEFFD3),
    backgroundSure = Color(0xFF9CCC65),
    backgroundMaybe = Color(0xFFDCEDC8),
    backgroundDontKnow = Color(0xFFE0E0E0),
    compositionGood = Color(0xFF9CCC65),
    compositionBadSelected = Color(0xFFEF9A9A),
    compositionBadNotSelected = Color(0xFFFFCDD2),
    statsItemsGood = Color(0xFF9CCC65),
    statsItemsBad = Color(0xFFEF9A9A),
    drawingDontKnow = Color(0xFFFF7F7F)
)

// Dark theme colors from XML
private val DarkThemeColors = ThemeAttributes(
    itemGood = Color(0xFF085300),
    itemMeh = Color(0xFF313F4D),
    itemBad = Color(0xFF500000),
    historyBackground = Color(0xFF222222),
    wrongAnswerBackground = Color(0xFF350000),
    correctAnswerBackground = Color(0xFF092600),
    backgroundSure = Color(0xFF085300),
    backgroundMaybe = Color(0xFF303F30),
    backgroundDontKnow = Color(0xFF282828),
    compositionGood = Color(0xFF085300),
    compositionBadSelected = Color(0xFF350000),
    compositionBadNotSelected = Color(0xFF650000),
    statsItemsGood = Color(0xFF085300),
    statsItemsBad = Color(0xFF650000),
    drawingDontKnow = Color(0xFFFF7F7F)
)

val LocalThemeAttributes = staticCompositionLocalOf { LightThemeColors }

data class ThemeAttributes(
    val itemGood: Color,
    val itemMeh: Color,
    val itemBad: Color,
    val historyBackground: Color,
    val wrongAnswerBackground: Color,
    val correctAnswerBackground: Color,
    val backgroundSure: Color,
    val backgroundMaybe: Color,
    val backgroundDontKnow: Color,
    val compositionGood: Color,
    val compositionBadSelected: Color,
    val compositionBadNotSelected: Color,
    val statsItemsGood: Color,
    val statsItemsBad: Color,
    val drawingDontKnow: Color
) {
    /**
     * Get color based on score value
     * Maps score ranges to item colors (bad, meh, good)
     */
    fun getColorFromScore(score: Double): Color {
        // BAD_WEIGHT = 0.3
        return when (score) {
            in 0.0..0.3 -> itemBad
            in 0.3..<1.0 -> itemMeh
            1.0 -> itemGood
            else -> itemBad
        }
    }
}

@Composable
fun KakugoTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean("dark_theme", false)

    val themeAttributes = if (darkTheme) DarkThemeColors else LightThemeColors
    val materialColors = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalThemeAttributes provides themeAttributes
    ) {
        MaterialTheme(
            colors = materialColors,
            content = content
        )
    }
}
