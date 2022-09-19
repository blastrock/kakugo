package org.kaqui.settings

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Toast
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import org.jetbrains.anko.*
import org.kaqui.BaseActivity
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.Classification
import org.kaqui.model.Classifier
import org.kaqui.model.Database
import org.kaqui.model.LearningDbView
import java.io.Serializable
import kotlin.coroutines.CoroutineContext

class ClassSelectionActivity : BaseActivity(), CoroutineScope {
    private lateinit var dbView: LearningDbView
    private lateinit var statsFragment: StatsFragment
    private lateinit var mode: SelectionMode
    private lateinit var adapter: ClassSelectionAdapter

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()

        val classification = when (val classificationStr = PreferenceManager.getDefaultSharedPreferences(this).getString("item_classification", "jlpt")) {
            "jlpt" -> Classification.JlptLevel
            "rtk" -> Classification.RtkIndexRange
            "rtk6" -> Classification.Rtk6IndexRange
            else -> throw RuntimeException("unknown kanji classification: $classificationStr")
        }

        mode = intent.getSerializableExtra("mode") as SelectionMode

        dbView = when (mode) {
            SelectionMode.KANJI -> Database.getInstance(this).getKanjiView()
            SelectionMode.WORD -> Database.getInstance(this).getWordView()
        }

        verticalLayout {
            frameLayout {
                id = R.id.global_stats
            }.lparams(width = matchParent, height = wrapContent)
            listView {
                this@ClassSelectionActivity.adapter = ClassSelectionAdapter(context, dbView, classification)
                adapter = this@ClassSelectionActivity.adapter
                onItemClickListener = AdapterView.OnItemClickListener(this@ClassSelectionActivity::onListItemClick)
            }.lparams(width = matchParent, height = matchParent)
        }

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance()
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(dbView)
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, R.id.search, 0, R.string.jlpt_search)
                .setIcon(android.R.drawable.ic_menu_search)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

        menu.add(Menu.NONE, R.id.save_selection, 1, R.string.save_current_selection)
        menu.add(Menu.NONE, R.id.load_selection, 2, R.string.load_selection)
        menu.add(Menu.NONE, R.id.select_none, 3, R.string.select_none)
        if (mode == SelectionMode.WORD)
            menu.add(Menu.NONE, R.id.autoselect, 4, R.string.autoselect_from_kanji)
        menu.add(Menu.NONE, R.id.import_selection, 4, R.string.import_selection)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search -> {
                startActivity(Intent(this, ItemSearchActivity::class.java)
                        .putExtra("mode", when (mode) {
                            SelectionMode.KANJI -> ItemSearchActivity.Mode.KANJI
                            SelectionMode.WORD -> ItemSearchActivity.Mode.WORD
                        }))
                true
            }
            R.id.save_selection -> {
                alert {
                    title = getString(R.string.enter_name_of_selection)
                    var name: EditText? = null
                    customView = UI {
                        linearLayout {
                            name = editText {
                                inputType = InputType.TYPE_CLASS_TEXT
                            }.lparams(width = matchParent, height = wrapContent) {
                                horizontalMargin = dip(16)
                            }
                        }
                    }.view
                    positiveButton(android.R.string.ok) { saveSelection(name!!.text.toString()) }
                    negativeButton(android.R.string.cancel) {}
                }.show()
                true
            }
            R.id.load_selection -> {
                startActivity<SavedSelectionsActivity>("mode" to mode as Serializable)
                true
            }
            R.id.select_none -> {
                dbView.setAllEnabled(false)
                adapter.notifyDataSetChanged()
                statsFragment.updateStats(dbView)
                true
            }
            R.id.import_selection -> {
                importItems()
                true
            }
            R.id.autoselect -> {
                alert {
                    title = getString(R.string.override_selection_title)
                    message = getString(R.string.override_selection_msg)
                    positiveButton(android.R.string.yes) {
                        launch {
                            val progressDialog = ProgressDialog(this@ClassSelectionActivity)
                            progressDialog.setMessage(getString(R.string.autoselecting_words))
                            progressDialog.setCancelable(false)
                            progressDialog.show()

                            withContext(Dispatchers.Default) { Database.getInstance(this@ClassSelectionActivity).autoSelectWords() }

                            adapter.notifyDataSetChanged()
                            statsFragment.updateStats(dbView)

                            progressDialog.dismiss()
                        }
                    }
                    negativeButton(android.R.string.no) {}
                }.show()
                return true
            }
            else ->
                super.onOptionsItemSelected(item)
        }
    }

    private fun onListItemClick(l: AdapterView<*>, v: View, position: Int, id: Long) {
        val item = l.adapter.getItem(position) as Map<String, Any>
        val classifier = item["classifier"] as Classifier

        startActivity(Intent(this, ItemSelectionActivity::class.java)
                .putExtra("mode", when (mode) {
                    SelectionMode.KANJI -> ItemSelectionActivity.Mode.KANJI
                    SelectionMode.WORD -> ItemSelectionActivity.Mode.WORD
                } as Serializable)
                .putExtra("classifier", classifier))
    }

    private fun saveSelection(name: String) {
        when (mode) {
            SelectionMode.KANJI -> Database.getInstance(this).saveKanjiSelectionTo(name)
            SelectionMode.WORD -> Database.getInstance(this).saveWordSelectionTo(name)
        }
        toast(getString(R.string.saved_selection, name))
    }

    private fun importItems() {
        val msg =
                if (mode == SelectionMode.KANJI)
                    R.string.import_kanji_help
                else
                    R.string.import_words_help

        alert(msg) {
            okButton { showSelectFileForImport() }
        }.show()
    }

    private fun showSelectFileForImport() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
                return
            }
        }

        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, PICK_IMPORT_FILE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED })
            showSelectFileForImport()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != PICK_IMPORT_FILE)
            return super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null)
            return

        try {
            val items = contentResolver.openInputStream(data.data!!)!!.bufferedReader().readText()
            if (mode == SelectionMode.KANJI)
                Database.getInstance(this).setKanjiSelection(items)
            else
                Database.getInstance(this).setWordSelection(items)
        } catch (e: Exception) {
            Log.e(TAG, "Could not import file", e)
            Toast.makeText(this, getString(R.string.could_not_import_file, e.toString()), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "ClassSelectionActivity"

        private const val PICK_IMPORT_FILE = 1
    }
}
