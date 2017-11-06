package org.kaqui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import kotlinx.android.synthetic.main.jlpt_selection_activity.*
import org.kaqui.StatsFragment
import org.kaqui.KanjiDb
import org.kaqui.R
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.widget.*


class KanjiSelectionActivity : AppCompatActivity() {
    private lateinit var db: KanjiDb
    private lateinit var listAdapter: KanjiSelectionAdapter
    private lateinit var statsFragment: StatsFragment

    private var isSearching = false
    private var selectedCategory: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.jlpt_selection_activity)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance(null)
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        db = KanjiDb.getInstance(this)

        jlpt_selection_list.adapter = JlptLevelSelectionAdapter(this)
        jlpt_selection_list.onItemClickListener = AdapterView.OnItemClickListener(this::onListItemClick)

        listAdapter = KanjiSelectionAdapter(this, statsFragment)
        kanji_list.adapter = listAdapter
        kanji_list.layoutManager = LinearLayoutManager(this)

        showCategoryList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.jlpt_selection_menu, menu)
        if (selectedCategory != null)
            menuInflater.inflate(R.menu.kanji_selection_menu, menu)

        val searchItem = menu.findItem(R.id.search)
        val searchView = searchItem.actionView as SearchView
        searchView.isSubmitButtonEnabled = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (isSearching)
                    searchKanjiList(newText)
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(p0: MenuItem?): Boolean {
                selectedCategory = null
                invalidateOptionsMenu()
                isSearching = true
                showKanjiList(null)
                return true
            }

            override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean {
                isSearching = false
                showCategoryList()
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.import_kanji_selection -> {
                importKanjis()
                return true
            }
            R.id.select_all -> {
                db.setLevelEnabled(selectedCategory!!, true)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats()
                return true
            }
            R.id.select_none -> {
                db.setLevelEnabled(selectedCategory!!, false)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats()
                return true
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
    }

    private fun showCategoryList() {
        jlpt_selection_list.visibility = View.VISIBLE
        kanji_list.visibility = View.GONE
        statsFragment.setLevel(null)
    }

    private fun showKanjiList(level: Int?) {
        jlpt_selection_list.visibility = View.GONE
        kanji_list.visibility = View.VISIBLE
        statsFragment.setLevel(level)
    }

    private fun searchKanjiList(str: String) {
        if (str.isEmpty())
            listAdapter.clearAll()
        else
            listAdapter.searchFor(str)
    }

    private fun onListItemClick(l: AdapterView<*>, v: View, position: Int, id: Long) {
        val item = l.adapter.getItem(position) as Map<String, Any>
        val level = item["level"] as Int

        showKanjiList(level)
        listAdapter.showLevel(level)
        selectedCategory = level
        invalidateOptionsMenu()
    }

    override fun onBackPressed() {
        if (isSearching)
            return
        if (selectedCategory != null) {
            selectedCategory = null
            showCategoryList()
            invalidateOptionsMenu()
            return
        }
        super.onBackPressed()
    }

    private fun importKanjis() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
                return
            }
        }

        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "file/*"
        startActivityForResult(intent, PICK_IMPORT_FILE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.all({ it == PackageManager.PERMISSION_GRANTED }))
            importKanjis()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != PICK_IMPORT_FILE)
            return super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null)
            return

        try {
            val kanjis = contentResolver.openInputStream(data.data).bufferedReader().readText()
            db.setSelection(kanjis)
        } catch (e: Exception) {
            Log.e(TAG, "Could not import file", e)
            Toast.makeText(this, "Could not import file: " + e.toString(), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private val TAG = this::class.java.simpleName!!

        private const val PICK_IMPORT_FILE = 1
    }
}
