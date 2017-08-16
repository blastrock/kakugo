package org.kaqui.settings

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import org.kaqui.*

class KanjiSelectionAdapter(private val context: Context, private val statsFragment: GlobalStatsFragment) : RecyclerView.Adapter<KanjiSelectionViewHolder>() {
    private val db = KanjiDb.getInstance(context)
    private var ids: List<Int> = listOf()

    fun searchFor(text: String) {
        ids = db.search(text)
        notifyDataSetChanged()
    }

    fun clearAll() {
        ids = listOf()
        notifyDataSetChanged()
    }

    fun showLevel(level: Int) {
        ids = db.getKanjisForJlptLevel(level)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = ids.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KanjiSelectionViewHolder {
        return KanjiSelectionViewHolder(db, LayoutInflater.from(parent.context).inflate(R.layout.kanji_item, parent, false), statsFragment)
    }

    override fun onBindViewHolder(holder: KanjiSelectionViewHolder, position: Int) {
        val kanji = db.getKanji(ids[position])
        holder.kanji = kanji.kanji
        holder.enabled.isChecked = kanji.enabled
        holder.kanjiText.text = kanji.kanji
        val background =
                when (kanji.weight) {
                    in 0.0f..BAD_WEIGHT -> R.drawable.round_red
                    in BAD_WEIGHT..GOOD_WEIGHT -> R.drawable.round_yellow
                    in GOOD_WEIGHT..1.0f -> R.drawable.round_green
                    else -> R.drawable.round_red
                }
        holder.kanjiText.background = ContextCompat.getDrawable(context, background)
        holder.kanjiDescription.text = getKanjiDescription(kanji)
    }
}