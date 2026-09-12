package org.kaqui.testactivities

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
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kaqui.R
import org.kaqui.model.Certainty
import org.kaqui.model.Item
import org.kaqui.model.Kana
import org.kaqui.model.TestType
import org.kaqui.model.getQuestionText
import org.kaqui.showItemProbabilityData
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

@Composable
fun TextTest(
    question: TestQuestion,
    kanaWords: Boolean,
    onAnswer: (Certainty, Item?) -> Unit,
    onNextQuestion: () -> Unit,
) {
    val context = LocalContext.current

    var userInput by rememberSaveable(question.item.id) { mutableStateOf("") }
    var isAnswered by rememberSaveable(question.item.id) { mutableStateOf(false) }

    val questionText = question.item.getQuestionText(question.testType, kanaWords)
    val correctAnswer = (question.item.contents as Kana).romaji

    val uiState = TextTestUiState(
        questionText = questionText,
        userInputText = userInput,
        isAnswered = isAnswered,
        correctAnswer = if (isAnswered) correctAnswer else "",
        showCorrectAnswer = isAnswered,
        currentTestType = question.testType,
    )

    TextTestScreenContent(
        uiState = uiState,
        onUserInputChanged = { if (!isAnswered) userInput = it },
        onAnswerSubmitted = { certainty ->
            if (isAnswered) {
                onNextQuestion()
            } else if (certainty == Certainty.DONTKNOW) {
                userInput = ""
                isAnswered = true
                onAnswer(Certainty.DONTKNOW, null)
            } else {
                val userAnswer = userInput.trim().lowercase(JavaLocale.ROOT)
                if (userAnswer.isNotBlank()) {
                    if (userAnswer == correctAnswer) {
                        onAnswer(certainty, null)
                        onNextQuestion()
                    } else {
                        isAnswered = true
                        onAnswer(Certainty.DONTKNOW, null)
                    }
                }
            }
        },
        onNextClicked = onNextQuestion,
        onQuestionLongClick = {
            question.debugData?.let { showItemProbabilityData(context, questionText, it) }
        }
    )
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
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
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
