package org.kaqui.settings

import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import org.jetbrains.anko.listView
import org.jetbrains.anko.toast
import org.kaqui.BaseActivity
import org.kaqui.R
import org.kaqui.model.Database

class SavedSelectionsActivity: BaseActivity() {
    private lateinit var listView: ListView
    private var selectedItem: Database.SavedSelection? = null

    private lateinit var mode: Mode

    enum class Mode {
        KANJI,
        WORD,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mode = Mode.valueOf(intent.getStringExtra("org.kaqui.MODE"))
        val db = Database.getInstance(this)
        listView = listView {
            adapter = SavedSelectionsAdapter(this@SavedSelectionsActivity, when (mode){
                Mode.KANJI -> db.listKanjiSelections()
                Mode.WORD -> db.listWordSelections()
            })
            onItemClickListener = AdapterView.OnItemClickListener(this@SavedSelectionsActivity::onListItemClick)
        }
        registerForContextMenu(listView)
    }

    private fun onListItemClick(l: AdapterView<*>, v: View, position: Int, id: Long) {
        val item = l.adapter.getItem(position) as Database.SavedSelection

        val db = Database.getInstance(this)

        when (mode) {
            Mode.KANJI -> db.restoreKanjiSelectionFrom(id)
            Mode.WORD -> db.restoreWordSelectionFrom(id)
        }

        toast(getString(R.string.loaded_selection, item.name))

        finish()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        val acmi = menuInfo as AdapterView.AdapterContextMenuInfo
        selectedItem = listView.adapter.getItem(acmi.position) as Database.SavedSelection

        val inflater = menuInflater
        inflater.inflate(R.menu.selection_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val db = Database.getInstance(this)
        return when (item.itemId) {
            R.id.delete -> {
                when (mode) {
                    Mode.KANJI -> db.deleteKanjiSelection(selectedItem!!.id)
                    Mode.WORD -> db.deleteWordSelection(selectedItem!!.id)
                }
                val adapter = (listView.adapter as SavedSelectionsAdapter)
                adapter.savedSelections = when (mode) {
                    Mode.KANJI -> db.listKanjiSelections()
                    Mode.WORD -> db.listWordSelections()
                }
                adapter.notifyDataSetChanged()
                toast(getString(R.string.deleted_selection, selectedItem!!.name))
                true
            }
            else -> return super.onContextItemSelected(item)
        }
    }
}
