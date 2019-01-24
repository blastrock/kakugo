package org.kaqui

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.v4.content.ContextCompat
import android.support.v4.widget.NestedScrollView
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.quizz_activity.*
import org.kaqui.model.*
import java.util.*

class QuizzActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "QuizzActivity"
        private const val COLUMNS = 2
    }

    private lateinit var quizzEngine: QuizzEngine

    private lateinit var statsFragment: StatsFragment
    private lateinit var answerTexts: List<TextView>
    private lateinit var sheetBehavior: BottomSheetBehavior<NestedScrollView>

    private val quizzType
        get() = intent.extras.getSerializable("quizz_type") as QuizzType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.quizz_activity)

        statsFragment = StatsFragment.newInstance()
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        when (quizzType) {
            QuizzType.WORD_TO_READING, QuizzType.WORD_TO_MEANING, QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING -> {
                question_text.textSize = 50.0f
                initButtons(listOf(answers_layout), QuizzEngine.NB_ANSWERS, R.layout.kanji_answer_line)
            }

            QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD, QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI, QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.ROMAJI_TO_HIRAGANA, QuizzType.KATAKANA_TO_ROMAJI, QuizzType.ROMAJI_TO_KATAKANA -> {
                when (quizzType) {
                    QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD, QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI -> {
                        question_text.textSize = 20.0f
                        (question_text.layoutParams as LinearLayout.LayoutParams).topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.0f, resources.displayMetrics).toInt()
                        (question_text.layoutParams as LinearLayout.LayoutParams).bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.0f, resources.displayMetrics).toInt()
                    }
                    QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.ROMAJI_TO_HIRAGANA, QuizzType.KATAKANA_TO_ROMAJI, QuizzType.ROMAJI_TO_KATAKANA ->
                        question_text.textSize = 50.0f
                    else -> Unit
                }

                val answersLayout = LinearLayout(this)
                answersLayout.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                answersLayout.orientation = LinearLayout.VERTICAL
                val lineLayouts = (0..QuizzEngine.NB_ANSWERS / COLUMNS).map {
                    val line = LinearLayout(this)
                    line.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    line.orientation = LinearLayout.HORIZONTAL
                    line
                }
                for (line in lineLayouts)
                    answersLayout.addView(line)
                initButtons(lineLayouts, COLUMNS, R.layout.kanji_answer_block)
                answers_layout.addView(answersLayout, 0)
            }
        }

        sheetBehavior = BottomSheetBehavior.from(history_scroll_view)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        history_action_button.setOnClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        quizzEngine = QuizzEngine(KaquiDb.getInstance(this), quizzType, this::addGoodAnswerToHistory, this::addWrongAnswerToHistory, this::addUnknownAnswerToHistory)

        if (savedInstanceState == null)
            quizzEngine.prepareNewQuestion()
        else
            quizzEngine.loadState(savedInstanceState)
        showCurrentQuestion()
    }

    private fun initButtons(lineLayouts: List<LinearLayout>, columns: Int, layoutToInflate: Int) {
        val answerTexts = ArrayList<TextView>(QuizzEngine.NB_ANSWERS)
        for (i in 0 until QuizzEngine.NB_ANSWERS) {
            val currentLine = lineLayouts[i / columns]
            val answerLine = LayoutInflater.from(this).inflate(layoutToInflate, currentLine, false)

            val textView: TextView = answerLine.findViewById(R.id.answer_text)
            when (quizzType) {
                QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD -> textView.textSize = 30.0f
                else -> Unit
            }
            answerTexts.add(textView)
            answerLine.findViewById<View>(R.id.maybe_button).setOnClickListener { _ -> this.onAnswerClicked(Certainty.MAYBE, i) }
            answerLine.findViewById<View>(R.id.sure_button).setOnClickListener { _ -> this.onAnswerClicked(Certainty.SURE, i) }

            currentLine.addView(answerLine, i % columns)
        }
        this.answerTexts = answerTexts
        dontknow_button.setOnClickListener { _ -> this.onAnswerClicked(Certainty.DONTKNOW, 0) }

        question_text.setOnLongClickListener { _ ->
            if (quizzEngine.currentDebugData != null)
                showItemProbabilityData(quizzEngine.currentQuestion.text, quizzEngine.currentDebugData!!)
            true
        }
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
            history_scroll_view.scrollTo(0, 0)
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

    private fun showCurrentQuestion() {
        // when showNewQuestion is called in onCreate, statsFragment is not visible yet
        if (statsFragment.isVisible)
            statsFragment.updateStats(getDbView(KaquiDb.getInstance(this)))
        updateSessionScore()

        question_text.text = quizzEngine.currentQuestion.getQuestionText(quizzType)

        for (i in 0 until QuizzEngine.NB_ANSWERS) {
            answerTexts[i].text = quizzEngine.currentAnswers[i].getAnswerText(quizzType)
        }
    }

    private fun updateSessionScore() {
        session_score.text = getString(R.string.score_string, quizzEngine.correctCount, quizzEngine.questionCount)
    }

    private fun onAnswerClicked(certainty: Certainty, position: Int) {
        quizzEngine.selectAnswer(certainty, position)

        quizzEngine.prepareNewQuestion()
        showCurrentQuestion()
    }

    private fun addGoodAnswerToHistory(correct: Item, probabilityData: QuizzEngine.DebugData?) {
        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_green)

        history_view.addView(layout, 0)
        updateSheetPeekHeight(layout)
        discardOldHistory()
    }

    private fun addWrongAnswerToHistory(correct: Item, probabilityData: QuizzEngine.DebugData?, wrong: Item) {
        val layoutGood = makeHistoryLine(correct, probabilityData, R.drawable.round_red, false)
        val layoutBad = makeHistoryLine(wrong, null, null)

        history_view.addView(layoutBad, 0)
        history_view.addView(layoutGood, 0)
        updateSheetPeekHeight(layoutGood)
        discardOldHistory()
    }

    private fun addUnknownAnswerToHistory(correct: Item, probabilityData: QuizzEngine.DebugData?) {
        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_red)

        history_view.addView(layout, 0)
        updateSheetPeekHeight(layout)
        discardOldHistory()
    }

    private fun makeHistoryLine(item: Item, probabilityData: QuizzEngine.DebugData?, style: Int?, withSeparator: Boolean = true): View {
        val line = LayoutInflater.from(this).inflate(R.layout.selection_item, history_view, false)

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

    private fun showItemProbabilityData(item: String, probabilityData: QuizzEngine.DebugData) {
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
        history_view.post {
            if (sheetBehavior.peekHeight == 0)
                history_action_button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).start()

            val va = ValueAnimator.ofInt(sheetBehavior.peekHeight, v.height)
            va.duration = 200 // ms
            va.addUpdateListener { sheetBehavior.peekHeight = it.animatedValue as Int }
            va.start()

            main_scrollview.layoutParams = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, main_coordlayout.height - v.height)
        }
    }

    private fun discardOldHistory() {
        for (position in history_view.childCount - 1 downTo QuizzEngine.MAX_HISTORY_SIZE - 1)
            history_view.removeViewAt(position)
    }

    private fun getDbView(db: KaquiDb): LearningDbView =
            when (quizzType) {
                QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.ROMAJI_TO_HIRAGANA -> db.hiraganaView
                QuizzType.KATAKANA_TO_ROMAJI, QuizzType.ROMAJI_TO_KATAKANA -> db.katakanaView

                QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING, QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI -> db.kanjiView

                QuizzType.WORD_TO_READING, QuizzType.WORD_TO_MEANING, QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD -> db.wordView
            }

    private fun getItem(db: KaquiDb, id: Int): Item =
            getDbView(db).getItem(id)
}
