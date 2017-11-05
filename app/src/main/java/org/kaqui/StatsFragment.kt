package org.kaqui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.stats_fragment.*

class StatsFragment : Fragment() {
    private var level: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (arguments != null && arguments.containsKey("level"))
            level = arguments.getInt("level")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.stats_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        updateStats()
    }

    fun setLevel(l: Int?) {
        level = l
        if (context != null)
            updateStats()
    }

    fun updateStats() {
        val db = KanjiDb.getInstance(context)
        val stats = db.getStats(level)
        val total = stats.bad + stats.meh + stats.good
        bad_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (stats.bad.toFloat() / total))
        meh_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (stats.meh.toFloat() / total))
        good_count.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, (stats.good.toFloat() / total))
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

    companion object {
        fun newInstance(level: Int?): StatsFragment {
            val fragment = StatsFragment()
            if (level != null)
                fragment.arguments.putInt("level", level)
            return fragment
        }
    }
}
