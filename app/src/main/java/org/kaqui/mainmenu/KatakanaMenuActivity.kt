package org.kaqui.mainmenu

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.widget.TextView
import org.jetbrains.anko.*
import org.kaqui.R
import org.kaqui.menuWidth
import org.kaqui.model.TestType
import org.kaqui.settings.ItemSelectionActivity
import org.kaqui.startTest
import java.io.Serializable

class KatakanaMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        verticalLayout {
            gravity = Gravity.CENTER

            scrollView {
                verticalLayout {
                    padding = dip(8)

                    textView(R.string.app_name) {
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        textSize = 50f
                    }.lparams(width = matchParent, height = wrapContent) {
                        margin = dip(8)
                    }

                    verticalLayout {
                        button(R.string.katakana_romaji_quizz) {
                            setOnClickListener { startTest(this@KatakanaMenuActivity, TestType.KATAKANA_TO_ROMAJI) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.romaji_katakana_quizz) {
                            setOnClickListener { startTest(this@KatakanaMenuActivity, TestType.ROMAJI_TO_KATAKANA) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.katakana_selection) {
                            setOnClickListener { startActivity<ItemSelectionActivity>("mode" to ItemSelectionActivity.Mode.KATAKANA as Serializable) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                    }
                }
            }.lparams(width = menuWidth)
        }
    }
}