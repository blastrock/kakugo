package org.kaqui.kaqui

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val db = KanjiDb.getInstance(this)
        if (db.empty) {
            db.addKanjis(parseXml(resources.getXml(R.xml.kanjidic2)))
        }

        val ids = db.getAllIds()
        val kanji = db.getKanji(pickRandom(ids, 1)[0])

        kanji_text.text = kanji.kanji
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
}
