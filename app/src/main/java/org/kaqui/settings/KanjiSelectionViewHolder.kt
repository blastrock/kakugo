package org.kaqui.settings

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import org.kaqui.StatsFragment
import org.kaqui.KanjiDb
import org.kaqui.R

class KanjiSelectionViewHolder(private val db: KanjiDb, v: View, private val statsFragment: StatsFragment) : RecyclerView.ViewHolder(v) {
    val enabled: CheckBox = v.findViewById<CheckBox>(R.id.kanji_item_checkbox)
    val kanjiText: TextView = v.findViewById<TextView>(R.id.kanji_item_text)
    val kanjiDescription: TextView = v.findViewById<TextView>(R.id.kanji_item_description)
    var kanji: String = ""

    init {
        enabled.setOnCheckedChangeListener { _, isChecked ->
            db.setKanjiEnabled(kanji, isChecked)
            statsFragment.updateStats()
        }
    }
}