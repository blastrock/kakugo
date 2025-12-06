package org.kaqui.testactivities

import android.app.Application
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kaqui.R
import org.kaqui.TestEngine
import org.kaqui.model.Certainty
import org.kaqui.model.Database
import org.kaqui.model.getQuestionText
import org.kaqui.model.text
import org.kaqui.showItemProbabilityData
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes
import kotlin.math.pow

data class DrawingUiState(
    val questionText: String = "",
    val isFinished: Boolean = false,
    val currentStrokeIndex: Int = 0,
    val totalStrokes: Int = 0,
    val currentStrokesNativeForDrawView: List<Path> = emptyList(),
    val hintPathForDrawView: Path? = null,
    val pathsToDraw: List<Path> = emptyList(),
    val pathsForAnswer: List<Path> = emptyList(),
    val clearCanvasSignal: Boolean = false,
    val showAnswerSignal: Boolean = false,
)

object DrawingConstants {
    const val KANJI_SIZE = 109f
    const val RESOLUTION = 4f
}

class DrawingViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DrawingUiState())
    val uiState: StateFlow<DrawingUiState> = _uiState.asStateFlow()

    private lateinit var testEngine: TestEngine

    private var currentQuestionStrokesInternal: List<Path> = emptyList()
    private var currentStrokeInternal = 0
    private var missCountInternal = 0
    private var isFinishedInternal = false

    private val kanaWords: Boolean =
        PreferenceManager.getDefaultSharedPreferences(application).getBoolean("kana_words", true)
    private var drawViewWidth: Int = 0

    private val _answerEvents = MutableSharedFlow<Certainty>()
    val answerEvents: SharedFlow<Certainty> = _answerEvents

    fun initialize(testEngine: TestEngine) {
        this.testEngine = testEngine
        refreshQuestionData()
    }

    fun setDrawViewWidth(width: Int) {
        if (width > 0 && drawViewWidth != width) {
            drawViewWidth = width
            prepareDrawViewContent()
        }
    }

    private fun refreshQuestionData() {
        val question = testEngine.currentQuestion
        currentQuestionStrokesInternal =
            Database.getInstance(getApplication()).getStrokes(question.id)
        currentStrokeInternal = 0
        missCountInternal = 0
        isFinishedInternal = false

        _uiState.update {
            it.copy(
                questionText = question.getQuestionText(testEngine.testType, kanaWords),
                isFinished = false,
                currentStrokeIndex = 0,
                totalStrokes = currentQuestionStrokesInternal.size,
                clearCanvasSignal = true, // Signal to clear
                showAnswerSignal = false,
                pathsForAnswer = listOf(),
            )
        }
        prepareDrawViewContent()
    }

    fun startNewQuestion() {
        refreshQuestionData()
    }

    private fun prepareDrawViewContent(isInitialSetup: Boolean = true) {
        if (drawViewWidth == 0 || currentQuestionStrokesInternal.isEmpty()) {
            _uiState.update {
                it.copy(
                    currentStrokesNativeForDrawView = emptyList(),
                    pathsToDraw = emptyList(),
                    pathsForAnswer = emptyList(),
                )
            }
            return
        }

        val scale = drawViewWidth.toFloat() / DrawingConstants.KANJI_SIZE
        val matrix = Matrix()
        matrix.postScale(scale, scale)

        val scaledStrokesForDrawView = currentQuestionStrokesInternal.map { nativePath ->
            Path(nativePath).apply { transform(matrix) }
        }

        val pathsToDraw = if (isFinishedInternal) {
            scaledStrokesForDrawView
        } else {
            scaledStrokesForDrawView.take(currentStrokeInternal)
        }

        _uiState.update {
            it.copy(
                currentStrokesNativeForDrawView = scaledStrokesForDrawView,
                pathsToDraw = pathsToDraw,
                clearCanvasSignal = isInitialSetup,
                showAnswerSignal = isFinishedInternal,
                hintPathForDrawView = null
            )
        }
    }


    fun onHintClicked() {
        if (isFinishedInternal || currentStrokeInternal >= currentQuestionStrokesInternal.size || drawViewWidth == 0) return

        missCountInternal++

        val scale = drawViewWidth.toFloat() / DrawingConstants.KANJI_SIZE
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        val hintPath =
            Path(currentQuestionStrokesInternal[currentStrokeInternal]).apply { transform(matrix) }

        _uiState.update {
            it.copy(
                hintPathForDrawView = hintPath,
            )
        }
    }

    fun onDontKnowClicked(onAnswerCallback: (Certainty) -> Unit) {
        if (isFinishedInternal) return
        isFinishedInternal = true
        onAnswerCallback(Certainty.DONTKNOW)

        val scale = drawViewWidth.toFloat() / DrawingConstants.KANJI_SIZE
        val matrix = Matrix()
        matrix.postScale(scale, scale)

        _uiState.update {
            it.copy(
                isFinished = true,
                showAnswerSignal = true,
                pathsForAnswer = currentQuestionStrokesInternal.drop(currentStrokeInternal)
                    .map { it.apply { transform(matrix) } }
            )
        }

        viewModelScope.launch {
            _answerEvents.emit(Certainty.DONTKNOW)
        }
    }


    fun onStrokeFinishedFromDrawView(drawnPath: Path) {
        if (isFinishedInternal || currentStrokeInternal >= currentQuestionStrokesInternal.size || drawViewWidth == 0) {
            _uiState.update { it.copy(hintPathForDrawView = null) } // Clear hint if any
            return
        }

        val scaleToKanji = DrawingConstants.KANJI_SIZE / drawViewWidth.toFloat()
        val matrixToKanji = Matrix()
        matrixToKanji.postScale(scaleToKanji, scaleToKanji)
        val scaledDrawnPathNative =
            Path(drawnPath).apply { transform(matrixToKanji) } // Work on a copy

        val squaredTolerance = (DrawingConstants.KANJI_SIZE / DrawingConstants.RESOLUTION).pow(2)
        val originalCompareStrokeNative = currentQuestionStrokesInternal[currentStrokeInternal]

        val originalPoints = toPoints(
            originalCompareStrokeNative,
            DrawingConstants.KANJI_SIZE / DrawingConstants.RESOLUTION
        )
        val drawnPoints = toPoints(
            scaledDrawnPathNative,
            DrawingConstants.KANJI_SIZE / DrawingConstants.RESOLUTION / 4f
        )

        var currentPointIndexReachedAt = 0
        var currentOriginalPointIndex = 0
        var matched = false

        if (originalPoints.isNotEmpty() && drawnPoints.isNotEmpty()) {
            var allDrawnPointsMatch = true
            for ((drawnPointIndex, drawnPoint) in drawnPoints.withIndex()) {
                if (currentOriginalPointIndex >= originalPoints.size) {
                    allDrawnPointsMatch = false
                    break
                }
                val squaredDistance =
                    originalPoints[currentOriginalPointIndex].squaredDistanceTo(drawnPoint)
                if (squaredDistance > squaredTolerance) {
                    allDrawnPointsMatch = false
                    break
                }
                if (currentOriginalPointIndex < originalPoints.size - 1 && originalPoints[currentOriginalPointIndex + 1].squaredDistanceTo(
                        drawnPoint
                    ) < squaredDistance
                ) {
                    currentPointIndexReachedAt = drawnPointIndex
                    currentOriginalPointIndex++
                }
            }
            if (allDrawnPointsMatch && currentOriginalPointIndex >= originalPoints.size - 1) {
                for (drawnPoint in drawnPoints.slice(currentPointIndexReachedAt until drawnPoints.size)) {
                    if (originalPoints.drop(currentOriginalPointIndex)
                            .any { it.squaredDistanceTo(drawnPoint) > squaredTolerance }
                    ) {
                        allDrawnPointsMatch = false
                        break
                    }
                }
                if (allDrawnPointsMatch && originalPoints.drop(currentOriginalPointIndex)
                        .all { point ->
                            drawnPoints.slice(currentPointIndexReachedAt until drawnPoints.size)
                                .any { it.squaredDistanceTo(point) <= squaredTolerance }
                        }
                ) {
                    matched = true
                }
            }
        }

        val newPathsToDraw = mutableListOf<Path>()
        if (!matched) {
            return
        }

        val scaleForDisplay = drawViewWidth.toFloat() / DrawingConstants.KANJI_SIZE
        val displayMatrix = Matrix()
        displayMatrix.postScale(scaleForDisplay, scaleForDisplay)
        val correctStrokePath = Path(currentQuestionStrokesInternal[currentStrokeInternal]).apply {
            transform(displayMatrix)
        }
        newPathsToDraw.add(correctStrokePath)

        currentStrokeInternal++
        if (currentStrokeInternal == currentQuestionStrokesInternal.size) {
            isFinishedInternal = true
            viewModelScope.launch {
                _answerEvents.emit(if (missCountInternal == 0) Certainty.SURE else Certainty.DONTKNOW)
            }
        }

        _uiState.update {
            it.copy(
                isFinished = isFinishedInternal,
                currentStrokeIndex = currentStrokeInternal,
                pathsToDraw = it.pathsToDraw + newPathsToDraw,
                showAnswerSignal = isFinishedInternal,
                hintPathForDrawView = null
            )
        }
        if (isFinishedInternal) {
            prepareDrawViewContent(isInitialSetup = false)
        }
    }

    private fun toPoints(path: Path, step: Float): List<PointF> {
        if (path.isEmpty) return emptyList()
        val pathMeasure = PathMeasure(path, false)
        if (pathMeasure.length == 0f) return emptyList()

        val points = mutableListOf<PointF>()
        var distance = 0f
        while (distance <= pathMeasure.length) {
            val pos = floatArrayOf(0f, 0f)
            pathMeasure.getPosTan(distance, pos, null)
            points.add(PointF(pos[0], pos[1]))
            if (step == 0f) break // Avoid infinite loop
            distance += step
        }
        // Add the last point explicitly
        val lastPos = floatArrayOf(0f, 0f)
        pathMeasure.getPosTan(pathMeasure.length, lastPos, null)
        if (points.isEmpty() || points.last().x != lastPos[0] || points.last().y != lastPos[1]) {
            points.add(PointF(lastPos[0], lastPos[1]))
        }
        return points
    }

    private fun PointF.squaredDistanceTo(other: PointF): Float {
        val dx = this.x - other.x
        val dy = this.y - other.y
        return dx * dx + dy * dy
    }
}

class DrawingTestFragmentCompose : Fragment(), TestFragment {
    private val testFragmentHolder: TestFragmentHolder
        get() = (requireActivity() as TestFragmentHolder)

    private val viewModel: DrawingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        viewModel.initialize(testFragmentHolder.testEngine)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.answerEvents.collectLatest { certainty ->
                        testFragmentHolder.onAnswer(null, certainty, null)
                    }
                }
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                val uiState by viewModel.uiState.collectAsState()

                KakugoTheme {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        DrawingTestScreen(
                            questionText = uiState.questionText,
                            isFinished = uiState.isFinished,
                            onSizeChanged = { w, h ->
                                viewModel.setDrawViewWidth(w)
                            },
                            onHintClick = viewModel::onHintClicked,
                            onDontKnowClick = {
                                viewModel.onDontKnowClicked { /* No-op, handled by event */ }
                            },
                            onNextClick = {
                                testFragmentHolder.nextQuestion()
                            },
                            onQuestionLongClick = {
                                testFragmentHolder.testEngine.currentDebugData?.let { data ->
                                    showItemProbabilityData(
                                        requireContext(),
                                        testFragmentHolder.testEngine.currentQuestion.text(
                                            PreferenceManager.getDefaultSharedPreferences(
                                                requireContext()
                                            )
                                                .getBoolean("kana_words", true)
                                        ),
                                        data
                                    )
                                }
                            },
                            onStrokeFinished = { path ->
                                viewModel.onStrokeFinishedFromDrawView(path)
                            },
                            hintPathForDrawView = uiState.hintPathForDrawView,
                            pathsToDraw = uiState.pathsToDraw, // Use new field
                            pathsForAnswer = uiState.pathsForAnswer,
                        )
                    }
                }
            }
        }
    }

    override fun startNewQuestion() {
        viewModel.startNewQuestion()
    }

    override fun setSensible(e: Boolean) {
        // Button sensibility is handled by Compose state derived from ViewModel
    }

    override fun refreshQuestion() {
        viewModel.startNewQuestion()
    }
}

@Composable
fun DrawingTestScreen(
    questionText: String,
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
            questionMinSizeSp = 10,
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
