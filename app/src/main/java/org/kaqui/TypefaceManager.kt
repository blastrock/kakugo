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
            val customFont = PreferenceManager.getDefaultSharedPreferences(context).getString("custom_font", null)
            if (customFont != null) {
                try {
                    font = Typeface.createFromFile(customFont)
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.failed_to_load_font, e.message), Toast.LENGTH_LONG).show()
                    return
                }
            } else {
                font = null
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