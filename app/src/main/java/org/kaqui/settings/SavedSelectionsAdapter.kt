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
import org.jetbrains.anko.*
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.KaquiDb
import org.kaqui.model.LearningDbView

class SavedSelectionsAdapter(private val context: Context, var savedSelections: List<KaquiDb.KanjiSelection>) : BaseAdapter() {
    private val ankoContext = AnkoContext.createReusable(context, this)

    private fun createView() = ankoContext.apply {
        textView {
            paddingHorizontal = dip(16)
            paddingVertical = dip(12)
        }
    }.view

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = (convertView ?: createView()) as TextView
        view.text = savedSelections[position].name
        return view
    }

    override fun getItem(position: Int): KaquiDb.KanjiSelection {
        return savedSelections[position]
    }

    override fun getItemId(position: Int): Long = savedSelections[position].id

    override fun getCount(): Int = savedSelections.size
}