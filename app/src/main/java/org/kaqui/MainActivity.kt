package org.kaqui

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.main_activity.*
import org.kaqui.kaqui.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)

        start_kanji_reading_quizz.setOnClickListener {
            startActivity(Intent(this, QuizzActivity::class.java))
        }
    }
}

