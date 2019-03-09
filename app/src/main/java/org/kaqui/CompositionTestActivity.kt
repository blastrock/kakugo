package org.kaqui

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.widget.NestedScrollView
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ToggleButton
import kotlinx.android.synthetic.main.selection_item.view.*
import org.jetbrains.anko.*
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

    private lateinit var testLayout: TestLayout

    override val historyScrollView: NestedScrollView get() = testLayout.historyScrollView
    override val historyActionButton: FloatingActionButton get() = testLayout.historyActionButton
    override val historyView: LinearLayout get() = testLayout.historyView
    override val sessionScore: TextView get() = testLayout.sessionScore
    override val mainView: View get() = testLayout.mainView
    override val mainCoordLayout: CoordinatorLayout get() = testLayout.mainCoordinatorLayout
    private lateinit var doneButton: Button
    private lateinit var dontKnowButton: Button
    private lateinit var nextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        val answerCount = getAnswerCount(testType)
        val answerButtons = mutableListOf<ToggleButton>()

        testLayout = TestLayout(this) { testLayout ->
            testLayout.makeMainBlock(this@CompositionTestActivity, this, 10) {
                testLayout.wrapInScrollView(this) {
                    verticalLayout {
                        repeat(answerCount / COLUMNS) {
                            linearLayout {
                                repeat(COLUMNS) {
                                    val button = toggleButton {
                                        textSize = 30.0f
                                    }.lparams(width = wrapContent, height = wrapContent, weight = 1f)
                                    answerButtons.add(button)
                                }
                            }.lparams(width = matchParent, height = wrapContent)
                        }
                        linearLayout {
                            doneButton = button(R.string.answerDone) {
                                setExtTint(R.color.answerSure)
                            }.lparams(width = 0, weight = 1.0f)
                            dontKnowButton = button(R.string.dont_know) {
                                setExtTint(R.color.answerDontKnow)
                            }.lparams(width = 0, weight = 1.0f)
                            nextButton = button(R.string.next).lparams(weight = 1.0f)
                        }.lparams(width = matchParent, height = wrapContent)
                    }.lparams(width = matchParent, height = wrapContent)
                }
            }
        }

        this.answerButtons = answerButtons

        super.onCreate(savedInstanceState)
        var finished = false
        var checkedAnswers: ArrayList<Int>? = null
        if (savedInstanceState != null) {
            partMode = savedInstanceState.getBoolean("partMode")
            finished = savedInstanceState.getBoolean("finished")
            checkedAnswers = savedInstanceState.getIntegerArrayList("checkedAnswers")!!
        }

        doneButton.setOnClickListener { this.onAnswerDone() }
        dontKnowButton.setOnClickListener { this.onDontKnow() }
        nextButton.setOnClickListener { this.showNextQuestion() }

        testLayout.questionText.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }

        showCurrentQuestion()

        if (checkedAnswers != null)
            for ((button, answer) in answerButtons.zip(testEngine.currentAnswers)) {
                if (answer.id in checkedAnswers)
                    button.isChecked = true
            }

        if (finished) {
            doneButton.visibility = View.GONE
            dontKnowButton.visibility = View.GONE
            validateAnswer()
        } else {
            nextButton.visibility = View.GONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("partMode", partMode)
        outState.putBoolean("finished", nextButton.visibility == View.VISIBLE)
        outState.putIntegerArrayList("checkedAnswers", ArrayList(answerButtons.zip(testEngine.currentAnswers).mapNotNull { (button, answer) ->
            if (button.isChecked)
                answer.id
            else
                null
        }))
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        partMode = savedInstanceState.getBoolean("partMode")
    }

    private fun setButtonTint(button: ToggleButton, color: Int, checked: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.backgroundTintMode = PorterDuff.Mode.MULTIPLY
            button.backgroundTintList = ColorStateList.valueOf(resources.getColor(color))
        } else {
            button.isChecked = checked
        }
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

        val wholeKanjisRatio = db.getEnabledWholeKanjiRatio()
        val threshold = lerp(0.1f, 0.5f, wholeKanjisRatio)
        partMode = Math.random() > threshold
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

        testLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        for ((button, answer) in answerButtons.zip(testEngine.currentAnswers)) {
            button.isClickable = true
            button.isChecked = false
            setButtonTint(button, R.color.answerDontKnow, false)
            button.text = answer.getAnswerText(testType)
            button.textOn = answer.getAnswerText(testType)
            button.textOff = answer.getAnswerText(testType)
        }
    }

    private fun validateAnswer(): Boolean {
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
                setButtonTint(button, R.color.compositionGood, true)
            } else if (buttonChecked && !answerValid) {
                ok = false
                setButtonTint(button, R.color.compositionBadSelected, false)
            } else if (!buttonChecked && answerValid) {
                ok = false
                setButtonTint(button, R.color.compositionBadNotSelected, true)
            }
        }
        return ok
    }

    private fun onAnswerDone() {
        val ok = validateAnswer()

        val result =
                if (ok)
                    Certainty.SURE
                else
                    Certainty.DONTKNOW

        testEngine.markAnswer(result)

        val offsetViewBounds = Rect()
        doneButton.getDrawingRect(offsetViewBounds)
        testLayout.mainCoordinatorLayout.offsetDescendantRectToMyCoords(doneButton, offsetViewBounds)
        testLayout.overlay.trigger(offsetViewBounds.centerX(), offsetViewBounds.centerY(), resources.getColor(result.toColorRes()))

        doneButton.visibility = View.GONE
        dontKnowButton.visibility = View.GONE
        nextButton.visibility = View.VISIBLE
    }

    private fun onDontKnow() {
        for (button in answerButtons)
            button.isChecked = false

        onAnswerDone()
    }

    private fun showNextQuestion() {
        prepareNewQuestion()

        doneButton.visibility = View.VISIBLE
        dontKnowButton.visibility = View.VISIBLE
        nextButton.visibility = View.GONE

        showCurrentQuestion()
    }
}