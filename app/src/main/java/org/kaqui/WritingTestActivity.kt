package org.kaqui

import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.anko.*
import org.kaqui.model.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.pow

class WritingTestActivity : TestActivityBase(), CoroutineScope {
    companion object {
        private const val TAG = "WritingTestActivity"

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

    private val currentStrokes get() = testEngine.currentQuestion.strokes
    private lateinit var currentScaledStrokes: List<Path>
    private var currentStroke = 0
    private var missCount = 0

    override val testType
        get() = intent.extras!!.getSerializable("test_type") as TestType

    private lateinit var testLayout: TestLayout

    override val historyScrollView: NestedScrollView get() = testLayout.historyScrollView
    override val historyActionButton: FloatingActionButton get() = testLayout.historyActionButton
    override val historyView: LinearLayout get() = testLayout.historyView
    override val sessionScore: TextView get() = testLayout.sessionScore
    override val mainView: View get() = testLayout.mainView
    override val mainCoordLayout: androidx.coordinatorlayout.widget.CoordinatorLayout get() = testLayout.mainCoordinatorLayout
    private lateinit var drawCanvas: DrawView
    private lateinit var hintButton: Button
    private lateinit var dontKnowButton: Button
    private lateinit var nextButton: Button

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        job = Job()

        testLayout = TestLayout(this) { testLayout ->
            testLayout.makeMainBlock(this@WritingTestActivity, this, 10) {
                verticalLayout {
                    drawCanvas = drawView().lparams(width = wrapContent, height = wrapContent, weight = 1.0f) {
                        gravity = Gravity.CENTER
                    }
                    linearLayout {
                        hintButton = button(R.string.hint) {
                            setExtTint(R.color.answerMaybe)
                        }.lparams(weight = 1.0f)
                        dontKnowButton = button(R.string.dont_know) {
                            setExtTint(R.color.answerDontKnow)
                        }.lparams(weight = 1.0f)
                        nextButton = button(R.string.next).lparams(weight = 1.0f)
                    }.lparams(width = matchParent, height = wrapContent)
                }
            }
        }

        drawCanvas.strokeCallback = this::onStrokeFinished
        drawCanvas.sizeChangedCallback = this::onDrawViewSizeChanged

        hintButton.setOnClickListener { this.showHint() }
        dontKnowButton.setOnClickListener { this.onAnswerDone(Certainty.DONTKNOW) }
        nextButton.setOnClickListener { this.showNextQuestion() }

        testLayout.questionText.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }

        setUpGui(savedInstanceState)

        if (savedInstanceState != null) {
            currentStroke = savedInstanceState.getInt("currentStroke")
            missCount = savedInstanceState.getInt("missCount")
        }

        if (currentStroke == currentStrokes.size) {
            dontKnowButton.visibility = View.GONE
            hintButton.visibility = View.GONE
        } else {
            nextButton.visibility = View.GONE
        }

        drawCanvas.post { showCurrentQuestion() }
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

        testLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        drawCanvas.clearCanvas()

        val targetSize = drawCanvas.width
        val scale = targetSize.toFloat() / KANJI_SIZE

        val matrix = Matrix()
        matrix.postScale(scale, scale)

        currentScaledStrokes = currentStrokes.map { val path = Path(it); path.transform(matrix); path }

        drawCanvas.setBoundingBox(RectF(1f, 1f, drawCanvas.width.toFloat() - 1f, drawCanvas.width.toFloat() - 1f))

        for (stroke in currentScaledStrokes.subList(0, currentStroke))
            drawCanvas.addPath(stroke)

        //setupDebug()
    }

    private fun onDrawViewSizeChanged(w: Int, h: Int) {
        showCurrentQuestion()
    }

    private val resolution = 4

    private fun showHint() {
        if (currentStroke >= currentStrokes.size)
            return

        drawCanvas.setHint(currentScaledStrokes[currentStroke])
        ++missCount
    }

    private fun onStrokeFinished(drawnPath: Path) {
        if (currentStroke >= currentStrokes.size)
            return

        val targetSize = drawCanvas.width
        val scale = KANJI_SIZE / targetSize.toFloat()
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        drawnPath.transform(matrix)

        val squaredTolerance = (KANJI_SIZE.toFloat() / resolution).pow(2)

        val currentPath = currentStrokes[currentStroke]
        val currentPoints = toPoints(currentPath, KANJI_SIZE.toFloat() / resolution)

        val drawnPoints = toPoints(drawnPath, KANJI_SIZE.toFloat() / resolution / 4)

        var currentPointIndexReachedAt = 0
        var currentPointIndex = 0

        for ((drawnPointIndex, drawnPoint) in drawnPoints.withIndex()) {
            val squaredDistance = currentPoints[currentPointIndex].squaredDistanceTo(drawnPoint)
            if (squaredDistance > squaredTolerance)
                return

            if (currentPointIndex < currentPoints.size - 1 && currentPoints[currentPointIndex + 1].squaredDistanceTo(drawnPoint) < squaredDistance) {
                currentPointIndexReachedAt = drawnPointIndex
                ++currentPointIndex
            }
        }

        for (drawnPoint in drawnPoints.slice(currentPointIndexReachedAt until drawnPoints.size)) {
            if (currentPoints.drop(currentPointIndex).any { it.squaredDistanceTo(drawnPoint) > squaredTolerance })
                return // strokes finishes too early
        }

        drawCanvas.addPath(currentScaledStrokes[currentStroke])

        ++currentStroke

        if (currentStroke == currentStrokes.size)
            onAnswerDone(if (missCount == 0) Certainty.SURE else Certainty.DONTKNOW)
    }

    private fun onAnswerDone(certainty: Certainty) {
        testEngine.markAnswer(certainty)

        testLayout.overlay.trigger(testLayout.overlay.width / 2, testLayout.overlay.height / 2, ContextCompat.getColor(this, certainty.toColorRes()))

        currentStroke = currentStrokes.size

        drawCanvas.clearCanvas()
        for (stroke in currentScaledStrokes)
            drawCanvas.addPath(stroke)

        hintButton.visibility = View.GONE
        dontKnowButton.visibility = View.GONE
        nextButton.visibility = View.VISIBLE
    }

    private fun showNextQuestion() {
        currentStroke = 0
        missCount = 0
        testEngine.prepareNewQuestion()

        nextButton.visibility = View.GONE
        hintButton.visibility = View.VISIBLE
        dontKnowButton.visibility = View.VISIBLE

        showCurrentQuestion()
    }

    private fun setupDebug() {
        val targetSize = drawCanvas.width
        drawCanvas.debugPaths = currentScaledStrokes.slice(listOf(3)).map {
            toPoints(it, targetSize.toFloat() / resolution)
                    .map {
                        val path = Path()
                        path.moveTo(it.x, it.y)
                        path.lineTo(it.x, it.y + 1)
                        path
                    }
        }.flatten()
        drawCanvas.debugStrokeWidth = targetSize.toFloat() / resolution * 2
    }
}
