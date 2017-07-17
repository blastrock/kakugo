package org.kaqui.kaqui.settings

import android.os.Bundle
import android.support.v4.app.ListFragment
import android.view.View
import android.widget.ListView
import android.widget.SimpleAdapter

class JlptSelectionFragment : ListFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemList = (5 downTo 1).map { mapOf("label" to "JLPT level " + it.toString(), "level" to it) } +
                mapOf("label" to "Additional kanjis", "level" to 0) +
                mapOf("label" to "Search")
        listAdapter = SimpleAdapter(
                context,
                itemList,
                android.R.layout.simple_list_item_1,
                arrayOf("label"),
                intArrayOf(android.R.id.text1))
    }

    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)

        val item = listAdapter.getItem(position) as Map<String, Any>

        if ("level" !in item) { // search
            fragmentManager.beginTransaction()
                    .replace(android.R.id.content, KanjiSearchFragment.newInstance())
                    .addToBackStack("kanjiSearch")
                    .commit()
        } else {
            fragmentManager.beginTransaction()
                    .replace(android.R.id.content, KanjiSelectionFragment.newInstance(item["level"] as Int))
                    .addToBackStack("kanjiSelection")
                    .commit()
        }
    }

    companion object {
        private val TAG = this::class.java.simpleName!!

        fun newInstance(): JlptSelectionFragment {
            val f = JlptSelectionFragment()
            return f
        }
    }
}