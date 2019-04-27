package org.kaqui.mainmenu

import android.os.Bundle
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import org.jetbrains.anko.*
import org.kaqui.*
import org.kaqui.model.TestType
import org.kaqui.settings.ItemSelectionActivity
import java.io.Serializable

class HiraganaMenuActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        verticalLayout {
            gravity = Gravity.CENTER

            scrollView {
                verticalLayout {
                    padding = dip(8)

                    appTitleImage(this@HiraganaMenuActivity).lparams(width = matchParent, height = dip(80)) {
                        margin = dip(8)
                    }

                    verticalLayout {
                        button(R.string.hiragana_romaji_quizz) {
                            setOnClickListener { startTest(this@HiraganaMenuActivity, TestType.HIRAGANA_TO_ROMAJI) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.romaji_hiragana_quizz) {
                            setOnClickListener { startTest(this@HiraganaMenuActivity, TestType.ROMAJI_TO_HIRAGANA) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.hiragana_drawing_quizz) {
                            setOnClickListener { startTest(this@HiraganaMenuActivity, TestType.HIRAGANA_WRITING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.hiragana_selection) {
                            setOnClickListener { startActivity<ItemSelectionActivity>("mode" to ItemSelectionActivity.Mode.HIRAGANA as Serializable) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                    }
                }
            }.lparams(width = menuWidth)
        }
    }
}
