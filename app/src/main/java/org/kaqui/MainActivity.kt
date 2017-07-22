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
            val intent = Intent(this, QuizzActivity::class.java)
            intent.putExtra("kanji_reading", true)
            startActivity(intent)
        }
        start_reading_kanji_quizz.setOnClickListener {
            val intent = Intent(this, QuizzActivity::class.java)
            intent.putExtra("kanji_reading", false)
            startActivity(intent)
        }
    }
}

