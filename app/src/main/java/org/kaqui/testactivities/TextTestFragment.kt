package org.kaqui.testactivities

import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.UI
import org.kaqui.*
import org.kaqui.model.*

class TextTestFragment : Fragment(), TestFragment {
    companion object {
        private const val TAG = "TextTestFragment"

        @JvmStatic
        fun newInstance() = TextTestFragment()
    }

    private lateinit var answerField: EditText

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

        testQuestionLayout = TestQuestionLayout()
        val mainBlock = UI {
            testQuestionLayout.makeMainBlock(activity!!, this, questionMinSize) {
                wrapInScrollView(this) {
                    verticalLayout {
                        separator(context!!)

                        verticalLayout {
                            linearLayout {
                                gravity = Gravity.CENTER_VERTICAL

                                val field = editText {
                                    gravity = Gravity.CENTER
                                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                    setOnEditorActionListener { v, actionId, event ->
                                        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_NULL)
                                            this@TextTestFragment.onTextAnswerClicked(v, Certainty.SURE)
                                        else
                                            false
                                    }
                                }.lparams(weight = 1f)
                                answerField = field

                                button(R.string.maybe) {
                                    setExtTint(R.color.answerMaybe)
                                    setOnClickListener {
                                        this@TextTestFragment.onTextAnswerClicked(this, Certainty.MAYBE)
                                    }
                                }

                                button(R.string.sure) {
                                    setExtTint(R.color.answerSure)
                                    setOnClickListener {
                                        this@TextTestFragment.onTextAnswerClicked(this, Certainty.SURE)
                                    }
                                }
                            }.lparams(width = matchParent, height = wrapContent)

                            linearLayout {
                                button(R.string.dont_know) {
                                    setExtTint(R.color.answerDontKnow)
                                    setOnClickListener { this@TextTestFragment.onTextAnswerClicked(this, Certainty.DONTKNOW) }
                                }.lparams(width = matchParent)
                            }.lparams(width = matchParent, height = wrapContent)

                            separator(context!!)
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

        refreshQuestion()

        return mainBlock
    }

    override fun refreshQuestion() {
        testQuestionLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        answerField.setText("")
        answerField.requestFocus()
    }

    private fun onTextAnswerClicked(view: View, certainty: Certainty): Boolean {
        val result =
                if (certainty == Certainty.DONTKNOW) {
                    Certainty.DONTKNOW
                } else {
                    val answer = answerField.text.trim().toString().toLowerCase()

                    if (answer.isBlank())
                        return false

                    if (answer == currentKana.romaji) {
                        certainty
                    } else {
                        Certainty.DONTKNOW
                    }
                }

        testFragmentHolder.onAnswer(view, result, null)
        testFragmentHolder.nextQuestion()

        return true
    }
}