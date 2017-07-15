package org.kaqui.kaqui

import android.content.res.ColorStateList
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v7.widget.AppCompatButton
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*

const val NB_ANSWERS = 6

class MainActivity : AppCompatActivity() {
    lateinit var answerTexts: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val db = KanjiDb.getInstance(this)
        if (db.empty) {
            db.addKanjis(parseXml(resources.getXml(R.xml.kanjidic2)))
        }

        val answerTexts = ArrayList<TextView>(NB_ANSWERS)
        for (i in 0 until NB_ANSWERS) {
            answerTexts.add(TextView(this))
            answerTexts[i].layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)

            val buttonMaybe = AppCompatButton(this)
            buttonMaybe.text = "Maybe"
            ViewCompat.setBackgroundTintList(buttonMaybe, ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_orange_light)))
            val buttonSure = AppCompatButton(this)
            buttonSure.text = "Sure"
            ViewCompat.setBackgroundTintList(buttonSure, ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_green_light)))

            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.addView(answerTexts[i])
            layout.addView(buttonMaybe)
            layout.addView(buttonSure)

            answers_layout.addView(layout, i)
        }
        this.answerTexts = answerTexts

        showNewQuestion()
    }

    private fun <T> pickRandom(list: List<T>, sample: Int): List<T> {
        if (sample > list.size)
            throw RuntimeException("can't get a sample of size $sample on list of size ${list.size}")

        val chosen = mutableSetOf<T>()
        while (chosen.size < sample) {
            val r = list[(Math.random() * list.size).toInt()]
            chosen.add(r)
        }
        return chosen.toList()
    }

    private fun showNewQuestion() {
        val db = KanjiDb.getInstance(this)

        val ids = db.getAllIds()
        val questionKanji = db.getKanji(pickRandom(ids, 1)[0])

        kanji_text.text = questionKanji.kanji

        val answers = pickRandom(ids, NB_ANSWERS)

        for (i in 0 until NB_ANSWERS) {
            val kanji = db.getKanji(answers[i])
            val answerText = kanji.readings.filter { it.readingType == "ja_on" }.map { it.reading }.joinToString(", ") + "\n" +
                    kanji.readings.filter { it.readingType == "ja_kun" }.map { it.reading }.joinToString(", ")
            answerTexts[i].text = answerText
        }
    }
}
