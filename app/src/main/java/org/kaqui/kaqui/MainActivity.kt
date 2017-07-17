package org.kaqui.kaqui

import android.animation.ValueAnimator
import android.app.ProgressDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v7.widget.AppCompatButton
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.kaqui.kaqui.settings.SettingsActivity
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.zip.GZIPInputStream


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val NB_ANSWERS = 6
    }

    private lateinit var answerTexts: List<TextView>
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>
    private var currentQuestion: Kanji? = null
    private var currentAnswers: List<Kanji>? = null

    private var downloadProgress: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val answerTexts = ArrayList<TextView>(NB_ANSWERS)
        for (i in 0 until NB_ANSWERS) {
            answerTexts.add(TextView(this))
            answerTexts[i].layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)

            val buttonMaybe = AppCompatButton(this)
            buttonMaybe.text = "Maybe"
            ViewCompat.setBackgroundTintList(buttonMaybe, ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_orange_light)))
            buttonMaybe.setOnClickListener { _ -> this.onAnswerClicked(Certainty.MAYBE, i) }
            val buttonSure = AppCompatButton(this)
            buttonSure.text = "Sure"
            ViewCompat.setBackgroundTintList(buttonSure, ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_green_light)))
            buttonSure.setOnClickListener { _ -> this.onAnswerClicked(Certainty.SURE, i) }

            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.addView(answerTexts[i])
            layout.addView(buttonMaybe)
            layout.addView(buttonSure)

            answers_layout.addView(layout, i)
        }
        this.answerTexts = answerTexts
        dontknow_button.setOnClickListener { _ -> this.onAnswerClicked(Certainty.DONTKNOW, 0) }

        sheetBehavior = BottomSheetBehavior.from(history_view)
        if (savedInstanceState?.getBoolean("playerOpen", false) ?: false)
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        else
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        val db = KanjiDb.getInstance(this)
        if (db.empty) {
            downloadProgress = ProgressDialog(this)
            downloadProgress!!.setMessage("Downloading kanjidic database")
            downloadProgress!!.setCancelable(false)
            downloadProgress!!.show()

            async(CommonPool) {
                downloadKanjiDic()
            }
        } else {
            showNewQuestion()
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
    }

    private fun downloadKanjiDic() {
        try {
            Log.v(TAG, "Downloading kanjidic")
            val url = URL("http://nihongo.monash.edu/kanjidic2/kanjidic2.xml.gz")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.doOutput = true
            urlConnection.connect()

            urlConnection.inputStream.use { gzipStream ->
                GZIPInputStream(gzipStream, 1024).use { textStream ->
                    val xpp = XmlPullParserFactory.newInstance().newPullParser()
                    xpp.setInput(textStream, "UTF-8")

                    val db = KanjiDb.getInstance(this)
                    db.addKanjis(parseXml(xpp))
                }
            }
            Log.v(TAG, "Finished downloading kanjidic")
            async(UI) {
                downloadProgress!!.dismiss()
                showNewQuestion()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and parse kanjidic", e)
            async(UI) {
                Toast.makeText(this@MainActivity, "Failed to download and parse kanjidic: " + e.message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun <T> pickRandom(list: List<T>, sample: Int, avoid: Set<T> = setOf()): List<T> {
        if (sample > list.size - avoid.size)
            throw RuntimeException("can't get a sample of size $sample on list of size ${list.size - avoid.size}")

        val chosen = mutableSetOf<T>()
        while (chosen.size < sample) {
            val r = list[(Math.random() * list.size).toInt()]
            if (r !in avoid)
                chosen.add(r)
        }
        return chosen.toList()
    }

    private fun showNewQuestion() {
        val db = KanjiDb.getInstance(this)

        val ids = db.getEnabledIds()

        if (ids.size < NB_ANSWERS) {
            Toast.makeText(this, "You must select at least $NB_ANSWERS kanjis", Toast.LENGTH_LONG).show()
            return
        }

        val questionId = pickRandom(ids, 1)[0]
        currentQuestion = db.getKanji(questionId)

        kanji_text.text = currentQuestion!!.kanji

        val currentAnswers = (pickRandom(ids, NB_ANSWERS - 1, setOf(questionId)).map { db.getKanji(it) } + listOf(currentQuestion!!)).toMutableList()
        shuffle(currentAnswers)
        this.currentAnswers = currentAnswers
        fillAnswers()
    }

    private fun <T> shuffle(l: MutableList<T>) {
        val rg = Random()
        for (i in l.size - 1 downTo 1) {
            val target = rg.nextInt(i)
            val tmp = l[i]
            l[i] = l[target]
            l[target] = tmp
        }
    }

    private fun fillAnswers() {
        for (i in 0 until NB_ANSWERS) {
            val kanji = currentAnswers!![i]
            val answerText = kanji.readings.filter { it.readingType == "ja_on" }.map { it.reading }.joinToString(", ") + "\n" +
                    kanji.readings.filter { it.readingType == "ja_kun" }.map { it.reading }.joinToString(", ")
            answerTexts[i].text = answerText
        }
    }

    private fun onAnswerClicked(certainty: Certainty, position: Int) {
        if (currentQuestion == null || currentAnswers == null) {
            showNewQuestion()
            return
        }

        val db = KanjiDb.getInstance(this)

        if (certainty == Certainty.DONTKNOW) {
            db.updateWeight(currentQuestion!!.kanji, Certainty.DONTKNOW)
            addUnknownAnswerToHistory(currentQuestion!!)
        } else if (currentAnswers!![position] == currentQuestion) {
            // correct
            db.updateWeight(currentQuestion!!.kanji, certainty)
            addGoodAnswerToHistory(currentQuestion!!)
        } else {
            // wrong
            db.updateWeight(currentQuestion!!.kanji, Certainty.DONTKNOW)
            db.updateWeight(currentAnswers!![position].kanji, Certainty.DONTKNOW)
            addWrongAnswerToHistory(currentQuestion!!, currentAnswers!![position])
        }

        showNewQuestion()
    }

    private fun addGoodAnswerToHistory(correct: Kanji) {
        val layout = makeHistoryLine(correct, R.drawable.round_green)

        history_view.addView(layout, 0)
        updateSheetPeekHeight(layout)
    }

    private fun addWrongAnswerToHistory(correct: Kanji, wrong: Kanji) {
        val layoutGood = makeHistoryLine(correct, R.drawable.round_yellow, false)
        val layoutBad = makeHistoryLine(wrong, R.drawable.round_red)

        history_view.addView(layoutBad, 0)
        history_view.addView(layoutGood, 0)
        updateSheetPeekHeight(layoutGood)
    }

    private fun addUnknownAnswerToHistory(correct: Kanji) {
        val layout = makeHistoryLine(correct, R.drawable.round_yellow)

        history_view.addView(layout, 0)
        updateSheetPeekHeight(layout)
    }

    private fun makeHistoryLine(kanji: Kanji, style: Int, withSeparator: Boolean = true): View {
        val line = LayoutInflater.from(this).inflate(R.layout.kanji_item, history_view, false)

        val checkbox = line.findViewById(R.id.kanji_item_checkbox)
        checkbox.visibility = View.GONE

        val kanjiView = line.findViewById(R.id.kanji_item_text) as TextView
        kanjiView.text = kanji.kanji
        kanjiView.background = ContextCompat.getDrawable(this, style)

        val detailView = line.findViewById(R.id.kanji_item_description) as TextView
        val detail = getKanjiDescription(kanji)
        detailView.text = detail

        if (!withSeparator) {
            line.findViewById(R.id.kanji_item_separator).visibility = View.GONE
        }

        return line
    }

    private fun updateSheetPeekHeight(v: View) {
        history_view.post {
            val va = ValueAnimator.ofInt(sheetBehavior.peekHeight, v.height)
            va.duration = 200 // ms
            va.addUpdateListener { sheetBehavior.peekHeight = it.animatedValue as Int }
            va.start()
        }
    }
}
