package org.kaqui

import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.widget.NestedScrollView
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.test_activity.*
import org.kaqui.model.*
import java.util.*

class TestActivity : TestActivityBase() {
    companion object {
        private const val TAG = "TestActivity"
        private const val COLUMNS = 2
    }

    private lateinit var answerTexts: List<TextView>

    override val historyScrollView: NestedScrollView get() = history_scroll_view
    override val historyActionButton: FloatingActionButton get() = history_action_button
    override val historyView: LinearLayout get() = history_view
    override val sessionScore: TextView get() = session_score
    override val mainView: View get() = main_scrollview
    override val mainCoordLayout: CoordinatorLayout get() = main_coordlayout

    override val testType
        get() = intent.extras.getSerializable("test_type") as TestType

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.test_activity)
        super.onCreate(savedInstanceState)

        when (testType) {
            TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING -> {
                question_text.textSize = 50.0f
                initButtons(listOf(answers_layout), testEngine.answerCount, R.layout.kanji_answer_line)
            }

            TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> {
                when (testType) {
                    TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI -> {
                        question_text.textSize = 20.0f
                        (question_text.layoutParams as LinearLayout.LayoutParams).topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.0f, resources.displayMetrics).toInt()
                        (question_text.layoutParams as LinearLayout.LayoutParams).bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.0f, resources.displayMetrics).toInt()
                    }
                    TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA ->
                        question_text.textSize = 50.0f
                    else -> Unit
                }

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
                initButtons(lineLayouts, COLUMNS, R.layout.kanji_answer_block)
                answers_layout.addView(answersLayout, 0)
            }
        }

        showCurrentQuestion()
    }

    private fun initButtons(lineLayouts: List<LinearLayout>, columns: Int, layoutToInflate: Int) {
        val answerTexts = ArrayList<TextView>(testEngine.answerCount)
        for (i in 0 until testEngine.answerCount) {
            val currentLine = lineLayouts[i / columns]
            val answerLine = LayoutInflater.from(this).inflate(layoutToInflate, currentLine, false)

            val textView: TextView = answerLine.findViewById(R.id.answer_text)
            when (testType) {
                TestType.READING_TO_WORD, TestType.MEANING_TO_WORD -> textView.textSize = 30.0f
                else -> Unit
            }
            answerTexts.add(textView)
            answerLine.findViewById<View>(R.id.maybe_button).setOnClickListener { this.onAnswerClicked(Certainty.MAYBE, i) }
            answerLine.findViewById<View>(R.id.sure_button).setOnClickListener { this.onAnswerClicked(Certainty.SURE, i) }

            currentLine.addView(answerLine, i % columns)
        }
        this.answerTexts = answerTexts
        dontknow_button.setOnClickListener { this.onAnswerClicked(Certainty.DONTKNOW, 0) }

        question_text.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }
    }

    override fun showCurrentQuestion() {
        super.showCurrentQuestion()

        question_text.text = testEngine.currentQuestion.getQuestionText(testType)

        for (i in 0 until testEngine.answerCount) {
            answerTexts[i].text = testEngine.currentAnswers[i].getAnswerText(testType)
        }
    }

    private fun onAnswerClicked(certainty: Certainty, position: Int) {
        testEngine.selectAnswer(certainty, position)

        testEngine.prepareNewQuestion()
        showCurrentQuestion()
    }
}
