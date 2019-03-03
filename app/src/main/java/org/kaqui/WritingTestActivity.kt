package org.kaqui

import android.content.res.ColorStateList
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.widget.NestedScrollView
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.anko.*
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.support.v4.nestedScrollView
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

    private val currentKanji get() = testEngine.currentQuestion.contents as Kanji
    private lateinit var currentScaledStrokes: List<Path>
    private var currentStroke = 0
    private var missCount = 0

    override val testType = TestType.KANJI_WRITING

    override lateinit var historyScrollView: NestedScrollView private set
    override lateinit var historyActionButton: FloatingActionButton private set
    override lateinit var historyView: LinearLayout private set
    override lateinit var sessionScore: TextView private set
    override lateinit var mainView: View private set
    override lateinit var mainCoordLayout: CoordinatorLayout private set
    private lateinit var questionText: TextView
    private lateinit var drawCanvas: DrawView
    private lateinit var hintButton: Button
    private lateinit var dontKnowButton: Button
    private lateinit var nextButton: Button

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        job = Job()

        mainCoordLayout = coordinatorLayout {
            verticalLayout {
                id = R.id.global_stats
            }.lparams(width = matchParent, height = wrapContent)
            mainView = verticalLayout {
                sessionScore = textView {
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                }
                questionText = textView {
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                }.lparams(width = wrapContent, height = wrapContent) {
                    bottomMargin = dip(8)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                drawCanvas = drawView().lparams(width = wrapContent, height = wrapContent, weight = 1.0f) {
                    gravity = Gravity.CENTER
                }
                linearLayout {
                    hintButton = button(R.string.hint) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            backgroundTintMode = PorterDuff.Mode.MULTIPLY
                            backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.answerMaybe))
                        }
                    }.lparams(weight = 1.0f)
                    dontKnowButton = button(R.string.dont_know) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            backgroundTintMode = PorterDuff.Mode.MULTIPLY
                            backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.answerDontKnow))
                        }
                    }.lparams(weight = 1.0f)
                    nextButton = button(R.string.next).lparams(weight = 1.0f)
                }.lparams(width = matchParent, height = wrapContent)
            }.lparams(width = matchParent, height = wrapContent)
            historyScrollView = nestedScrollView {
                id = R.id.history_scroll_view
                backgroundColor = Color.rgb(0xcc, 0xcc, 0xcc)
                historyView = verticalLayout().lparams(width = matchParent, height = wrapContent)
            }.lparams(width = matchParent, height = matchParent) {
                val bottomSheetBehavior = BottomSheetBehavior<NestedScrollView>()
                bottomSheetBehavior.peekHeight = 0
                bottomSheetBehavior.isHideable = false
                behavior = bottomSheetBehavior
            }
            historyActionButton = floatingActionButton {
                size = FloatingActionButton.SIZE_MINI
                scaleX = 0f
                scaleY = 0f
                setImageDrawable(resources.getDrawable(R.drawable.ic_arrow_upward))
            }.lparams(width = matchParent, height = wrapContent) {
                anchorId = R.id.history_scroll_view
                anchorGravity = Gravity.TOP or Gravity.RIGHT

                marginEnd = dip(20)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    elevation = 12.0f
                }
            }
        }

        mainView.padding = dip(16)

        super.onCreate(savedInstanceState)

        questionText.textSize = 20.0f
        drawCanvas.strokeCallback = this::onStrokeFinished
        drawCanvas.sizeChangedCallback = this::onDrawViewSizeChanged

        hintButton.setOnClickListener { this.showHint() }
        dontKnowButton.setOnClickListener { this.onAnswerDone(Certainty.DONTKNOW) }
        nextButton.setOnClickListener { this.showNextQuestion() }
        nextButton.visibility = View.GONE

        questionText.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }

        if (savedInstanceState != null) {
            currentStroke = savedInstanceState.getInt("currentStroke")
            missCount = savedInstanceState.getInt("missCount")
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

        questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        drawCanvas.clearCanvas()

        val targetSize = drawCanvas.width
        val scale = targetSize.toFloat() / KANJI_SIZE

        val matrix = Matrix()
        matrix.postScale(scale, scale)

        currentScaledStrokes = currentKanji.strokes.map { val path = Path(it); path.transform(matrix); path }

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
        if (currentStroke >= currentKanji.strokes.size)
            return

        drawCanvas.setHint(currentScaledStrokes[currentStroke])
        ++missCount
    }

    private fun onStrokeFinished(drawnPath: Path) {
        if (currentStroke >= currentKanji.strokes.size)
            return

        val targetSize = drawCanvas.width
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

        if (currentStroke == currentKanji.strokes.size)
            onAnswerDone(if (missCount == 0) Certainty.SURE else Certainty.DONTKNOW)
    }

    private fun onAnswerDone(certainty: Certainty) {
        testEngine.markAnswer(certainty)
        currentStroke = currentKanji.strokes.size

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
