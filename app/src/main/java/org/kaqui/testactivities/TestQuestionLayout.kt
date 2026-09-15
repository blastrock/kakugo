package org.kaqui.testactivities

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kaqui.TypefaceManager
import org.kaqui.theme.KakugoLandscapePreview
import org.kaqui.theme.KakugoTheme
import kotlin.math.floor

private val QUESTION_FONT_STEP = 10.sp
private const val QUESTION_MAX_HEIGHT_FRACTION = 0.4f
private const val TALL_SCREEN_HEIGHT_DP = 1000

// -1 is against the top, 0 is centered, 1 is against the bottom
private val PORTRAIT_CONTENT_ALIGNMENT = BiasAlignment.Vertical(-0.4f)

enum class QuestionAutoSize {
    FitBounds,
    AvoidWrapping,
}

// Sizes the text on the lines it already has, so it only wraps once even the smallest font size
// cannot keep it unwrapped, in which case it is sized like FitBounds.
private data class AvoidWrappingAutoSize(
    private val minFontSize: TextUnit,
    private val maxFontSize: TextUnit,
    private val stepSize: TextUnit,
) : TextAutoSize {
    private val wrappingAutoSize = TextAutoSize.StepBased(minFontSize, maxFontSize, stepSize)

    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString,
    ): TextUnit {
        val unwrappedLineCount = text.text.count { it == '\n' } + 1

        fun fitsUnwrapped(fontSize: Float) =
            performLayout(constraints, text, fontSize.toSp()).fitsUnwrapped(unwrappedLineCount)

        val stepSize = stepSize.toPx()
        val smallest = minFontSize.toPx()
        val largest = maxFontSize.toPx()

        if (!fitsUnwrapped(smallest))
            return with(wrappingAutoSize) { getFontSize(constraints, text) }

        var min = smallest
        var max = largest
        var current = (min + max) / 2

        while ((max - min) >= stepSize) {
            if (fitsUnwrapped(current))
                min = current
            else
                max = current
            current = (min + max) / 2
        }
        // used size minus minFontSize must be divisible by stepSize
        current = floor((min - smallest) / stepSize) * stepSize + smallest

        // We have found a size that fits, but we can still try one step up
        if ((current + stepSize) <= largest && fitsUnwrapped(current + stepSize))
            current += stepSize

        return current.toSp()
    }

    private fun TextLayoutResult.fitsUnwrapped(unwrappedLineCount: Int) =
        !hasVisualOverflow && lineCount <= unwrappedLineCount
}

@Composable
private fun QuestionText(
    question: String,
    minFontSize: TextUnit,
    maxFontSize: TextUnit,
    autoSize: QuestionAutoSize,
    onQuestionLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val fontFamily = TypefaceManager.getTypeface(LocalContext.current)?.let { FontFamily(it) }
    BasicText(
        text = question,
        modifier = modifier
            .let { base ->
                if (onQuestionLongClick != null)
                    base.pointerInput(onQuestionLongClick) {
                        detectTapGestures(onLongPress = { onQuestionLongClick() })
                    }
                else
                    base
            }
            .fillMaxWidth()
            .wrapContentHeight(Alignment.CenterVertically),
        style = TextStyle(
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.9f),
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        ),
        autoSize = when (autoSize) {
            QuestionAutoSize.FitBounds ->
                TextAutoSize.StepBased(minFontSize, maxFontSize, QUESTION_FONT_STEP)

            QuestionAutoSize.AvoidWrapping ->
                AvoidWrappingAutoSize(minFontSize, maxFontSize, QUESTION_FONT_STEP)
        },
    )
}

@Composable
fun TestQuestionLayoutCompose(
    question: String,
    questionMinFontSize: TextUnit,
    questionMaxFontSize: TextUnit,
    questionAutoSize: QuestionAutoSize,
    forceLandscape: Boolean = false,
    onQuestionLongClick: (() -> Unit)? = null,
    answersBlock: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val windowInfo = LocalWindowInfo.current

    val density = LocalDensity.current
    val screenWidthDp = with(density) { (windowInfo.containerSize.width / this.density).toInt() }
    val screenHeightDp = with(density) { (windowInfo.containerSize.height / this.density).toInt() }

    if (forceLandscape || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuestionText(
                question = question,
                minFontSize = questionMinFontSize,
                maxFontSize = questionMaxFontSize,
                autoSize = questionAutoSize,
                onQuestionLongClick = onQuestionLongClick,
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
            )

            val answerHeightMod = { modifier: Modifier ->
                if (screenWidthDp >= 1000)
                    modifier.width(500.dp - 16.dp)
                else
                    modifier.fillMaxHeight()
            }

            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .let(answerHeightMod),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                answersBlock()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(PORTRAIT_CONTENT_ALIGNMENT),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val extraQuestionPadding = if (screenHeightDp > TALL_SCREEN_HEIGHT_DP) 64.dp else 0.dp

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                QuestionText(
                    question = question,
                    minFontSize = questionMinFontSize,
                    maxFontSize = questionMaxFontSize,
                    autoSize = questionAutoSize,
                    onQuestionLongClick = onQuestionLongClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * QUESTION_MAX_HEIGHT_FRACTION)
                        .padding(vertical = 32.dp + extraQuestionPadding),
                )
            }

            val answerWidthMod = { modifier: Modifier ->
                if (screenWidthDp >= 500)
                    modifier.width(500.dp - 32.dp)
                else
                    modifier.fillMaxWidth()
            }

            Column(
                modifier = Modifier
                    .let(answerWidthMod),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                answersBlock()
            }
        }
    }
}

@Composable
private fun PreviewBlock(color: Color, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(height)
            .background(color),
    )
}

@Composable
private fun PreviewAnswer(text: String) {
    OutlinedButton(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(backgroundColor = Color.Transparent),
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "light", showBackground = true, widthDp = 400, heightDp = 700)
@Preview(
    name = "dark",
    showBackground = true,
    widthDp = 400,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PreviewTestQuestionLayoutBlocks() {
    KakugoTheme {
        TestQuestionLayoutCompose(
            question = "質問",
            questionMinFontSize = 50.sp,
            questionMaxFontSize = 120.sp,
            questionAutoSize = QuestionAutoSize.AvoidWrapping,
        ) {
            PreviewBlock(Color(0xFFE57373), 100.dp)
            PreviewBlock(Color(0xFF81C784), 100.dp)
            PreviewBlock(Color(0xFF64B5F6), 100.dp)
        }
    }
}

@Composable
private fun PreviewTestQuestionLayoutAnswersContent() {
    KakugoTheme {
        TestQuestionLayoutCompose(
            question = "華やか",
            questionMinFontSize = 10.sp,
            questionMaxFontSize = 120.sp,
            questionAutoSize = QuestionAutoSize.AvoidWrapping,
        ) {
            for (answer in listOf("はなやか", "さわやか", "にぎやか", "おだやか", "しずやか", "あざやか"))
                PreviewAnswer(answer)
        }
    }
}

@Preview(name = "light", showBackground = true, widthDp = 400, heightDp = 700)
@Preview(
    name = "dark",
    showBackground = true,
    widthDp = 400,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PreviewTestQuestionLayoutAnswers() {
    PreviewTestQuestionLayoutAnswersContent()
}

@KakugoLandscapePreview
@Composable
fun PreviewTestQuestionLayoutAnswersLandscape() {
    PreviewTestQuestionLayoutAnswersContent()
}

@Preview(name = "light", showBackground = true, widthDp = 400, heightDp = 700)
@Preview(
    name = "dark",
    showBackground = true,
    widthDp = 400,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PreviewTestQuestionLayoutManyAnswers() {
    KakugoTheme {
        TestQuestionLayoutCompose(
            question = "華やか",
            questionMinFontSize = 10.sp,
            questionMaxFontSize = 120.sp,
            questionAutoSize = QuestionAutoSize.AvoidWrapping,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                for (i in 1..20)
                    PreviewAnswer("こたえ $i")
            }
        }
    }
}
