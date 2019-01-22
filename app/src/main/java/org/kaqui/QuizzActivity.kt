package org.kaqui

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.quizz_activity.*
import org.kaqui.model.*
import java.util.*

class QuizzActivity : QuizzActivityBase() {
    companion object {
        private const val TAG = "QuizzActivity"
        private const val COLUMNS = 2
    }

    private lateinit var answerTexts: List<TextView>

    override val historyScrollView get() = history_scroll_view
    override val historyActionButton get() = history_action_button
    override val historyView get() = history_view
    override val sessionScore get() = session_score
    override val mainView get() = main_scrollview
    override val mainCoordLayout get() = main_coordlayout

    override val quizzType
        get() = intent.extras.getSerializable("quizz_type") as QuizzType

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.quizz_activity)
        super.onCreate(savedInstanceState)

        when (quizzType) {
            QuizzType.WORD_TO_READING, QuizzType.WORD_TO_MEANING, QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING -> {
                question_text.textSize = 50.0f
                initButtons(listOf(answers_layout), QuizzEngine.NB_ANSWERS, R.layout.kanji_answer_line)
            }

            QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD, QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI, QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.ROMAJI_TO_HIRAGANA, QuizzType.KATAKANA_TO_ROMAJI, QuizzType.ROMAJI_TO_KATAKANA -> {
                when (quizzType) {
                    QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD, QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI -> {
                        question_text.textSize = 20.0f
                        (question_text.layoutParams as LinearLayout.LayoutParams).topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.0f, resources.displayMetrics).toInt()
                        (question_text.layoutParams as LinearLayout.LayoutParams).bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.0f, resources.displayMetrics).toInt()
                    }
                    QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.ROMAJI_TO_HIRAGANA, QuizzType.KATAKANA_TO_ROMAJI, QuizzType.ROMAJI_TO_KATAKANA ->
                        question_text.textSize = 50.0f
                    else -> Unit
                }

                val answersLayout = LinearLayout(this)
                answersLayout.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                answersLayout.orientation = LinearLayout.VERTICAL
                val lineLayouts = (0..QuizzEngine.NB_ANSWERS / COLUMNS).map {
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
        val answerTexts = ArrayList<TextView>(QuizzEngine.NB_ANSWERS)
        for (i in 0 until QuizzEngine.NB_ANSWERS) {
            val currentLine = lineLayouts[i / columns]
            val answerLine = LayoutInflater.from(this).inflate(layoutToInflate, currentLine, false)

            val textView: TextView = answerLine.findViewById(R.id.answer_text)
            when (quizzType) {
                QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD -> textView.textSize = 30.0f
                else -> Unit
            }
            answerTexts.add(textView)
            answerLine.findViewById<View>(R.id.maybe_button).setOnClickListener { _ -> this.onAnswerClicked(Certainty.MAYBE, i) }
            answerLine.findViewById<View>(R.id.sure_button).setOnClickListener { _ -> this.onAnswerClicked(Certainty.SURE, i) }

            currentLine.addView(answerLine, i % columns)
        }
        this.answerTexts = answerTexts
        dontknow_button.setOnClickListener { _ -> this.onAnswerClicked(Certainty.DONTKNOW, 0) }

        question_text.setOnLongClickListener { _ ->
            if (quizzEngine.currentDebugData != null)
                showItemProbabilityData(quizzEngine.currentQuestion.text, quizzEngine.currentDebugData!!)
            true
        }
    }

    override fun showCurrentQuestion() {
        super.showCurrentQuestion()

        question_text.text = quizzEngine.currentQuestion.getQuestionText(quizzType)

        for (i in 0 until QuizzEngine.NB_ANSWERS) {
            answerTexts[i].text = quizzEngine.currentAnswers[i].getAnswerText(quizzType)
        }
    }

    private fun onAnswerClicked(certainty: Certainty, position: Int) {
        quizzEngine.selectAnswer(certainty, position)

        quizzEngine.prepareNewQuestion()
        showCurrentQuestion()
    }
}
