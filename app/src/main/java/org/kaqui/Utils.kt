package org.kaqui

import android.content.res.ColorStateList
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.PorterDuff
import android.os.Build
import android.support.annotation.ColorRes
import android.support.v7.widget.AppCompatTextView
import android.view.ViewManager
import android.widget.Button
import org.jetbrains.anko.custom.ankoView
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

fun lerp(a: Float, b: Float, r: Float): Float = a + (r * (b - a))

fun <T> MutableList<T>.shuffle() {
    val rg = Random()
    for (i in this.size - 1 downTo 1) {
        val target = rg.nextInt(i)
        val tmp = this[i]
        this[i] = this[target]
        this[target] = tmp
    }
}

fun <T> pickRandom(list: List<T>, sample: Int, avoid: Set<T> = setOf()): List<T> {
    if (sample > list.size - avoid.size)
        throw RuntimeException("can't get a sample of size $sample on list of size ${list.size - avoid.size}")

    val chosen = mutableSetOf<T>()
    while (chosen.size < sample) {
        val r = list[(Math.random() * list.size).toInt()]
        if (r !in avoid)
            chosen.add(r)
    }
    return chosen.toList()
}

fun PointF.squaredDistanceTo(o: PointF): Float = (this.x - o.x).pow(2) + (this.y - o.y).pow(2)

fun PathMeasure.getPoint(position: Float): PointF {
    val out = floatArrayOf(0f, 0f)
    getPosTan(position, out, null)
    return PointF(out[0], out[1])
}

fun Button.setExtTint(@ColorRes color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        backgroundTintMode = PorterDuff.Mode.MULTIPLY
        backgroundTintList = ColorStateList.valueOf(resources.getColor(color))
    }
}

inline fun ViewManager.appCompatTextView(init: AppCompatTextView.() -> Unit = {}): AppCompatTextView {
    return ankoView({ AppCompatTextView(it) }, theme = 0, init = init)
}
