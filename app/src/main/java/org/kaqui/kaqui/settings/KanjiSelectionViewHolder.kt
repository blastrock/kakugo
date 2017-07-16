package org.kaqui.kaqui.settings

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.kaqui.kaqui.R

class KanjiSelectionViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    val kanjiText: TextView = v.findViewById(R.id.kanji_item_text) as TextView
    val kanjiDescription: TextView = v.findViewById(R.id.kanji_item_description) as TextView
}