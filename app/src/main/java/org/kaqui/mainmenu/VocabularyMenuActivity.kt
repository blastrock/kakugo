package org.kaqui.mainmenu

import android.os.Bundle
import android.view.Gravity
import org.jetbrains.anko.*
import org.kaqui.*
import org.kaqui.model.TestType
import org.kaqui.settings.ClassSelectionActivity
import java.io.Serializable

class VocabularyMenuActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        verticalLayout {
            gravity = Gravity.CENTER

            scrollView {
                verticalLayout {
                    padding = dip(8)

                    appTitleImage(this@VocabularyMenuActivity).lparams(width = matchParent, height = dip(80)) {
                        margin = dip(8)
                    }

                    verticalLayout {
                        button(R.string.word_to_reading) {
                            setOnClickListener { startTest(this@VocabularyMenuActivity, TestType.WORD_TO_READING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.reading_to_word) {
                            setOnClickListener { startTest(this@VocabularyMenuActivity, TestType.READING_TO_WORD) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.word_to_meaning) {
                            setOnClickListener { startTest(this@VocabularyMenuActivity, TestType.WORD_TO_MEANING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.meaning_to_word) {
                            setOnClickListener { startTest(this@VocabularyMenuActivity, TestType.MEANING_TO_WORD) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        separator(this@VocabularyMenuActivity).lparams(height = dip(1)) { margin = dip(8) }
                        button(R.string.word_selection) {
                            setOnClickListener { startActivity<ClassSelectionActivity>("mode" to ClassSelectionActivity.Mode.WORD as Serializable) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                    }
                }
            }.lparams(width = menuWidth)
        }
    }
}
