package org.kaqui.mainmenu

import android.os.Bundle
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import org.jetbrains.anko.*
import org.kaqui.R
import org.kaqui.appTitleImage
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

                    appTitleImage(this@KatakanaMenuActivity).lparams(width = matchParent, height = dip(80)) {
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
                        button(R.string.katakana_drawing_quizz) {
                            setOnClickListener { startTest(this@KatakanaMenuActivity, TestType.KATAKANA_WRITING) }
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