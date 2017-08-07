package org.kaqui

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.v4.content.ContextCompat
import android.support.v4.widget.NestedScrollView
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.quizz_activity.*
import java.util.*

class QuizzActivity : AppCompatActivity() {
    private sealed class HistoryLine {
        data class Correct(val kanjiId: Int) : HistoryLine()
        data class Unknown(val kanjiId: Int) : HistoryLine()
        data class Incorrect(val correctKanjiId: Int, val answerKanjiId: Int) : HistoryLine()
    }

    companion object {
        private const val TAG = "QuizzActivity"
        private const val NB_ANSWERS = 6
        private const val MAX_HISTORY_SIZE = 40
    }

    private lateinit var globalStatsFragment: GlobalStatsFragment
    private lateinit var answerTexts: List<TextView>
    private lateinit var sheetBehavior: BottomSheetBehavior<NestedScrollView>

    private lateinit var currentQuestion: Kanji
    private lateinit var currentAnswers: List<Kanji>

    private var correctCount = 0
    private var questionCount = 0

    private val history = ArrayList<HistoryLine>()

    private val quizzType
        get() = intent.extras.getSerializable("quizz_type") as QuizzType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.quizz_activity)

        globalStatsFragment = GlobalStatsFragment.newInstance()
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, globalStatsFragment)
                .commit()

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        when (quizzType) {
            QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING -> {
                question_text.textSize = 50.0f
                initButtons(answers_layout, R.layout.kanji_answer_line)
            }
            QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI -> {
                question_text.textSize = 20.0f

                val gridLayout = GridLayout(this)
                gridLayout.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                gridLayout.columnCount = 3
                initButtons(gridLayout, R.layout.kanji_answer_block)
                answers_layout.addView(gridLayout, 0)
            }
        }

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

        history_action_button.setOnClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        if (savedInstanceState == null)
            showNewQuestion()
        else {
            val db = KanjiDb.getInstance(this)
            currentQuestion = db.getKanji(savedInstanceState.getInt("question"))
            currentAnswers = savedInstanceState.getIntArray("answers").map { db.getKanji(it) }
            correctCount = savedInstanceState.getInt("correctCount")
            questionCount = savedInstanceState.getInt("questionCount")
            unserializeHistory(savedInstanceState.getByteArray("history"))
            showCurrentQuestion()
        }
    }

    private fun initButtons(parentLayout: ViewGroup, layoutToInflate: Int) {
        val answerTexts = ArrayList<TextView>(NB_ANSWERS)
        for (i in 0 until NB_ANSWERS) {
            val answerLine = LayoutInflater.from(this).inflate(layoutToInflate, parentLayout, false)

            answerTexts.add(answerLine.findViewById<TextView>(R.id.answer_text))
            answerLine.findViewById<View>(R.id.maybe_button).setOnClickListener { _ -> this.onAnswerClicked(Certainty.MAYBE, i) }
            answerLine.findViewById<View>(R.id.sure_button).setOnClickListener { _ -> this.onAnswerClicked(Certainty.SURE, i) }

            parentLayout.addView(answerLine, i)
        }
        this.answerTexts = answerTexts
        dontknow_button.setOnClickListener { _ -> this.onAnswerClicked(Certainty.DONTKNOW, 0) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("question", currentQuestion.id)
        outState.putIntArray("answers", currentAnswers.map { it.id }.toIntArray())
        outState.putInt("correctCount", correctCount)
        outState.putInt("questionCount", questionCount)
        outState.putByteArray("history", serializeHistory())

        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED)
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        else
            super.onBackPressed()
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

        currentQuestion = db.getKanji(questionId)

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

        showCurrentQuestion()
    }

    private fun showCurrentQuestion() {
        // when showNewQuestion is called in onCreate, globalStatsFragment is not visible yet
        if (globalStatsFragment.isVisible)
            globalStatsFragment.updateGlobalStats()
        updateSessionScore()

        question_text.text = currentQuestion.getQuestionText(quizzType)

        for (i in 0 until NB_ANSWERS) {
            answerTexts[i].text = currentAnswers[i].getAnswerText(quizzType)
        }
    }

    private fun updateSessionScore() {
        session_score.text = getString(R.string.score_string, correctCount, questionCount)
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

    private fun onAnswerClicked(certainty: Certainty, position: Int) {
        val db = KanjiDb.getInstance(this)

        if (certainty == Certainty.DONTKNOW) {
            db.updateWeight(currentQuestion.kanji, Certainty.DONTKNOW)
            addUnknownAnswerToHistory(currentQuestion)
        } else if (currentAnswers[position] == currentQuestion ||
                // also compare answer texts because different answers can have the same readings
                // like 副 and 福 and we don't want to penalize the user for that
                currentAnswers[position].getAnswerText(quizzType) == currentQuestion.getAnswerText(quizzType)) {
            // correct
            db.updateWeight(currentQuestion.kanji, certainty)
            addGoodAnswerToHistory(currentQuestion)
            correctCount += 1
        } else {
            // wrong
            db.updateWeight(currentQuestion.kanji, Certainty.DONTKNOW)
            db.updateWeight(currentAnswers[position].kanji, Certainty.DONTKNOW)
            addWrongAnswerToHistory(currentQuestion, currentAnswers[position])
        }

        questionCount += 1

        showNewQuestion()
    }

    private fun addGoodAnswerToHistory(correct: Kanji) {
        history.add(HistoryLine.Correct(correct.id))

        val layout = makeHistoryLine(correct, R.drawable.round_green)

        history_view.addView(layout, 0)
        updateSheetPeekHeight(layout)
        discardOldHistory()
    }

    private fun addWrongAnswerToHistory(correct: Kanji, wrong: Kanji) {
        history.add(HistoryLine.Incorrect(correct.id, wrong.id))

        val layoutGood = makeHistoryLine(correct, R.drawable.round_red, false)
        val layoutBad = makeHistoryLine(wrong, null)

        history_view.addView(layoutBad, 0)
        history_view.addView(layoutGood, 0)
        updateSheetPeekHeight(layoutGood)
        discardOldHistory()
    }

    private fun addUnknownAnswerToHistory(correct: Kanji) {
        history.add(HistoryLine.Unknown(correct.id))

        val layout = makeHistoryLine(correct, R.drawable.round_red)

        history_view.addView(layout, 0)
        updateSheetPeekHeight(layout)
        discardOldHistory()
    }

    private fun makeHistoryLine(kanji: Kanji, style: Int?, withSeparator: Boolean = true): View {
        val line = LayoutInflater.from(this).inflate(R.layout.kanji_item, history_view, false)

        val checkbox = line.findViewById<View>(R.id.kanji_item_checkbox)
        checkbox.visibility = View.GONE

        val kanjiView = line.findViewById<TextView>(R.id.kanji_item_text)
        kanjiView.text = kanji.kanji
        if (style != null)
            kanjiView.background = ContextCompat.getDrawable(this, style)
        kanjiView.setOnClickListener {
            val intent = Intent("sk.baka.aedict3.action.ACTION_SEARCH_JMDICT")
            intent.putExtra("kanjis", kanji.kanji)
            intent.putExtra("search_in_kanjidic", true)
            intent.putExtra("showEntryDetailOnSingleResult", true)
            startActivity(intent)
        }

        val detailView = line.findViewById<TextView>(R.id.kanji_item_description)
        val detail = getKanjiDescription(kanji)
        detailView.text = detail

        if (!withSeparator) {
            line.findViewById<View>(R.id.kanji_item_separator).visibility = View.GONE
        }

        return line
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
        for (position in history_view.childCount - 1 downTo MAX_HISTORY_SIZE - 1)
            history_view.removeViewAt(position)
        while (history.size > MAX_HISTORY_SIZE)
            history.removeAt(0)
    }

    private fun serializeHistory(): ByteArray {
        val parcel = Parcel.obtain()
        parcel.writeInt(history.size)
        for (line in history)
            when (line) {
                is HistoryLine.Correct -> {
                    parcel.writeByte(0)
                    parcel.writeInt(line.kanjiId)
                }
                is HistoryLine.Unknown -> {
                    parcel.writeByte(1)
                    parcel.writeInt(line.kanjiId)
                }
                is HistoryLine.Incorrect -> {
                    parcel.writeByte(2)
                    parcel.writeInt(line.correctKanjiId)
                    parcel.writeInt(line.answerKanjiId)
                }
            }
        val data = parcel.marshall()
        parcel.recycle()
        return data
    }

    private fun unserializeHistory(data: ByteArray) {
        val parcel = Parcel.obtain()
        parcel.unmarshall(data, 0, data.size)
        parcel.setDataPosition(0)

        history.clear()
        history_view.removeAllViews()

        val db = KanjiDb.getInstance(this)

        val count = parcel.readInt()
        repeat(count, {
            val type = parcel.readByte()
            when (type.toInt()) {
                0 -> {
                    addGoodAnswerToHistory(db.getKanji(parcel.readInt()))
                }
                1 -> {
                    addUnknownAnswerToHistory(db.getKanji(parcel.readInt()))
                }
                2 -> {
                    addWrongAnswerToHistory(db.getKanji(parcel.readInt()), db.getKanji(parcel.readInt()))
                }
            }
        })

        parcel.recycle()
    }
}