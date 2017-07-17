package org.kaqui.kaqui.settings

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.search_fragment.*
import org.kaqui.kaqui.R

class KanjiSearchFragment : Fragment() {
    private lateinit var listAdapter: KanjiSelectionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        return inflater.inflate(R.layout.search_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        listAdapter = KanjiSelectionAdapter(context)
        kanji_list.adapter = listAdapter
        kanji_list.layoutManager = LinearLayoutManager(context)

        search_field.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isEmpty())
                    listAdapter.clearAll()
                else
                    listAdapter.searchFor(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })
    }

    companion object {
        private val TAG = this::class.java.simpleName!!

        fun newInstance(): KanjiSearchFragment {
            val f = KanjiSearchFragment()
            return f
        }
    }
}