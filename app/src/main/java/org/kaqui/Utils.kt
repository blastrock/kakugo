package org.kaqui

import android.graphics.PathMeasure
import android.graphics.PointF
import org.kaqui.model.BAD_WEIGHT
import org.kaqui.model.GOOD_WEIGHT
import java.util.*
import kotlin.math.pow

fun getBackgroundFromScore(score: Double) =
        when (score) {
            in 0.0f..BAD_WEIGHT -> R.drawable.round_red
            in BAD_WEIGHT..GOOD_WEIGHT -> R.drawable.round_yellow
            in GOOD_WEIGHT..1.0f -> R.drawable.round_green
            else -> R.drawable.round_red
        }

fun <T> MutableList<T>.shuffle() {
    val rg = Random()
    for (i in this.size - 1 downTo 1) {
        val target = rg.nextInt(i)
        val tmp = this[i]
        this[i] = this[target]
        this[target] = tmp
    }
}

fun PointF.squaredDistanceTo(o: PointF): Float = (this.x - o.x).pow(2) + (this.y - o.y).pow(2)

fun PathMeasure.getPoint(position: Float): PointF {
    val out = floatArrayOf(0f, 0f)
    getPosTan(position, out, null)
    return PointF(out[0], out[1])
}
