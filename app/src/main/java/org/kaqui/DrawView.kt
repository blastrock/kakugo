package org.kaqui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class DrawView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var mPaths = mutableListOf<Path>()

    private var mPaint = Paint()
    private var mBoundingBoxPaint = Paint()
    private var mPath = Path()
    private var mHintPath = Path()
    private var mHintAnimator: ValueAnimator? = null
    private var mHintPaint = Paint()
    private var mPoints = mutableListOf<PointF>()

    private var mCurX = 0f
    private var mCurY = 0f
    private var mStartX = 0f
    private var mStartY = 0f

    private var boundingPath = Path()

    var debugPaths = listOf<Path>()
    var debugStrokeWidth = 0f

    var strokeCallback: ((Path) -> Unit)? = null
    var sizeChangedCallback: ((w: Int, h: Int) -> Unit)? = null

    init {
        mPaint.apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 20f
            isAntiAlias = true
        }
        mBoundingBoxPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.argb(150, 0, 0, 0)
        }
        mHintPaint.set(mPaint)
    }

    fun setBoundingBox(value: RectF) {
        boundingPath.reset()
        boundingPath.moveTo(value.left, value.top)
        boundingPath.lineTo(value.right, value.top)
        boundingPath.lineTo(value.right, value.bottom)
        boundingPath.lineTo(value.left, value.bottom)
        boundingPath.close()
    }

    fun addPath(path: Path) {
        mPaths.add(path)
        invalidate()
    }

    fun setHint(path: Path) {
        mHintPath = path
        val animator = ValueAnimator()
        animator.setIntValues(0, 255, 0)
        animator.duration = 1000
        animator.addUpdateListener {
            val color = resources.getColor(android.R.color.holo_green_light)
            mHintPaint.color = (it.animatedValue as Int shl 24) or (color and 0xffffff)
            invalidate()
        }
        animator.start()
        mHintAnimator = animator
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))

        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sizeChangedCallback?.invoke(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (path in debugPaths) {
            val paint = Paint()
            paint.set(mPaint)
            paint.strokeWidth = debugStrokeWidth
            paint.setARGB(255, 255, 0, 0)
            canvas.drawPath(path, paint)
        }

        canvas.drawPath(boundingPath, mBoundingBoxPaint)

        for (path in mPaths) {
            canvas.drawPath(path, mPaint)
        }

        canvas.drawPath(mHintPath, mHintPaint)

        canvas.drawPath(mPath, mPaint)
    }

    fun clearCanvas() {
        mPath.reset()
        mPaths.clear()
        invalidate()
    }

    private fun actionDown(x: Float, y: Float) {
        mPath.reset()
        mPath.moveTo(x, y)
        mCurX = x
        mCurY = y
    }

    private fun actionMove(x: Float, y: Float) {
        mPath.quadTo(mCurX, mCurY, (x + mCurX) / 2, (y + mCurY) / 2)
        mCurX = x
        mCurY = y
    }

    private fun actionUp() {
        mPath.lineTo(mCurX, mCurY)

        strokeCallback?.invoke(mPath)
        mPoints.clear()
        mPath.reset()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)

        val x = event.x
        val y = event.y

        mPoints.add(PointF(x, y))

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mStartX = x
                mStartY = y
                actionDown(x, y)
            }
            MotionEvent.ACTION_MOVE -> actionMove(x, y)
            MotionEvent.ACTION_UP -> actionUp()
        }

        invalidate()
        return true
    }
}
