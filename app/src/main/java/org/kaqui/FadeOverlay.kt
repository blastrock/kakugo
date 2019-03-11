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
    private val animator = TimeAnimator()

    private var positionX: Int = 0
    private var positionY: Int = 0
    private var radius: Float = 0f

    init {
        radialPaint.style = Paint.Style.FILL

        animator.setTimeListener(this::updateRadius)
    }

    fun trigger(x: Int, y: Int, color: Int) {
        positionX = x
        positionY = y

        radialPaint.shader = RadialGradient(0f, 0f, 1f, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        radialPaint.alpha = Color.WHITE

        animator.start()
    }

    private fun updateRadius(animation: TimeAnimator, totalTime: Long, deltaTime: Long) {
        when (totalTime) {
            in 0..radiusGradientTime -> {
                radius = max(width, height) * (totalTime / radiusGradientTime.toFloat())
            }
            in radiusGradientTime..(radiusGradientTime + fadeTime) -> {
                radialPaint.alpha = (0xff * (1 - (totalTime - radiusGradientTime) / fadeTime.toFloat())).toInt()
            }
            else -> {
                radius = 0f
                animation.end()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius == 0f)
            return

        canvas.save()
        canvas.translate(positionX.toFloat(), positionY.toFloat())
        canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, radialPaint)
        canvas.restore()
    }
}
