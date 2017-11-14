package org.kaqui.settings

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import org.kaqui.*
import org.kaqui.model.KaquiDb
import org.kaqui.model.description
import org.kaqui.model.text

class KanaSelectionAdapter(private val context: Context, private val statsFragment: StatsFragment) : RecyclerView.Adapter<ItemSelectionViewHolder>() {
    private val db = KaquiDb.getInstance(context)
    private var ids: List<Int> = listOf()

    fun setup() {
        ids = db.hiraganaView.getAllItems()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = ids.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ItemSelectionViewHolder(db.hiraganaView, LayoutInflater.from(parent.context).inflate(R.layout.selection_item, parent, false), statsFragment)

    override fun onBindViewHolder(holder: ItemSelectionViewHolder, position: Int) {
        val hiragana = db.getHiragana(ids[position])
        holder.itemId = hiragana.id
        holder.enabled.isChecked = hiragana.enabled
        holder.itemText.text = hiragana.text
        val background = getBackgroundFromScore(hiragana.shortScore)
        holder.itemText.background = ContextCompat.getDrawable(context, background)
        holder.itemDescription.text = hiragana.description
    }
}
