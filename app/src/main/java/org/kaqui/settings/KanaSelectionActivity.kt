package org.kaqui.settings

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.kana_selection_activity.*
import org.kaqui.model.KaquiDb
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.LearningDbView

class KanaSelectionActivity : AppCompatActivity() {
    private lateinit var dbView: LearningDbView
    private lateinit var listAdapter: KanaSelectionAdapter
    private lateinit var statsFragment: StatsFragment

    enum class Mode {
        HIRAGANA,
        KATAKANA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getSerializableExtra("mode") as Mode

        setContentView(R.layout.kana_selection_activity)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance(null)
        statsFragment.mode = when (mode) {
            Mode.HIRAGANA -> StatsFragment.Mode.HIRAGANA
            Mode.KATAKANA -> StatsFragment.Mode.KATAKANA
        }
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        dbView = when (mode) {
            Mode.HIRAGANA -> KaquiDb.getInstance(this).hiraganaView
            Mode.KATAKANA -> KaquiDb.getInstance(this).katakanaView
        }

        listAdapter = KanaSelectionAdapter(dbView, this, statsFragment)
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
                dbView.setAllEnabled(true)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats()
                return true
            }
            R.id.select_none -> {
                dbView.setAllEnabled(false)
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
