package org.kaqui.settings

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.item_selection_activity.*
import org.kaqui.BaseActivity
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.Database
import org.kaqui.model.LearningDbView

class ItemSelectionActivity : BaseActivity() {
    private lateinit var dbView: LearningDbView
    private lateinit var listAdapter: ItemSelectionAdapter
    private lateinit var statsFragment: StatsFragment
    private lateinit var mode: Mode
    private var selectedLevel: Int? = null

    enum class Mode {
        HIRAGANA,
        KATAKANA,
        KANJI,
        WORD,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mode = intent.getSerializableExtra("mode") as Mode

        if (mode == Mode.KANJI || mode == Mode.WORD) {
            selectedLevel = intent.getIntExtra("level", -1)
            if (selectedLevel!! < 0)
                throw RuntimeException("Invalid level $selectedLevel")
        }

        setContentView(R.layout.item_selection_activity)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance()
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        dbView = when (mode) {
            Mode.HIRAGANA -> Database.getInstance(this).hiraganaView
            Mode.KATAKANA -> Database.getInstance(this).katakanaView
            Mode.KANJI -> Database.getInstance(this).getKanjiView(selectedLevel!!)
            Mode.WORD -> Database.getInstance(this).getWordView(selectedLevel!!)
        }

        listAdapter = ItemSelectionAdapter(dbView, this, statsFragment)
        item_list.adapter = listAdapter
        item_list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        listAdapter.setup()
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(dbView)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.item_selection_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.select_all -> {
                dbView.setAllEnabled(true)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats(dbView)
                return true
            }
            R.id.select_none -> {
                dbView.setAllEnabled(false)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats(dbView)
                return true
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "ItemSelectionActivity"
    }
}
