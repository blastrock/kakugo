package org.kaqui

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.v4.content.ContextCompat
import android.support.v4.widget.NestedScrollView
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
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
        private const val LAST_QUESTIONS_TO_AVOID_COUNT = 6
        private const val MAX_HISTORY_SIZE = 40
    }

    data class DebugData(var probabilityData: KanjiDb.ProbabilityData, var probaParamsStage1: KanjiDb.ProbaParamsStage1, var probaParamsStage2: KanjiDb.ProbaParamsStage2, var totalWeight: Double)

    private lateinit var statsFragment: StatsFragment
    private lateinit var answerTexts: List<TextView>
    private lateinit var sheetBehavior: BottomSheetBehavior<NestedScrollView>

    private lateinit var currentQuestion: Kanji
    private var currentDebugData: DebugData? = null
    private lateinit var currentAnswers: List<Kanji>

    private var correctCount = 0
    private var questionCount = 0

    private val history = ArrayList<HistoryLine>()
    private val lastQuestionsIds = ArrayDeque<Int>()

    private val quizzType
        get() = intent.extras.getSerializable("quizz_type") as QuizzType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.quizz_activity)

        statsFragment = StatsFragment.newInstance(null)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
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

        history_action_button.setOnClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        lastQuestionsIds.clear()

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

        question_text.setOnLongClickListener { _ ->
            if (currentDebugData != null)
                showKanjiProbabilityData(currentQuestion.kanji, currentDebugData!!)
            true
        }
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

        val (ids, debugParams) = db.getEnabledIdsAndProbalities()
        if (ids.size < NB_ANSWERS) {
            Log.wtf(TAG, "Too few kanjis selected for a quizz: ${ids.size}")
            return
        }

        val question = pickQuestion(db, ids)
        Log.v(TAG, "Selected question: $question")
        currentQuestion = question.kanji
        currentDebugData = DebugData(question.probabilityData, debugParams.probaParamsStage1, debugParams.probaParamsStage2, question.totalWeight)
        currentAnswers = pickAnswers(db, ids, currentQuestion)

        addIdToLastQuestions(currentQuestion.id)

        showCurrentQuestion()
    }

    data class PickedQuestion(val kanji: Kanji, val probabilityData: KanjiDb.ProbabilityData, val totalWeight: Double)

    private fun pickQuestion(db: KanjiDb, ids: List<KanjiDb.ProbabilityData>): PickedQuestion {
        val idsWithoutRecent = ids.filter { it.kanjiId !in lastQuestionsIds }

        val totalWeight = idsWithoutRecent.map { it.finalProbability }.sum()
        val questionPos = Math.random() * totalWeight
        Log.v(TAG, "Picking a question, questionPos: $questionPos, totalWeight: $totalWeight")
        var question = idsWithoutRecent.last() // take last, it is probably safer with float arithmetic
        run {
            var currentWeight = 0.0
            for (kanjiData in idsWithoutRecent) {
                currentWeight += kanjiData.finalProbability
                if (currentWeight >= questionPos) {
                    question = kanjiData
                    break
                }
            }
            if (currentWeight < questionPos)
                Log.v(TAG, "Couldn't pick a question")
        }

        return PickedQuestion(db.getKanji(question.kanjiId), question, totalWeight)
    }

    private fun pickAnswers(db: KanjiDb, ids: List<KanjiDb.ProbabilityData>, currentQuestion: Kanji): List<Kanji> {
        val similarKanjiIds = currentQuestion.similarities.map { it.id }.filter { db.isKanjiEnabled(it) }
        val similarKanjis =
                if (similarKanjiIds.size >= NB_ANSWERS - 1)
                    pickRandom(similarKanjiIds, NB_ANSWERS - 1)
                else
                    similarKanjiIds

        val additionalAnswers = pickRandom(ids.map { it.kanjiId }, NB_ANSWERS - 1 - similarKanjis.size, setOf(currentQuestion.id) + similarKanjis)

        val currentAnswers = ((additionalAnswers + similarKanjis).map { db.getKanji(it) } + listOf(currentQuestion)).toMutableList()
        if (currentAnswers.size != NB_ANSWERS)
            Log.wtf(TAG, "Got ${currentAnswers.size} answers instead of $NB_ANSWERS")
        shuffle(currentAnswers)

        return currentAnswers
    }

    private fun addIdToLastQuestions(id: Int) {
        while (lastQuestionsIds.size > LAST_QUESTIONS_TO_AVOID_COUNT - 1)
            lastQuestionsIds.removeFirst()
        lastQuestionsIds.add(id)
    }

    private fun showCurrentQuestion() {
        // when showNewQuestion is called in onCreate, statsFragment is not visible yet
        if (statsFragment.isVisible)
            statsFragment.updateStats()
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
            db.updateScores(currentQuestion.kanji, Certainty.DONTKNOW)
            addUnknownAnswerToHistory(currentQuestion, currentDebugData)
        } else if (currentAnswers[position] == currentQuestion ||
                // also compare answer texts because different answers can have the same readings
                // like 副 and 福 and we don't want to penalize the user for that
                currentAnswers[position].getAnswerText(quizzType) == currentQuestion.getAnswerText(quizzType)) {
            // correct
            db.updateScores(currentQuestion.kanji, certainty)
            addGoodAnswerToHistory(currentQuestion, currentDebugData)
            correctCount += 1
        } else {
            // wrong
            db.updateScores(currentQuestion.kanji, Certainty.DONTKNOW)
            db.updateScores(currentAnswers[position].kanji, Certainty.DONTKNOW)
            addWrongAnswerToHistory(currentQuestion, currentDebugData, currentAnswers[position])
        }

        questionCount += 1

        showNewQuestion()
    }

    private fun addGoodAnswerToHistory(correct: Kanji, probabilityData: DebugData?) {
        history.add(HistoryLine.Correct(correct.id))

        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_green)

        history_view.addView(layout, 0)
        updateSheetPeekHeight(layout)
        discardOldHistory()
    }

    private fun addWrongAnswerToHistory(correct: Kanji, probabilityData: DebugData?, wrong: Kanji) {
        history.add(HistoryLine.Incorrect(correct.id, wrong.id))

        val layoutGood = makeHistoryLine(correct, probabilityData, R.drawable.round_red, false)
        val layoutBad = makeHistoryLine(wrong, null, null)

        history_view.addView(layoutBad, 0)
        history_view.addView(layoutGood, 0)
        updateSheetPeekHeight(layoutGood)
        discardOldHistory()
    }

    private fun addUnknownAnswerToHistory(correct: Kanji, probabilityData: DebugData?) {
        history.add(HistoryLine.Unknown(correct.id))

        val layout = makeHistoryLine(correct, probabilityData, R.drawable.round_red)

        history_view.addView(layout, 0)
        updateSheetPeekHeight(layout)
        discardOldHistory()
    }

    private fun makeHistoryLine(kanji: Kanji, probabilityData: DebugData?, style: Int?, withSeparator: Boolean = true): View {
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
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.aedict_not_installed, Toast.LENGTH_SHORT).show()
            }
        }
        kanjiView.setOnLongClickListener {
            if (probabilityData != null)
                showKanjiProbabilityData(kanji.kanji, probabilityData)
            true
        }

        val detailView = line.findViewById<TextView>(R.id.kanji_item_description)
        val detail = getKanjiDescription(kanji)
        detailView.text = detail

        if (!withSeparator) {
            line.findViewById<View>(R.id.kanji_item_separator).visibility = View.GONE
        }

        return line
    }

    private fun showKanjiProbabilityData(kanji: String, probabilityData: DebugData) {
        AlertDialog.Builder(this)
                .setTitle(kanji)
                .setMessage(
                        getString(R.string.debug_info,
                                probabilityData.probabilityData.daysSinceCorrect,
                                probabilityData.probabilityData.longScore,
                                probabilityData.probabilityData.longWeight,
                                probabilityData.probabilityData.shortScore,
                                probabilityData.probabilityData.shortWeight,
                                probabilityData.probabilityData.finalProbability,
                                probabilityData.totalWeight))
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
                    addGoodAnswerToHistory(db.getKanji(parcel.readInt()), null)
                }
                1 -> {
                    addUnknownAnswerToHistory(db.getKanji(parcel.readInt()), null)
                }
                2 -> {
                    addWrongAnswerToHistory(db.getKanji(parcel.readInt()), null, db.getKanji(parcel.readInt()))
                }
            }
        })

        parcel.recycle()
    }
}
