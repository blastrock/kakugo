package org.kaqui.kaqui.settings

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import org.kaqui.kaqui.KanjiDb
import org.kaqui.kaqui.R

class KanjiSelectionFragment : Fragment() {
    private lateinit var db: KanjiDb
    private lateinit var listAdapter: KanjiSelectionAdapter

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        setHasOptionsMenu(true)

        db = KanjiDb.getInstance(context)

        listAdapter = KanjiSelectionAdapter(context)
        listAdapter.showLevel(arguments.getInt("level"))

        val recyclerView = RecyclerView(context)
        recyclerView.adapter = listAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        return recyclerView
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.kanji_selection_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.select_all -> {
                db.setLevelEnabled(arguments.getInt("level"), true)
                listAdapter.notifyDataSetChanged()
                return true
            }
            R.id.select_none -> {
                db.setLevelEnabled(arguments.getInt("level"), false)
                listAdapter.notifyDataSetChanged()
                return true
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val TAG = this::class.java.simpleName!!

        fun newInstance(level: Int): KanjiSelectionFragment {
            val f = KanjiSelectionFragment()

            val args = Bundle()
            args.putInt("level", level)
            f.arguments = args

            return f
        }
    }
}