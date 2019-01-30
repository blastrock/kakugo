package org.kaqui.settings

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import kotlinx.android.synthetic.main.jlpt_selection_activity.*
import kotlinx.coroutines.*
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.model.KaquiDb
import org.kaqui.model.LearningDbView
import java.io.Serializable
import kotlin.coroutines.CoroutineContext

class JlptSelectionActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var dbView: LearningDbView
    private lateinit var statsFragment: StatsFragment
    private lateinit var mode: Mode

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    enum class Mode {
        KANJI,
        WORD,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()

        mode = intent.getSerializableExtra("mode") as Mode

        setContentView(R.layout.jlpt_selection_activity)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance()
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        dbView = when (mode) {
            Mode.KANJI -> KaquiDb.getInstance(this).kanjiView
            Mode.WORD -> KaquiDb.getInstance(this).wordView
        }

        jlpt_selection_list.adapter = JlptLevelSelectionAdapter(this, dbView)
        jlpt_selection_list.onItemClickListener = AdapterView.OnItemClickListener(this::onListItemClick)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(dbView)
        (jlpt_selection_list.adapter as JlptLevelSelectionAdapter).notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(when (mode) {
            Mode.KANJI -> R.menu.kanji_jlpt_selection_menu
            Mode.WORD -> R.menu.word_jlpt_selection_menu
        }, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search -> {
                startActivity(Intent(this, ItemSearchActivity::class.java)
                        .putExtra("mode", when (mode) {
                            Mode.KANJI -> ItemSearchActivity.Mode.KANJI
                            Mode.WORD -> ItemSearchActivity.Mode.WORD
                        }))
                true
            }
            R.id.import_kanji_selection -> {
                importKanjis()
                true
            }
            R.id.autoselect -> {
                launch {
                    val progressDialog = ProgressDialog(this@JlptSelectionActivity)
                    progressDialog.setMessage(getString(R.string.autoselecting_words))
                    progressDialog.setCancelable(false)
                    progressDialog.show()

                    withContext(Dispatchers.Default) { KaquiDb.getInstance(this@JlptSelectionActivity).autoSelectWords() }

                    (jlpt_selection_list.adapter as JlptLevelSelectionAdapter).notifyDataSetChanged()
                    statsFragment.updateStats(dbView)

                    progressDialog.dismiss()
                }
                return true
            }
            else ->
                super.onOptionsItemSelected(item)
        }
    }

    private fun onListItemClick(l: AdapterView<*>, v: View, position: Int, id: Long) {
        val item = l.adapter.getItem(position) as Map<String, Any>
        val level = item["level"] as Int

        startActivity(Intent(this, ItemSelectionActivity::class.java)
                .putExtra("mode", when (mode) {
                    Mode.KANJI -> ItemSelectionActivity.Mode.KANJI
                    Mode.WORD -> ItemSelectionActivity.Mode.WORD
                } as Serializable)
                .putExtra("level", level))
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
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED })
            importKanjis()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != PICK_IMPORT_FILE)
            return super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null)
            return

        try {
            val kanjis = contentResolver.openInputStream(data.data).bufferedReader().readText()
            KaquiDb.getInstance(this).setSelection(kanjis)
        } catch (e: Exception) {
            Log.e(TAG, "Could not import file", e)
            Toast.makeText(this, "Could not import file: $e", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "JlptSelectionActivity"

        private const val PICK_IMPORT_FILE = 1
    }
}
