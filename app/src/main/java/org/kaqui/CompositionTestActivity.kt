package org.kaqui

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.widget.NestedScrollView
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ToggleButton
import kotlinx.android.synthetic.main.composition_test_activity.*
import org.kaqui.model.*

class CompositionTestActivity : TestActivityBase() {
    companion object {
        private const val TAG = "CompositionTestActivity"

        private const val COLUMNS = 3
    }

    private lateinit var answerButtons: List<ToggleButton>
    private var partMode = false

    private val currentKanji get() = testEngine.currentQuestion.contents as Kanji

    override val testType = TestType.KANJI_COMPOSITION

    override val historyScrollView: NestedScrollView get() = history_scroll_view
    override val historyActionButton: FloatingActionButton get() = history_action_button
    override val historyView: LinearLayout get() = history_view
    override val sessionScore: TextView get() = session_score
    override val mainView: View get() = main_scrollview
    override val mainCoordLayout: CoordinatorLayout get() = main_coordlayout

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.composition_test_activity)
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null)
            partMode = savedInstanceState.getBoolean("partMode")

        question_text.textSize = 20.0f

        val answersLayout = LinearLayout(this)
        answersLayout.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        answersLayout.orientation = LinearLayout.VERTICAL
        val lineLayouts = (0..testEngine.answerCount / COLUMNS).map {
            val line = LinearLayout(this)
            line.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            line.orientation = LinearLayout.HORIZONTAL
            line
        }
        for (line in lineLayouts)
            answersLayout.addView(line)
        initButtons(lineLayouts, COLUMNS)
        answers_layout.addView(answersLayout, 0)

        done_button.setOnClickListener { this.onAnswerDone() }
        dontknow_button.setOnClickListener { this.onDontKnow() }
        next_button.setOnClickListener { this.showNextQuestion() }
        next_button.visibility = View.GONE

        question_text.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }

        showCurrentQuestion()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("partMode", partMode)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        partMode = savedInstanceState.getBoolean("partMode")
    }

    private fun initButtons(lineLayouts: List<LinearLayout>, columns: Int) {
        val answerButtons = ArrayList<ToggleButton>(testEngine.answerCount)
        for (i in 0 until testEngine.answerCount) {
            val currentLine = lineLayouts[i / columns]
            val button = ToggleButton(this)

            button.textSize = 30.0f
            button.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            answerButtons.add(button)

            currentLine.addView(button, i % columns)
        }
        this.answerButtons = answerButtons
    }

    private fun sampleAnswers(possibleAnswers: List<List<Int>>, currentAnswers: List<Int>): List<Int> {
        if (possibleAnswers.isEmpty() || currentAnswers.size == testEngine.answerCount)
            return currentAnswers

        val currentList = possibleAnswers[0] - currentAnswers

        return if (currentList.size <= testEngine.answerCount - currentAnswers.size) {
            sampleAnswers(possibleAnswers.drop(1), currentAnswers + currentList)
        } else {
            currentAnswers + pickRandom(currentList, testEngine.answerCount - currentAnswers.size, setOf())
        }
    }

    override fun prepareNewQuestion() {
        val db = KaquiDb.getInstance(this)
        val ids = testEngine.prepareNewQuestion().map { it.itemId }

        val questionPartsIds = currentKanji.parts.map { it.id }
        val possiblePartsIds = db.getCompositionAnswerIds(testEngine.currentQuestion.id) - testEngine.currentQuestion.id
        val similarItemIds = testEngine.currentQuestion.similarities.map { it.id }.filter { testEngine.itemView.isItemEnabled(it) } - testEngine.currentQuestion.id
        val restOfAnswers = ids - testEngine.currentQuestion.id

        Log.d(TAG, "Possible parts for ${currentKanji.kanji}: ${possiblePartsIds.map { (db.getKanji(it).contents as Kanji).kanji }}")

        partMode = Math.random() > 0.5
        val currentAnswers =
                if (partMode) {
                    Log.d(TAG, "Composition mode: part")
                    sampleAnswers(listOf(possiblePartsIds, similarItemIds, restOfAnswers), questionPartsIds).map { db.getKanji(it) }.toMutableList()
                } else {
                    Log.d(TAG, "Composition mode: kanji")
                    // remove just one part from all sets so that the user can't answer with parts and is forced to select the whole kanji
                    sampleAnswers(listOf(possiblePartsIds, similarItemIds, restOfAnswers).map { it - currentKanji.parts.random().id }, listOf(testEngine.currentQuestion.id)).map { db.getKanji(it) }.toMutableList()
                }

        if (currentAnswers.size != testEngine.answerCount)
            Log.wtf(TAG, "Got ${currentAnswers.size} answers instead of ${testEngine.answerCount}")
        currentAnswers.shuffle()

        testEngine.currentAnswers = currentAnswers
    }

    override fun showCurrentQuestion() {
        super.showCurrentQuestion()

        question_text.text = testEngine.currentQuestion.getQuestionText(testType)

        for ((button, answer) in answerButtons.zip(testEngine.currentAnswers)) {
            button.isClickable = true
            button.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.answerDontKnow))
            button.backgroundTintMode = PorterDuff.Mode.MULTIPLY
            button.text = answer.getAnswerText(testType)
            button.textOn = answer.getAnswerText(testType)
            button.textOff = answer.getAnswerText(testType)
        }
    }

    private fun onAnswerDone() {
        var ok = true
        for ((button, answer) in answerButtons.zip(testEngine.currentAnswers)) {
            button.isClickable = false
            val buttonChecked = button.isChecked
            val answerValid =
                    if (partMode)
                        currentKanji.parts.find { it.id == answer.id } != null
                    else
                        answer.id == testEngine.currentQuestion.id
            if (buttonChecked && answerValid) {
                button.backgroundTintMode = PorterDuff.Mode.MULTIPLY
                button.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.compositionGood))
            } else if (buttonChecked && !answerValid) {
                ok = false
                button.backgroundTintMode = PorterDuff.Mode.MULTIPLY
                button.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.compositionBadSelected))
            } else if (!buttonChecked && answerValid) {
                ok = false
                button.backgroundTintMode = PorterDuff.Mode.MULTIPLY
                button.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.compositionBadNotSelected))
            }
        }

        if (ok)
            testEngine.markAnswer(Certainty.SURE)
        else
            testEngine.markAnswer(Certainty.DONTKNOW)

        done_button.visibility = View.GONE
        dontknow_button.visibility = View.GONE
        next_button.visibility = View.VISIBLE
    }

    private fun onDontKnow() {
        for (button in answerButtons)
            button.isChecked = false

        onAnswerDone()
    }

    private fun showNextQuestion() {
        prepareNewQuestion()

        done_button.visibility = View.VISIBLE
        dontknow_button.visibility = View.VISIBLE
        next_button.visibility = View.GONE
        for (answerButton in answerButtons)
            answerButton.isChecked = false

        showCurrentQuestion()
    }
}