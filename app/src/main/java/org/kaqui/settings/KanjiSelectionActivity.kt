package org.kaqui.settings

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.kanji_selection_activity.*
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.KaquiDb
import org.kaqui.model.LearningDbView

class KanjiSelectionActivity : AppCompatActivity() {
    private lateinit var dbView: LearningDbView
    private lateinit var listAdapter: ItemSelectionAdapter
    private lateinit var statsFragment: StatsFragment

    private var selectedLevel: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedLevel = intent.getIntExtra("level", -1)
        if (selectedLevel < 0)
            throw RuntimeException("Invalid level $selectedLevel")

        setContentView(R.layout.kanji_selection_activity)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance(selectedLevel)
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        dbView = KaquiDb.getInstance(this).getKanjiView(selectedLevel)

        listAdapter = ItemSelectionAdapter(dbView, this, statsFragment)
        kanji_list.adapter = listAdapter
        kanji_list.layoutManager = LinearLayoutManager(this)
        listAdapter.setup()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.kanji_selection_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.select_all -> {
                dbView.setAllEnabled(true)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats()
                true
            }
            R.id.select_none -> {
                dbView.setAllEnabled(false)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats()
                true
            }
            else ->
                super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "KanjiSelectionActivity"
    }
}
