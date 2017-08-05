package org.kaqui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.SimpleAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.jlpt_selection_fragment.*
import org.kaqui.GlobalStatsFragment
import org.kaqui.KanjiDb
import org.kaqui.R

class JlptSelectionFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        childFragmentManager.beginTransaction()
                .replace(R.id.global_stats, GlobalStatsFragment.newInstance())
                .commit()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        val view = inflater.inflate(R.layout.jlpt_selection_fragment, container, false)
        setHasOptionsMenu(true)

        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val itemList = (5 downTo 1).map { mapOf("label" to "JLPT level " + it.toString(), "level" to it) } +
                mapOf("label" to "Additional kanjis", "level" to 0) +
                mapOf("label" to "Search")
        jlpt_selection_list.adapter = SimpleAdapter(
                context,
                itemList,
                android.R.layout.simple_list_item_1,
                arrayOf("label"),
                intArrayOf(android.R.id.text1))

        jlpt_selection_list.onItemClickListener = AdapterView.OnItemClickListener(this::onListItemClick)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.jlpt_selection_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.import_kanji_selection -> {
                importKanjis()
                return true
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
    }

    private fun onListItemClick(l: AdapterView<*>, v: View, position: Int, id: Long) {
        val item = l.adapter.getItem(position) as Map<String, Any>

        if ("level" !in item) { // search
            fragmentManager.beginTransaction()
                    .replace(android.R.id.content, KanjiSearchFragment.newInstance())
                    .addToBackStack("kanjiSearch")
                    .commit()
        } else {
            fragmentManager.beginTransaction()
                    .replace(android.R.id.content, KanjiSelectionFragment.newInstance(item["level"] as Int))
                    .addToBackStack("kanjiSelection")
                    .commit()
        }
    }

    private fun importKanjis() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
            val kanjis = context.contentResolver.openInputStream(data.data).bufferedReader().readText()
            KanjiDb.getInstance(context).setSelection(kanjis)
        } catch (e: Exception) {
            Log.e(TAG, "Could not import file", e)
            Toast.makeText(context, "Could not import file: " + e.toString(), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private val TAG = this::class.java.simpleName!!

        private const val PICK_IMPORT_FILE = 1

        fun newInstance(): JlptSelectionFragment {
            val f = JlptSelectionFragment()
            return f
        }
    }
}
