package org.kaqui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.util.TypedValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.kaqui.R

private val LightColors = lightColors(
    primary = Color(0xFF99CC00),
    primaryVariant = Color(0xFF9CCC65),
    secondary = Color(0xFF3F51B5),
    surface = Color(0xFFDDDDDD),
)

private val DarkColors = darkColors(
    primary = Color(0xFF99CC00),
    primaryVariant = Color(0xFF9CCC65),
    secondary = Color(0xFF303F9F)
)

val LocalThemeAttributes = staticCompositionLocalOf {
    ThemeAttributes(
        itemGood = Color.Unspecified,
        itemBad = Color.Unspecified,
        historyBackground = Color.Unspecified
    )
}

data class ThemeAttributes(
    val itemGood: Color,
    val itemBad: Color,
    val historyBackground: Color
)

@Composable
fun KakugoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val typedValue = TypedValue()

    // Resolve theme attributes
    context.theme.resolveAttribute(R.attr.itemGood, typedValue, true)
    val itemGood = Color(typedValue.data)

    context.theme.resolveAttribute(R.attr.itemBad, typedValue, true)
    val itemBad = Color(typedValue.data)

    context.theme.resolveAttribute(R.attr.historyBackground, typedValue, true)
    val historyBackground = Color(typedValue.data)

    val themeAttributes = ThemeAttributes(
        itemGood = itemGood,
        itemBad = itemBad,
        historyBackground = historyBackground
    )

    CompositionLocalProvider(
        LocalThemeAttributes provides themeAttributes
    ) {
        MaterialTheme(
            colors = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
