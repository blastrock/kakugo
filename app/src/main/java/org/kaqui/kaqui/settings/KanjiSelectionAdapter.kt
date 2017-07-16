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

class KanjiSelectionAdapter(private val context: Context, private val ids: List<Int>) : RecyclerView.Adapter<KanjiSelectionViewHolder>() {
    val db = KanjiDb.getInstance(context)

    override fun getItemCount(): Int = ids.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KanjiSelectionViewHolder {
        return KanjiSelectionViewHolder(db, LayoutInflater.from(parent.context).inflate(R.layout.kanji_item, parent, false))
    }

    override fun onBindViewHolder(holder: KanjiSelectionViewHolder, position: Int) {
        val kanji = db.getKanji(ids[position])
        holder.kanji = kanji.kanji
        holder.enabled.isChecked = kanji.enabled
        holder.kanjiText.text = kanji.kanji
        val background =
                when (kanji.weight) {
                    in 0.0f..0.3f -> R.drawable.round_red
                    in 0.3f..0.8f -> R.drawable.round_yellow
                    in 0.8f..1.0f -> R.drawable.round_green
                    else -> R.drawable.round_red
                }
        holder.kanjiText.background = ContextCompat.getDrawable(context, background)
        holder.kanjiDescription.text = getKanjiDescription(kanji)
    }
}