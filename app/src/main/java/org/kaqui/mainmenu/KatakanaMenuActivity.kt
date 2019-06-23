package org.kaqui.mainmenu

import android.os.Bundle
import android.view.Gravity
import org.jetbrains.anko.*
import org.kaqui.*
import org.kaqui.model.TestType
import org.kaqui.settings.ItemSelectionActivity
import java.io.Serializable

class KatakanaMenuActivity : BaseActivity() {
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
                        button(R.string.katakana_to_romaji) {
                            setOnClickListener { startTest(this@KatakanaMenuActivity, TestType.KATAKANA_TO_ROMAJI) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.romaji_to_katakana) {
                            setOnClickListener { startTest(this@KatakanaMenuActivity, TestType.ROMAJI_TO_KATAKANA) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.katakana_to_romaji_typing) {
                            setOnClickListener { startTest(this@KatakanaMenuActivity, TestType.KATAKANA_TO_ROMAJI_TEXT) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.katakana_drawing) {
                            setOnClickListener { startTest(this@KatakanaMenuActivity, TestType.KATAKANA_DRAWING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        separator(this@KatakanaMenuActivity).lparams(height = dip(1)) { margin = dip(8) }
                        button(R.string.katakana_selection) {
                            setOnClickListener { startActivity<ItemSelectionActivity>("mode" to ItemSelectionActivity.Mode.KATAKANA as Serializable) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                    }
                }
            }.lparams(width = menuWidth)
        }
    }
}
