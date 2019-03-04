package org.kaqui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
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
    val mainCoordinatorLayout: CoordinatorLayout
    lateinit var mainView: View
    lateinit var sessionScore: TextView
    lateinit var questionText: TextView
    lateinit var historyScrollView: NestedScrollView
    lateinit var historyView: LinearLayout
    lateinit var historyActionButton: FloatingActionButton

    init {
        with(activity) {
            mainCoordinatorLayout = coordinatorLayout {
                verticalLayout {
                    id = R.id.global_stats
                }.lparams(width = matchParent, height = wrapContent)
                mainView = mainBlock(this@TestLayout).lparams(width = matchParent, height = matchParent)
                historyScrollView = nestedScrollView {
                    id = R.id.history_scroll_view
                    backgroundColor = Color.rgb(0xcc, 0xcc, 0xcc)
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
                    setImageDrawable(resources.getDrawable(R.drawable.ic_arrow_upward))
                }.lparams(width = matchParent, height = wrapContent) {
                    anchorId = R.id.history_scroll_view
                    anchorGravity = Gravity.TOP or Gravity.RIGHT

                    marginEnd = dip(20)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        elevation = 12.0f
                    }
                }
            }
        }
    }

    fun <T : ViewManager> makeMainBlock(activity: Activity, subLayout: T, answersBlock: _LinearLayout.() -> View): LinearLayout {
        with(subLayout) {
            return verticalLayout {
                padding = dip(16)

                sessionScore = textView {
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                }
                activity.configuration(orientation = Orientation.LANDSCAPE) {
                    linearLayout {
                        gravity = Gravity.CENTER
                        questionText = textView {
                            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 10, 200, 10, TypedValue.COMPLEX_UNIT_SP)
                            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                            gravity = Gravity.CENTER
                        }.lparams(width = 0, height = matchParent, weight = 1f) {
                            bottomMargin = dip(8)
                        }
                        this.answersBlock().lparams(width = 0, height = matchParent, weight = 1f)
                    }.lparams(width = matchParent, height = matchParent)
                }
                activity.configuration(orientation = Orientation.PORTRAIT) {
                    questionText = textView {
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    }.lparams(width = wrapContent, height = wrapContent) {
                        bottomMargin = dip(8)
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                    this.answersBlock()
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
