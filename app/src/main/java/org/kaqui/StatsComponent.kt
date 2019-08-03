package org.kaqui

import android.view.ViewManager
import android.widget.LinearLayout
import android.widget.TextView
import org.jetbrains.anko.*

fun ViewManager.statsComponent() =
        linearLayout {
            orientation = LinearLayout.HORIZONTAL

            textView {
                id = R.id.disabled_count
                backgroundColor = context.getColorFromAttr(R.attr.backgroundDontKnow)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            }.lparams(width = wrapContent, height = wrapContent, weight = 1f)
            textView {
                id = R.id.bad_count
                backgroundColor = context.getColorFromAttr(R.attr.itemBad)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            }.lparams(width = wrapContent, height = wrapContent, weight = 1f)
            textView {
                id = R.id.meh_count
                backgroundColor = context.getColorFromAttr(R.attr.itemMeh)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            }.lparams(width = wrapContent, height = wrapContent, weight = 1f)
            textView {
                id = R.id.good_count
                backgroundColor = context.getColorFromAttr(R.attr.itemGood)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            }.lparams(width = wrapContent, height = wrapContent, weight = 1f)
        }
