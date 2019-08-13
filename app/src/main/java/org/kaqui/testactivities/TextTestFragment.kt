package org.kaqui.testactivities

import android.os.Bundle
import android.text.InputType
import android.text.method.KeyListener
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.UI
import org.kaqui.*
import org.kaqui.model.Certainty
import org.kaqui.model.Kana
import org.kaqui.model.getQuestionText
import org.kaqui.model.text

class TextTestFragment : Fragment(), TestFragment {
    companion object {
        private const val TAG = "TextTestFragment"

        const val defaultInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        @JvmStatic
        fun newInstance() = TextTestFragment()
    }

    private lateinit var answerField: EditText
    private var answerKeyListener: KeyListener? = null
    private lateinit var correctAnswer: TextView
    private var answer: String? = null
    private lateinit var answerButtons: List<Button>
    private lateinit var nextButton: Button

    private lateinit var testQuestionLayout: TestQuestionLayout

    private val testFragmentHolder
        get() = (activity!! as TestFragmentHolder)
    private val testEngine
        get() = testFragmentHolder.testEngine
    private val testType
        get() = testFragmentHolder.testType

    private val currentKana get() = testEngine.currentQuestion.contents as Kana

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreate(savedInstanceState)

        val questionMinSize = 30

        val answerButtons = mutableListOf<Button>()

        testQuestionLayout = TestQuestionLayout()
        val mainBlock = UI {
            testQuestionLayout.makeMainBlock(activity!!, this, questionMinSize, forceLandscape = true) {
                wrapInScrollView(this) {
                    verticalLayout {
                        correctAnswer = textView {
                            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                            visibility = View.GONE
                            textSize = 18f
                            backgroundColor = context!!.getColorFromAttr(R.attr.correctAnswerBackground)
                        }.lparams(width = matchParent, height = wrapContent)

                        answerField = editText {
                            gravity = Gravity.CENTER
                            inputType = defaultInputType
                            setOnEditorActionListener { v, actionId, event ->
                                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_NULL)
                                    if (event == null || event.action == KeyEvent.ACTION_DOWN)
                                        this@TextTestFragment.onTextAnswerClicked(v, Certainty.SURE)
                                    else if (event.action == KeyEvent.ACTION_UP)
                                        true
                                    else
                                        false
                                else
                                    false
                            }
                            answerKeyListener = keyListener
                        }.lparams(width = matchParent)

                        linearLayout {
                            val maybeButton = button(R.string.maybe) {
                                setExtTint(R.attr.backgroundMaybe)
                                setOnClickListener {
                                    this@TextTestFragment.onTextAnswerClicked(this, Certainty.MAYBE)
                                }
                            }.lparams(weight = 1f)

                            val sureButton = button(R.string.sure) {
                                setExtTint(R.attr.backgroundSure)
                                setOnClickListener {
                                    this@TextTestFragment.onTextAnswerClicked(this, Certainty.SURE)
                                }
                            }.lparams(weight = 1f)
                            answerButtons.add(maybeButton)
                            answerButtons.add(sureButton)
                        }.lparams(width = matchParent, height = wrapContent)

                        linearLayout {
                            val dontKnowButton = button(R.string.dont_know) {
                                setExtTint(R.attr.backgroundDontKnow)
                                setOnClickListener { this@TextTestFragment.onTextAnswerClicked(this, Certainty.DONTKNOW) }
                            }.lparams(width = matchParent)
                            answerButtons.add(dontKnowButton)
                        }.lparams(width = matchParent, height = wrapContent)

                        nextButton = button(R.string.next) {
                            setOnClickListener { this@TextTestFragment.onNextClicked() }
                        }
                    }.lparams(width = matchParent, height = wrapContent)
                }
            }
        }.view

        testQuestionLayout.questionText.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(context!!, testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }

        this.answerButtons = answerButtons

        if (savedInstanceState != null) {
            answer =
                    if (savedInstanceState.containsKey("answer"))
                        savedInstanceState.getString("answer")
                    else
                        null
        }

        refreshQuestion()

        return mainBlock
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (answer != null)
            outState.putString("answer", answer!!)
    }

    private fun refreshState() {
        if (answer != null) {
            answerField.setText(answer!!, TextView.BufferType.NORMAL)
            answerField.backgroundColor = context!!.getColorFromAttr(R.attr.wrongAnswerBackground)
            answerField.keyListener = null
            correctAnswer.text = currentKana.romaji
            correctAnswer.visibility = View.VISIBLE
            for (button in answerButtons)
                button.visibility = View.GONE
            nextButton.visibility = View.VISIBLE
        } else {
            answerField.text.clear()
            val attrs = context!!.obtainStyledAttributes(intArrayOf(android.R.attr.editTextBackground))
            answerField.background = attrs.getDrawable(0)
            attrs.recycle()
            answerField.inputType = defaultInputType
            correctAnswer.visibility = View.GONE
            for (button in answerButtons)
                button.visibility = View.VISIBLE
            nextButton.visibility = View.GONE
        }
    }

    override fun setSensible(e: Boolean) {
        nextButton.isClickable = e
        for (button in answerButtons) {
            button.isClickable = e
        }
    }

    override fun refreshQuestion() {
        testQuestionLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        refreshState()

        answerField.requestFocus()
    }

    private fun onTextAnswerClicked(view: View, certainty: Certainty): Boolean {
        if (answer != null) {
            onNextClicked()
            return true
        }

        if (certainty == Certainty.DONTKNOW)
            answerField.text.clear()

        val result =
                if (certainty == Certainty.DONTKNOW) {
                    Certainty.DONTKNOW
                } else {
                    val answer = answerField.text.trim().toString().toLowerCase()

                    if (answer.isBlank())
                        return true

                    if (answer == currentKana.romaji) {
                        certainty
                    } else {
                        Certainty.DONTKNOW
                    }
                }

        testFragmentHolder.onAnswer(view, result, null)

        if (result != Certainty.DONTKNOW)
            testFragmentHolder.nextQuestion()
        else {
            answer = answerField.text.toString()
            refreshState()
        }

        return true
    }

    private fun onNextClicked() {
        answer = null
        testFragmentHolder.nextQuestion()
    }
}