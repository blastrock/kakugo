package org.kaqui.settings

import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.LearningDbView

class ItemSelectionViewHolder(private val dbView: LearningDbView, v: View, private val statsFragment: StatsFragment) : RecyclerView.ViewHolder(v) {
    val enabled: CheckBox = v.findViewById(R.id.item_checkbox)
    val itemText: TextView = v.findViewById(R.id.item_text)
    val itemDescription: TextView = v.findViewById(R.id.item_description)
    var itemId: Int = 0

    init {
        enabled.setOnClickListener {
            dbView.setItemEnabled(itemId, enabled.isChecked)
            statsFragment.updateStats(dbView)
        }
    }
}