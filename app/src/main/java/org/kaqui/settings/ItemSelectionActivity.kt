package org.kaqui.settings

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.jetbrains.anko.*
import org.kaqui.BaseActivity
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.databinding.ItemSelectionActivityBinding
import org.kaqui.model.Classifier
import org.kaqui.model.Database
import org.kaqui.model.LearningDbView

class ItemSelectionActivity : BaseActivity() {
    private lateinit var dbView: LearningDbView
    private lateinit var listAdapter: ItemSelectionAdapter
    private lateinit var statsFragment: StatsFragment
    private lateinit var mode: Mode
    private var classifier: Classifier? = null

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
            classifier = intent.getParcelableExtra("classifier")
        }

        val binding = ItemSelectionActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance()
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        dbView = when (mode) {
            Mode.HIRAGANA -> Database.getInstance(this).getHiraganaView()
            Mode.KATAKANA -> Database.getInstance(this).getKatakanaView()
            Mode.KANJI -> Database.getInstance(this).getKanjiView(classifier = classifier!!)
            Mode.WORD -> Database.getInstance(this).getWordView(classifier = classifier!!)
        }

        listAdapter = ItemSelectionAdapter(dbView, this, statsFragment)
        binding.itemList.adapter = listAdapter
        binding.itemList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        listAdapter.setup()
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(dbView)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (mode in arrayOf(Mode.KANJI, Mode.WORD)) {
            menu.add(Menu.NONE, R.id.search, 1, R.string.jlpt_search)
                    .setIcon(android.R.drawable.ic_menu_search)
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        if (mode == Mode.WORD) {
            menu.add(Menu.NONE, R.id.autoselect, 2, R.string.autoselect_from_kanji)
        }

        menu.add(Menu.NONE, R.id.select_all, 3, R.string.select_all)
        menu.add(Menu.NONE, R.id.select_none, 4, R.string.select_none)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.search -> {
                startActivity<ItemSearchActivity>(
                        "mode" to (when (mode) {
                            Mode.KANJI -> ItemSearchActivity.Mode.KANJI
                            Mode.WORD -> ItemSearchActivity.Mode.WORD
                            else -> throw RuntimeException("Can't search on this item type!")
                        }))
                return true
            }
            R.id.autoselect -> {
                alert {
                    titleResource = R.string.override_selection_title
                    messageResource = R.string.override_selection_msg
                    positiveButton(android.R.string.yes) {
                        Database.getInstance(this@ItemSelectionActivity).autoSelectWords(classifier!!)
                        listAdapter.notifyDataSetChanged()
                        statsFragment.updateStats(dbView)
                    }
                    negativeButton(android.R.string.no) {}
                }.show()
                return true
            }
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
