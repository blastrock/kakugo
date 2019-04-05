package org.kaqui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.PorterDuff
import android.os.Build
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatTextView
import android.view.ViewManager
import android.widget.Button
import org.jetbrains.anko.custom.ankoView
import org.jetbrains.anko.dip
import org.jetbrains.anko.longToast
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.startActivity
import org.kaqui.model.*
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
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
    }
}

inline fun ViewManager.appCompatTextView(init: AppCompatTextView.() -> Unit = {}): AppCompatTextView {
    return ankoView({ AppCompatTextView(it) }, theme = 0, init = init)
}

inline fun ViewManager.drawView(init: DrawView.() -> Unit = {}): DrawView {
    return ankoView({ DrawView(it) }, theme = 0, init = init)
}

inline fun ViewManager.fadeOverlay(init: FadeOverlay.() -> Unit = {}): FadeOverlay {
    return ankoView({ FadeOverlay(it) }, theme = 0, init = init)
}

fun Certainty.toColorRes() =
        when (this) {
            Certainty.DONTKNOW -> R.color.feedbackDontKnow
            Certainty.MAYBE -> R.color.feedbackMaybe
            Certainty.SURE -> R.color.feedbackSure
        }

fun startTest(activity: Activity, type: TestType) {
    val db = Database.getInstance(activity)
    if (TestEngine.getItemView(db, type).getEnabledCount() < 10) {
        activity.longToast(R.string.enable_a_few_items)
    } else when (type) {
        TestType.KANJI_WRITING, TestType.HIRAGANA_WRITING, TestType.KATAKANA_WRITING -> activity.startActivity<WritingTestActivity>()
        TestType.KANJI_COMPOSITION -> activity.startActivity<CompositionTestActivity>()
        else -> activity.startActivity<TestActivity>("test_type" to type)
    }
}

val Activity.menuWidth
        get() =
            if (resources.configuration.screenWidthDp >= 500)
                dip(500)
            else
                matchParent
