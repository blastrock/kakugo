package org.kaqui.testactivities

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kaqui.TypefaceManager
import org.kaqui.theme.KakugoTheme

@Composable
private fun QuestionText(
    question: String,
    minFontSize: TextUnit,
    maxFontSize: TextUnit,
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
            .fillMaxWidth(),
        style = TextStyle(
            color = MaterialTheme.colors.onBackground.copy(alpha = LocalContentAlpha.current),
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        ),
        autoSize = TextAutoSize.StepBased(
            minFontSize = minFontSize,
            maxFontSize = maxFontSize,
            stepSize = 10.sp,
        ),
    )
}

@Composable
fun TestQuestionLayoutCompose(
    question: String,
    questionMinFontSize: TextUnit,
    questionMaxFontSize: TextUnit,
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
    } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuestionText(
                question = question,
                minFontSize = questionMinFontSize,
                maxFontSize = questionMaxFontSize,
                onQuestionLongClick = onQuestionLongClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
            )

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
        ) {
            PreviewBlock(Color(0xFFE57373), 100.dp)
            PreviewBlock(Color(0xFF81C784), 100.dp)
            PreviewBlock(Color(0xFF64B5F6), 100.dp)
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
    KakugoTheme {
        TestQuestionLayoutCompose(
            question = "華やか",
            questionMinFontSize = 10.sp,
            questionMaxFontSize = 120.sp,
        ) {
            for (answer in listOf("はなやか", "さわやか", "にぎやか", "おだやか"))
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
fun PreviewTestQuestionLayoutManyAnswers() {
    KakugoTheme {
        TestQuestionLayoutCompose(
            question = "華やか",
            questionMinFontSize = 10.sp,
            questionMaxFontSize = 120.sp,
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
