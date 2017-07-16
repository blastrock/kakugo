package org.kaqui.kaqui.settings

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import org.kaqui.kaqui.KanjiDb
import org.kaqui.kaqui.R
import org.kaqui.kaqui.getKanjiDescription

class KanjiSelectionAdapter(val db: KanjiDb, val ids: List<Int>) : RecyclerView.Adapter<KanjiSelectionViewHolder>() {
    override fun getItemCount(): Int = ids.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KanjiSelectionViewHolder {
        return KanjiSelectionViewHolder(db, LayoutInflater.from(parent.context).inflate(R.layout.kanji_item, parent, false))
    }

    override fun onBindViewHolder(holder: KanjiSelectionViewHolder, position: Int) {
        val kanji = db.getKanji(ids[position])
        holder.kanji = kanji.kanji
        holder.enabled.isChecked = kanji.enabled
        holder.kanjiText.text = kanji.kanji
        holder.kanjiDescription.text = getKanjiDescription(kanji)
    }
}