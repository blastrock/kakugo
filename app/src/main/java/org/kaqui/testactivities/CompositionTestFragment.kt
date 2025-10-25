package org.kaqui.testactivities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
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
import org.kaqui.TypefaceManager
import org.kaqui.showItemProbabilityData
import org.kaqui.model.Certainty
import org.kaqui.model.Kanji
import org.kaqui.model.TestType
import org.kaqui.model.getAnswerText
import org.kaqui.model.getQuestionText
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes

enum class ButtonValidationState {
    NONE,           // Not validated yet
    CORRECT,        // Correct and selected
    WRONG_SELECTED, // Wrong but selected
    WRONG_NOT_SELECTED // Correct but not selected
}

data class CompositionTestUiState(
    val questionText: String = "",
    val answerOptions: List<String> = emptyList(),
    val selectedIndices: Set<Int> = emptySet(),
    val isValidated: Boolean = false,
    val validationResults: Map<Int, ButtonValidationState> = emptyMap(),
    val currentTestType: TestType? = null,
) {
    val isAnySelected
        get() = selectedIndices.isNotEmpty()
    val showNextButton
        get() = isValidated
}

class CompositionViewModel : ViewModel() {
    var uiState by mutableStateOf(CompositionTestUiState())
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
        val currentAnswers = testEngine.currentAnswers

        uiState = uiState.copy(
            questionText = currentQuestion.getQuestionText(testType, kanaWordsPref),
            answerOptions = currentAnswers.map { it.getAnswerText(testType, kanaWordsPref) },
            selectedIndices = emptySet(),
            isValidated = false,
            validationResults = emptyMap()
        )
    }

    fun onToggleAnswer(index: Int) {
        if (uiState.isValidated) return

        val newSelectedIndices = if (index in uiState.selectedIndices) {
            uiState.selectedIndices - index
        } else {
            uiState.selectedIndices + index
        }
        uiState = uiState.copy(selectedIndices = newSelectedIndices)
    }

    fun onDoneClicked() {
        val result = validateAnswer()

        uiState = uiState.copy(isValidated = true)

        viewModelScope.launch {
            _onAnswerProcessedEvent.emit(OnAnswerProcessedEventParams(result, null))
        }
    }

    fun onDontKnowClicked() {
        // Clear all selections and then validate
        uiState = uiState.copy(selectedIndices = emptySet())
        onDoneClicked()
    }

    fun onNextClicked() {
        viewModelScope.launch {
            _requestNextQuestionEvent.emit(Unit)
        }
    }

    private fun validateAnswer(): Certainty {
        val currentKanji = testEngine.currentQuestion.contents as Kanji
        val correctPartIds = currentKanji.parts.map { it.id }.toSet()
        val currentAnswers = testEngine.currentAnswers

        val validationResults = mutableMapOf<Int, ButtonValidationState>()
        var allCorrect = true

        currentAnswers.forEachIndexed { index, answer ->
            val isSelected = index in uiState.selectedIndices
            val isCorrect = answer.id in correctPartIds

            validationResults[index] = when {
                isSelected && isCorrect -> ButtonValidationState.CORRECT
                isSelected && !isCorrect -> {
                    allCorrect = false
                    ButtonValidationState.WRONG_SELECTED
                }
                !isSelected && isCorrect -> {
                    allCorrect = false
                    ButtonValidationState.WRONG_NOT_SELECTED
                }
                else -> ButtonValidationState.NONE
            }
        }

        uiState = uiState.copy(validationResults = validationResults)

        return if (allCorrect) Certainty.SURE else Certainty.DONTKNOW
    }

    fun refreshQuestionViewFromExternal() {
        loadQuestionData()
    }

    fun setTestEngine(testEngine: TestEngine) {
        this.testEngine = testEngine
    }
}

class CompositionTestFragmentCompose : Fragment(), TestFragment {
    private val testFragmentHolderRef
        get() = (requireActivity() as TestFragmentHolder)

    private val viewModel: CompositionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (viewModel.uiState.currentTestType == null) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val kanaWordsPref = sharedPreferences.getBoolean("kana_words", false)

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

                CompositionTestScreenContent(
                    uiState = uiState,
                    onToggleAnswer = viewModel::onToggleAnswer,
                    onDoneClicked = viewModel::onDoneClicked,
                    onDontKnowClicked = viewModel::onDontKnowClicked,
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
        // Button state is handled by Compose UI state
    }

    companion object {
        @JvmStatic
        fun newInstance() = CompositionTestFragmentCompose()
    }
}

const val COMPOSITION_COLUMNS = 3

@Composable
fun CompositionTestScreenContent(
    uiState: CompositionTestUiState,
    onToggleAnswer: (Int) -> Unit,
    onDoneClicked: () -> Unit,
    onDontKnowClicked: () -> Unit,
    onNextClicked: () -> Unit,
    onQuestionLongClick: (() -> Unit)? = null
) {
    val questionMinSize = 10
    val themeColors = LocalThemeAttributes.current

    KakugoTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TestQuestionLayoutCompose(
                question = uiState.questionText,
                questionMinSizeSp = questionMinSize,
                onQuestionLongClick = onQuestionLongClick
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Grid of toggle buttons (3x3)
                    uiState.answerOptions.chunked(COMPOSITION_COLUMNS)
                        .forEachIndexed { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEachIndexed { columnIndex, answerText ->
                                    val index = rowIndex * COMPOSITION_COLUMNS + columnIndex
                                    val isSelected = index in uiState.selectedIndices
                                    val validationState =
                                        uiState.validationResults[index] ?: ButtonValidationState.NONE

                                    CompositionToggleButton(
                                        text = answerText,
                                        isSelected = isSelected,
                                        validationState = validationState,
                                        enabled = !uiState.isValidated,
                                        onClick = { onToggleAnswer(index) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                    // Bottom buttons
                    if (!uiState.showNextButton) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onDoneClicked,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = themeColors.backgroundSure
                                )
                            ) {
                                Text(stringResource(id = R.string.answerDone).toUpperCase(Locale.current))
                            }

                            Button(
                                onClick = onDontKnowClicked,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = themeColors.backgroundDontKnow
                                )
                            ) {
                                Text(stringResource(id = R.string.dont_know).toUpperCase(Locale.current))
                            }
                        }
                    } else {
                        Button(
                            onClick = onNextClicked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
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
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CompositionToggleButton(
    text: String,
    isSelected: Boolean,
    validationState: ButtonValidationState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeColors = LocalThemeAttributes.current

    val backgroundColor = when (validationState) {
        ButtonValidationState.CORRECT -> themeColors.compositionGood
        ButtonValidationState.WRONG_SELECTED -> themeColors.compositionBadSelected
        ButtonValidationState.WRONG_NOT_SELECTED -> themeColors.compositionBadNotSelected
        ButtonValidationState.NONE -> {
            if (isSelected) {
                themeColors.compositionGood
            } else {
                themeColors.backgroundDontKnow
            }
        }
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .padding(4.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = backgroundColor,
                disabledBackgroundColor = backgroundColor
            ),
            contentPadding = PaddingValues(8.dp)
        ) {
            Text(
                text = text,
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                fontFamily = TypefaceManager.getTypeface(context)?.let { FontFamily(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true, name = "Composition Test - Initial State")
@Composable
fun PreviewCompositionTestInitialState() {
    val sampleUiState = CompositionTestUiState(
        questionText = "木",
        answerOptions = listOf("一", "二", "三", "木", "林", "森", "火", "水", "土"),
        selectedIndices = emptySet(),
        isValidated = false,
        validationResults = emptyMap(),
        currentTestType = TestType.KANJI_COMPOSITION
    )

    CompositionTestScreenContent(
        uiState = sampleUiState,
        onToggleAnswer = {},
        onDoneClicked = {},
        onDontKnowClicked = {},
        onNextClicked = {}
    )
}

@Preview(showBackground = true, name = "Composition Test - Some Selected")
@Composable
fun PreviewCompositionTestSomeSelected() {
    val sampleUiState = CompositionTestUiState(
        questionText = "林",
        answerOptions = listOf("一", "二", "三", "木", "林", "森", "火", "水", "土"),
        selectedIndices = setOf(3, 4),
        isValidated = false,
        validationResults = emptyMap(),
        currentTestType = TestType.KANJI_COMPOSITION
    )

    CompositionTestScreenContent(
        uiState = sampleUiState,
        onToggleAnswer = {},
        onDoneClicked = {},
        onDontKnowClicked = {},
        onNextClicked = {}
    )
}

@Preview(showBackground = true, name = "Composition Test - Validated Correct")
@Composable
fun PreviewCompositionTestValidatedCorrect() {
    val sampleUiState = CompositionTestUiState(
        questionText = "林",
        answerOptions = listOf("一", "二", "三", "木", "林", "森", "火", "水", "土"),
        selectedIndices = setOf(3, 3),
        isValidated = true,
        validationResults = mapOf(
            3 to ButtonValidationState.CORRECT,
        ),
        currentTestType = TestType.KANJI_COMPOSITION
    )

    CompositionTestScreenContent(
        uiState = sampleUiState,
        onToggleAnswer = {},
        onDoneClicked = {},
        onDontKnowClicked = {},
        onNextClicked = {}
    )
}

@Preview(showBackground = true, name = "Composition Test - Validated With Errors")
@Composable
fun PreviewCompositionTestValidatedWithErrors() {
    val sampleUiState = CompositionTestUiState(
        questionText = "林",
        answerOptions = listOf("一", "二", "三", "木", "林", "森", "火", "水", "土"),
        selectedIndices = setOf(2, 3, 4),
        isValidated = true,
        validationResults = mapOf(
            2 to ButtonValidationState.WRONG_SELECTED,    // Wrong but selected
            3 to ButtonValidationState.CORRECT,           // Correct and selected
            4 to ButtonValidationState.WRONG_SELECTED,    // Wrong but selected
            5 to ButtonValidationState.WRONG_NOT_SELECTED // Should have been selected
        ),
        currentTestType = TestType.KANJI_COMPOSITION
    )

    CompositionTestScreenContent(
        uiState = sampleUiState,
        onToggleAnswer = {},
        onDoneClicked = {},
        onDontKnowClicked = {},
        onNextClicked = {}
    )
}
