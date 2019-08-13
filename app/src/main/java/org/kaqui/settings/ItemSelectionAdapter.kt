package org.kaqui.settings

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.jetbrains.anko.*
import org.kaqui.*
import org.kaqui.model.LearningDbView
import org.kaqui.model.description
import org.kaqui.model.text

class ItemSelectionAdapter(private val view: LearningDbView, private val context: Context, private val statsFragment: StatsFragment) : RecyclerView.Adapter<ItemSelectionViewHolder>() {
    private val ankoContext = AnkoContext.createReusable(context, this)

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemSelectionViewHolder {
        val selectionItem = ankoContext.apply {
            verticalLayout {
                linearLayout {
                    orientation = LinearLayout.HORIZONTAL
                    verticalPadding = dip(6)
                    horizontalPadding = dip(8)

                    checkBox {
                        id = R.id.item_checkbox
                        scaleX = 1.5f
                        scaleY = 1.5f
                    }.lparams(width = wrapContent, height = wrapContent) {
                        gravity = Gravity.CENTER
                        margin = dip(8)
                    }
                    textView {
                        id = R.id.item_text
                        typeface = TypefaceManager.getTypeface(context)
                        textSize = 25f
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        gravity = Gravity.CENTER
                    }.lparams(width = sp(35), height = sp(35)) {
                        margin = dip(8)
                        gravity = Gravity.CENTER
                    }
                    textView {
                        id = R.id.item_description
                        typeface = TypefaceManager.getTypeface(context)
                    }.lparams(width = matchParent, height = wrapContent, weight = 1f) {
                        gravity = Gravity.CENTER_VERTICAL
                        horizontalMargin = dip(8)
                    }
                }.lparams(width = matchParent, height = wrapContent)

                separator(context)
            }
        }.view

        return ItemSelectionViewHolder(view, selectionItem, statsFragment)
    }

    override fun onBindViewHolder(holder: ItemSelectionViewHolder, position: Int) {
        val item = view.getItem(ids[position])
        holder.itemId = item.id
        holder.enabled.isChecked = item.enabled
        holder.itemText.text = item.text
        if (item.text.length > 1)
            (holder.itemText.layoutParams as LinearLayout.LayoutParams).width = LinearLayout.LayoutParams.WRAP_CONTENT
        holder.itemText.background = getColoredCircle(context, getColorFromScore(item.shortScore))
        holder.itemDescription.text = item.description
    }
}
