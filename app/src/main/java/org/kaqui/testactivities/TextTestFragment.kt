package org.kaqui.testactivities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
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
import org.kaqui.R
import org.kaqui.TestEngine
import org.kaqui.model.Certainty
import org.kaqui.showItemProbabilityData
import org.kaqui.model.Kana
import org.kaqui.model.TestType
import org.kaqui.model.getQuestionText
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes
import java.util.Locale as JavaLocale

data class TextTestUiState(
    val questionText: String = "",
    val userInputText: String = "",
    val isAnswered: Boolean = false,
    val correctAnswer: String = "",
    val showCorrectAnswer: Boolean = false,
    val currentTestType: TestType? = null
)

class TextViewModel : ViewModel() {
    var uiState by mutableStateOf(TextTestUiState())
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
        kanaWordsPreference: Boolean
    ) {
        testEngine = engine
        testType = type
        kanaWordsPref = kanaWordsPreference
        uiState = uiState.copy(currentTestType = type)
        loadQuestionData()
    }

    private fun loadQuestionData() {
        val currentQuestion = testEngine.currentQuestion

        uiState = uiState.copy(
            questionText = currentQuestion.getQuestionText(testType, kanaWordsPref),
            userInputText = "",
            isAnswered = false,
            correctAnswer = "",
            showCorrectAnswer = false
        )
    }

    fun onUserInputChanged(newInput: String) {
        if (!uiState.isAnswered) {
            uiState = uiState.copy(userInputText = newInput)
        }
    }

    fun onAnswerSubmitted(certainty: Certainty) {
        if (uiState.isAnswered) {
            // If already answered, treat as next click
            onNextClicked()
            return
        }

        val currentKana = testEngine.currentQuestion.contents as Kana

        val result = if (certainty == Certainty.DONTKNOW) {
            // Clear input and show correct answer
            uiState = uiState.copy(
                userInputText = "",
                isAnswered = true,
                correctAnswer = currentKana.romaji,
                showCorrectAnswer = true
            )
            Certainty.DONTKNOW
        } else {
            val userAnswer = uiState.userInputText.trim().lowercase(JavaLocale.ROOT)

            if (userAnswer.isBlank()) {
                return // Don't process empty answers
            }

            if (userAnswer == currentKana.romaji) {
                // Correct answer - emit event and request next question
                viewModelScope.launch {
                    _onAnswerProcessedEvent.emit(OnAnswerProcessedEventParams(certainty, null))
                    _requestNextQuestionEvent.emit(Unit)
                }
                return
            } else {
                // Wrong answer - show correct answer
                uiState = uiState.copy(
                    isAnswered = true,
                    correctAnswer = currentKana.romaji,
                    showCorrectAnswer = true
                )
                Certainty.DONTKNOW
            }
        }

        viewModelScope.launch {
            _onAnswerProcessedEvent.emit(OnAnswerProcessedEventParams(result, null))
        }
    }

    fun onNextClicked() {
        viewModelScope.launch {
            _requestNextQuestionEvent.emit(Unit)
        }
    }

    fun refreshQuestionViewFromExternal() {
        loadQuestionData()
    }

    fun setTestEngine(testEngine: TestEngine) {
        this.testEngine = testEngine
    }
}

class TextTestFragmentCompose : Fragment(), TestFragment {
    private val testFragmentHolderRef
        get() = (requireActivity() as TestFragmentHolder)

    private val viewModel: TextViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (viewModel.uiState.currentTestType == null) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val kanaWordsPref = sharedPreferences.getBoolean("kana_words", true)

            viewModel.initialize(
                testFragmentHolderRef.testEngine,
                testFragmentHolderRef.testType,
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

                TextTestScreenContent(
                    uiState = uiState,
                    onUserInputChanged = viewModel::onUserInputChanged,
                    onAnswerSubmitted = viewModel::onAnswerSubmitted,
                    onNextClicked = viewModel::onNextClicked,
                    onQuestionLongClick = {
                        testFragmentHolderRef.testEngine.currentDebugData?.let { data ->
                            showItemProbabilityData(
                                requireContext(),
                                testFragmentHolderRef.testEngine.currentQuestion.getQuestionText(
                                    testFragmentHolderRef.testType,
                                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                                        .getBoolean("kana_words", true)
                                ),
                                data
                            )
                        }
                    }
                )
            }
        }
    }

    override fun refreshQuestion() {
        viewModel.refreshQuestionViewFromExternal()
    }

    override fun setSensible(e: Boolean) {
        // TODO: Implement if needed
    }

    companion object {
        @JvmStatic
        fun newInstance() = TextTestFragmentCompose()
    }
}

@Composable
fun TextTestScreenContent(
    uiState: TextTestUiState,
    onUserInputChanged: (String) -> Unit,
    onAnswerSubmitted: (Certainty) -> Unit,
    onNextClicked: () -> Unit,
    onQuestionLongClick: (() -> Unit)? = null
) {
    val questionMinSize = 30
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val themeColors = LocalThemeAttributes.current

    // Auto-focus the text field and keep keyboard open
    LaunchedEffect(uiState.isAnswered, uiState.questionText) {
        focusRequester.requestFocus()
        if (uiState.isAnswered) {
            // Keep keyboard open even when answer is wrong
            keyboardController?.show()
        }
    }

    KakugoTheme {
        TestQuestionLayoutCompose(
            question = uiState.questionText,
            questionMinSizeSp = questionMinSize,
            forceLandscape = true,
            onQuestionLongClick = onQuestionLongClick
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Answer input field (always shown to keep keyboard open)
                OutlinedTextField(
                    value = uiState.userInputText,
                    onValueChange = onUserInputChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .focusRequester(focusRequester),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onAnswerSubmitted(Certainty.SURE)
                        }
                    ),
                    colors = if (uiState.isAnswered) {
                        TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = themeColors.wrongAnswerBackground
                        )
                    } else {
                        TextFieldDefaults.outlinedTextFieldColors()
                    },
                    singleLine = false
                )

                // Correct answer display (shown when wrong)
                if (uiState.showCorrectAnswer) {
                    Text(
                        text = uiState.correctAnswer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(themeColors.correctAnswerBackground)
                            .padding(8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp
                    )
                }

                if (!uiState.isAnswered) {
                    // Maybe and Sure buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            //.padding(vertical = 4.dp)
                    ) {
                        Button(
                            onClick = { onAnswerSubmitted(Certainty.MAYBE) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = themeColors.backgroundMaybe
                            )
                        ) {
                            Text(stringResource(id = R.string.maybe).toUpperCase(Locale.current))
                        }

                        Button(
                            onClick = { onAnswerSubmitted(Certainty.SURE) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = themeColors.backgroundSure
                            )
                        ) {
                            Text(stringResource(id = R.string.sure).toUpperCase(Locale.current))
                        }
                    }

                    // Don't Know button
                    Button(
                        onClick = { onAnswerSubmitted(Certainty.DONTKNOW) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = themeColors.backgroundDontKnow
                        )
                    ) {
                        Text(stringResource(id = R.string.dont_know).toUpperCase(Locale.current))
                    }
                } else {
                    // Next button (shown when answered)
                    Button(
                        onClick = onNextClicked,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xd6, 0xd7, 0xd7)
                        )
                    ) {
                        Text(stringResource(id = R.string.next).toUpperCase(Locale.current))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Text Test Screen - Not Answered")
@Composable
fun PreviewTextTestScreenContentNotAnswered() {
    val sampleUiState = TextTestUiState(
        questionText = "あ",
        userInputText = "",
        isAnswered = false,
        correctAnswer = "",
        showCorrectAnswer = false,
        currentTestType = TestType.HIRAGANA_TO_ROMAJI_TEXT
    )

    TextTestScreenContent(
        uiState = sampleUiState,
        onUserInputChanged = {},
        onAnswerSubmitted = {},
        onNextClicked = {}
    )
}

@Preview(showBackground = true, name = "Text Test Screen - Wrong Answer")
@Composable
fun PreviewTextTestScreenContentWrongAnswer() {
    val sampleUiState = TextTestUiState(
        questionText = "あ",
        userInputText = "wrong",
        isAnswered = true,
        correctAnswer = "a",
        showCorrectAnswer = true,
        currentTestType = TestType.HIRAGANA_TO_ROMAJI_TEXT
    )

    TextTestScreenContent(
        uiState = sampleUiState,
        onUserInputChanged = {},
        onAnswerSubmitted = {},
        onNextClicked = {}
    )
}

@Preview(showBackground = true, name = "Text Test Screen - With Input")
@Composable
fun PreviewTextTestScreenContentWithInput() {
    val sampleUiState = TextTestUiState(
        questionText = "か",
        userInputText = "ka",
        isAnswered = false,
        correctAnswer = "",
        showCorrectAnswer = false,
        currentTestType = TestType.HIRAGANA_TO_ROMAJI_TEXT
    )

    TextTestScreenContent(
        uiState = sampleUiState,
        onUserInputChanged = {},
        onAnswerSubmitted = {},
        onNextClicked = {}
    )
}
