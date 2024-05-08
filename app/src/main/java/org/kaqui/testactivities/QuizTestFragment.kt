package org.kaqui.testactivities

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.UI
import org.kaqui.*
import org.kaqui.model.*

class QuizTestFragment : Fragment(), TestFragment {
    private lateinit var testQuestionLayout: TestQuestionLayout
    private lateinit var dontKnowButton: Button
    private lateinit var nextButton: Button

    private lateinit var answerTexts: List<TextView>
    private lateinit var answerButtons: List<Button>
    private lateinit var answerViews: List<View>
    private lateinit var scrollView: ScrollView
    private lateinit var showAnswersButton: Button
    private lateinit var answersToShow: LinearLayout

    private var answer: Int = NO_ANSWER

    private val testFragmentHolder
        get() = (requireActivity() as TestFragmentHolder)
    private val testEngine
        get() = testFragmentHolder.testEngine
    private val testType
        get() = testFragmentHolder.testType

    private var singleButtonMode = false
    private var hideAnswers = false
    private var kanaWords = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        singleButtonMode = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("single_button_mode", false)
        hideAnswers = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("hide_answers", true)
        kanaWords = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("kana_words", true)

        val answerCount = getAnswerCount(testType)
        val answerTexts = mutableListOf<TextView>()
        val answerButtons = mutableListOf<Button>()
        val answerViews = mutableListOf<View>()

        val questionMinSize =
                when (testType) {
                    TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING -> 50
                    TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI -> 10
                    TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> 50
                    else -> throw RuntimeException("unsupported test type for TestActivity")
                }

        testQuestionLayout = TestQuestionLayout()
        val mainBlock = UI {
            testQuestionLayout.makeMainBlock(requireActivity(), this, questionMinSize) {
                linearLayout {
                    gravity = Gravity.CENTER
                    showAnswersButton = button {
                        text = context.getString(R.string.show_answers)
                        padding = dip(24)
                        setOnClickListener {
                            showAnswersButton.visibility = View.GONE
                            answersToShow.visibility = View.VISIBLE
                        }
                    }.lparams {
                        gravity = Gravity.CENTER
                    }
                    answersToShow = wrapInScrollView(this) {
                        scrollView = this
                        verticalLayout {
                            when (testType) {
                                TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING -> {
                                    repeat(answerCount) {
                                        val answerView =
                                                if (singleButtonMode) {
                                                    val position = answerTexts.size
                                                    val answerButton = button {
                                                        gravity = Gravity.START
                                                        typeface = TypefaceManager.getTypeface(context)
                                                        transformationMethod = null

                                                        setOnClickListener { onAnswerClicked(this, Certainty.SURE, position) }
                                                        setOnLongClickListener { onAnswerClicked(this, Certainty.MAYBE, position); true }
                                                    }.lparams(width = matchParent)
                                                    answerTexts.add(answerButton)
                                                    answerButtons.add(answerButton)
                                                    answerButton
                                                } else {
                                                    verticalLayout {
                                                        separator(requireActivity())
                                                        linearLayout {
                                                            gravity = Gravity.CENTER_VERTICAL

                                                            val answerText = textView {
                                                                typeface = TypefaceManager.getTypeface(context)
                                                            }.lparams(weight = 1f)
                                                            val position = answerTexts.size
                                                            answerTexts.add(answerText)

                                                            val maybeButton = button(R.string.maybe) {
                                                                if (resources.configuration.smallestScreenWidthDp < 480) {
                                                                    minimumWidth = 0
                                                                    minWidth = 0
                                                                }
                                                                setExtTint(R.attr.backgroundMaybe)
                                                                setOnClickListener { onAnswerClicked(this, Certainty.MAYBE, position) }
                                                            }
                                                            val sureButton = button(R.string.sure) {
                                                                if (resources.configuration.smallestScreenWidthDp < 480) {
                                                                    minimumWidth = 0
                                                                    minWidth = 0
                                                                }
                                                                setExtTint(R.attr.backgroundSure)
                                                                setOnClickListener { onAnswerClicked(this, Certainty.SURE, position) }
                                                            }
                                                            answerButtons.add(maybeButton)
                                                            answerButtons.add(sureButton)
                                                        }
                                                    }
                                                }
                                        answerViews.add(answerView)
                                    }
                                }

                                TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> {
                                    repeat(answerCount / COLUMNS) {
                                        linearLayout {
                                            repeat(COLUMNS) {
                                                val answerView =
                                                        if (singleButtonMode) {
                                                            gravity = Gravity.CENTER_VERTICAL

                                                            val position = answerTexts.size
                                                            val answerButton = button {
                                                                typeface = TypefaceManager.getTypeface(context)
                                                                transformationMethod = null
                                                                setTextSize(TypedValue.COMPLEX_UNIT_SP,
                                                                        when (testType) {
                                                                            TestType.READING_TO_WORD, TestType.MEANING_TO_WORD -> 30f
                                                                            else -> 50f
                                                                        })
                                                                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                                                setOnClickListener { onAnswerClicked(this, Certainty.SURE, position) }
                                                                setOnLongClickListener { onAnswerClicked(this, Certainty.MAYBE, position); true }
                                                            }.lparams(weight = 1f)
                                                            answerTexts.add(answerButton)
                                                            answerButtons.add(answerButton)
                                                            answerButton
                                                        } else {
                                                            verticalLayout {
                                                                separator(requireActivity())
                                                                linearLayout {
                                                                    gravity = Gravity.CENTER_VERTICAL

                                                                    val answerText = textView {
                                                                        typeface = TypefaceManager.getTypeface(context)
                                                                        setTextSize(TypedValue.COMPLEX_UNIT_SP,
                                                                                when (testType) {
                                                                                    TestType.READING_TO_WORD, TestType.MEANING_TO_WORD -> 30f
                                                                                    else -> 50f
                                                                                })
                                                                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                                                    }.lparams(weight = 1f)
                                                                    val position = answerTexts.size
                                                                    answerTexts.add(answerText)

                                                                    verticalLayout {
                                                                        val sureButton = button(R.string.sure) {
                                                                            minimumHeight = 0
                                                                            minimumWidth = 0
                                                                            minHeight = 0
                                                                            minWidth = 0
                                                                            setExtTint(R.attr.backgroundSure)
                                                                            setOnClickListener { onAnswerClicked(this, Certainty.SURE, position) }
                                                                        }.lparams(width = matchParent, height = wrapContent)
                                                                        val maybeButton = button(R.string.maybe) {
                                                                            minimumHeight = 0
                                                                            minimumWidth = 0
                                                                            minHeight = 0
                                                                            minWidth = 0
                                                                            setExtTint(R.attr.backgroundMaybe)
                                                                            setOnClickListener { onAnswerClicked(this, Certainty.MAYBE, position) }
                                                                        }.lparams(width = matchParent, height = wrapContent)
                                                                        answerButtons.add(sureButton)
                                                                        answerButtons.add(maybeButton)
                                                                    }.lparams(weight = 0f)
                                                                }.lparams(width = matchParent, height = wrapContent)
                                                            }.lparams(weight = 1f)
                                                        }
                                                answerViews.add(answerView)
                                            }
                                        }.lparams(width = matchParent, height = wrapContent)
                                    }
                                }
                                else -> throw RuntimeException("unsupported test type for TestActivity")
                            }
                            separator(requireActivity())
                            nextButton = button(R.string.next) {
                                setOnClickListener { onNextClicked() }
                            }.lparams(width = matchParent)
                            dontKnowButton = button(R.string.dont_know) {
                                setExtTint(R.attr.backgroundDontKnow)
                                setOnClickListener { onAnswerClicked(this, Certainty.DONTKNOW, 0) }
                            }.lparams(width = matchParent)
                        }.lparams(width = matchParent, height = wrapContent)
                    }.lparams(width = matchParent, height = matchParent)
                }.lparams(width = matchParent, height = matchParent)
            }
        }.view

        testQuestionLayout.questionText.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(requireContext(), testEngine.currentQuestion.text(kanaWords), testEngine.currentDebugData!!)
            true
        }

        this.answerTexts = answerTexts
        this.answerButtons = answerButtons
        this.answerViews = answerViews

        if (savedInstanceState != null) {
            answer = savedInstanceState.getInt("answer")
        }

        refreshQuestion()

        return mainBlock
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("answer", answer)
    }

    override fun setSensible(e: Boolean) {
        nextButton.isClickable = e
        dontKnowButton.isClickable = e
        for (answerButton in answerButtons) {
            answerButton.isClickable = e
        }
    }

    private fun setColor(index: Int, @AttrRes color: Int?) {
        if (color != null)
            if (singleButtonMode)
                (answerViews[index] as Button).setExtTint(color)
            else
                answerViews[index].backgroundColor = requireContext().getColorFromAttr(color)
        else
            if (singleButtonMode)
                (answerViews[index] as Button).setExtTint(null)
            else
                answerViews[index].backgroundColor = Color.TRANSPARENT
    }

    private fun refreshState() {
        if (answer != NO_ANSWER) {
            nextButton.visibility = View.VISIBLE
            dontKnowButton.visibility = View.GONE

            val correct = testEngine.currentAnswers.indexOfFirst { it.id == testEngine.currentQuestion.id }
            if (answer != DONT_KNOW)
                setColor(answer, R.attr.wrongAnswerBackground)
            setColor(correct, R.attr.correctAnswerBackground)

            for (answerButton in answerButtons) {
                answerButton.isEnabled = false
            }

            showAnswersButton.visibility = View.GONE
            answersToShow.visibility = View.VISIBLE
        } else {
            nextButton.visibility = View.GONE
            dontKnowButton.visibility = View.VISIBLE

            for (i in answerViews.indices)
                setColor(i, null)

            for (answerButton in answerButtons) {
                answerButton.isEnabled = true
            }

            if (hideAnswers) {
                showAnswersButton.visibility = View.VISIBLE
                answersToShow.visibility = View.GONE
            } else {
                showAnswersButton.visibility = View.GONE
                answersToShow.visibility = View.VISIBLE
            }
        }
    }

    override fun refreshQuestion() {
        testQuestionLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType, kanaWords)

        for (i in answerTexts.indices) {
            answerTexts[i].text = testEngine.currentAnswers[i].getAnswerText(testType, kanaWords)
        }
        scrollView.scrollTo(0, 0)

        refreshState()
    }

    private fun onAnswerClicked(button: View, certainty: Certainty, position: Int) {
        if (certainty != Certainty.DONTKNOW && (testEngine.currentAnswers[position] == testEngine.currentQuestion ||
                        // also compare answer texts because different answers can have the same readings
                        // like 副 and 福 and we don't want to penalize the user for that
                        testEngine.currentAnswers[position].getAnswerText(testType, kanaWords) == testEngine.currentQuestion.getAnswerText(testType, kanaWords) ||
                        // same for question text
                        testEngine.currentAnswers[position].getQuestionText(testType, kanaWords) == testEngine.currentQuestion.getQuestionText(testType, kanaWords))) {
            testFragmentHolder.onAnswer(button, certainty, null)

            testFragmentHolder.nextQuestion()
        } else {
            if (certainty == Certainty.DONTKNOW) {
                testFragmentHolder.onAnswer(button, Certainty.DONTKNOW, null)
                answer = DONT_KNOW
            } else {
                testFragmentHolder.onAnswer(button, Certainty.DONTKNOW, testEngine.currentAnswers[position])
                answer = position
            }

            refreshState()
        }
    }

    private fun onNextClicked() {
        answer = NO_ANSWER
        testFragmentHolder.nextQuestion()
    }

    companion object {
        private const val TAG = "QuizTestFragment"
        private const val COLUMNS = 2

        private const val NO_ANSWER = 0x1000
        private const val DONT_KNOW = 0x1001

        @JvmStatic
        fun newInstance() = QuizTestFragment()
    }
}
