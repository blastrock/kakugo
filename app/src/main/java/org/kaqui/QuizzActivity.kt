package org.kaqui

import android.animation.ValueAnimator
import android.app.ProgressDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.view.ViewCompat
import android.support.v4.widget.NestedScrollView
import android.support.v7.widget.AppCompatButton
import android.util.Log
import android.view.*
import android.widget.*
import kotlinx.android.synthetic.main.quizz_activity.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.kaqui.kaqui.R
import org.kaqui.kaqui.settings.SettingsActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.zip.GZIPInputStream
import android.util.TypedValue

class QuizzActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "QuizzActivity"
        private const val NB_ANSWERS = 6
        private const val MAX_HISTORY_SIZE = 40
    }

    private lateinit var answerTexts: List<TextView>
    private lateinit var sheetBehavior: BottomSheetBehavior<NestedScrollView>
    private var currentQuestion: Kanji? = null
    private var currentAnswers: List<Kanji>? = null

    private var downloadProgress: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.quizz_activity)

        val answerTexts = ArrayList<TextView>(NB_ANSWERS)
        for (i in 0 until NB_ANSWERS) {
            val setGravity = fun(layoutParams: LinearLayout.LayoutParams): LinearLayout.LayoutParams {
                layoutParams.gravity = Gravity.CENTER_VERTICAL
                return layoutParams
            }

            answerTexts.add(TextView(this))
            answerTexts[i].layoutParams = setGravity(LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f))

            val buttonMaybe = AppCompatButton(this)
            buttonMaybe.text = "Maybe"
            ViewCompat.setBackgroundTintList(buttonMaybe, ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_orange_light)))
            buttonMaybe.setOnClickListener { _ -> this.onAnswerClicked(Certainty.MAYBE, i) }
            buttonMaybe.layoutParams = setGravity(LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0.0f))
            val buttonSure = AppCompatButton(this)
            buttonSure.text = "Sure"
            ViewCompat.setBackgroundTintList(buttonSure, ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_green_light)))
            buttonSure.setOnClickListener { _ -> this.onAnswerClicked(Certainty.SURE, i) }
            buttonSure.layoutParams = setGravity(LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0.0f))

            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.addView(answerTexts[i])
            layout.addView(buttonMaybe)
            layout.addView(buttonSure)

            val separator = View(this)
            separator.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1, 0.0f)

            val typedvalueattr = TypedValue()
            theme.resolveAttribute(android.R.attr.listDivider, typedvalueattr, true)

            separator.background = ResourcesCompat.getDrawable(resources, typedvalueattr.resourceId, null)

            answers_layout.addView(layout, i * 2)
            answers_layout.addView(separator, i * 2 + 1)
        }
        this.answerTexts = answerTexts
        dontknow_button.setOnClickListener { _ -> this.onAnswerClicked(Certainty.DONTKNOW, 0) }

        sheetBehavior = BottomSheetBehavior.from(history_scroll_view)
        if (savedInstanceState?.getBoolean("playerOpen", false) ?: false)
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        else
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        sheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED)
                    history_scroll_view.smoothScrollTo(0, 0)
            }
        })

        val db = KanjiDb.getInstance(this)
        if (db.empty) {
            showDownloadProgressDialog()

            async(CommonPool) {
                downloadKanjiDic(true)
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
            R.id.download_database -> {
                showDownloadProgressDialog()
                async(CommonPool) {
                    downloadKanjiDic()
                }
                return true
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED)
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        else
            super.onBackPressed()
    }

    private fun showDownloadProgressDialog() {
        downloadProgress = ProgressDialog(this)
        downloadProgress!!.setMessage("Downloading kanjidic database")
        downloadProgress!!.setCancelable(false)
        downloadProgress!!.show()
    }

    private fun downloadKanjiDic(abortOnError: Boolean = false) {
        try {
            Log.v(TAG, "Downloading kanjidic")
            val url = URL("https://axanux.net/kanjidic_.gz")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.connect()

            urlConnection.inputStream.use { gzipStream ->
                GZIPInputStream(gzipStream, 1024).use { textStream ->
                    val db = KanjiDb.getInstance(this)
                    val dump = db.dumpUserData()
                    db.replaceKanjis(parseFile(textStream.bufferedReader()))
                    db.restoreUserDataDump(dump)
                }
            }
            Log.v(TAG, "Finished downloading kanjidic")
            async(UI) {
                downloadProgress!!.dismiss()
                downloadProgress = null
                showNewQuestion()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and parse kanjidic", e)
            async(UI) {
                Toast.makeText(this@QuizzActivity, "Failed to download and parse kanjidic: " + e.message, Toast.LENGTH_LONG).show()
                if (abortOnError)
                    finish()
                else {
                    downloadProgress!!.dismiss()
                    downloadProgress = null
                }
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
        updateGlobalStats()

        val db = KanjiDb.getInstance(this)

        val ids = db.getEnabledIdsAndWeights().map { (id, weight) -> Pair(id, 1.0f - weight) }

        if (ids.size < NB_ANSWERS) {
            Toast.makeText(this, "You must select at least $NB_ANSWERS kanjis", Toast.LENGTH_LONG).show()
            return
        }

        val totalWeight = ids.map { it.second }.sum()
        val questionPos = Math.random() * totalWeight
        var questionId = ids[0].first
        run {
            var currentWeight = 0.0f
            for ((id, weight) in ids) {
                currentWeight += weight
                if (currentWeight >= questionPos) {
                    questionId = id
                    break
                }
            }
        }

        val currentQuestion = db.getKanji(questionId)
        this.currentQuestion = currentQuestion

        kanji_text.text = currentQuestion.kanji

        val similarKanjiIds = currentQuestion.similarities.map { it.id }.filter { db.isKanjiEnabled(it) }
        val similarKanjis =
                if (similarKanjiIds.size >= NB_ANSWERS - 1)
                    pickRandom(similarKanjiIds, NB_ANSWERS - 1)
                else
                    similarKanjiIds

        val additionalAnswers = pickRandom(ids.map { it.first }, NB_ANSWERS - 1 - similarKanjis.size, setOf(questionId) + similarKanjis)

        val currentAnswers = ((additionalAnswers + similarKanjis).map { db.getKanji(it) } + listOf(currentQuestion)).toMutableList()
        if (currentAnswers.size != NB_ANSWERS)
            Log.wtf(TAG, "Got ${currentAnswers.size} answers instead of $NB_ANSWERS")
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
        discardOldHistory()
    }

    private fun addWrongAnswerToHistory(correct: Kanji, wrong: Kanji) {
        val layoutGood = makeHistoryLine(correct, R.drawable.round_yellow, false)
        val layoutBad = makeHistoryLine(wrong, R.drawable.round_red)

        history_view.addView(layoutBad, 0)
        history_view.addView(layoutGood, 0)
        updateSheetPeekHeight(layoutGood)
        discardOldHistory()
    }

    private fun addUnknownAnswerToHistory(correct: Kanji) {
        val layout = makeHistoryLine(correct, R.drawable.round_yellow)

        history_view.addView(layout, 0)
        updateSheetPeekHeight(layout)
        discardOldHistory()
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

    private fun updateGlobalStats() {
        val db = KanjiDb.getInstance(this)
        val stats = db.getStats()
        val total = stats.bad + stats.meh + stats.good
        bad_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (stats.bad.toFloat() / total))
        meh_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (stats.meh.toFloat() / total))
        good_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (stats.good.toFloat() / total))
        bad_count.text =
                if (stats.bad > 0)
                    stats.bad.toString()
                else
                    ""
        meh_count.text =
                if (stats.meh > 0)
                    stats.meh.toString()
                else
                    ""
        good_count.text =
                if (stats.good > 0)
                    stats.good.toString()
                else
                    ""
    }

    private fun updateSheetPeekHeight(v: View) {
        history_view.post {
            val va = ValueAnimator.ofInt(sheetBehavior.peekHeight, v.height)
            va.duration = 200 // ms
            va.addUpdateListener { sheetBehavior.peekHeight = it.animatedValue as Int }
            va.start()

            main_scrollview.layoutParams = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, main_coordlayout.height - v.height)
        }
    }

    private fun discardOldHistory() {
        for (position in history_view.childCount - 1 downTo MAX_HISTORY_SIZE - 1)
            history_view.removeViewAt(position)
    }
}
