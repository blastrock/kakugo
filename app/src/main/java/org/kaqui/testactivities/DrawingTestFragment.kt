package org.kaqui.testactivities

import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.UI
import org.kaqui.*
import org.kaqui.model.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.pow

class DrawingTestFragment : Fragment(), CoroutineScope, TestFragment {
    companion object {
        private const val TAG = "DrawingTestFragment"

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

        @JvmStatic
        fun newInstance() = DrawingTestFragment()
    }

    private val testFragmentHolder
        get() = (activity!! as TestFragmentHolder)
    private val testEngine
        get() = testFragmentHolder.testEngine
    private val testType
        get() = testFragmentHolder.testType

    private lateinit var currentStrokes: List<Path>
    private lateinit var currentScaledStrokes: List<Path>
    private var finished = false
    private var currentStroke = 0
    private var missCount = 0

    private lateinit var testQuestionLayout: TestQuestionLayout

    private lateinit var drawCanvas: DrawView
    private lateinit var buttons: LinearLayout
    private lateinit var hintButton: Button
    private lateinit var dontKnowButton: Button
    private lateinit var nextButton: Button

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        job = Job()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        testQuestionLayout = TestQuestionLayout()
        val mainBlock = UI {
            testQuestionLayout.makeMainBlock(activity!!, this, 10) {
                verticalLayout {
                    drawCanvas = drawView().lparams(width = wrapContent, height = wrapContent, weight = 1.0f) {
                        gravity = Gravity.CENTER
                    }
                    buttons = linearLayout {
                        hintButton = button(R.string.hint) {
                            setExtTint(R.attr.backgroundMaybe)
                        }.lparams(weight = 1.0f)
                        dontKnowButton = button(R.string.dont_know) {
                            setExtTint(R.attr.backgroundDontKnow)
                        }.lparams(weight = 1.0f)
                        nextButton = button(R.string.next).lparams(weight = 1.0f)
                    }.lparams(width = matchParent, height = wrapContent)
                }
            }
        }.view

        drawCanvas.strokeCallback = this::onStrokeFinished
        drawCanvas.sizeChangedCallback = this::onDrawViewSizeChanged

        hintButton.setOnClickListener { this.showHint() }
        dontKnowButton.setOnClickListener { this.onAnswerDone(false) }
        nextButton.setOnClickListener { testFragmentHolder.nextQuestion() }

        testQuestionLayout.questionText.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(context!!, testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }

        if (savedInstanceState != null) {
            currentStroke = savedInstanceState.getInt("currentStroke")
            missCount = savedInstanceState.getInt("missCount")
            finished = savedInstanceState.getBoolean("finished")
        }

        drawCanvas.post { refreshQuestion() }

        return mainBlock
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("currentStroke", currentStroke)
        outState.putInt("missCount", missCount)
        outState.putBoolean("finished", finished)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun refreshState() {
        if (finished) {
            drawCanvas.setAnswerPaths(currentScaledStrokes)
            dontKnowButton.visibility = View.GONE
            hintButton.visibility = View.GONE
            nextButton.visibility = View.VISIBLE
        } else {
            drawCanvas.setAnswerPaths(listOf())
            nextButton.visibility = View.GONE
            dontKnowButton.visibility = View.VISIBLE
            hintButton.visibility = View.VISIBLE
        }
        buttons.requestLayout()
    }

    override fun startNewQuestion() {
        currentStroke = 0
        missCount = 0
        finished = false
    }

    override fun setSensible(e: Boolean) {
        nextButton.isClickable = e
        hintButton.isClickable = e
        dontKnowButton.isClickable = e
    }

    override fun refreshQuestion() {
        currentStrokes = Database.getInstance(context!!).getStrokes(testEngine.currentQuestion.id)

        testQuestionLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        drawCanvas.clearCanvas()

        val targetSize = drawCanvas.width
        val scale = targetSize.toFloat() / KANJI_SIZE

        val matrix = Matrix()
        matrix.postScale(scale, scale)

        currentScaledStrokes = currentStrokes.map { val path = Path(it); path.transform(matrix); path }

        drawCanvas.setBoundingBox(RectF(1f, 1f, drawCanvas.width.toFloat() - 1f, drawCanvas.width.toFloat() - 1f))

        for (stroke in currentScaledStrokes.subList(0, currentStroke))
            drawCanvas.addPath(stroke)

        refreshState()

        //setupDebug()
    }

    private fun onDrawViewSizeChanged(w: Int, h: Int) {
        refreshQuestion()
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
            onAnswerDone(missCount == 0)
    }

    private fun onAnswerDone(correct: Boolean) {
        if (finished)
            return

        finished = true
        if (correct)
            testFragmentHolder.onAnswer(null, Certainty.SURE, null)
        else
            testFragmentHolder.onAnswer(null, Certainty.DONTKNOW, null)

        refreshState()
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
