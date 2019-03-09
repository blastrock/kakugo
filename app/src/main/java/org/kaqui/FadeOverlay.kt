package org.kaqui

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.max
import kotlin.math.pow

class FadeOverlay(context: Context) : View(context) {
    companion object {
        const val radiusGradientTime = 200 // ms
        const val fadeTime = 200 // ms
    }

    private val radialPaint = Paint()
    private val fillPaint = Paint()
    private val animator = TimeAnimator()

    private var positionX: Int = 0
    private var positionY: Int = 0
    private var radius: Float = 0f

    init {
        radialPaint.strokeWidth = 1f
        radialPaint.style = Paint.Style.FILL_AND_STROKE

        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.TRANSPARENT

        animator.setTimeListener(this::updateRadius)
    }

    fun trigger(x: Int, y: Int, color: Int) {
        positionX = x
        positionY = y

        radialPaint.shader = RadialGradient(0f, 0f, 1f, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        fillPaint.color = color and 0xffffff

        animator.start()
    }

    private fun updateRadius(animation: TimeAnimator, totalTime: Long, deltaTime: Long) {
        when (totalTime) {
            in 0..radiusGradientTime -> {
                radius = 2 * max(width, height) * (totalTime / radiusGradientTime.toFloat()).pow(2)
            }
            in radiusGradientTime..(radiusGradientTime + fadeTime) -> {
                radius = 0f
                fillPaint.alpha = (0xff * (1 - (totalTime - radiusGradientTime) / fadeTime.toFloat())).toInt()
            }
            else -> {
                fillPaint.alpha = Color.TRANSPARENT
                animation.end()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius == 0f && fillPaint.alpha == 0)
            return

        canvas.save()
        canvas.translate(positionX.toFloat(), positionY.toFloat())
        canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, radialPaint)
        canvas.restore()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
    }
}
