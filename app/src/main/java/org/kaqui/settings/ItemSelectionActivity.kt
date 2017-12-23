package org.kaqui.settings

import android.app.ProgressDialog
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.item_selection_activity.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.kaqui.model.KaquiDb
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.LearningDbView

class ItemSelectionActivity : AppCompatActivity() {
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

        if (mode == Mode.KANJI) {
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
            Mode.HIRAGANA -> KaquiDb.getInstance(this).hiraganaView
            Mode.KATAKANA -> KaquiDb.getInstance(this).katakanaView
            Mode.KANJI -> KaquiDb.getInstance(this).getKanjiView(selectedLevel!!)
            Mode.WORD -> KaquiDb.getInstance(this).wordView
        }

        listAdapter = ItemSelectionAdapter(dbView, this, statsFragment)
        item_list.adapter = listAdapter
        item_list.layoutManager = LinearLayoutManager(this)
        listAdapter.setup()
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(dbView)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.kanji_selection_menu, menu)
        if (mode == Mode.WORD) {
            val autoselect = menu.findItem(R.id.autoselect)
            autoselect.isVisible = true
        }
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
            R.id.autoselect -> {
                async(UI) {
                    val progressDialog = ProgressDialog(this@ItemSelectionActivity)
                    progressDialog.setMessage(getString(R.string.autoselecting_words))
                    progressDialog.setCancelable(false)
                    progressDialog.show()

                    async(CommonPool) { KaquiDb.getInstance(this@ItemSelectionActivity).autoSelectWords() }.await()

                    listAdapter.notifyDataSetChanged()
                    statsFragment.updateStats(dbView)

                    progressDialog.dismiss()
                }
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
