package org.kaqui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.PorterDuff
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.ankoView
import org.kaqui.model.*
import org.kaqui.testactivities.*
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
        textColor = ContextCompat.getColor(context, R.color.answerTextColor)
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

fun _LinearLayout.separator(context: Context) =
    view {
        backgroundColor = ContextCompat.getColor(context, R.color.separator)
    }.lparams(width = matchParent, height = dip(1))

inline fun ViewManager.appTitleImage(context: Context) =
        imageView {
            val drawable = AppCompatResources.getDrawable(context, R.drawable.kakugo)!!
            val mWrappedDrawable = DrawableCompat.wrap(drawable)
            DrawableCompat.setTint(mWrappedDrawable, context.getColorFromAttr(android.R.attr.colorForeground))
            DrawableCompat.setTintMode(mWrappedDrawable, PorterDuff.Mode.SRC_IN)
            setImageDrawable(drawable)
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
        TestType.KANJI_WRITING, TestType.HIRAGANA_WRITING, TestType.KATAKANA_WRITING -> activity.startActivity<QuizTestActivity>("test_type" to type)
        TestType.KANJI_COMPOSITION -> activity.startActivity<QuizTestActivity>("test_type" to TestType.KANJI_COMPOSITION)
        TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.KATAKANA_TO_ROMAJI_TEXT -> activity.startActivity<TextTestActivity>("test_type" to type)
        else -> activity.startActivity<QuizTestActivity>("test_type" to type)
    }
}

val Activity.menuWidth
    get() =
        if (resources.configuration.screenWidthDp >= 500)
            dip(500)
        else
            matchParent

fun Context.getColorFromAttr(
        @AttrRes attrColor: Int
): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrColor, typedValue, true)
    return typedValue.data
}

fun Int.asUnicodeCodePoint() = Character.toChars(this).joinToString("")

fun showItemProbabilityData(context: Context, item: String, probabilityData: TestEngine.DebugData) {
    AlertDialog.Builder(context)
            .setTitle(item)
            .setMessage(
                    context.getString(R.string.debug_info,
                            probabilityData.probabilityData.daysSinceCorrect,
                            probabilityData.probabilityData.longScore,
                            probabilityData.probabilityData.longWeight,
                            probabilityData.probabilityData.shortScore,
                            probabilityData.probabilityData.shortWeight,
                            probabilityData.probaParamsStage2.shortCoefficient,
                            probabilityData.probaParamsStage2.longCoefficient,
                            probabilityData.probabilityData.finalProbability,
                            probabilityData.totalWeight,
                            probabilityData.scoreUpdate?.shortScore,
                            probabilityData.scoreUpdate?.longScore))
            .setPositiveButton(android.R.string.ok, null)
            .show()
}

fun wrapInScrollView(subLayout: _LinearLayout, block: _ScrollView.() -> Unit): LinearLayout {
    with(subLayout) {
        return verticalLayout {
            gravity = Gravity.CENTER

            scrollView {
                block()
            }.lparams(width = matchParent, height = wrapContent, weight = 0f)
        }.lparams(width = matchParent, height = matchParent)
    }
}
