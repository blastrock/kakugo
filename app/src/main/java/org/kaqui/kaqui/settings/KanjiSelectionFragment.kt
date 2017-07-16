package org.kaqui.kaqui.settings

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.kaqui.kaqui.KanjiDb

class KanjiSelectionFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        val db = KanjiDb.getInstance(context)
        val kanjis = db.getKanjisForJlptLevel(arguments.getInt("level"))

        val recyclerView = RecyclerView(context)
        recyclerView.adapter = KanjiSelectionAdapter(context, kanjis)
        recyclerView.layoutManager = LinearLayoutManager(context)
        return recyclerView
    }

    companion object {
        val TAG = this::class.java.simpleName

        fun newInstance(level: Int): KanjiSelectionFragment {
            val f = KanjiSelectionFragment()

            val args = Bundle()
            args.putInt("level", level)
            f.arguments = args

            return f
        }
    }
}