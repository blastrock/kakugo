package org.kaqui

import android.graphics.*
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.writing_quizz_activity.*
import kotlinx.coroutines.*
import org.kaqui.model.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.pow

class WritingQuizzActivity : QuizzActivityBase(), CoroutineScope {
    companion object {
        private const val TAG = "WritingQuizzActivity"

        private const val KANJI_SIZE = 109

        private fun toPoints(path: Path, step: Float): List<PointF> {
            val scale = 64

            val pathMeasure = PathMeasure(path, false)
            val points = (0..(pathMeasure.length.toInt() * scale) step (step * scale).toInt())
                    .map { pathMeasure.getPoint(it.toFloat() / scale) }
                    .toMutableList()
            points.add(pathMeasure.getPoint(pathMeasure.length))
            return points
        }
    }

    private val currentKanji get() = quizzEngine.currentQuestion.contents as Kanji
    private lateinit var currentScaledStrokes: List<Path>
    private var currentStroke = 0
    private var missCount = 0

    override val quizzType = QuizzType.KANJI_WRITING

    override val historyScrollView get() = history_scroll_view
    override val historyActionButton get() = history_action_button
    override val historyView get() = history_view
    override val sessionScore get() = session_score
    override val mainView get() = main_layout
    override val mainCoordLayout get() = main_coordlayout

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        job = Job()
        setContentView(R.layout.writing_quizz_activity)
        super.onCreate(savedInstanceState)

        question_text.textSize = 20.0f
        draw_canevas.strokeCallback = this::onStrokeFinished
        draw_canevas.sizeChangedCallback = this::onDrawViewSizeChanged

        hint_button.setOnClickListener { _ -> this.showHint() }
        dontknow_button.setOnClickListener { _ -> this.onAnswerDone(Certainty.DONTKNOW) }
        next_button.setOnClickListener { _ -> this.showNextQuestion() }
        next_button.visibility = View.GONE

        question_text.setOnLongClickListener { _ ->
            if (quizzEngine.currentDebugData != null)
                showItemProbabilityData(quizzEngine.currentQuestion.text, quizzEngine.currentDebugData!!)
            true
        }

        if (savedInstanceState != null) {
            currentStroke = savedInstanceState.getInt("currentStroke")
            missCount = savedInstanceState.getInt("missCount")
        }

        draw_canevas.post { showCurrentQuestion() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("currentStroke", currentStroke)
        outState.putInt("missCount", missCount)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun showCurrentQuestion() {
        super.showCurrentQuestion()

        question_text.text = quizzEngine.currentQuestion.getQuestionText(quizzType)

        draw_canevas.clearCanvas()

        val targetSize = draw_canevas.width
        val scale = targetSize.toFloat() / KANJI_SIZE

        val matrix = Matrix()
        matrix.postScale(scale, scale)

        currentScaledStrokes = currentKanji.strokes.map { val path = Path(it); path.transform(matrix); path }

        draw_canevas.setBoundingBox(RectF(1f, 1f, draw_canevas.width.toFloat() - 1f, draw_canevas.width.toFloat() - 1f))

        for (stroke in currentScaledStrokes.subList(0, currentStroke))
            draw_canevas.addPath(stroke)

        //setupDebug()
    }

    private fun onDrawViewSizeChanged(w: Int, h: Int) {
        showCurrentQuestion()
    }

    private val resolution = 4

    private fun showHint() {
        if (currentStroke >= currentKanji.strokes.size)
            return

        draw_canevas.setHint(currentScaledStrokes[currentStroke])
        ++missCount
    }

    private fun onStrokeFinished(drawnPath: Path) {
        if (currentStroke >= currentKanji.strokes.size)
            return

        val targetSize = draw_canevas.width
        val scale = KANJI_SIZE / targetSize.toFloat()
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        drawnPath.transform(matrix)

        val squaredTolerance = (KANJI_SIZE.toFloat() / resolution).pow(2)

        val currentPath = currentKanji.strokes[currentStroke]
        val currentPoints = toPoints(currentPath, KANJI_SIZE.toFloat() / resolution)

        val drawnPoints = toPoints(drawnPath, KANJI_SIZE.toFloat() / resolution / 4)

        var currentPointIndexReachedAt = 0
        var currentPointIndex = 0

        for ((drawnPointIndex, drawnPoint) in drawnPoints.withIndex()) {
            val squaredDistance = currentPoints[currentPointIndex].squaredDistanceTo(drawnPoint)
            if (squaredDistance > squaredTolerance)
                return

            if (currentPointIndex < currentPoints.size - 1 && currentPoints[currentPointIndex+1].squaredDistanceTo(drawnPoint) < squaredDistance) {
                currentPointIndexReachedAt = drawnPointIndex
                ++currentPointIndex
            }
        }

        for (drawnPoint in drawnPoints.slice(currentPointIndexReachedAt until drawnPoints.size)) {
            if (currentPoints.drop(currentPointIndex).any { it.squaredDistanceTo(drawnPoint) > squaredTolerance })
                return // strokes finishes too early
        }

        draw_canevas.addPath(currentScaledStrokes[currentStroke])

        ++currentStroke

        if (currentStroke == currentKanji.strokes.size)
            onAnswerDone(if (missCount == 0) Certainty.SURE else Certainty.DONTKNOW)
    }

    private fun onAnswerDone(certainty: Certainty) {
        quizzEngine.markAnswer(certainty)
        currentStroke = currentKanji.strokes.size

        draw_canevas.clearCanvas()
        for (stroke in currentScaledStrokes)
            draw_canevas.addPath(stroke)

        hint_button.visibility = View.GONE
        dontknow_button.visibility = View.GONE
        next_button.visibility = View.VISIBLE
    }

    private fun showNextQuestion() {
        currentStroke = 0
        missCount = 0
        quizzEngine.prepareNewQuestion()

        next_button.visibility = View.GONE
        hint_button.visibility = View.VISIBLE
        dontknow_button.visibility = View.VISIBLE

        showCurrentQuestion()
    }

    private fun setupDebug() {
        val targetSize = draw_canevas.width
        draw_canevas.debugPaths = currentScaledStrokes.slice(listOf(3)).map {
            toPoints(it, targetSize.toFloat() / resolution)
                    .map {
                        val path = Path()
                        path.moveTo(it.x, it.y)
                        path.lineTo(it.x, it.y + 1)
                        path
                    }
        }.flatten()
        draw_canevas.debugStrokeWidth = targetSize.toFloat() / resolution * 2
    }
}
