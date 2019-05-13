package org.kaqui.testactivities

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
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
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.transaction
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.textColor
import org.kaqui.*
import org.kaqui.model.*

class QuizTestActivity : BaseActivity(), TestFragmentHolder {
    companion object {
        private const val TAG = "QuizTestActivity"
    }

    override lateinit var testEngine: TestEngine

    private lateinit var statsFragment: StatsFragment
    private lateinit var sheetBehavior: BottomSheetBehavior<NestedScrollView>

    private lateinit var testLayout: TestLayout
    private lateinit var testFragment: TestFragment

    private val historyScrollView: NestedScrollView get() = testLayout.historyScrollView
    private val historyActionButton: FloatingActionButton get() = testLayout.historyActionButton
    private val historyView: LinearLayout get() = testLayout.historyView
    private val sessionScore: TextView get() = testLayout.sessionScore
    private val mainView: View get() = testLayout.mainView
    private val mainCoordLayout: androidx.coordinatorlayout.widget.CoordinatorLayout get() = testLayout.mainCoordinatorLayout

    override val testType
        get() = intent.extras!!.getSerializable("test_type") as TestType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        testLayout = TestLayout(this) {
            frameLayout {
                id = R.id.main_test_block
            }
        }

        statsFragment = StatsFragment.newInstance()

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        sheetBehavior = BottomSheetBehavior.from(historyScrollView)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        historyActionButton.setOnClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        testEngine = TestEngine(Database.getInstance(this), testType, this::addGoodAnswerToHistory, this::addWrongAnswerToHistory, this::addUnknownAnswerToHistory)

        if (savedInstanceState == null)
            testEngine.prepareNewQuestion()
        else
            testEngine.loadState(savedInstanceState)

        // handle view resize due to keyboard opening and closing
        val rootView = findViewById<View>(android.R.id.content)
        rootView.addOnLayoutChangeListener(this::onLayoutChange)

        updateSessionScore()

        supportFragmentManager.transaction {
            val testFragment: Fragment =
                    when (testType) {
                        TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING, TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> QuizTestFragment.newInstance()
                        TestType.HIRAGANA_WRITING, TestType.KATAKANA_WRITING, TestType.KANJI_WRITING -> WritingTestFragment.newInstance()
                        TestType.KANJI_COMPOSITION -> CompositionTestFragment.newInstance()
                        TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.KATAKANA_TO_ROMAJI_TEXT -> TextTestFragment.newInstance()
                    }
            this@QuizTestActivity.testFragment = testFragment as TestFragment
            replace(R.id.main_test_block, testFragment)
            replace(R.id.global_stats, statsFragment)
        }
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(getDbView(Database.getInstance(this)))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        testEngine.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom)
            return

        val shownLine = historyView.getChildAt(0) ?: return
        updateSheetPeekHeight(shownLine)
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

    override fun onGoodAnswer(button: View?, certainty: Certainty) {
        testEngine.markAnswer(certainty)
        finishQuestion(button, certainty)
    }

    override fun onWrongAnswer(button: View?, wrong: Item?) {
        testEngine.markAnswer(Certainty.DONTKNOW, wrong)
        finishQuestion(button, Certainty.DONTKNOW)
    }

    override fun onAnswer(button: View?, certainty: Certainty) {
        if (certainty == Certainty.DONTKNOW)
            onWrongAnswer(button, null)
        else
            onGoodAnswer(button, certainty)
    }

    private fun finishQuestion(button: View?, certainty: Certainty) {
        if (button != null) {
            val offsetViewBounds = Rect()
            button.getDrawingRect(offsetViewBounds)
            testLayout.mainCoordinatorLayout.offsetDescendantRectToMyCoords(button, offsetViewBounds)
            testLayout.overlay.trigger(offsetViewBounds.centerX(), offsetViewBounds.centerY(), ContextCompat.getColor(this, certainty.toColorRes()))
        } else {
            testLayout.overlay.trigger(testLayout.overlay.width / 2, testLayout.overlay.height / 2, ContextCompat.getColor(this, certainty.toColorRes()))
        }
    }

    override fun nextQuestion() {
        testEngine.prepareNewQuestion()
        testFragment.startNewQuestion()
        testFragment.refreshQuestion()

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
        else
            itemView.textColor = getColorFromAttr(android.R.attr.colorForeground)
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
                showItemProbabilityData(this, item.text, probabilityData)
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
                TestType.HIRAGANA_TO_ROMAJI, TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_HIRAGANA, TestType.HIRAGANA_WRITING -> db.hiraganaView
                TestType.KATAKANA_TO_ROMAJI, TestType.KATAKANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_KATAKANA, TestType.KATAKANA_WRITING -> db.katakanaView

                TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.KANJI_WRITING, TestType.KANJI_COMPOSITION -> db.kanjiView

                TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.READING_TO_WORD, TestType.MEANING_TO_WORD -> db.wordView
            }
}
