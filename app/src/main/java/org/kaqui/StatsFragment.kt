package org.kaqui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.stats_fragment.*
import org.kaqui.model.KaquiDb
import org.kaqui.model.LearningDbView

class StatsFragment : Fragment() {
    private var _mode: Mode = Mode.KANJI
    private var level: Int? = null
    private var showDisabled: Boolean = false

    enum class Mode {
        HIRAGANA,
        KATAKANA,
        KANJI,
        WORD,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (arguments != null && arguments!!.containsKey("level"))
            level = arguments!!.getInt("level")
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

    var mode: Mode
        get() = _mode
        set(m) {
            _mode = m
            if (context != null)
                updateStats()
        }

    fun setLevel(l: Int?) {
        level = l
        if (context != null)
            updateStats()
    }

    fun setShowDisabled(v: Boolean) {
        showDisabled = v
        if (context != null)
            updateStats()
    }

    fun updateStats() {
        val db = KaquiDb.getInstance(context!!)
        when (mode) {
            Mode.HIRAGANA ->
                updateStats(db.hiraganaView.getStats(null), disabled_count, bad_count, meh_count, good_count, showDisabled = showDisabled)
            Mode.KATAKANA ->
                updateStats(db.katakanaView.getStats(null), disabled_count, bad_count, meh_count, good_count, showDisabled = showDisabled)
            Mode.KANJI ->
                updateStats(db.kanjiView.getStats(level), disabled_count, bad_count, meh_count, good_count, showDisabled = showDisabled)
            Mode.WORD ->
                updateStats(db.wordView.getStats(null), disabled_count, bad_count, meh_count, good_count, showDisabled = showDisabled)
        }
    }

    companion object {
        fun newInstance(level: Int?): StatsFragment {
            val fragment = StatsFragment()
            if (level != null) {
                val args = Bundle()
                args.putInt("level", level)
                fragment.arguments = args
            }
            return fragment
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
