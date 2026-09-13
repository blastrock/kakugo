package org.kaqui.testactivities

import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kaqui.TypefaceManager

@Composable
private fun QuestionText(
    question: String,
    questionMinSizeSp: Int,
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
            .fillMaxSize()
            .wrapContentHeight(Alignment.CenterVertically),
        style = TextStyle(
            color = MaterialTheme.colors.onBackground.copy(alpha = LocalContentAlpha.current),
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        ),
        autoSize = TextAutoSize.StepBased(
            minFontSize = questionMinSizeSp.sp,
            maxFontSize = 200.sp,
            stepSize = 10.sp,
        ),
    )
}

@Composable
fun TestQuestionLayoutCompose(
    question: String,
    questionMinSizeSp: Int,
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
                questionMinSizeSp = questionMinSizeSp,
                onQuestionLongClick = onQuestionLongClick,
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .padding(bottom = 8.dp),
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
        val (weightQuestion, weightAnswers) =
            when {
                screenHeightDp < 800 -> Pair(.25f, .75f)
                screenHeightDp < 1000 -> Pair(.4f, .6f)
                else -> Pair(.5f, .5f)
            }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuestionText(
                question = question,
                questionMinSizeSp = questionMinSizeSp,
                onQuestionLongClick = onQuestionLongClick,
                modifier = Modifier
                    .weight(weightQuestion)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            val answerWidthMod = { modifier: Modifier ->
                if (screenWidthDp >= 500)
                    modifier.width(500.dp - 32.dp)
                else
                    modifier.fillMaxWidth()
            }

            Column(
                modifier = Modifier
                    .weight(weightAnswers)
                    .let(answerWidthMod),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                answersBlock()
            }
        }
    }
}
