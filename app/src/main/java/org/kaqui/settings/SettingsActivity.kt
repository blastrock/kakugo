package org.kaqui.settings

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, JlptSelectionFragment.newInstance())
                .commit()
    }

    override fun onBackPressed() {
        val f = supportFragmentManager.findFragmentById(android.R.id.content) as JlptSelectionFragment
        if (!f.onBackPressed())
            super.onBackPressed()
    }
}
