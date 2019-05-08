package org.kaqui.testactivities

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.jetbrains.anko.*
import org.jetbrains.anko.design._CoordinatorLayout
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.support.v4.nestedScrollView
import org.kaqui.*

class TestLayout(activity: Activity, mainBlock: _CoordinatorLayout.(testLayout: TestLayout) -> View) {
    lateinit var mainCoordinatorLayout: androidx.coordinatorlayout.widget.CoordinatorLayout
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
                        frameLayout {
                            id = R.id.global_stats
                        }.lparams(width = matchParent, height = wrapContent)
                        sessionScore = textView {
                            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        }
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
        val tql = TestQuestionLayout()
        val view = tql.makeMainBlock(activity, subLayout, questionMinSize, answersBlock)
        questionText = tql.questionText
        return view
    }
}
