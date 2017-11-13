package org.kaqui.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import org.kaqui.KaquiDb
import org.kaqui.R
import org.kaqui.StatsFragment

class JlptLevelSelectionAdapter(private val context: Context) : BaseAdapter() {
    private val levels = (5 downTo 1).map { mapOf("label" to "JLPT level " + it.toString(), "level" to it) } +
            mapOf("label" to "Additional kanjis", "level" to 0)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.jlpt_level_item, parent, false)
        val textView = view.findViewById<TextView>(R.id.jlpt_level_label)
        textView.text = levels[position]["label"] as String
        val disabledCount = view.findViewById<TextView>(R.id.disabled_count)
        val badCount = view.findViewById<TextView>(R.id.bad_count)
        val mehCount = view.findViewById<TextView>(R.id.meh_count)
        val goodCount = view.findViewById<TextView>(R.id.good_count)
        val statsLayout = view.findViewById<LinearLayout>(R.id.stats_layout)
        statsLayout.elevation = 8.0f
        statsLayout.outlineProvider = ViewOutlineProvider.BOUNDS
        val db = KaquiDb.getInstance(context)
        StatsFragment.updateStats(db.kanjiView.getStats(levels[position]["level"] as Int), disabledCount, badCount, mehCount, goodCount, showDisabled = true)

        return view
    }

    override fun getItem(position: Int): Any {
        return levels[position]
    }

    override fun getItemId(position: Int): Long = (levels[position]["level"] as Int).toLong()

    override fun getCount(): Int = 6
}