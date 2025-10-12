package org.kaqui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.PreferenceManager
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.kaqui.model.Database
import org.kaqui.model.TestType
import org.kaqui.testactivities.TestActivity
import java.io.Serializable
import java.util.Calendar

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

fun Calendar.roundToPreviousDay() {
    this.clear(Calendar.HOUR)
    this.clear(Calendar.HOUR_OF_DAY)
    this.clear(Calendar.MINUTE)
    this.clear(Calendar.SECOND)
    this.clear(Calendar.MILLISECOND)
    this.clear(Calendar.AM_PM)
}

@Composable
fun Separator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier // Apply external modifier first
            .fillMaxWidth()    // width = matchParent
            .height(1.dp)      // height = dip(1)
            .background(color = colorResource(id = R.color.separator)) // backgroundColor = ...
    )
}

fun Drawable.applyTint(@ColorInt color: Int): Drawable {
    val mWrappedDrawable = DrawableCompat.wrap(this)
    DrawableCompat.setTint(mWrappedDrawable, color)
    DrawableCompat.setTintMode(mWrappedDrawable, PorterDuff.Mode.SRC_IN)
    return this
}

@Composable
fun AppTitleImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember {
        AppCompatResources.getDrawable(context, R.drawable.kakugo)!!
    }
    val foregroundColor = MaterialTheme.colors.onSurface

    val tintedDrawable = remember(foregroundColor) {
        drawable.mutate().also {
            DrawableCompat.setTint(it, foregroundColor.toArgb())
            DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
        }
    }

    Image(
        painter = rememberDrawablePainter(tintedDrawable),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun TopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.h6
            )
        },
        navigationIcon = onBackClick?.let {
            {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = actions,
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary,
        elevation = 4.dp
    )
}

fun startTest(activity: Context, types: List<TestType>) {
    val db = Database.getInstance(activity)
    if (TestEngine.getItemView(activity, db, types[0]).getEnabledCount() < 10) {
        Toast.makeText(activity, R.string.enable_a_few_items, Toast.LENGTH_LONG).show()
        return
    }

    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
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
                    AlertDialog.Builder(activity)
                        .setMessage(R.string.select_at_least_one_type)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
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

fun startTest(activity: Context, type: TestType) {
    val db = Database.getInstance(activity)
    if (TestEngine.getItemView(activity, db, type).getEnabledCount() < 10) {
        Toast.makeText(activity, R.string.enable_a_few_items, Toast.LENGTH_LONG).show()
        return
    }
    activity.startActivity<TestActivity>("test_types" to listOf(type))
}

val Activity.menuWidth
    get() =
        if (resources.configuration.screenWidthDp >= 500)
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500f, resources.displayMetrics).toInt()
        else
            ViewGroup.LayoutParams.MATCH_PARENT

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

inline fun <reified T : Activity> Context.startActivity(vararg params: Pair<String, Any?>) {
    val intent = Intent(this, T::class.java)
    params.forEach { (key, value) ->
        when (value) {
            is String -> intent.putExtra(key, value)
            is Int -> intent.putExtra(key, value)
            is Boolean -> intent.putExtra(key, value)
            is Serializable -> intent.putExtra(key, value)
            is Parcelable -> intent.putExtra(key, value)
        }
    }
    startActivity(intent)
}

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

// Button with long press support
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BetterButton(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val viewConfiguration = LocalViewConfiguration.current

    LaunchedEffect(interactionSource) {
        var isLongClick = false

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isLongClick = false
                    delay(viewConfiguration.longPressTimeoutMillis)
                    isLongClick = true
                    onLongPress()
                }

                is PressInteraction.Release -> {
                    if (isLongClick.not()) {
                        onClick()
                    }
                }
            }
        }
    }

    Button(
        onClick = {},
        interactionSource = interactionSource,
        modifier = modifier,
        enabled = enabled,
        content = content,
        colors = colors,
        contentPadding = contentPadding,
    )
}
