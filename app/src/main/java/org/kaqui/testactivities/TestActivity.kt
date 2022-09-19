package org.kaqui.testactivities

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.annotation.AttrRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import org.jetbrains.anko.*
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.support.v4.nestedScrollView
import org.kaqui.*
import org.kaqui.model.*
import kotlin.coroutines.CoroutineContext

class TestActivity : BaseActivity(), TestFragmentHolder, CoroutineScope {
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
    private lateinit var lastWrongItemBundle: View
    private lateinit var lastWrongItemText: TextView
    private lateinit var lastWrongInfo: ImageView
    private lateinit var lastItemBundle: View
    private lateinit var lastItemText: TextView
    private lateinit var lastInfo: ImageView
    private lateinit var lastDescription: TextView

    private lateinit var sessionScore: TextView
    private lateinit var mainView: View
    private lateinit var mainCoordLayout: androidx.coordinatorlayout.widget.CoordinatorLayout

    private lateinit var lastWrongItemButtonAnimation: StateListAnimator
    private lateinit var lastItemButtonAnimation: StateListAnimator

    private val testTypes
        get() = intent.extras!!.getSerializable("test_types") as List<TestType>
    private var localTestType: TestType? = null

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        job = Job()

        if (applicationContext.defaultSharedPreferences.getBoolean("keep_on", false))
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
                        lastWrongItemBundle = relativeLayout {
                            visibility = View.GONE
                            padding = dip(4)
                            clipToPadding = false
                            lastWrongItemText = button {
                                id = View.generateViewId()
                                typeface = TypefaceManager.getTypeface(context)
                                textSize = 25f
                                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                gravity = Gravity.CENTER
                                background = getColoredCircle(context, R.attr.itemBad)
                                minWidth = sp(35)
                                minimumWidth = sp(35)
                                verticalPadding = dip(0)
                                horizontalPadding = sp(4)

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    lastWrongItemButtonAnimation = stateListAnimator
                                }
                            }.lparams(width = wrapContent, height = sp(35))
                            lastWrongInfo = imageView {
                                val drawable = AppCompatResources.getDrawable(context, android.R.drawable.ic_dialog_info)!!
                                val mWrappedDrawable = DrawableCompat.wrap(drawable)
                                DrawableCompat.setTint(mWrappedDrawable, ContextCompat.getColor(context, R.color.colorPrimary))
                                DrawableCompat.setTintMode(mWrappedDrawable, PorterDuff.Mode.SRC_IN)
                                setImageDrawable(drawable)
                                contentDescription = context.getString(R.string.info_button)
                                visibility = View.INVISIBLE
                            }.lparams(width = sp(10), height = sp(10)) {
                                sameBottom(lastWrongItemText)
                                sameEnd(lastWrongItemText)
                            }
                        }.lparams {
                            margin = dip(4)
                            gravity = Gravity.CENTER
                        }
                        lastItemBundle = relativeLayout {
                            visibility = View.GONE
                            padding = dip(4)
                            clipToPadding = false
                            lastItemText = button {
                                id = View.generateViewId()
                                typeface = TypefaceManager.getTypeface(context)
                                textSize = 25f
                                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                gravity = Gravity.CENTER
                                background = getColoredCircle(context, R.attr.itemGood)
                                minWidth = sp(35)
                                minimumWidth = sp(35)
                                verticalPadding = dip(0)
                                horizontalPadding = sp(4)

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    lastItemButtonAnimation = stateListAnimator
                                }
                            }.lparams(width = wrapContent, height = sp(35))
                            lastInfo = imageView {
                                val drawable = AppCompatResources.getDrawable(context, android.R.drawable.ic_dialog_info)!!
                                val mWrappedDrawable = DrawableCompat.wrap(drawable)
                                DrawableCompat.setTint(mWrappedDrawable, ContextCompat.getColor(context, R.color.colorPrimary))
                                DrawableCompat.setTintMode(mWrappedDrawable, PorterDuff.Mode.SRC_IN)
                                setImageDrawable(drawable)
                                contentDescription = context.getString(R.string.info_button)
                                visibility = View.INVISIBLE
                            }.lparams(width = sp(10), height = sp(10)) {
                                sameBottom(lastItemText)
                                sameEnd(lastItemText)
                            }
                        }.lparams {
                            margin = dip(4)
                            gravity = Gravity.CENTER
                        }
                        lastDescription = textView {
                            typeface = TypefaceManager.getTypeface(context)
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

        testEngine = TestEngine(Database.getInstance(this), testTypes, this::addGoodAnswerToHistory, this::addWrongAnswerToHistory, this::addUnknownAnswerToHistory)

        // handle view resize due to keyboard opening and closing
        val rootView = findViewById<View>(android.R.id.content)
        rootView.addOnLayoutChangeListener(this::onLayoutChange)

        supportFragmentManager.commit {
            replace(R.id.global_stats, statsFragment)
        }

        val previousFragment = supportFragmentManager.findFragmentById(R.id.main_test_block)
        if (previousFragment != null)
            testFragment = previousFragment as TestFragment

        if (savedInstanceState == null) {
            nextQuestion()
        } else
            testEngine.loadState(savedInstanceState)

        updateSessionScore()
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(testEngine.itemView)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
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

    @Deprecated("Deprecated in Java")
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
        updateSessionScore()
    }

    override fun nextQuestion() {
        testEngine.prepareNewQuestion()

        if (localTestType == testType) {
            testFragment.startNewQuestion()
            testFragment.refreshQuestion()
            testFragment.setSensible(false)
            launch(job) {
                delay(300)
                testFragment.setSensible(true)
            }
        } else {
            localTestType = testType
            val testFragment: Fragment =
                    when (testType) {
                        TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING, TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> QuizTestFragment.newInstance()
                        TestType.HIRAGANA_DRAWING, TestType.KATAKANA_DRAWING, TestType.KANJI_DRAWING -> DrawingTestFragment.newInstance()
                        TestType.KANJI_COMPOSITION -> CompositionTestFragment.newInstance()
                        TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.KATAKANA_TO_ROMAJI_TEXT -> TextTestFragment.newInstance()
                    }
            this@TestActivity.testFragment = testFragment as TestFragment

            supportFragmentManager.commit {
                replace(R.id.main_test_block, testFragment)
            }

            title = getString(testType.toName())

            updateSessionScore()
        }
    }

    private fun updateSessionScore() {
        sessionScore.text = getString(R.string.score_string, testEngine.correctCount, testEngine.questionCount)

        // when showNewQuestion is called in onCreate, statsFragment is not visible yet
        if (statsFragment.isVisible)
            statsFragment.updateStats(testEngine.itemView)
    }

    private fun setLastLine(correct: Item, wrong: Item?, probabilityData: TestEngine.DebugData?) {
        ObjectAnimator.ofFloat(lastItem, "translationY", lastItem.height.toFloat()).apply {
            duration = 100
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    lastItem.translationY = -lastItem.height.toFloat()
                    updateLastLine(correct, wrong, probabilityData)

                    ObjectAnimator.ofFloat(lastItem, "translationY", 0f).apply {
                        duration = 200
                        start()
                    }
                }

                override fun onAnimationRepeat(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationStart(animation: Animator) {}
            })
            start()
        }
    }

    private fun updateLastLine(correct: Item, wrong: Item?, probabilityData: TestEngine.DebugData?) {
        if (wrong != null) {
            lastWrongItemText.text = wrong.text
            lastWrongItemBundle.visibility = View.VISIBLE
            if (correct.contents is Kanji) {
                lastWrongInfo.visibility = View.VISIBLE
                lastWrongItemText.setOnClickListener {
                    showItemInDict(wrong.contents as Kanji)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lastWrongItemText.stateListAnimator = lastItemButtonAnimation
                }
            } else if (correct.contents is Word) {
                lastWrongInfo.visibility = View.VISIBLE
                lastWrongItemText.setOnClickListener {
                    showItemInDict(wrong.contents as Word)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lastWrongItemText.stateListAnimator = lastItemButtonAnimation
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lastWrongItemText.stateListAnimator = null
                    lastWrongItemText.elevation = 0.0f
                }
            }
            lastWrongItemText.setOnLongClickListener {
                if (probabilityData != null)
                    showItemProbabilityData(this, wrong.text, probabilityData)
                true
            }
        } else {
            lastWrongItemBundle.visibility = View.GONE
        }
        if (correct != wrong) {
            lastItemText.text = correct.text
            lastItemBundle.visibility = View.VISIBLE
            if (correct.contents is Kanji) {
                lastInfo.visibility = View.VISIBLE
                lastItemText.setOnClickListener {
                    showItemInDict(correct.contents as Kanji)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lastItemText.stateListAnimator = lastItemButtonAnimation
                }
            } else if (correct.contents is Word) {
                lastInfo.visibility = View.VISIBLE
                lastItemText.setOnClickListener {
                    showItemInDict(correct.contents as Word)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lastItemText.stateListAnimator = lastItemButtonAnimation
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lastItemText.stateListAnimator = null
                    lastItemText.elevation = 0.0f
                }
            }
            lastItemText.setOnLongClickListener {
                if (probabilityData != null)
                    showItemProbabilityData(this, correct.text, probabilityData)
                true
            }
        } else {
            lastItemBundle.visibility = View.GONE
        }

        if (lastItemBundle.visibility == View.VISIBLE && lastWrongItemBundle.visibility == View.VISIBLE) {
            (lastWrongItemBundle.layoutParams as LinearLayout.LayoutParams).rightMargin = 0
            (lastItemBundle.layoutParams as LinearLayout.LayoutParams).leftMargin = 0
            lastWrongItemBundle.rightPadding = 0
            lastItemBundle.leftPadding = 0
        } else {
            (lastWrongItemBundle.layoutParams as LinearLayout.LayoutParams).rightMargin = dip(4)
            (lastItemBundle.layoutParams as LinearLayout.LayoutParams).leftMargin = dip(4)
            lastWrongItemBundle.rightPadding = dip(4)
            lastItemBundle.leftPadding = dip(4)
        }

        lastDescription.text = correct.description
    }

    private fun addGoodAnswerToHistory(correct: Item, probabilityData: TestEngine.DebugData?, refresh: Boolean) {
        val layout = makeHistoryLine(correct, probabilityData, R.attr.itemGood)

        historyView.addView(layout, 0)
        if (refresh) {
            updateSheetPeekHeight(layout)
            discardOldHistory()
            setLastLine(correct, null, probabilityData)
        }
    }

    private fun addWrongAnswerToHistory(correct: Item, probabilityData: TestEngine.DebugData?, wrong: Item, refresh: Boolean) {
        val layoutGood = makeHistoryLine(correct, probabilityData, R.attr.itemBad, false)
        val layoutBad = makeHistoryLine(wrong, null, null)

        historyView.addView(layoutBad, 0)
        historyView.addView(layoutGood, 0)
        if (refresh) {
            updateSheetPeekHeight(layoutGood)
            discardOldHistory()
            setLastLine(correct, wrong, probabilityData)
        }
    }

    private fun addUnknownAnswerToHistory(correct: Item, probabilityData: TestEngine.DebugData?, refresh: Boolean) {
        val layout = makeHistoryLine(correct, probabilityData, R.attr.itemBad)

        historyView.addView(layout, 0)
        if (refresh) {
            updateSheetPeekHeight(layout)
            discardOldHistory()
            setLastLine(correct, correct, probabilityData)
        }
    }

    private fun makeHistoryLine(item: Item, probabilityData: TestEngine.DebugData?, @AttrRes style: Int?, withSeparator: Boolean = true): View {
        val line = LayoutInflater.from(this).inflate(R.layout.selection_item, historyView, false)

        val checkbox = line.findViewById<View>(R.id.item_checkbox)
        checkbox.visibility = View.GONE

        val itemView = line.findViewById<Button>(R.id.item_text)
        itemView.text = item.text
        if (item.text.length > 1)
            (itemView.layoutParams as RelativeLayout.LayoutParams).width = LinearLayout.LayoutParams.WRAP_CONTENT
        if (style != null) {
            itemView.background = getColoredCircle(this, style)
        } else {
            itemView.background = getColoredCircle(this, R.attr.historyBackground)
            itemView.textColor = getColorFromAttr(android.R.attr.colorForeground)
        }
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
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                itemView.stateListAnimator = null
                itemView.elevation = 0.0f
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
}
