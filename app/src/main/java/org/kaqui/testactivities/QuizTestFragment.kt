package org.kaqui.testactivities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.kaqui.BetterButton
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.TestEngine
import org.kaqui.TypefaceManager
import org.kaqui.getColorFromAttr
import org.kaqui.model.Certainty
import org.kaqui.model.Item
import org.kaqui.model.TestType
import org.kaqui.model.getAnswerText
import org.kaqui.model.getQuestionText

data class QuizScreenUiState(
    val questionText: String = "",
    val answerOptions: List<String> = emptyList(),
    val answer: Int = QuizViewModel.NO_ANSWER,
    val correctAnswerIndex: Int? = null,
    val answersCurrentlyVisible: Boolean = true,
    val initialHideAnswers: Boolean = false,
    val singleButtonMode: Boolean = false,
    val currentTestType: TestType? = null,
) {
    val isAnswerGiven
        get() = answer != QuizViewModel.NO_ANSWER
}

data class OnAnswerProcessedEventParams(val certainty: Certainty, val questionItem: Item?)

class QuizViewModel : ViewModel() {
    companion object {
        const val NO_ANSWER = 0x1000
        const val DONT_KNOW = 0x1001
    }

    var uiState by mutableStateOf(QuizScreenUiState())
        private set

    private val _onAnswerProcessedEvent = MutableSharedFlow<OnAnswerProcessedEventParams>()
    val onAnswerProcessedEvent = _onAnswerProcessedEvent.asSharedFlow()

    private val _requestNextQuestionEvent = MutableSharedFlow<Unit>()
    val requestNextQuestionEvent = _requestNextQuestionEvent.asSharedFlow()

    private lateinit var testEngine: TestEngine
    private lateinit var testType: TestType
    private var kanaWordsPref: Boolean = false

    fun initialize(
        engine: TestEngine,
        type: TestType,
        initialHideAnswersPref: Boolean,
        singleButtonModePref: Boolean,
        kanaWordsPreference: Boolean
    ) {
        testEngine = engine
        testType = type
        kanaWordsPref = kanaWordsPreference
        uiState = uiState.copy(
            initialHideAnswers = initialHideAnswersPref,
            answersCurrentlyVisible = !initialHideAnswersPref,
            singleButtonMode = singleButtonModePref,
            currentTestType = type,
        )
        loadQuestionData()
    }

    private fun loadQuestionData() {
        val currentQuestion = testEngine.currentQuestion
        val currentAnswers = testEngine.currentAnswers

        uiState = uiState.copy(
            questionText = currentQuestion.getQuestionText(testType, kanaWordsPref),
            answerOptions = currentAnswers.map { it.getAnswerText(testType, kanaWordsPref) },
            answer = NO_ANSWER,
            correctAnswerIndex = currentAnswers.indexOfFirst { it.id == testEngine.currentQuestion.id },
            answersCurrentlyVisible = !uiState.initialHideAnswers || uiState.answer != NO_ANSWER
        )
    }

    fun onAnswerSelected(selectedIndex: Int, certainty: Certainty) {
        val currentQuestion = testEngine.currentQuestion
        val currentAnswers = testEngine.currentAnswers

        if (certainty != Certainty.DONTKNOW &&
            (currentAnswers[selectedIndex].id == currentQuestion.id ||
                    currentAnswers[selectedIndex].getAnswerText(
                        testType,
                        kanaWordsPref
                    ) == currentQuestion.getAnswerText(testType, kanaWordsPref) ||
                    currentAnswers[selectedIndex].getQuestionText(
                        testType,
                        kanaWordsPref
                    ) == currentQuestion.getQuestionText(testType, kanaWordsPref))
        ) {
            viewModelScope.launch {
                _onAnswerProcessedEvent.emit(OnAnswerProcessedEventParams(certainty, null))
                _requestNextQuestionEvent.emit(Unit)
            }
        } else {
            if (certainty == Certainty.DONTKNOW) {
                uiState = uiState.copy(answer = DONT_KNOW)
                viewModelScope.launch {
                    _onAnswerProcessedEvent.emit(
                        OnAnswerProcessedEventParams(
                            Certainty.DONTKNOW,
                            null
                        )
                    )
                }
            } else { // Wrong answer selected
                uiState = uiState.copy(answer = selectedIndex)
                viewModelScope.launch {
                    _onAnswerProcessedEvent.emit(
                        OnAnswerProcessedEventParams(
                            Certainty.DONTKNOW,
                            currentAnswers[selectedIndex]
                        )
                    )
                }
            }
        }
    }

    fun onNextClicked() {
        uiState = uiState.copy(answer = NO_ANSWER)
        viewModelScope.launch {
            _requestNextQuestionEvent.emit(Unit)
        }
    }

    fun onShowAnswersClicked() {
        uiState = uiState.copy(answersCurrentlyVisible = true)
    }

    fun refreshQuestionViewFromExternal() {
        loadQuestionData()
    }

    fun setTestEngine(testEngine: TestEngine) {
        this.testEngine = testEngine
    }
}

class QuizTestFragmentCompose : Fragment(), TestFragment {
    private val testFragmentHolderRef
        get() = (requireActivity() as TestFragmentHolder)

    private val viewModel: QuizViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (viewModel.uiState.currentTestType == null) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val initialHideAnswersPref = sharedPreferences.getBoolean("hide_answers", true)
            val singleButtonModePref = sharedPreferences.getBoolean("single_button_mode", false)
            val kanaWordsPref = sharedPreferences.getBoolean("kana_words", false)

            viewModel.initialize(
                testFragmentHolderRef.testEngine,
                testFragmentHolderRef.testType,
                initialHideAnswersPref,
                singleButtonModePref,
                kanaWordsPref
            )
        } else {
            viewModel.setTestEngine(testFragmentHolderRef.testEngine)
        }

        // Collect events from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.onAnswerProcessedEvent.collectLatest { params ->
                        testFragmentHolderRef.onAnswer(
                            null,
                            params.certainty,
                            params.questionItem
                        )
                    }
                }
                launch {
                    viewModel.requestNextQuestionEvent.collectLatest {
                        testFragmentHolderRef.nextQuestion()
                    }
                }
            }
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState = viewModel.uiState

                QuizTestScreenContent(
                    uiState = uiState,
                    onNextClicked = viewModel::onNextClicked,
                    onAnswerSelected = viewModel::onAnswerSelected,
                    onShowAnswersClicked = viewModel::onShowAnswersClicked,
                )
            }
        }
    }

    override fun refreshQuestion() {
        viewModel.refreshQuestionViewFromExternal()
    }

    override fun setSensible(e: Boolean) {
        // TODO
    }

    companion object {
        @JvmStatic
        fun newInstance() = QuizTestFragmentCompose()
    }
}

const val COLUMNS = 2

@Composable
fun QuizTestScreenContent(
    uiState: QuizScreenUiState,
    onNextClicked: () -> Unit,
    onAnswerSelected: (answerIndex: Int, certainty: Certainty) -> Unit,
    onShowAnswersClicked: () -> Unit
) {
    val singleButtonMode = uiState.singleButtonMode
    val initialHideAnswers = uiState.initialHideAnswers
    val answersCurrentlyVisible = uiState.answersCurrentlyVisible

    val questionMinSize =
        when (uiState.currentTestType) {
            TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING -> 50
            TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI -> 10
            TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> 50
            else -> throw RuntimeException("unsupported test type for TestActivity")
        }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TestQuestionLayoutCompose(
                question = uiState.questionText,
                questionMinSizeSp = questionMinSize
            ) {
                if (initialHideAnswers && !answersCurrentlyVisible && !uiState.isAnswerGiven) {
                    Button(
                        onClick = onShowAnswersClicked,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(LocalContext.current.getColorFromAttr(R.attr.backgroundDontKnow)),
                        ),
                    ) {
                        Text(stringResource(id = R.string.show_answers).uppercase())
                    }
                }

                if (answersCurrentlyVisible) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        val testType = uiState.currentTestType
                        when (testType) {
                            TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING -> {
                                uiState.answerOptions.forEachIndexed { index, answerText ->
                                    val backgroundColor = getButtonBackgroundColor(uiState, index)

                                    if (!singleButtonMode) {
                                        Separator()
                                    }

                                    AnswerRow(
                                        answerText = answerText,
                                        singleButtonMode = singleButtonMode,
                                        enabled = !uiState.isAnswerGiven,
                                        highlight = backgroundColor,
                                        onClick = { certainty ->
                                            onAnswerSelected(index, certainty)
                                        }
                                    )
                                }
                            }

                            TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI,
                            TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> {
                                uiState.answerOptions.chunked(COLUMNS)
                                    .forEachIndexed { rowIndex, rowItems ->
                                        if (!singleButtonMode)
                                            Separator()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                        ) {
                                            rowItems.forEachIndexed { columnIndex, answerText ->
                                                val originalIndex =
                                                    uiState.answerOptions.indexOf(answerText)
                                                val fontSize = when (testType) {
                                                    TestType.READING_TO_WORD, TestType.MEANING_TO_WORD -> 30.sp
                                                    else -> 50.sp
                                                }
                                                val index = rowIndex * COLUMNS + columnIndex
                                                val backgroundColor =
                                                    getButtonBackgroundColor(uiState, index)
                                                AnswerGridItem(
                                                    answerText = answerText,
                                                    singleButtonMode = singleButtonMode,
                                                    fontSize = fontSize,
                                                    enabled = !uiState.isAnswerGiven,
                                                    modifier = Modifier.weight(1f),
                                                    highlight = backgroundColor,
                                                    onClick = { certainty ->
                                                        if (!uiState.isAnswerGiven) {
                                                            onAnswerSelected(
                                                                originalIndex,
                                                                certainty
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                            }

                            else -> throw RuntimeException("unsupported test type $testType for QuizTest")
                        }

                        Separator()

                        if (!uiState.isAnswerGiven)
                            Button(
                                onClick = {
                                    onAnswerSelected(
                                        QuizViewModel.NO_ANSWER,
                                        Certainty.DONTKNOW
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(LocalContext.current.getColorFromAttr(R.attr.backgroundDontKnow)),
                                ),
                            ) {
                                Text(stringResource(id = R.string.dont_know).toUpperCase(Locale.current))
                            }
                        else
                            Button(
                                onClick = onNextClicked,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xd6, 0xd7, 0xd7),
                                ),
                            ) {
                                Text(stringResource(id = R.string.next).toUpperCase(Locale.current))
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun getButtonBackgroundColor(
    uiState: QuizScreenUiState,
    index: Int
): Color? {
    val backgroundColor = when {
        uiState.answer == QuizViewModel.NO_ANSWER -> null
        index == uiState.correctAnswerIndex -> Color(
            LocalContext.current.getColorFromAttr(
                R.attr.correctAnswerBackground
            )
        )

        index == uiState.answer -> Color(
            LocalContext.current.getColorFromAttr(
                R.attr.wrongAnswerBackground
            )
        )

        else -> null
    }
    return backgroundColor
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AnswerButton(
    onClick: () -> Unit,
    enabled: Boolean,
    textResId: Int,
    backgroundColorAttrResId: Int,
    modifier: Modifier = Modifier,
) {
    // Allows buttons to be thinner than the minimum height
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(
                    LocalContext.current.getColorFromAttr(
                        backgroundColorAttrResId
                    )
                ),
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = modifier,
        ) {
            Text(
                text = stringResource(id = textResId).toUpperCase(Locale.current),
                fontFamily = TypefaceManager.getTypeface(LocalContext.current)
                    ?.let { FontFamily(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SingleButtonAnswer(
    onClick: (Certainty) -> Unit,
    enabled: Boolean,
    highlight: Color?,
    answerText: String,
    textAlign: TextAlign,
    modifier: Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    // Allows buttons to be thinner than the minimum height
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        BetterButton(
            onClick = { onClick(Certainty.SURE) },
            onLongPress = { onClick(Certainty.MAYBE) },
            modifier = modifier,
            enabled = enabled,
            contentPadding = PaddingValues(8.dp),
            colors =
                if (highlight != null)
                    ButtonDefaults.buttonColors(disabledBackgroundColor = highlight)
                else
                    ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xd6, 0xd7, 0xd7),
                    ),
        ) {
            Text(
                text = answerText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = textAlign,
                fontSize = fontSize,
                fontFamily = TypefaceManager.getTypeface(LocalContext.current)?.let { FontFamily(it) }
            )
        }
    }
}

@Composable
fun AnswerRow(
    answerText: String,
    singleButtonMode: Boolean,
    enabled: Boolean,
    highlight: Color?,
    onClick: (Certainty) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!singleButtonMode && highlight != null)
                    Modifier.background(highlight)
                else
                    Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (singleButtonMode) {
            SingleButtonAnswer(
                onClick,
                enabled,
                highlight,
                answerText,
                TextAlign.Start,
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        } else {
            Text(
                text = answerText,
                modifier = Modifier.weight(1f),
                fontFamily = TypefaceManager.getTypeface(LocalContext.current)?.let { FontFamily(it) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            AnswerButton(
                onClick = { onClick(Certainty.MAYBE) },
                enabled = enabled,
                backgroundColorAttrResId = R.attr.backgroundMaybe,
                textResId = R.string.maybe,
                modifier = Modifier
                    .defaultMinSize(minWidth = 0.dp)
                    .padding(4.dp)
            )
            AnswerButton(
                onClick = { onClick(Certainty.SURE) },
                enabled = enabled,
                backgroundColorAttrResId = R.attr.backgroundSure,
                textResId = R.string.sure,
                modifier = Modifier
                    .defaultMinSize(minWidth = 0.dp)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun AnswerGridItem(
    answerText: String,
    singleButtonMode: Boolean,
    fontSize: TextUnit,
    enabled: Boolean,
    highlight: Color?,
    modifier: Modifier,
    onClick: (Certainty) -> Unit,
) {
    if (singleButtonMode) {
        SingleButtonAnswer(
            onClick = { certainty -> onClick(certainty) },
            enabled = enabled,
            fontSize = fontSize,
            highlight = highlight,
            answerText = answerText,
            textAlign = TextAlign.Center,
            modifier = modifier.padding(4.dp),
        )
    } else {
        Row(
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (highlight != null)
                        Modifier.background(highlight)
                    else
                        Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = answerText,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                fontFamily = TypefaceManager.getTypeface(LocalContext.current)?.let { FontFamily(it) },
                modifier = Modifier.weight(1f),
            )
            Column {
                AnswerButton(
                    onClick = { onClick(Certainty.SURE) },
                    enabled = enabled,
                    backgroundColorAttrResId = R.attr.backgroundSure,
                    textResId = R.string.sure,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
                        .padding(4.dp)
                )
                AnswerButton(
                    onClick = { onClick(Certainty.MAYBE) },
                    enabled = enabled,
                    backgroundColorAttrResId = R.attr.backgroundMaybe,
                    textResId = R.string.maybe,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
                        .padding(4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Quiz Screen Preview - Answers Shown")
@Composable
fun PreviewQuizTestScreenContentAnswersVisible() {
    val sampleUiState = QuizScreenUiState(
        questionText = "犬は何ですか？",
        answerOptions = listOf("Dog", "Cat", "Bird", "Fish"),
        correctAnswerIndex = 0,
        answer = QuizViewModel.NO_ANSWER,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = false,
        currentTestType = TestType.WORD_TO_MEANING,
    )

    MaterialTheme {
        QuizTestScreenContent(
            uiState = sampleUiState,
            onNextClicked = { },
            onAnswerSelected = { index, certainty -> },
            onShowAnswersClicked = { }
        )
    }
}

@Preview(showBackground = true, name = "Quiz Screen Preview - Meaning to word")
@Composable
fun PreviewQuizTestScreenContentMeaningToWord() {
    val sampleUiState = QuizScreenUiState(
        questionText = "test 123",
        answerOptions = listOf("Dog", "Cat", "Bird", "Fish"),
        correctAnswerIndex = 0,
        answer = QuizViewModel.NO_ANSWER,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = false,
        currentTestType = TestType.MEANING_TO_WORD,
    )

    MaterialTheme {
        QuizTestScreenContent(
            uiState = sampleUiState,
            onNextClicked = { },
            onAnswerSelected = { index, certainty -> },
            onShowAnswersClicked = { }
        )
    }
}

@Preview(showBackground = true, name = "Quiz Screen Preview - Answered Wrongly")
@Composable
fun PreviewQuizTestScreenContentAnsweredWrongly() {
    val sampleUiState = QuizScreenUiState(
        questionText = "鳥は何ですか？",
        answerOptions = listOf("Dog", "Cat", "Bird", "Fish"),
        correctAnswerIndex = 2,
        answer = 3,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = false,
        currentTestType = TestType.WORD_TO_MEANING,
    )

    MaterialTheme {
        QuizTestScreenContent(
            uiState = sampleUiState,
            onNextClicked = { },
            onAnswerSelected = { index, certainty -> },
            onShowAnswersClicked = { }
        )
    }
}

@Preview(showBackground = true, name = "Quiz Screen Preview - Single Button")
@Composable
fun PreviewQuizTestScreenContentSingleButton() {
    val sampleUiState = QuizScreenUiState(
        questionText = "鳥は何ですか？",
        answerOptions = listOf("Dog", "Cat", "Bird", "Fish"),
        correctAnswerIndex = 2,
        answer = QuizViewModel.NO_ANSWER,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = true,
        currentTestType = TestType.WORD_TO_MEANING,
    )

    MaterialTheme {
        QuizTestScreenContent(
            uiState = sampleUiState,
            onNextClicked = { },
            onAnswerSelected = { index, certainty -> },
            onShowAnswersClicked = { }
        )
    }
}

@Preview(showBackground = true, name = "Quiz Screen Preview - Single Button - Answered Wrongly")
@Composable
fun PreviewQuizTestScreenContentSingleButtonAnsweredWrongly() {
    val sampleUiState = QuizScreenUiState(
        questionText = "鳥は何ですか？",
        answerOptions = listOf("Dog\nBob", "Cat\nJam", "Bird", "Fish"),
        correctAnswerIndex = 2,
        answer = 3,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = true,
        currentTestType = TestType.WORD_TO_MEANING,
    )

    MaterialTheme {
        QuizTestScreenContent(
            uiState = sampleUiState,
            onNextClicked = { },
            onAnswerSelected = { index, certainty -> },
            onShowAnswersClicked = { }
        )
    }
}

@Preview(showBackground = true, name = "Quiz Screen Preview - Grid - Not Answered")
@Composable
fun PreviewQuizTestScreenContentGridNotAnswered() {
    val sampleUiState = QuizScreenUiState(
        questionText = "か",
        answerOptions = listOf("Dog", "Cat", "Bird", "Fish"),
        correctAnswerIndex = 2,
        answer = QuizViewModel.NO_ANSWER,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = false,
        currentTestType = TestType.HIRAGANA_TO_ROMAJI,
    )

    MaterialTheme {
        QuizTestScreenContent(
            uiState = sampleUiState,
            onNextClicked = { },
            onAnswerSelected = { index, certainty -> },
            onShowAnswersClicked = { }
        )
    }
}

@Preview(showBackground = true, name = "Quiz Screen Preview - Grid - Answered Wrongly")
@Composable
fun PreviewQuizTestScreenContentGridAnsweredWrongly() {
    val sampleUiState = QuizScreenUiState(
        questionText = "か",
        answerOptions = listOf("Dog", "Cat", "Bird", "Fish"),
        correctAnswerIndex = 2,
        answer = 3,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = false,
        currentTestType = TestType.HIRAGANA_TO_ROMAJI,
    )

    MaterialTheme {
        QuizTestScreenContent(
            uiState = sampleUiState,
            onNextClicked = { },
            onAnswerSelected = { index, certainty -> },
            onShowAnswersClicked = { }
        )
    }
}

@Preview(showBackground = true, name = "Quiz Screen Preview - Single Button - Answered Wrongly")
@Composable
fun PreviewQuizTestScreenContentGridSingleButtonAnsweredWrongly() {
    val sampleUiState = QuizScreenUiState(
        questionText = "鳥は何ですか？",
        answerOptions = listOf("Dog", "Cat", "Bird", "Fish"),
        correctAnswerIndex = 2,
        answer = 3,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = true,
        currentTestType = TestType.HIRAGANA_TO_ROMAJI,
    )

    MaterialTheme {
        QuizTestScreenContent(
            uiState = sampleUiState,
            onNextClicked = { },
            onAnswerSelected = { index, certainty -> },
            onShowAnswersClicked = { }
        )
    }
}
