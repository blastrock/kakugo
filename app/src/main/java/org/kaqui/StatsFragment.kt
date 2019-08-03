package org.kaqui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.jetbrains.anko.support.v4.UI
import org.kaqui.model.LearningDbView

class StatsFragment : androidx.fragment.app.Fragment() {
    private var showDisabled: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return UI {
            statsComponent()
        }.view
    }

    fun setShowDisabled(v: Boolean) {
        showDisabled = v
    }

    fun updateStats(dbView: LearningDbView) {
        updateStats(this.view!!, dbView.getStats(), showDisabled = showDisabled)
    }

    companion object {
        fun newInstance(): StatsFragment {
            return StatsFragment()
        }

        fun updateStats(statsComponent: View, stats: LearningDbView.Stats, showDisabled: Boolean = false) {
            updateStats(stats, statsComponent.findViewById(R.id.disabled_count), statsComponent.findViewById(R.id.bad_count), statsComponent.findViewById(R.id.meh_count), statsComponent.findViewById(R.id.good_count), showDisabled)
        }

        fun updateStats(stats: LearningDbView.Stats, disabled_count: TextView, bad_count: TextView, meh_count: TextView, good_count: TextView, showDisabled: Boolean = false) {
            val disabledCount =
                    if (showDisabled)
                        stats.disabled
                    else
                        0
            val total = stats.bad + stats.meh + stats.good + disabledCount
            disabled_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (disabledCount.toFloat() / total))
            bad_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (stats.bad.toFloat() / total))
            meh_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (stats.meh.toFloat() / total))
            good_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (stats.good.toFloat() / total))
            disabled_count.text =
                    if (disabledCount > 0)
                        disabledCount.toString()
                    else
                        ""
            bad_count.text =
                    if (stats.bad > 0)
                        stats.bad.toString()
                    else
                        ""
            meh_count.text =
                    if (stats.meh > 0)
                        stats.meh.toString()
                    else
                        ""
            good_count.text =
                    if (stats.good > 0)
                        stats.good.toString()
                    else
                        ""
        }
    }
}
