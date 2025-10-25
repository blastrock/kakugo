package org.kaqui.testactivities

import android.content.res.Configuration
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import org.kaqui.TypefaceManager

@Composable
fun TestQuestionLayoutCompose(
    question: String,
    questionMinSizeSp: Int,
    forceLandscape: Boolean = false,
    onQuestionLongClick: (() -> Unit)? = null,
    answersBlock: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current

    if (forceLandscape || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(bottom = 8.dp),
                factory = { context ->
                    androidx.appcompat.widget.AppCompatTextView(context).apply {
                        text = question
                        typeface = TypefaceManager.getTypeface(context)
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this,
                            questionMinSizeSp,
                            200,
                            10,
                            TypedValue.COMPLEX_UNIT_SP
                        )
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        gravity = Gravity.CENTER
                        onQuestionLongClick?.let { callback ->
                            setOnLongClickListener {
                                callback()
                                true
                            }
                        }
                    }
                },
                update = { view ->
                    view.text = question
                })

            val answerHeightMod = { modifier: Modifier ->
                if (configuration.screenWidthDp >= 1000)
                    modifier.width(500.dp - 16.dp)
                else
                    modifier.fillMaxHeight()
            }

            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .let(answerHeightMod),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                answersBlock()
            }
        }
    } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        val (weightQuestion, weightAnswers) =
            when {
                configuration.screenHeightDp < 800 -> Pair(.25f, .75f)
                configuration.screenHeightDp < 1000 -> Pair(.4f, .6f)
                else -> Pair(.5f, .5f)
            }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .weight(weightQuestion)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                factory = { context ->
                    androidx.appcompat.widget.AppCompatTextView(context).apply {
                        text = question
                        typeface = TypefaceManager.getTypeface(context)
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this,
                            questionMinSizeSp,
                            200,
                            10,
                            TypedValue.COMPLEX_UNIT_SP
                        )
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        gravity = Gravity.CENTER
                        onQuestionLongClick?.let { callback ->
                            setOnLongClickListener {
                                callback()
                                true
                            }
                        }
                    }
                },
                update = { view ->
                    view.text = question
                })

            val answerWidthMod = { modifier: Modifier ->
                if (configuration.screenWidthDp >= 500)
                    modifier.width(500.dp - 32.dp)
                else
                    modifier.fillMaxWidth()
            }

            Column(
                modifier = Modifier
                    .weight(weightAnswers)
                    .let(answerWidthMod),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                answersBlock()
            }
        }
    }
}
