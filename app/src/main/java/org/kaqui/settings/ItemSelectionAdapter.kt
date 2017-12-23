package org.kaqui.settings

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import org.kaqui.*
import org.kaqui.model.LearningDbView
import org.kaqui.model.description
import org.kaqui.model.text

class ItemSelectionAdapter(private val view: LearningDbView, private val context: Context, private val statsFragment: StatsFragment) : RecyclerView.Adapter<ItemSelectionViewHolder>() {
    private var ids: List<Int> = listOf()

    fun setup() {
        ids = view.getAllItems()
        notifyDataSetChanged()
    }

    fun searchFor(text: String) {
        ids = view.search(text)
        notifyDataSetChanged()
    }

    fun clearAll() {
        ids = listOf()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = ids.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ItemSelectionViewHolder(view, LayoutInflater.from(parent.context).inflate(R.layout.selection_item, parent, false), statsFragment)

    override fun onBindViewHolder(holder: ItemSelectionViewHolder, position: Int) {
        val item = view.getItem(ids[position])
        holder.itemId = item.id
        holder.enabled.isChecked = item.enabled
        holder.itemText.text = item.text
        if (item.text.length > 1)
            (holder.itemText.layoutParams as RelativeLayout.LayoutParams).width = LinearLayout.LayoutParams.WRAP_CONTENT
        val background = getBackgroundFromScore(item.shortScore)
        holder.itemText.background = ContextCompat.getDrawable(context, background)
        holder.itemDescription.text = item.description
    }
}
