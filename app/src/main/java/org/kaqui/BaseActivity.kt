package org.kaqui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.kaqui.theme.isDarkTheme

abstract class BaseActivity : AppCompatActivity() {
    private var currentTheme: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        currentTheme = getPrefTheme()
        setTheme(currentTheme)

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        refreshTheme()

        super.onResume()
    }

    private fun refreshTheme() {
        val newTheme = getPrefTheme()
        if (newTheme != currentTheme) {
            recreate()
        }
    }

    private fun getPrefTheme() =
            if (isDarkTheme(this))
                R.style.AppThemeDark
            else
                R.style.AppThemeLight
}
