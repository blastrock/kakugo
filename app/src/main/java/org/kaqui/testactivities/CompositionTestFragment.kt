package org.kaqui.testactivities

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ToggleButton
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.UI
import org.kaqui.*
import org.kaqui.model.*
import kotlin.collections.shuffle

class CompositionTestFragment : Fragment(), TestFragment {
    companion object {
        private const val TAG = "CompositionTestFragment"

        private const val COLUMNS = 3

        @JvmStatic
        fun newInstance() = CompositionTestFragment()
    }

    private val testFragmentHolder
        get() = (activity!! as TestFragmentHolder)
    private val testEngine
        get() = testFragmentHolder.testEngine
    private val testType
        get() = testFragmentHolder.testType

    private lateinit var answerButtons: List<ToggleButton>

    private val currentKanji get() = testEngine.currentQuestion.contents as Kanji

    private lateinit var testQuestionLayout: TestQuestionLayout
    private lateinit var doneButton: Button
    private lateinit var dontKnowButton: Button
    private lateinit var nextButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val answerCount = getAnswerCount(testType)
        val answerButtons = mutableListOf<ToggleButton>()

        testQuestionLayout = TestQuestionLayout()
        val mainBlock = UI {
            testQuestionLayout.makeMainBlock(activity!!, this, 10) {
                wrapInScrollView(this) {
                    verticalLayout {
                        repeat(answerCount / COLUMNS) {
                            linearLayout {
                                repeat(COLUMNS) {
                                    val button = toggleButton {
                                        typeface = TypefaceManager.getTypeface(context)
                                        textSize = 30.0f
                                        setOnClickListener { colorCheckedButton(it as ToggleButton) }
                                    }.lparams(width = wrapContent, height = wrapContent, weight = 1f)
                                    answerButtons.add(button)
                                }
                            }.lparams(width = matchParent, height = wrapContent)
                        }
                        linearLayout {
                            doneButton = button(R.string.answerDone) {
                                setExtTint(R.attr.backgroundSure)
                            }.lparams(width = 0, weight = 1.0f)
                            dontKnowButton = button(R.string.dont_know) {
                                setExtTint(R.attr.backgroundDontKnow)
                            }.lparams(width = 0, weight = 1.0f)
                            nextButton = button(R.string.next).lparams(weight = 1.0f)
                        }.lparams(width = matchParent, height = wrapContent)
                    }.lparams(width = matchParent, height = wrapContent)
                }
            }
        }.view

        this.answerButtons = answerButtons

        var finished = false
        var checkedAnswers: ArrayList<Int>? = null
        if (savedInstanceState != null) {
            finished = savedInstanceState.getBoolean("finished")
            checkedAnswers = savedInstanceState.getIntegerArrayList("checkedAnswers")!!
        }

        doneButton.setOnClickListener { this.onAnswerDone() }
        dontKnowButton.setOnClickListener { this.onDontKnow() }
        nextButton.setOnClickListener { testFragmentHolder.nextQuestion() }

        testQuestionLayout.questionText.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(context!!, testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }

        if (checkedAnswers != null)
            for ((button, answer) in answerButtons.zip(testEngine.currentAnswers)) {
                if (answer.id in checkedAnswers)
                    button.isChecked = true
            }

        refreshQuestion()

        if (finished) {
            doneButton.visibility = View.GONE
            dontKnowButton.visibility = View.GONE
            validateAnswer()
        } else {
            nextButton.visibility = View.GONE
        }

        return mainBlock
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("finished", nextButton.visibility == View.VISIBLE)
        outState.putIntegerArrayList("checkedAnswers", ArrayList(answerButtons.zip(testEngine.currentAnswers).mapNotNull { (button, answer) ->
            if (button.isChecked)
                answer.id
            else
                null
        }))
    }

    private fun colorCheckedButton(button: ToggleButton) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val color =
                    if (button.isChecked)
                        R.attr.compositionGood
                    else
                        R.attr.backgroundDontKnow

            button.backgroundTintMode = PorterDuff.Mode.MULTIPLY
            button.backgroundTintList = ColorStateList.valueOf(context!!.getColorFromAttr(color))
        }
    }

    private fun setButtonTint(button: ToggleButton, @AttrRes color: Int, checked: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.backgroundTintMode = PorterDuff.Mode.MULTIPLY
            button.backgroundTintList = ColorStateList.valueOf(context!!.getColorFromAttr(color))
        } else {
            button.isChecked = checked
        }
    }

    override fun startNewQuestion() {
        doneButton.visibility = View.VISIBLE
        dontKnowButton.visibility = View.VISIBLE
        nextButton.visibility = View.GONE

        for (button in answerButtons) {
            button.isClickable = true
            button.isChecked = false
            colorCheckedButton(button)
        }
    }

    override fun setSensible(e: Boolean) {
        doneButton.isClickable = e
        nextButton.isClickable = e
        dontKnowButton.isClickable = e
        for ((button, _) in answerButtons.zip(testEngine.currentAnswers)) {
            button.isClickable = e
        }
    }

    override fun refreshQuestion() {
        testQuestionLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        for ((button, answer) in answerButtons.zip(testEngine.currentAnswers)) {
            button.text = answer.getAnswerText(testType)
            button.textOn = answer.getAnswerText(testType)
            button.textOff = answer.getAnswerText(testType)
            colorCheckedButton(button)
        }
    }

    private fun validateAnswer(): Boolean {
        var ok = true
        for ((button, answer) in answerButtons.zip(testEngine.currentAnswers)) {
            button.isClickable = false
            val buttonChecked = button.isChecked
            val answerValid = currentKanji.parts.find { it.id == answer.id } != null
            if (buttonChecked && answerValid) {
                setButtonTint(button, R.attr.compositionGood, true)
            } else if (buttonChecked && !answerValid) {
                ok = false
                setButtonTint(button, R.attr.compositionBadSelected, false)
            } else if (!buttonChecked && answerValid) {
                ok = false
                setButtonTint(button, R.attr.compositionBadNotSelected, true)
            }
        }
        return ok
    }

    private fun onAnswerDone() {
        val result =
                if (validateAnswer())
                    Certainty.SURE
                else
                    Certainty.DONTKNOW
        testFragmentHolder.onAnswer(doneButton, result, null)

        doneButton.visibility = View.GONE
        dontKnowButton.visibility = View.GONE
        nextButton.visibility = View.VISIBLE
    }

    private fun onDontKnow() {
        for (button in answerButtons)
            button.isChecked = false

        onAnswerDone()
    }
}
