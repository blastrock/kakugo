package org.kaqui.settings

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.kana_selection_activity.*
import org.kaqui.KaquiDb
import org.kaqui.R
import org.kaqui.StatsFragment

class KanaSelectionActivity : AppCompatActivity() {
    private lateinit var db: KaquiDb
    private lateinit var listAdapter: KanaSelectionAdapter
    private lateinit var statsFragment: StatsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.kana_selection_activity)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance(null)
        statsFragment.setHiraganaMode(true)
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        db = KaquiDb.getInstance(this)

        listAdapter = KanaSelectionAdapter(this, statsFragment)
        item_list.adapter = listAdapter
        item_list.layoutManager = LinearLayoutManager(this)
        listAdapter.setup()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.kanji_selection_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.select_all -> {
                db.hiraganaView.setAllEnabled(true)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats()
                return true
            }
            R.id.select_none -> {
                db.hiraganaView.setAllEnabled(false)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats()
                return true
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "KanaSelectionActivity"
    }
}
