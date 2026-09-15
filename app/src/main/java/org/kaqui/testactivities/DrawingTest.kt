package org.kaqui.testactivities

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.update
import org.kaqui.R
import org.kaqui.model.Certainty
import org.kaqui.model.Database
import org.kaqui.model.Item
import org.kaqui.model.TestType
import org.kaqui.model.getQuestionText
import org.kaqui.model.text
import org.kaqui.showItemProbabilityData
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes
import kotlin.math.pow

object DrawingConstants {
    const val KANJI_SIZE = 109f
    const val RESOLUTION = 4f
}

@Composable
fun DrawingTest(
    question: TestQuestion,
    kanaWords: Boolean,
    onAnswer: (Certainty, Item?) -> Unit,
    onNextQuestion: () -> Unit,
) {
    val context = LocalContext.current

    val strokes = remember(question.item.id) {
        Database.getInstance(context).getStrokes(question.item.id)
    }

    // The width the DrawView reports; every stroke is scaled from native kanji size to it.
    // Not keyed on the question: the DrawView is reused, so onSizeChanged would not fire again.
    var drawViewWidth by remember { mutableIntStateOf(0) }
    var currentStroke by rememberSaveable(question.item.id) { mutableIntStateOf(0) }
    var missCount by rememberSaveable(question.item.id) { mutableIntStateOf(0) }
    var gaveUp by rememberSaveable(question.item.id) { mutableStateOf(false) }
    var hintPath by remember(question.item.id) { mutableStateOf<Path?>(null) }

    val scaledStrokes = remember(strokes, drawViewWidth) {
        if (drawViewWidth == 0) {
            emptyList()
        } else {
            val matrix = Matrix()
            val scale = drawViewWidth.toFloat() / DrawingConstants.KANJI_SIZE
            matrix.postScale(scale, scale)
            strokes.map { Path(it).apply { transform(matrix) } }
        }
    }

    val finished = gaveUp || (strokes.isNotEmpty() && currentStroke == strokes.size)

    DrawingTestScreen(
        questionText = question.item.getQuestionText(question.testType, kanaWords),
        questionAutoSize =
            if (question.testType == TestType.KANJI_DRAWING)
                QuestionAutoSize.FitBounds
            else
                QuestionAutoSize.AvoidWrapping,
        isFinished = finished,
        onSizeChanged = { w, _ -> if (w > 0) drawViewWidth = w },
        onHintClick = {
            if (!finished && currentStroke < scaledStrokes.size) {
                missCount++
                hintPath = scaledStrokes[currentStroke]
            }
        },
        onDontKnowClick = {
            if (!finished) {
                gaveUp = true
                onAnswer(Certainty.DONTKNOW, null)
            }
        },
        onNextClick = onNextQuestion,
        onQuestionLongClick = {
            question.debugData?.let {
                showItemProbabilityData(context, question.item.text(kanaWords), it)
            }
        },
        onStrokeFinished = { drawnPath ->
            // A stroke that does not match leaves the hint alone, so it keeps showing.
            if (finished || currentStroke >= strokes.size || drawViewWidth == 0) {
                hintPath = null
            } else if (strokeMatches(drawnPath, strokes[currentStroke], drawViewWidth)) {
                currentStroke++
                if (currentStroke == strokes.size)
                    onAnswer(if (missCount == 0) Certainty.SURE else Certainty.DONTKNOW, null)
                hintPath = null
            }
        },
        hintPathForDrawView = hintPath,
        pathsToDraw = scaledStrokes.take(currentStroke),
        pathsForAnswer = if (gaveUp) scaledStrokes.drop(currentStroke) else emptyList(),
    )
}

// All the maths is done in native kanji size coordinates.
private fun strokeMatches(drawnPath: Path, expectedStroke: Path, drawViewWidth: Int): Boolean {
    val scaleToKanji = DrawingConstants.KANJI_SIZE / drawViewWidth.toFloat()
    val matrixToKanji = Matrix()
    matrixToKanji.postScale(scaleToKanji, scaleToKanji)
    val scaledDrawnPathNative = Path(drawnPath).apply { transform(matrixToKanji) }

    val squaredTolerance = (DrawingConstants.KANJI_SIZE / DrawingConstants.RESOLUTION).pow(2)

    val originalPoints = toPoints(
        expectedStroke,
        DrawingConstants.KANJI_SIZE / DrawingConstants.RESOLUTION
    )
    val drawnPoints = toPoints(
        scaledDrawnPathNative,
        DrawingConstants.KANJI_SIZE / DrawingConstants.RESOLUTION / 4f
    )

    var currentPointIndexReachedAt = 0
    var currentOriginalPointIndex = 0

    for ((drawnPointIndex, drawnPoint) in drawnPoints.withIndex()) {
        val squaredDistance =
            originalPoints[currentOriginalPointIndex].squaredDistanceTo(drawnPoint)
        if (squaredDistance > squaredTolerance)
            return false

        if (currentOriginalPointIndex < originalPoints.size - 1 &&
            originalPoints[currentOriginalPointIndex + 1].squaredDistanceTo(drawnPoint) < squaredDistance
        ) {
            currentPointIndexReachedAt = drawnPointIndex
            ++currentOriginalPointIndex
        }
    }

    for (drawnPoint in drawnPoints.slice(currentPointIndexReachedAt until drawnPoints.size)) {
        if (originalPoints
                .drop(currentOriginalPointIndex)
                .any { it.squaredDistanceTo(drawnPoint) > squaredTolerance }
        )
        // stroke finished too early
            return false
    }

    return true
}

private fun PathMeasure.getPoint(position: Float): PointF {
    val out = floatArrayOf(0f, 0f)
    getPosTan(position, out, null)
    return PointF(out[0], out[1])
}

private fun toPoints(path: Path, step: Float): List<PointF> {
    val scale = 64

    val pathMeasure = PathMeasure(path, false)
    val points = (0..(pathMeasure.length.toInt() * scale) step (step * scale).toInt())
        .map { pathMeasure.getPoint(it.toFloat() / scale) }
        .toMutableList()
    points.add(pathMeasure.getPoint(pathMeasure.length))
    return points
}

private fun PointF.squaredDistanceTo(other: PointF): Float {
    val dx = this.x - other.x
    val dy = this.y - other.y
    return dx * dx + dy * dy
}

@Composable
fun DrawingTestScreen(
    questionText: String,
    questionAutoSize: QuestionAutoSize,
    isFinished: Boolean,
    onSizeChanged: (Int, Int) -> Unit,
    onHintClick: () -> Unit,
    onDontKnowClick: () -> Unit,
    onNextClick: () -> Unit,
    onQuestionLongClick: () -> Unit,
    onStrokeFinished: (path: Path) -> Unit,
    hintPathForDrawView: Path?,
    pathsToDraw: List<Path>,
    pathsForAnswer: List<Path>,
) {
    val themeColors = LocalThemeAttributes.current
    val paintColor = MaterialTheme.colors.onBackground.toArgb()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TestQuestionLayoutCompose(
            question = questionText,
            questionMinFontSize = 10.sp,
            questionMaxFontSize = 120.sp,
            questionAutoSize = questionAutoSize,
            onQuestionLongClick = onQuestionLongClick,
        ) {
                AndroidView(
                    factory = { context -> DrawView(context) },
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    update = { view ->
                        view.strokeCallback = onStrokeFinished
                        view.sizeChangedCallback = { w, h ->
                            onSizeChanged(w, h)
                            view.setBoundingBox(RectF(1f, 1f, w.toFloat() - 1f, w.toFloat() - 1f))
                        }
                        if (hintPathForDrawView != null)
                            view.setHint(hintPathForDrawView)
                        view.setStrokes(pathsToDraw)
                        view.setAnswerPaths(pathsForAnswer)
                        view.paintColor = paintColor
                        view.answerPaintColor = themeColors.drawingDontKnow.toArgb()
                    }
                )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isFinished) {
                    Button(
                        onClick = onHintClick,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = themeColors.backgroundMaybe
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(id = R.string.hint).toUpperCase(Locale.current),
                        )
                    }

                    Button(
                        onClick = onDontKnowClick,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = themeColors.backgroundDontKnow
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(id = R.string.dont_know).toUpperCase(Locale.current),
                        )
                    }
                } else {
                    Button(
                        onClick = onNextClick,
                        modifier = Modifier.weight(1f),
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

@Preview(showBackground = true, widthDp = 400, heightDp = 500)
@Composable
fun PreviewDrawingTestScreen() {
    KakugoTheme {
        DrawingTestScreen(
            questionText = "漢",
            questionAutoSize = QuestionAutoSize.AvoidWrapping,
            isFinished = false,
            onSizeChanged = { _, _ -> },
            onHintClick = {},
            onDontKnowClick = {},
            onNextClick = {},
            onQuestionLongClick = {},
            onStrokeFinished = {},
            hintPathForDrawView = null,
            pathsToDraw = emptyList(),
            pathsForAnswer = emptyList(),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDrawingTestScreenTall() {
    KakugoTheme {
        DrawingTestScreen(
            questionText = "漢",
            questionAutoSize = QuestionAutoSize.AvoidWrapping,
            isFinished = false,
            onSizeChanged = { _, _ -> },
            onHintClick = {},
            onDontKnowClick = {},
            onNextClick = {},
            onQuestionLongClick = {},
            onStrokeFinished = {},
            hintPathForDrawView = null,
            pathsToDraw = emptyList(),
            pathsForAnswer = emptyList(),
        )
    }
}
