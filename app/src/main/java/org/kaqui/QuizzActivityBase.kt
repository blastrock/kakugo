package org.kaqui

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.ContextCompat
import android.support.v4.widget.NestedScrollView
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import org.kaqui.model.*

abstract class QuizzActivityBase : AppCompatActivity() {
    companion object {
        private const val TAG = "QuizzActivityBase"
    }

    protected lateinit var quizzEngine: QuizzEngine

    private lateinit var statsFragment: StatsFragment
    private lateinit var sheetBehavior: BottomSheetBehavior<NestedScrollView>

    protected abstract val quizzType: QuizzType

    protected abstract val historyScrollView: NestedScrollView
    protected abstract val historyActionButton: FloatingActionButton
    protected abstract val historyView: LinearLayout
    protected abstract val sessionScore: TextView
    protected abstract val mainScrollView: ScrollView
    protected abstract val mainCoordLayout: CoordinatorLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statsFragment = StatsFragment.newInstance()
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        sheetBehavior = BottomSheetBehavior.from(historyScrollView)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        historyActionButton.setOnClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        quizzEngine = QuizzEngine(KaquiDb.getInstance(this), quizzType, this::addGoodAnswerToHistory, this::addWrongAnswerToHistory, this::addUnknownAnswerToHistory)

        if (savedInstanceState == null)
            quizzEngine.prepareNewQuestion()
        else
            quizzEngine.loadState(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(getDbView(KaquiDb.getInstance(this)))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        quizzEngine.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            // smoothScrollTo doesn't work, it always scrolls at the end or does nothing
            historyScrollView.scrollTo(0, 0)
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else
            confirmActivityClose()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                confirmActivityClose()
                true
            }
            else ->
                super.onOptionsItemSelected(item)
        }
    }

    private fun confirmActivityClose() {
        AlertDialog.Builder(this)
                .setTitle(R.string.confirm_quizz_stop_title)
                .setMessage(R.string.confirm_quizz_stop_message)
                .setPositiveButton(android.R.string.yes, { _, _ -> finish() })
                .setNegativeButton(android.R.string.no, null)
                .show()
    }

    protected open fun showCurrentQuestion() {
        // when showNewQuestion is called in onCreate, statsFragment is not visible yet
        if (statsFragment.isVisible)
            statsFragment.updateStats(getDbView(KaquiDb.getInstance(this)))
        updateSessionScore()
    }

    private fun updateSessionScore() {
        sessionScore.text = getString(R.string.score_string, quizzEngine.correctCount, quizzEngine.questionCount)
    }

    private fun addGoodAnswerToHistory(correct: Item, probabilityData: QuizzEngine.DebugData?) {
        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_green)

        historyView.addView(layout, 0)
        updateSheetPeekHeight(layout)
        discardOldHistory()
    }

    private fun addWrongAnswerToHistory(correct: Item, probabilityData: QuizzEngine.DebugData?, wrong: Item) {
        val layoutGood = makeHistoryLine(correct, probabilityData, R.drawable.round_red, false)
        val layoutBad = makeHistoryLine(wrong, null, null)

        historyView.addView(layoutBad, 0)
        historyView.addView(layoutGood, 0)
        updateSheetPeekHeight(layoutGood)
        discardOldHistory()
    }

    private fun addUnknownAnswerToHistory(correct: Item, probabilityData: QuizzEngine.DebugData?) {
        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_red)

        historyView.addView(layout, 0)
        updateSheetPeekHeight(layout)
        discardOldHistory()
    }

    private fun makeHistoryLine(item: Item, probabilityData: QuizzEngine.DebugData?, style: Int?, withSeparator: Boolean = true): View {
        val line = LayoutInflater.from(this).inflate(R.layout.selection_item, historyView, false)

        val checkbox = line.findViewById<View>(R.id.item_checkbox)
        checkbox.visibility = View.GONE

        val itemView = line.findViewById<TextView>(R.id.item_text)
        itemView.text = item.text
        if (item.text.length > 1)
            (itemView.layoutParams as RelativeLayout.LayoutParams).width = LinearLayout.LayoutParams.WRAP_CONTENT
        if (style != null)
            itemView.background = ContextCompat.getDrawable(this, style)
        if (item.contents is Kanji) {
            line.findViewById<ImageView>(R.id.item_info).visibility = View.VISIBLE
            itemView.setOnClickListener {
                showItemInDict(item.contents as Kanji)
            }
        } else if (item.contents is Word) {
            line.findViewById<ImageView>(R.id.item_info).visibility = View.VISIBLE
            itemView.setOnClickListener {
                showItemInDict(item.contents as Word)
            }
        }
        itemView.setOnLongClickListener {
            if (probabilityData != null)
                showItemProbabilityData(item.text, probabilityData)
            true
        }

        val detailView = line.findViewById<TextView>(R.id.item_description)
        val detail = item.description
        detailView.text = detail

        if (!withSeparator) {
            line.findViewById<View>(R.id.item_separator).visibility = View.GONE
        }

        return line
    }

    private fun showItemInDict(kanji: Kanji) {
        val intent = Intent("sk.baka.aedict3.action.ACTION_SEARCH_JMDICT")
        intent.putExtra("kanjis", kanji.kanji)
        intent.putExtra("search_in_kanjidic", true)
        intent.putExtra("showEntryDetailOnSingleResult", true)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://jisho.org/search/${kanji.kanji}%20%23kanji")))
        }
    }

    private fun showItemInDict(word: Word) {
        val intent = Intent("sk.baka.aedict3.action.ACTION_SEARCH_JMDICT")
        intent.putExtra("kanjis", word.word)
        intent.putExtra("showEntryDetailOnSingleResult", true)
        intent.putExtra("match_jp", "Exact")
        intent.putExtra("deinflect", false)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://jisho.org/search/${word.word}")))
        }
    }

    protected fun showItemProbabilityData(item: String, probabilityData: QuizzEngine.DebugData) {
        AlertDialog.Builder(this)
                .setTitle(item)
                .setMessage(
                        getString(R.string.debug_info,
                                probabilityData.probabilityData.daysSinceCorrect,
                                probabilityData.probabilityData.longScore,
                                probabilityData.probabilityData.longWeight,
                                probabilityData.probabilityData.shortScore,
                                probabilityData.probabilityData.shortWeight,
                                probabilityData.probaParamsStage2.shortCoefficient,
                                probabilityData.probaParamsStage2.longCoefficient,
                                probabilityData.probabilityData.finalProbability,
                                probabilityData.totalWeight,
                                probabilityData.scoreUpdate?.shortScore,
                                probabilityData.scoreUpdate?.longScore))
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }

    private fun updateSheetPeekHeight(v: View) {
        historyView.post {
            if (sheetBehavior.peekHeight == 0)
                historyActionButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).start()

            val va = ValueAnimator.ofInt(sheetBehavior.peekHeight, v.height)
            va.duration = 200 // ms
            va.addUpdateListener { sheetBehavior.peekHeight = it.animatedValue as Int }
            va.start()

            mainScrollView.layoutParams = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, mainCoordLayout.height - v.height)
        }
    }

    private fun discardOldHistory() {
        for (position in historyView.childCount - 1 downTo QuizzEngine.MAX_HISTORY_SIZE - 1)
            historyView.removeViewAt(position)
    }

    private fun getDbView(db: KaquiDb): LearningDbView =
            when (quizzType) {
                QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.ROMAJI_TO_HIRAGANA -> db.hiraganaView
                QuizzType.KATAKANA_TO_ROMAJI, QuizzType.ROMAJI_TO_KATAKANA -> db.katakanaView

                QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING, QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI -> db.kanjiView

                QuizzType.WORD_TO_READING, QuizzType.WORD_TO_MEANING, QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD -> db.wordView
            }
}
