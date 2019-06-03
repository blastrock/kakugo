package org.kaqui.testactivities

import android.app.Activity
import android.content.res.Configuration
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import org.jetbrains.anko.*
import org.kaqui.TypefaceManager
import org.kaqui.appCompatTextView

class TestQuestionLayout {
    lateinit var questionText: TextView

    fun <T : ViewManager> makeMainBlock(activity: Activity, subLayout: T, questionMinSize: Int, forceLandscape: Boolean = false, answersBlock: _LinearLayout.() -> View): LinearLayout {
        with(subLayout) {
            return verticalLayout {
                if (forceLandscape || activity.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    linearLayout {
                        gravity = Gravity.CENTER
                        questionText = appCompatTextView {
                            typeface = TypefaceManager.getTypeface(activity)
                            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, questionMinSize, 200, 10, TypedValue.COMPLEX_UNIT_SP)
                            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                            gravity = Gravity.CENTER
                        }.lparams(width = 0, height = matchParent, weight = 1f) {
                            bottomMargin = dip(8)
                        }
                        if (resources.configuration.screenWidthDp >= 1000) {
                            linearLayout {
                                gravity = Gravity.CENTER
                                this.answersBlock().lparams(width = dip(500 - 16), height = matchParent)
                            }.lparams(width = 0, height = matchParent, weight = 1f)
                        } else {
                            this.answersBlock().lparams(width = 0, height = matchParent, weight = 1f)
                        }
                    }.lparams(width = matchParent, height = matchParent)
                } else if (activity.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    val (weightQuestion, weightAnswers) =
                            when {
                                resources.configuration.screenHeightDp < 800 -> Pair(.25f, .75f)
                                resources.configuration.screenHeightDp < 1000 -> Pair(.4f, .6f)
                                else -> Pair(.5f, .5f)
                            }
                    gravity = Gravity.CENTER
                    questionText = appCompatTextView {
                        typeface = TypefaceManager.getTypeface(activity)
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, questionMinSize, 200, 10, TypedValue.COMPLEX_UNIT_SP)
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        gravity = Gravity.CENTER
                    }.lparams(width = matchParent, height = 0, weight = weightQuestion) {
                        bottomMargin = dip(8)
                    }
                    val answerWidth =
                            if (resources.configuration.screenWidthDp >= 500)
                                dip(500 - 32)
                            else
                                matchParent
                    this.answersBlock().lparams(width = answerWidth, height = 0, weight = weightAnswers)
                }
            }
        }
    }
}
