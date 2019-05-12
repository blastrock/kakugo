package org.kaqui.testactivities

import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.jetbrains.anko.*
import org.kaqui.R
import org.kaqui.model.*
import org.kaqui.separator
import org.kaqui.setExtTint
import org.kaqui.toColorRes
import kotlin.concurrent.schedule
import java.util.*

const val INPUT_DELAY_ON_RIGHT_ANSWER: Long = 100
const val INPUT_DELAY_ON_WRONG_ANSWER: Long = 500

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

    private val currentKana get() = testEngine.currentQuestion.contents as Kana
    private var shouldIgnoreTextInput = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val questionMinSize = 30
        val fastTextInput = PreferenceManager.getDefaultSharedPreferences(this@TextTestActivity).getBoolean("fast_text_input", false)

        testLayout = TestLayout(this) { testLayout ->
            testLayout.makeMainBlock(this@TextTestActivity, this, questionMinSize) {
                testLayout.wrapInScrollView(this) {
                    verticalLayout {
                        separator(this@TextTestActivity)

                        verticalLayout {
                            linearLayout {
                                gravity = Gravity.CENTER_VERTICAL

                                val field = editText {
                                    gravity = Gravity.CENTER
                                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                    setOnEditorActionListener { v, actionId, _ ->
                                        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_NULL)
                                            this@TextTestActivity.onTextAnswerClicked(v, Certainty.SURE)
                                        else
                                            false
                                    }

                                    if (fastTextInput) {
                                        addTextChangedListener(object : TextWatcher {
                                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                            override fun afterTextChanged(s: Editable?) {
                                                answerTextDidChange(s)
                                            }
                                        })

                                        filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
                                            if (shouldIgnoreTextInput) {
                                                ""
                                            } else {
                                                source
                                            }
                                        })
                                    }
                                }.lparams(weight = 1f)
                                answerField = field

                                if (!fastTextInput) {
                                    button(R.string.maybe) {
                                        setExtTint(R.color.answerMaybe)
                                        setOnClickListener {
                                            this@TextTestActivity.onTextAnswerClicked(this, Certainty.MAYBE)
                                        }
                                    }

                                    button(R.string.sure) {
                                        setExtTint(R.color.answerSure)
                                        setOnClickListener {
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

                            separator(this@TextTestActivity)
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

        setUpGui(savedInstanceState)

        showCurrentQuestion()
    }

    override fun showCurrentQuestion() {
        super.showCurrentQuestion()
        testLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        answerField.setText("")
        answerField.requestFocus()
    }

    private fun onTextAnswerClicked(view: View, certainty: Certainty): Boolean {
        val result =
                if (certainty == Certainty.DONTKNOW) {
                    testEngine.markAnswer(Certainty.DONTKNOW)
                    Certainty.DONTKNOW
                } else {
                    val answer = answerField.text.trim().toString().toLowerCase()

                    if (answer.isBlank())
                        return false

                    if (answer == currentKana.romaji) {
                        testEngine.markAnswer(certainty)
                        certainty
                    } else {
                        testEngine.markAnswer(Certainty.DONTKNOW)
                        Certainty.DONTKNOW
                    }
                }
        showAnswerAnimation(view, ContextCompat.getColor(this, result.toColorRes()))
        didAnswer()
        return true
    }

    private fun answerTextDidChange(s: Editable?) {
        val input = s.toString().toLowerCase()
        if (input.isEmpty()) {
            return
        }

        val romaji = currentKana.romaji.toLowerCase()
        val result =
                if (input == romaji) {
                    Certainty.SURE
                } else if (romaji.startsWith(input)) {
                    return
                } else {
                    Certainty.DONTKNOW
                }
        testEngine.markAnswer(result)

        shouldIgnoreTextInput = true
        showAnswerAnimation(answerField, ContextCompat.getColor(this, result.toColorRes()))
        val delay = if (result == Certainty.DONTKNOW) INPUT_DELAY_ON_WRONG_ANSWER else INPUT_DELAY_ON_RIGHT_ANSWER
        Timer("NextQuestionPause",false).schedule(delay) {
            runOnUiThread {
                shouldIgnoreTextInput = false
                didAnswer()
            }
        }
    }

    private fun showAnswerAnimation(view: View, color: Int) {
        val offsetViewBounds = Rect()
        view.getDrawingRect(offsetViewBounds)
        testLayout.mainCoordinatorLayout.offsetDescendantRectToMyCoords(view, offsetViewBounds)
        testLayout.overlay.trigger(offsetViewBounds.centerX(), offsetViewBounds.centerY(), color)
    }

    private fun didAnswer() {
        testEngine.prepareNewQuestion()
        showCurrentQuestion()
    }
}
