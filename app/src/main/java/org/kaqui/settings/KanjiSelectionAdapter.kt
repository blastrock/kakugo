package org.kaqui.settings

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import org.kaqui.*
import org.kaqui.model.KaquiDb

class KanjiSelectionAdapter(private val context: Context, private val statsFragment: StatsFragment) : RecyclerView.Adapter<ItemSelectionViewHolder>() {
    private val db = KaquiDb.getInstance(context)
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
        ids = db.kanjiView.getItemsForLevel(level)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = ids.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ItemSelectionViewHolder(db.kanjiView, LayoutInflater.from(parent.context).inflate(R.layout.selection_item, parent, false), statsFragment)

    override fun onBindViewHolder(holder: ItemSelectionViewHolder, position: Int) {
        val kanji = db.getKanji(ids[position])
        holder.itemId = kanji.id
        holder.enabled.isChecked = kanji.enabled
        holder.itemText.text = getItemText(kanji)
        val background = getBackgroundFromScore(kanji.shortScore)
        holder.itemText.background = ContextCompat.getDrawable(context, background)
        holder.itemDescription.text = getItemDescription(kanji)
    }
}