package org.kaqui

import android.content.Context
import android.graphics.Typeface
import android.widget.Toast
import androidx.preference.PreferenceManager

class TypefaceManager {
    companion object {
        private var font: Typeface? = null
        private var ready = false

        fun updateTypeface(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            // Fall back to the legacy custom_font preference for users upgrading
            // from before the gothic/mincho/custom font choice existed.
            val fontType = prefs.getString("font_type", null)
                    ?: if (prefs.getString("custom_font", null) != null) "custom" else "mincho"
            font = when (fontType) {
                // Mincho-style serif font (Noto Serif CJK on Android).
                "mincho" -> Typeface.SERIF
                "custom" -> {
                    val customFont = prefs.getString("custom_font", null)
                    if (customFont != null) {
                        try {
                            Typeface.createFromFile(customFont)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.failed_to_load_font, e.message), Toast.LENGTH_LONG).show()
                            return
                        }
                    } else {
                        // No custom font picked yet, fall back to the gothic font.
                        Typeface.DEFAULT
                    }
                }
                // The system default Japanese font is a gothic (sans-serif) font.
                else -> Typeface.DEFAULT
            }
            ready = true
        }

        fun getTypeface(context: Context): Typeface? {
            if (!ready)
                updateTypeface(context)
            return font
        }
    }
}