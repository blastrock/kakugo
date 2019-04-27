package org.kaqui

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.kaqui.model.*

abstract class TestActivityBase : AppCompatActivity() {
    companion object {
        private const val TAG = "TestActivityBase"
    }

    protected lateinit var testEngine: TestEngine

    private lateinit var statsFragment: StatsFragment
    private lateinit var sheetBehavior: BottomSheetBehavior<NestedScrollView>

    protected abstract val testType: TestType

    protected abstract val historyScrollView: NestedScrollView
    protected abstract val historyActionButton: FloatingActionButton
    protected abstract val historyView: LinearLayout
    protected abstract val sessionScore: TextView
    protected abstract val mainView: View
    protected abstract val mainCoordLayout: androidx.coordinatorlayout.widget.CoordinatorLayout

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

        testEngine = TestEngine(Database.getInstance(this), testType, this::addGoodAnswerToHistory, this::addWrongAnswerToHistory, this::addUnknownAnswerToHistory)

        if (savedInstanceState == null)
            prepareNewQuestion()
        else
            testEngine.loadState(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(getDbView(Database.getInstance(this)))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        testEngine.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            // smoothScrollTo doesn't work, it always scrolls at the end or does nothing
            historyScrollView.scrollTo(0, 0)
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else
            confirmActivityClose(false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                confirmActivityClose(true)
                true
            }
            else ->
                super.onOptionsItemSelected(item)
        }
    }

    private fun confirmActivityClose(upNavigation: Boolean) {
        AlertDialog.Builder(this)
                .setTitle(R.string.confirm_test_stop_title)
                .setMessage(R.string.confirm_test_stop_message)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    if (upNavigation)
                        NavUtils.navigateUpFromSameTask(this)
                    else
                        finish()
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
    }

    protected open fun prepareNewQuestion() {
        testEngine.prepareNewQuestion()
    }

    protected open fun showCurrentQuestion() {
        // when showNewQuestion is called in onCreate, statsFragment is not visible yet
        if (statsFragment.isVisible)
            statsFragment.updateStats(getDbView(Database.getInstance(this)))
        updateSessionScore()
    }

    private fun updateSessionScore() {
        sessionScore.text = getString(R.string.score_string, testEngine.correctCount, testEngine.questionCount)
    }

    private fun addGoodAnswerToHistory(correct: Item, probabilityData: TestEngine.DebugData?, refresh: Boolean) {
        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_green)

        historyView.addView(layout, 0)
        if (refresh) {
            updateSheetPeekHeight(layout)
            discardOldHistory()
        }
    }

    private fun addWrongAnswerToHistory(correct: Item, probabilityData: TestEngine.DebugData?, wrong: Item, refresh: Boolean) {
        val layoutGood = makeHistoryLine(correct, probabilityData, R.drawable.round_red, false)
        val layoutBad = makeHistoryLine(wrong, null, null)

        historyView.addView(layoutBad, 0)
        historyView.addView(layoutGood, 0)
        if (refresh) {
            updateSheetPeekHeight(layoutGood)
            discardOldHistory()
        }
    }

    private fun addUnknownAnswerToHistory(correct: Item, probabilityData: TestEngine.DebugData?, refresh: Boolean) {
        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_red)

        historyView.addView(layout, 0)
        if (refresh) {
            updateSheetPeekHeight(layout)
            discardOldHistory()
        }
    }

    private fun makeHistoryLine(item: Item, probabilityData: TestEngine.DebugData?, style: Int?, withSeparator: Boolean = true): View {
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

    protected fun showItemProbabilityData(item: String, probabilityData: TestEngine.DebugData) {
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
        v.measure(MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))

        if (sheetBehavior.peekHeight == 0)
            historyActionButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).start()

        run {
            val va = ValueAnimator.ofInt(sheetBehavior.peekHeight, v.measuredHeight)
            va.duration = 200 // ms
            va.addUpdateListener {
                sheetBehavior.peekHeight = it.animatedValue as Int
                mainView.layoutParams.height = mainCoordLayout.height - it.animatedValue as Int
            }
            va.start()
        }

        run {
            val va = ValueAnimator.ofInt(-v.measuredHeight, 0)
            va.duration = 200 // ms
            va.addUpdateListener {
                (v.layoutParams as LinearLayout.LayoutParams).topMargin = it.animatedValue as Int
                historyView.requestLayout()
            }
            va.start()
        }
    }

    private fun discardOldHistory() {
        for (position in historyView.childCount - 1 downTo TestEngine.MAX_HISTORY_SIZE - 1)
            historyView.removeViewAt(position)
    }

    private fun getDbView(db: Database): LearningDbView =
            when (testType) {
                TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.HIRAGANA_WRITING -> db.hiraganaView
                TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA, TestType.KATAKANA_WRITING -> db.katakanaView

                TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.KANJI_WRITING, TestType.KANJI_COMPOSITION -> db.kanjiView

                TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.READING_TO_WORD, TestType.MEANING_TO_WORD -> db.wordView
            }
}
