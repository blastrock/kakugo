package org.kaqui.settings

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.*

class ClassSelectionAdapter(private val context: Context, private val dbView: LearningDbView, classification: Classification) : BaseAdapter() {
    private val levels = getClassifiers(classification).map { mapOf("label" to it.name(context), "classifier" to it) }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.jlpt_level_item, parent, false)
        val textView = view.findViewById<TextView>(R.id.jlpt_level_label)
        textView.text = levels[position]["label"] as String
        val disabledCount = view.findViewById<TextView>(R.id.disabled_count)
        val badCount = view.findViewById<TextView>(R.id.bad_count)
        val mehCount = view.findViewById<TextView>(R.id.meh_count)
        val goodCount = view.findViewById<TextView>(R.id.good_count)
        val statsLayout = view.findViewById<LinearLayout>(R.id.stats_layout)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            statsLayout.elevation = 8.0f
            statsLayout.outlineProvider = ViewOutlineProvider.BOUNDS
        }
        StatsFragment.updateStats(dbView.withClassifier(levels[position]["classifier"] as Classifier).getStats(), disabledCount, badCount, mehCount, goodCount, showDisabled = true)

        return view
    }

    override fun getItem(position: Int): Any {
        return levels[position]
    }

    override fun getItemId(position: Int): Long = 0L

    override fun getCount(): Int = levels.size
}