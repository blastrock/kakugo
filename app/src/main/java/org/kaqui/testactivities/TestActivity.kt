package org.kaqui.testactivities

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.transaction
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.jetbrains.anko.*
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.support.v4.nestedScrollView
import org.kaqui.*
import org.kaqui.model.*

class TestActivity : BaseActivity(), TestFragmentHolder {
    companion object {
        private const val TAG = "TestActivity"
    }

    override lateinit var testEngine: TestEngine

    private lateinit var statsFragment: StatsFragment
    private lateinit var sheetBehavior: BottomSheetBehavior<NestedScrollView>

    private lateinit var testFragment: TestFragment

    private lateinit var historyScrollView: NestedScrollView
    private lateinit var historyActionButton: FloatingActionButton
    private lateinit var historyView: LinearLayout
    private lateinit var lastItem: LinearLayout
    private lateinit var lastKanji: TextView
    private lateinit var lastInfo: ImageView
    private lateinit var lastDescription: TextView
    private lateinit var sessionScore: TextView
    private lateinit var mainView: View
    private lateinit var mainCoordLayout: androidx.coordinatorlayout.widget.CoordinatorLayout

    override val testType
        get() = intent.extras!!.getSerializable("test_type") as TestType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainCoordLayout = coordinatorLayout {
            verticalLayout {
                frameLayout {
                    id = R.id.global_stats
                }.lparams(width = matchParent, height = wrapContent)
                sessionScore = textView {
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                }
                mainView = frameLayout {
                    id = R.id.main_test_block
                }.lparams(width = matchParent, height = matchParent, weight = 1f) {
                    horizontalMargin = dip(16)
                }
                frameLayout {
                    backgroundColor = getColorFromAttr(R.attr.historyBackground)
                    lastItem = linearLayout {
                        relativeLayout {
                            lastKanji = textView {
                                id = View.generateViewId()
                                textSize = 25f
                                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                textColor = ContextCompat.getColor(context, R.color.itemTextColor)
                            }.lparams(width = matchParent, height = matchParent)
                            lastInfo = imageView {
                                val drawable = AppCompatResources.getDrawable(context, android.R.drawable.ic_dialog_info)!!
                                val mWrappedDrawable = DrawableCompat.wrap(drawable)
                                DrawableCompat.setTint(mWrappedDrawable, ContextCompat.getColor(context, R.color.colorPrimary))
                                DrawableCompat.setTintMode(mWrappedDrawable, PorterDuff.Mode.SRC_IN)
                                setImageDrawable(drawable)
                                contentDescription = context.getString(R.string.info_button)
                                visibility = View.INVISIBLE
                            }.lparams(width = sp(10), height = sp(10)) {
                                sameBottom(lastKanji)
                                sameEnd(lastKanji)
                            }
                        }.lparams(width = sp(35), height = sp(35)) {
                            margin = dip(8)
                            gravity = Gravity.CENTER
                        }
                        lastDescription = textView {
                            // disable line wrapping
                            setHorizontallyScrolling(true)
                            setLineSpacing(0f, .8f)
                        }.lparams(height = wrapContent) {
                            gravity = Gravity.CENTER
                        }
                    }
                }.lparams(width = matchParent, height = sp(50))
            }.lparams(width = matchParent, height = matchParent)
            historyScrollView = nestedScrollView {
                id = R.id.history_scroll_view
                backgroundColor = getColorFromAttr(R.attr.historyBackground)
                historyView = verticalLayout().lparams(width = matchParent, height = wrapContent)
            }.lparams(width = matchParent, height = matchParent) {
                val bottomSheetBehavior = BottomSheetBehavior<NestedScrollView>()
                bottomSheetBehavior.peekHeight = 0
                bottomSheetBehavior.isHideable = false
                behavior = bottomSheetBehavior
            }
            historyActionButton = floatingActionButton {
                size = FloatingActionButton.SIZE_MINI
                scaleX = 0f
                scaleY = 0f
                setImageResource(R.drawable.ic_arrow_upward)
            }.lparams(width = matchParent, height = wrapContent) {
                anchorId = R.id.history_scroll_view
                anchorGravity = Gravity.TOP or Gravity.END

                marginEnd = dip(6)
                bottomMargin = dip(6)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    elevation = 12.0f
                }
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

        val previousFragment = supportFragmentManager.findFragmentById(R.id.main_test_block)
        val testFragment: Fragment =
                if (previousFragment != null)
                    previousFragment
                else
                    when (testType) {
                        TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING, TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> QuizTestFragment.newInstance()
                        TestType.HIRAGANA_WRITING, TestType.KATAKANA_WRITING, TestType.KANJI_WRITING -> WritingTestFragment.newInstance()
                        TestType.KANJI_COMPOSITION -> CompositionTestFragment.newInstance()
                        TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.KATAKANA_TO_ROMAJI_TEXT -> TextTestFragment.newInstance()
                    }
        this@TestActivity.testFragment = testFragment as TestFragment

        supportFragmentManager.transaction {
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

    override fun onAnswer(button: View?, certainty: Certainty, wrong: Item?) {
        testEngine.markAnswer(certainty, wrong)
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

    private fun setLastLine(correct: Item, style: Int) {
        ObjectAnimator.ofFloat(lastItem, "translationY", lastItem.height.toFloat()).apply {
            duration = 100
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator?) {
                    lastItem.translationY = -lastItem.height.toFloat()
                    updateLastLine(correct, style)

                    ObjectAnimator.ofFloat(lastItem, "translationY", 0f).apply {
                        duration = 200
                        start()
                    }
                }

                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })
            start()
        }
    }

    private fun updateLastLine(correct: Item, style: Int) {
        lastKanji.text = correct.text
        lastKanji.background = ContextCompat.getDrawable(this, style)
        lastDescription.text = correct.description
        if (correct.contents is Kanji) {
            lastInfo.visibility = View.VISIBLE
            lastKanji.setOnClickListener {
                showItemInDict(correct.contents as Kanji)
            }
        } else if (correct.contents is Word) {
            lastInfo.visibility = View.VISIBLE
            lastKanji.setOnClickListener {
                showItemInDict(correct.contents as Word)
            }
        }
    }

    private fun addGoodAnswerToHistory(correct: Item, probabilityData: TestEngine.DebugData?, refresh: Boolean) {
        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_green)
        setLastLine(correct, R.drawable.round_green)

        historyView.addView(layout, 0)
        if (refresh) {
            updateSheetPeekHeight(layout)
            discardOldHistory()
        }
    }

    private fun addWrongAnswerToHistory(correct: Item, probabilityData: TestEngine.DebugData?, wrong: Item, refresh: Boolean) {
        val layoutGood = makeHistoryLine(correct, probabilityData, R.drawable.round_red, false)
        val layoutBad = makeHistoryLine(wrong, null, null)
        setLastLine(correct, R.drawable.round_red)

        historyView.addView(layoutBad, 0)
        historyView.addView(layoutGood, 0)
        if (refresh) {
            updateSheetPeekHeight(layoutGood)
            discardOldHistory()
        }
    }

    private fun addUnknownAnswerToHistory(correct: Item, probabilityData: TestEngine.DebugData?, refresh: Boolean) {
        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_red)
        setLastLine(correct, R.drawable.round_red)

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
        if (sheetBehavior.peekHeight == 0)
            historyActionButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).start()
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
