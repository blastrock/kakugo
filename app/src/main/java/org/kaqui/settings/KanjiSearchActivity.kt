package org.kaqui.settings

import android.os.Bundle
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.view.ContextThemeWrapper
import kotlinx.android.synthetic.main.kanji_selection_activity.*
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.KaquiDb

class KanjiSearchActivity : AppCompatActivity() {
    private lateinit var db: KaquiDb
    private lateinit var listAdapter: ItemSelectionAdapter
    private lateinit var statsFragment: StatsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.kanji_selection_activity)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance()
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        db = KaquiDb.getInstance(this)

        listAdapter = ItemSelectionAdapter(db.kanjiView, this, statsFragment)
        kanji_list.adapter = listAdapter
        kanji_list.layoutManager = LinearLayoutManager(this)

        val searchView = SearchView(ContextThemeWrapper(this, R.style.ThemeOverlay_AppCompat_Dark))
        searchView.isSubmitButtonEnabled = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchKanjiList(newText)
                return true
            }
        })
        // never collapse the searchView
        searchView.setOnCloseListener { true }
        searchView.isIconified = false
        searchView.queryHint = resources.getString(R.string.search_kanji_hint)
        supportActionBar!!.customView = searchView
        supportActionBar!!.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM or ActionBar.DISPLAY_HOME_AS_UP
        searchView.requestFocusFromTouch()
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(db.kanjiView)
    }

    private fun searchKanjiList(str: String) {
        if (str.isEmpty())
            listAdapter.clearAll()
        else
            listAdapter.searchFor(str)
    }

    companion object {
        private const val TAG = "KanjiSearchActivity"
    }
}
