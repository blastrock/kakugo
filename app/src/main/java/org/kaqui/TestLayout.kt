package org.kaqui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.ContextCompat
import android.support.v4.widget.NestedScrollView
import android.support.v4.widget.TextViewCompat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import android.widget.LinearLayout
import android.widget.TextView
import org.jetbrains.anko.*
import org.jetbrains.anko.design._CoordinatorLayout
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.support.v4.nestedScrollView

class TestLayout(activity: Activity, mainBlock: _CoordinatorLayout.(testLayout: TestLayout) -> View) {
    lateinit var mainCoordinatorLayout: CoordinatorLayout
    lateinit var overlay: FadeOverlay
    lateinit var mainView: View
    lateinit var sessionScore: TextView
    lateinit var questionText: TextView
    lateinit var historyScrollView: NestedScrollView
    lateinit var historyView: LinearLayout
    lateinit var historyActionButton: FloatingActionButton

    init {
        with(activity) {
            frameLayout {
                mainCoordinatorLayout = coordinatorLayout {
                    verticalLayout {
                        id = R.id.global_stats
                    }.lparams(width = matchParent, height = wrapContent)
                    mainView = mainBlock(this@TestLayout).lparams(width = matchParent, height = matchParent)
                    historyScrollView = nestedScrollView {
                        id = R.id.history_scroll_view
                        backgroundColor = this@with.getColorFromAttr(R.attr.historyBackground)
                        historyView = verticalLayout().lparams(width = matchParent, height = wrapContent)
                    }.lparams(width = matchParent, height = matchParent) {
                        val bottomSheetBehavior = BottomSheetBehavior<NestedScrollView>()
                        bottomSheetBehavior.peekHeight = 0
                        bottomSheetBehavior.isHideable = false
                        behavior = bottomSheetBehavior
                    }
                    historyActionButton = floatingActionButton {
                        size = FloatingActionButton.SIZE_MINI
                        scaleX = 0f
                        scaleY = 0f
                        setImageResource(R.drawable.ic_arrow_upward)
                    }.lparams(width = matchParent, height = wrapContent) {
                        anchorId = R.id.history_scroll_view
                        @SuppressLint("RtlHardcoded")
                        anchorGravity = Gravity.TOP or Gravity.RIGHT

                        marginEnd = dip(20)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            elevation = 12.0f
                        }
                    }
                }
                this@TestLayout.overlay = fadeOverlay {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        elevation = 100f
                    }
                }
            }
        }
    }

    fun <T : ViewManager> makeMainBlock(activity: Activity, subLayout: T, questionMinSize: Int, answersBlock: _LinearLayout.() -> View): LinearLayout {
        with(subLayout) {
            return verticalLayout {
                padding = dip(16)

                sessionScore = textView {
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                }
                activity.configuration(orientation = Orientation.LANDSCAPE) {
                    linearLayout {
                        gravity = Gravity.CENTER
                        questionText = appCompatTextView {
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
                }
                activity.configuration(orientation = Orientation.PORTRAIT) {
                    val (weightQuestion, weightAnswers) =
                            when {
                                resources.configuration.screenHeightDp < 800 -> Pair(.25f, .75f)
                                resources.configuration.screenHeightDp < 1000 -> Pair(.4f, .6f)
                                else -> Pair(.5f, .5f)
                            }
                    gravity = Gravity.CENTER
                    questionText = appCompatTextView {
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, questionMinSize, 200, 2, TypedValue.COMPLEX_UNIT_SP)
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
}
