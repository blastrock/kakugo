package org.kaqui

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.ContextCompat
import android.support.v4.widget.NestedScrollView
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.jetbrains.anko.*
import org.kaqui.model.Certainty
import org.kaqui.model.TestType
import org.kaqui.model.getQuestionText
import org.kaqui.model.text

class TextTestActivity : TestActivityBase() {
    companion object {
        private const val TAG = "TextTestActivity"
    }

    private lateinit var answerField: EditText

    private lateinit var testLayout: TestLayout

    override val historyScrollView: NestedScrollView get() = testLayout.historyScrollView
    override val historyActionButton: FloatingActionButton get() = testLayout.historyActionButton
    override val historyView: LinearLayout get() = testLayout.historyView
    override val sessionScore: TextView get() = testLayout.sessionScore
    override val mainView: View get() = testLayout.mainView
    override val mainCoordLayout: CoordinatorLayout get() = testLayout.mainCoordinatorLayout

    override val testType
        get() = intent.extras!!.getSerializable("test_type") as TestType

    override fun onCreate(savedInstanceState: Bundle?) {
        val questionMinSize = 30

        testLayout = TestLayout(this) { testLayout ->
            testLayout.makeMainBlock(this@TextTestActivity, this, questionMinSize) {
                testLayout.wrapInScrollView(this) {
                    verticalLayout {
                        view {
                            backgroundColor = Color.rgb(0xcc, 0xcc, 0xcc)
                        }.lparams(width = matchParent, height = dip(1))

                        verticalLayout {
                            linearLayout {
                                gravity = Gravity.CENTER_VERTICAL
                                
                                val field = editText {
                                    gravity = Gravity.CENTER
                                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                    setOnEditorActionListener { v, actionId, event ->
                                        val answer = v.text.toString()
                                        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_NULL) {
                                            if (answer.isBlank()) {
                                                false
                                            }
                                            else {
                                                this@TextTestActivity.onTextAnswerClicked(v, Certainty.SURE)
                                                true
                                            }
                                        }
                                        else {
                                            false
                                        }
                                    }
                                }.lparams(weight = 1f)
                                answerField = field

                                button(R.string.maybe) {
                                    setExtTint(R.color.answerMaybe)
                                    setOnClickListener {
                                        if (!answerField.text.toString().isBlank()) {
                                            this@TextTestActivity.onTextAnswerClicked(this, Certainty.MAYBE)
                                        }
                                    }
                                }

                                button(R.string.sure) {
                                    setExtTint(R.color.answerSure)
                                    setOnClickListener {
                                        if (!answerField.text.toString().isBlank()) {
                                            this@TextTestActivity.onTextAnswerClicked(this, Certainty.SURE)
                                        }
                                    }
                                }
                            }.lparams(width = matchParent, height = wrapContent)

                            linearLayout {
                                button(R.string.dont_know) {
                                    setExtTint(R.color.answerDontKnow)
                                    setOnClickListener { this@TextTestActivity.onTextAnswerClicked(this, Certainty.DONTKNOW) }
                                }.lparams(width = matchParent)
                            }.lparams(width = matchParent, height = wrapContent)

                            view {
                                backgroundColor = Color.rgb(0xcc, 0xcc, 0xcc)
                            }.lparams(width = matchParent, height = dip(1))
                        }

                    }.lparams(width = matchParent, height = wrapContent)
                }
            }
        }

        testLayout.questionText.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }

        super.onCreate(savedInstanceState)

        showCurrentQuestion()
    }

    override fun showCurrentQuestion() {
        super.showCurrentQuestion()
        testLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        answerField.setText("")
        answerField.requestFocus()
    }

    private fun onTextAnswerClicked(view: View, certainty: Certainty) {
        val answer = answerField.text.toString()
        val result = testEngine.selectAnswer(certainty, answer)

        val offsetViewBounds = Rect()
        view.getDrawingRect(offsetViewBounds)
        testLayout.mainCoordinatorLayout.offsetDescendantRectToMyCoords(view, offsetViewBounds)
        testLayout.overlay.trigger(offsetViewBounds.centerX(), offsetViewBounds.centerY(), ContextCompat.getColor(this, result.toColorRes()))

        testEngine.prepareNewQuestion()
        showCurrentQuestion()
    }
}