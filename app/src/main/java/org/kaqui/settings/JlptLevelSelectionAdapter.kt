package org.kaqui.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.kaqui.R

class JlptLevelSelectionAdapter(private val context: Context) : BaseAdapter() {
    private val levels = (5 downTo 1).map { mapOf("label" to "JLPT level " + it.toString(), "level" to it) } +
            mapOf("label" to "Additional kanjis", "level" to 0)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.jlpt_level_item, parent, false)
        val textView = view.findViewById<TextView>(R.id.jlpt_level_label)
        textView.text = levels[position]["label"] as String

        return view
    }

    override fun getItem(position: Int): Any {
        return levels[position]
    }

    override fun getItemId(position: Int): Long = (levels[position]["level"] as Int).toLong()

    override fun getCount(): Int = 6
}