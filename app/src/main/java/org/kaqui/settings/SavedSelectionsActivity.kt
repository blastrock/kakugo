package org.kaqui.settings

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import org.jetbrains.anko.listView
import org.jetbrains.anko.toast
import org.kaqui.R
import org.kaqui.model.Database

class SavedSelectionsActivity: AppCompatActivity() {
    private lateinit var listView: ListView
    private var selectedItem: Database.KanjiSelection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Database.getInstance(this)
        listView = listView {
            adapter = SavedSelectionsAdapter(this@SavedSelectionsActivity, db.listKanjiSelections())
            onItemClickListener = AdapterView.OnItemClickListener(this@SavedSelectionsActivity::onListItemClick)
        }
        registerForContextMenu(listView)
    }

    private fun onListItemClick(l: AdapterView<*>, v: View, position: Int, id: Long) {
        val item = l.adapter.getItem(position) as Database.KanjiSelection

        val db = Database.getInstance(this)
        db.restoreKanjiSelectionFrom(id)

        toast(getString(R.string.loaded_selection, item.name))

        finish()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        val acmi = menuInfo as AdapterView.AdapterContextMenuInfo
        selectedItem = listView.adapter.getItem(acmi.position) as Database.KanjiSelection

        val inflater = menuInflater
        inflater.inflate(R.menu.selection_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val db = Database.getInstance(this)
        return when (item.itemId) {
            R.id.delete -> {
                db.deleteKanjiSelection(selectedItem!!.id)
                val adapter = (listView.adapter as SavedSelectionsAdapter)
                adapter.savedSelections = db.listKanjiSelections()
                adapter.notifyDataSetChanged()
                toast(getString(R.string.deleted_selection, selectedItem!!.name))
                true
            }
            else -> return super.onContextItemSelected(item)
        }
    }
}