package org.kaqui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import com.github.mikephil.charting.charts.BarChart
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.ankoView
import org.kaqui.model.BAD_WEIGHT
import org.kaqui.model.Database
import org.kaqui.model.GOOD_WEIGHT
import org.kaqui.model.TestType
import org.kaqui.testactivities.DrawView
import org.kaqui.testactivities.TestActivity
import java.util.*
import kotlin.math.pow

fun getColorFromScore(score: Double) =
        when (score) {
            in 0.0f..BAD_WEIGHT -> R.attr.itemBad
            in BAD_WEIGHT..GOOD_WEIGHT -> R.attr.itemMeh
            in GOOD_WEIGHT..1.0f -> R.attr.itemGood
            else -> R.attr.itemBad
        }

fun getColoredCircle(context: Context, @AttrRes color: Int): Drawable {
    val drawable = ContextCompat.getDrawable(context, R.drawable.round)!!
    drawable.applyTint(context.getColorFromAttr(color))
    return drawable
}

fun unitStep(x: Double): Double =
        if (x < 0)
            0.0
        else
            1.0

fun lerp(a: Double, b: Double, r: Double): Double = a + (r * (b - a))
fun invLerp(a: Double, b: Double, r: Double): Double = (r - a) / (b - a)

fun secondsToDays(timestamp: Long) = timestamp / 3600.0 / 24.0
fun daysToSeconds(days: Long) = days * 3600.0 * 24.0

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

fun Button.setExtTint(@AttrRes attrColor: Int?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        if (attrColor != null) {
            backgroundTintMode = PorterDuff.Mode.MULTIPLY
            backgroundTintList = ColorStateList.valueOf(context.getColorFromAttr(attrColor))
        } else {
            backgroundTintMode = PorterDuff.Mode.MULTIPLY
            backgroundTintList = ColorStateList.valueOf(context.getColorFromAttr(R.attr.backgroundDontKnow))
        }
    }
}

inline fun ViewManager.appCompatTextView(init: AppCompatTextView.() -> Unit = {}): AppCompatTextView {
    return ankoView({ AppCompatTextView(it) }, theme = 0, init = init)
}

inline fun ViewManager.drawView(init: DrawView.() -> Unit = {}): DrawView {
    return ankoView({ DrawView(it) }, theme = 0, init = init)
}

inline fun ViewManager.barChart(init: BarChart.() -> Unit = {}): BarChart {
    return ankoView({ BarChart(it) }, theme = 0, init = init)
}

fun Calendar.roundToPreviousDay() {
    this.clear(Calendar.HOUR)
    this.clear(Calendar.HOUR_OF_DAY)
    this.clear(Calendar.MINUTE)
    this.clear(Calendar.SECOND)
    this.clear(Calendar.MILLISECOND)
    this.clear(Calendar.AM_PM)
}

fun _LinearLayout.separator(context: Context) =
        view {
            backgroundColor = ContextCompat.getColor(context, R.color.separator)
        }.lparams(width = matchParent, height = dip(1))

fun Drawable.applyTint(@ColorInt color: Int): Drawable {
    val mWrappedDrawable = DrawableCompat.wrap(this)
    DrawableCompat.setTint(mWrappedDrawable, color)
    DrawableCompat.setTintMode(mWrappedDrawable, PorterDuff.Mode.SRC_IN)
    return this
}

fun ViewManager.appTitleImage(context: Context) =
        imageView {
            val drawable = AppCompatResources.getDrawable(context, R.drawable.kakugo)!!
            drawable.applyTint(context.getColorFromAttr(android.R.attr.colorForeground))
            setImageDrawable(drawable)
        }

fun startTest(activity: Activity, types: List<TestType>) {
    val db = Database.getInstance(activity)
    if (TestEngine.getItemView(db, types[0]).getEnabledCount() < 10) {
        activity.longToast(R.string.enable_a_few_items)
        return
    }

    val sharedPrefs = activity.defaultSharedPreferences
    val defaultTypes = sharedPrefs.getStringSet("custom_test_types", setOf())!!

    val selected = types.filter { it.name in defaultTypes }.toMutableList()
    val checkedIndexes = types.map { v -> v in selected }.toBooleanArray()
    AlertDialog.Builder(activity)
            .setTitle(R.string.select_test_types)
            .setMultiChoiceItems(types.map { activity.getString(it.toName()) }.toTypedArray(), checkedIndexes) { _, which, isChecked ->
                if (isChecked)
                    selected.add(types[which])
                else
                    selected.remove(types[which])
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selected.isEmpty())
                    activity.alert(R.string.select_at_least_one_type) {
                        positiveButton(android.R.string.ok) {}
                    }.show()
                else {
                    sharedPrefs.edit(true) {
                        putStringSet("custom_test_types", selected.map { it.name }.toSet())
                    }
                    activity.startActivity<TestActivity>("test_types" to selected)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
}

fun startTest(activity: Activity, type: TestType) {
    val db = Database.getInstance(activity)
    if (TestEngine.getItemView(db, type).getEnabledCount() < 10) {
        activity.longToast(R.string.enable_a_few_items)
        return
    }
    activity.startActivity<TestActivity>("test_types" to listOf(type))
}

val Activity.menuWidth
    get() =
        if (resources.configuration.screenWidthDp >= 500)
            dip(500)
        else
            matchParent

@ColorInt
fun Context.getColorFromAttr(
        @AttrRes attrColor: Int
): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrColor, typedValue, true)
    return (
            if (typedValue.resourceId != 0)
                ContextCompat.getColor(this, typedValue.resourceId)
            else
                typedValue.data
            )
}

fun Int.asUnicodeCodePoint() = Character.toChars(this).joinToString("")

fun showItemProbabilityData(context: Context, item: String, probabilityData: TestEngine.DebugData) {
    AlertDialog.Builder(context)
            .setTitle("$item - ${item.codePointAt(0)}")
            .setMessage(
                    context.getString(R.string.debug_info,
                            probabilityData.probabilityData.daysSinceAsked,
                            probabilityData.probabilityData.longScore,
                            probabilityData.probabilityData.longWeight,
                            probabilityData.probabilityData.shortScore,
                            probabilityData.probabilityData.shortWeight,
                            probabilityData.probaParamsStage2.shortCoefficient,
                            probabilityData.probaParamsStage2.longCoefficient,
                            probabilityData.probabilityData.finalProbability,
                            probabilityData.totalWeight,
                            if (probabilityData.scoreUpdate != null)
                                secondsToDays(probabilityData.scoreUpdate!!.lastAsked - probabilityData.scoreUpdate!!.minLastAsked)
                            else
                                null,
                            probabilityData.scoreUpdate?.shortScore,
                            probabilityData.scoreUpdate?.longScore,
                            probabilityData.probaParamsStage2.minProbaShort))
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

fun TestType.toName(): Int =
        when (this) {
            TestType.HIRAGANA_TO_ROMAJI -> R.string.hiragana_to_romaji_title
            TestType.ROMAJI_TO_HIRAGANA -> R.string.romaji_to_hiragana_title
            TestType.HIRAGANA_TO_ROMAJI_TEXT -> R.string.hiragana_to_romaji_typing_title
            TestType.HIRAGANA_DRAWING -> R.string.hiragana_drawing_title
            TestType.KATAKANA_TO_ROMAJI -> R.string.katakana_to_romaji_title
            TestType.ROMAJI_TO_KATAKANA -> R.string.romaji_to_katakana_title
            TestType.KATAKANA_TO_ROMAJI_TEXT -> R.string.katakana_to_romaji_typing_title
            TestType.KATAKANA_DRAWING -> R.string.katakana_drawing_title

            TestType.KANJI_TO_READING -> R.string.kanji_to_reading_title
            TestType.KANJI_TO_MEANING -> R.string.kanji_to_meaning_title
            TestType.READING_TO_KANJI -> R.string.reading_to_kanji_title
            TestType.MEANING_TO_KANJI -> R.string.meaning_to_kanji_title
            TestType.KANJI_COMPOSITION -> R.string.kanji_composition_title
            TestType.KANJI_DRAWING -> R.string.kanji_drawing_title

            TestType.WORD_TO_READING -> R.string.word_to_reading_title
            TestType.WORD_TO_MEANING -> R.string.word_to_meaning_title
            TestType.READING_TO_WORD -> R.string.reading_to_word_title
            TestType.MEANING_TO_WORD -> R.string.meaning_to_word_title
        }
