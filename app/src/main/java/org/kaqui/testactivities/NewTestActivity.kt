package org.kaqui.testactivities

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import org.kaqui.R
import org.kaqui.TestEngine
import org.kaqui.model.Certainty
import org.kaqui.model.Database
import org.kaqui.model.Item
import org.kaqui.model.Kanji
import org.kaqui.model.TestType
import org.kaqui.model.Word
import org.kaqui.model.getAnswerText
import org.kaqui.model.getQuestionText
import org.kaqui.showItemProbabilityData
import org.kaqui.startActivity
import org.kaqui.toName

class NewTestActivity : ComponentActivity() {
    private lateinit var testEngine: TestEngine

    private var kanaWords = false

    private val viewModel: TestViewModel by viewModels()

    @Suppress("DEPRECATION")
    private val testTypes: List<TestType>
        get() = (intent.extras?.getSerializable("test_types") as? List<*>)?.filterIsInstance<TestType>()
            ?: emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                Color.TRANSPARENT
            )
        )

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        kanaWords = sharedPrefs.getBoolean("kana_words", true)

        if (sharedPrefs.getBoolean("keep_on", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        testEngine = TestEngine(
            this,
            Database.getInstance(this),
            testTypes,
            viewModel::addGoodAnswerToHistory,
            viewModel::addWrongAnswerToHistory,
            viewModel::addUnknownAnswerToHistory
        )

        viewModel.resetHistory()

        if (savedInstanceState == null) {
            nextQuestion()
        } else {
            testEngine.loadState(savedInstanceState)
            viewModel.setQuestion(testEngine)
        }

        viewModel.setStats(testEngine.itemView.getStats())

        viewModel.initialize(testEngine)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val question = uiState.question

            TestScreen(
                title = if (question != null) stringResource(question.testType.toName()) else "",
                stats = uiState.stats,
                correctCount = uiState.correctCount,
                questionCount = uiState.questionCount,
                uniqueCorrectCount = uiState.uniqueCorrectCount,
                uniqueItemCount = uiState.uniqueItemCount,
                historyState = uiState.historyState,
                sheetExpanded = uiState.sheetExpanded,
                onSheetExpandedChange = { viewModel.setSheetExpanded(it) },
                kanaWords = kanaWords,
                onItemClick = this::openItemInDictionary,
                onBackClick = { confirmActivityClose() },
                onSwapLastAnswer = { viewModel.swapLastAnswer() },
            ) {
                if (question != null) {
                    TestContent(
                        question = question,
                        kanaWords = kanaWords,
                        onAnswer = viewModel::onAnswer,
                        onNextQuestion = this::nextQuestion,
                    )
                }
            }
        }
    }

    private fun openItemInDictionary(item: Item) {
        when (item.contents) {
            is Kanji -> startActivity<org.kaqui.itemdetails.KanjiDisplayActivity>("kanji_id" to item.id)
            is Word -> startActivity<org.kaqui.itemdetails.WordDisplayActivity>("word_id" to item.id)
            else -> { /* do nothing */
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        testEngine.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun confirmActivityClose() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_test_stop_title)
            .setMessage(R.string.confirm_test_stop_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun nextQuestion() {
        testEngine.prepareNewQuestion()
        viewModel.setQuestion(testEngine)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setStats(testEngine.itemView.getStats())
    }
}

@Composable
fun TestContent(
    question: TestQuestion,
    kanaWords: Boolean,
    onAnswer: (Certainty, Item?) -> Unit,
    onNextQuestion: () -> Unit,
) {
    when (question.testType) {
        TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.KATAKANA_TO_ROMAJI_TEXT ->
            TextTest(question, kanaWords, onAnswer, onNextQuestion)

        TestType.KANJI_COMPOSITION ->
            CompositionTest(question, kanaWords, onAnswer, onNextQuestion)

        TestType.HIRAGANA_DRAWING, TestType.KATAKANA_DRAWING, TestType.KANJI_DRAWING ->
            DrawingTest(question, kanaWords, onAnswer, onNextQuestion)

        else ->
            QuizTest(question, kanaWords, onAnswer, onNextQuestion)
    }
}

@Composable
fun QuizTest(
    question: TestQuestion,
    kanaWords: Boolean,
    onAnswer: (Certainty, Item?) -> Unit,
    onNextQuestion: () -> Unit,
) {
    val context = LocalContext.current
    val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val hideAnswers = remember { sharedPreferences.getBoolean("hide_answers", true) }
    val singleButtonMode = remember { sharedPreferences.getBoolean("single_button_mode", false) }

    // The engine never asks the same item twice in a row, so the item id identifies the question.
    // It also survives a rotation, unlike the debug data, which loadState does not restore.
    var answer by rememberSaveable(question.item.id) {
        mutableIntStateOf(QuizViewModel.NO_ANSWER)
    }
    var answersRevealed by rememberSaveable(question.item.id) { mutableStateOf(false) }

    val questionText = question.item.getQuestionText(question.testType, kanaWords)
    val uiState = QuizScreenUiState(
        questionText = questionText,
        answerOptions = question.answers.map { it.getAnswerText(question.testType, kanaWords) },
        answer = answer,
        correctAnswerIndex = question.answers.indexOfFirst { it.id == question.item.id },
        answersCurrentlyVisible = !hideAnswers || answersRevealed ||
                answer != QuizViewModel.NO_ANSWER,
        initialHideAnswers = hideAnswers,
        singleButtonMode = singleButtonMode,
        currentTestType = question.testType,
    )

    QuizTestScreenContent(
        uiState = uiState,
        onNextClicked = onNextQuestion,
        onAnswerSelected = { selectedIndex, certainty ->
            if (certainty == Certainty.DONTKNOW) {
                answer = QuizViewModel.DONT_KNOW
                onAnswer(Certainty.DONTKNOW, null)
            } else if (isCorrectAnswer(question, selectedIndex, kanaWords)) {
                onAnswer(certainty, null)
                onNextQuestion()
            } else {
                answer = selectedIndex
                onAnswer(Certainty.DONTKNOW, question.answers[selectedIndex])
            }
        },
        onShowAnswersClicked = { answersRevealed = true },
        onQuestionLongClick = {
            question.debugData?.let { showItemProbabilityData(context, questionText, it) }
        }
    )
}

// Answers that only look the same as the expected one still count as correct, otherwise items
// sharing a reading or a meaning would be impossible to answer.
private fun isCorrectAnswer(question: TestQuestion, selectedIndex: Int, kanaWords: Boolean): Boolean {
    val selected = question.answers[selectedIndex]
    return selected.id == question.item.id ||
            selected.getAnswerText(question.testType, kanaWords) ==
            question.item.getAnswerText(question.testType, kanaWords) ||
            selected.getQuestionText(question.testType, kanaWords) ==
            question.item.getQuestionText(question.testType, kanaWords)
}
