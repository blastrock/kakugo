package org.kaqui.testactivities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import org.kaqui.BetterButton
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.TypefaceManager
import org.kaqui.model.Certainty
import org.kaqui.model.Item
import org.kaqui.model.TestType
import org.kaqui.model.getAnswerText
import org.kaqui.model.getQuestionText
import org.kaqui.showItemProbabilityData
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes

data class QuizScreenUiState(
    val questionText: String = "",
    val answerOptions: List<String> = emptyList(),
    val answer: Int = NO_ANSWER,
    val correctAnswerIndex: Int? = null,
    val answersCurrentlyVisible: Boolean = true,
    val initialHideAnswers: Boolean = false,
    val singleButtonMode: Boolean = false,
    val currentTestType: TestType? = null,
) {
    val isAnswerGiven
        get() = answer != NO_ANSWER
}

const val NO_ANSWER = 0x1000
const val DONT_KNOW = 0x1001

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
        mutableIntStateOf(NO_ANSWER)
    }
    var answersRevealed by rememberSaveable(question.item.id) { mutableStateOf(false) }

    val questionText = question.item.getQuestionText(question.testType, kanaWords)
    val uiState = QuizScreenUiState(
        questionText = questionText,
        answerOptions = question.answers.map { it.getAnswerText(question.testType, kanaWords) },
        answer = answer,
        correctAnswerIndex = question.answers.indexOfFirst { it.id == question.item.id },
        answersCurrentlyVisible = !hideAnswers || answersRevealed ||
                answer != NO_ANSWER,
        initialHideAnswers = hideAnswers,
        singleButtonMode = singleButtonMode,
        currentTestType = question.testType,
    )

    KakugoTheme {
        QuizTestScreenContent(
            uiState = uiState,
            onNextClicked = onNextQuestion,
            onAnswerSelected = { selectedIndex, certainty ->
                if (certainty == Certainty.DONTKNOW) {
                    answer = DONT_KNOW
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
}

// Answers that only look the same as the expected one still count as correct, otherwise items
// sharing a reading or a meaning would be impossible to answer.
private fun isCorrectAnswer(
    question: TestQuestion,
    selectedIndex: Int,
    kanaWords: Boolean,
): Boolean {
    val selected = question.answers[selectedIndex]
    return selected.id == question.item.id ||
            selected.getAnswerText(question.testType, kanaWords) ==
            question.item.getAnswerText(question.testType, kanaWords) ||
            selected.getQuestionText(question.testType, kanaWords) ==
            question.item.getQuestionText(question.testType, kanaWords)
}

private data class QuizLayout(
    val questionMinSizeSp: Int,
    val columns: Int,
    val answerFontSize: TextUnit,
    val answerTextAlign: TextAlign,
) {
    val isGrid get() = columns > 1
}

// Questions whose answer is a reading or a meaning show prose, which needs a full-width row,
// the others show a single word or character, which fits two per row at a large font size.
private fun quizLayout(testType: TestType?) =
    when (testType) {
        TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING ->
            QuizLayout(50, 1, TextUnit.Unspecified, TextAlign.Start)

        TestType.READING_TO_WORD, TestType.MEANING_TO_WORD ->
            QuizLayout(10, 2, 30.sp, TextAlign.Center)

        TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI ->
            QuizLayout(10, 2, 50.sp, TextAlign.Center)

        TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA ->
            QuizLayout(50, 2, 50.sp, TextAlign.Center)

        else -> throw RuntimeException("unsupported test type $testType for QuizTest")
    }

@Composable
fun QuizTestScreenContent(
    uiState: QuizScreenUiState,
    onNextClicked: () -> Unit,
    onAnswerSelected: (answerIndex: Int, certainty: Certainty) -> Unit,
    onShowAnswersClicked: () -> Unit,
    onQuestionLongClick: (() -> Unit)? = null,
) {
    val singleButtonMode = uiState.singleButtonMode
    val initialHideAnswers = uiState.initialHideAnswers
    val answersCurrentlyVisible = uiState.answersCurrentlyVisible
    val themeColors = LocalThemeAttributes.current

    val layout = quizLayout(uiState.currentTestType)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TestQuestionLayoutCompose(
            question = uiState.questionText,
            questionMinSizeSp = layout.questionMinSizeSp,
            onQuestionLongClick = onQuestionLongClick
        ) {
            if (initialHideAnswers && !answersCurrentlyVisible && !uiState.isAnswerGiven) {
                Button(
                    onClick = onShowAnswersClicked,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = themeColors.backgroundDontKnow,
                    ),
                ) {
                    Text(stringResource(id = R.string.show_answers).uppercase())
                }
            }

            if (answersCurrentlyVisible) {
                val scrollState = remember(uiState.questionText) { ScrollState(0) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    uiState.answerOptions.chunked(layout.columns)
                        .forEachIndexed { rowIndex, rowAnswers ->
                            if (!singleButtonMode)
                                Separator()

                            Row(
                                // Measuring at the tallest answer's height lets the single
                                // buttons of a row stretch to match each other.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                            ) {
                                rowAnswers.forEachIndexed { columnIndex, answerText ->
                                    val index = rowIndex * layout.columns + columnIndex
                                    AnswerCell(
                                        answerText = answerText,
                                        layout = layout,
                                        singleButtonMode = singleButtonMode,
                                        enabled = !uiState.isAnswerGiven,
                                        highlight = getButtonBackgroundColor(
                                            uiState,
                                            index,
                                            themeColors
                                        ),
                                        modifier = Modifier.weight(1f),
                                        onClick = { certainty ->
                                            onAnswerSelected(index, certainty)
                                        }
                                    )
                                }
                            }
                        }

                    Separator()

                    if (!uiState.isAnswerGiven)
                        Button(
                            onClick = {
                                onAnswerSelected(
                                    NO_ANSWER,
                                    Certainty.DONTKNOW
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = themeColors.backgroundDontKnow,
                            ),
                        ) {
                            Text(stringResource(id = R.string.dont_know).toUpperCase(Locale.current))
                        }
                    else
                        Button(
                            onClick = onNextClicked,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = themeColors.backgroundDontKnow,
                            ),
                        ) {
                            Text(stringResource(id = R.string.next).toUpperCase(Locale.current))
                        }
                }
            }
        }
    }
}

@Composable
private fun getButtonBackgroundColor(
    uiState: QuizScreenUiState,
    index: Int,
    themeColors: org.kaqui.theme.ThemeAttributes,
): Color? {
    val backgroundColor = when {
        uiState.answer == NO_ANSWER -> null
        index == uiState.correctAnswerIndex -> themeColors.correctAnswerBackground

        index == uiState.answer -> themeColors.wrongAnswerBackground

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
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    // Allows buttons to be thinner than the minimum height
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = backgroundColor,
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = modifier,
        ) {
            Text(
                text = stringResource(id = textResId).toUpperCase(Locale.current),
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
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    disabledBackgroundColor = highlight ?: MaterialTheme.colors.onSurface
                        .copy(alpha = 0.12f)
                        .compositeOver(MaterialTheme.colors.surface),
                ),
        ) {
            Text(
                text = answerText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = textAlign,
                fontSize = fontSize,
                lineHeight = 1.2.em,
                fontFamily = TypefaceManager.getTypeface(LocalContext.current)
                    ?.let { FontFamily(it) }
            )
        }
    }
}

@Composable
private fun AnswerCell(
    answerText: String,
    layout: QuizLayout,
    singleButtonMode: Boolean,
    enabled: Boolean,
    highlight: Color?,
    modifier: Modifier,
    onClick: (Certainty) -> Unit,
) {
    if (singleButtonMode) {
        SingleButtonAnswer(
            onClick = onClick,
            enabled = enabled,
            highlight = highlight,
            answerText = answerText,
            textAlign = layout.answerTextAlign,
            fontSize = layout.answerFontSize,
            modifier = modifier
                .fillMaxHeight()
                .padding(
                    if (layout.isGrid) PaddingValues(4.dp) else PaddingValues(vertical = 4.dp)
                ),
        )
    } else {
        TwoButtonAnswer(
            answerText = answerText,
            layout = layout,
            enabled = enabled,
            highlight = highlight,
            modifier = modifier,
            onClick = onClick,
        )
    }
}

@Composable
private fun TwoButtonAnswer(
    answerText: String,
    layout: QuizLayout,
    enabled: Boolean,
    highlight: Color?,
    modifier: Modifier,
    onClick: (Certainty) -> Unit,
) {
    val themeColors = LocalThemeAttributes.current

    val maybeButton: @Composable () -> Unit = {
        AnswerButton(
            onClick = { onClick(Certainty.MAYBE) },
            enabled = enabled,
            backgroundColor = themeColors.backgroundMaybe,
            textResId = R.string.maybe,
            modifier = Modifier
                .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
                .padding(4.dp)
        )
    }
    val sureButton: @Composable () -> Unit = {
        AnswerButton(
            onClick = { onClick(Certainty.SURE) },
            enabled = enabled,
            backgroundColor = themeColors.backgroundSure,
            textResId = R.string.sure,
            modifier = Modifier
                .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
                .padding(4.dp)
        )
    }

    Row(
        modifier = modifier
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
            fontSize = layout.answerFontSize,
            lineHeight = 1.2.em,
            textAlign = layout.answerTextAlign,
            fontFamily = TypefaceManager.getTypeface(LocalContext.current)?.let { FontFamily(it) },
            modifier = Modifier.weight(1f),
        )
        // A grid cell is too narrow to fit both buttons side by side.
        if (layout.isGrid) {
            Column {
                sureButton()
                maybeButton()
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            maybeButton()
            sureButton()
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
        answer = NO_ANSWER,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = false,
        currentTestType = TestType.WORD_TO_MEANING,
    )

    KakugoTheme {
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
        answer = NO_ANSWER,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = false,
        currentTestType = TestType.MEANING_TO_WORD,
    )

    KakugoTheme {
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

    KakugoTheme {
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
        answer = NO_ANSWER,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = true,
        currentTestType = TestType.WORD_TO_MEANING,
    )

    KakugoTheme {
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

    KakugoTheme {
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
        answer = NO_ANSWER,
        answersCurrentlyVisible = true,
        initialHideAnswers = true,
        singleButtonMode = false,
        currentTestType = TestType.HIRAGANA_TO_ROMAJI,
    )

    KakugoTheme {
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

    KakugoTheme {
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

    KakugoTheme {
        QuizTestScreenContent(
            uiState = sampleUiState,
            onNextClicked = { },
            onAnswerSelected = { index, certainty -> },
            onShowAnswersClicked = { }
        )
    }
}
