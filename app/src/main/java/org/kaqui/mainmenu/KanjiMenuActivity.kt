package org.kaqui.mainmenu

import android.os.Bundle
import android.view.Gravity
import org.jetbrains.anko.*
import org.kaqui.*
import org.kaqui.model.TestType
import org.kaqui.settings.ClassSelectionActivity
import java.io.Serializable

class KanjiMenuActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        verticalLayout {
            gravity = Gravity.CENTER

            scrollView {
                verticalLayout {
                    padding = dip(8)

                    appTitleImage(this@KanjiMenuActivity).lparams(width = matchParent, height = dip(80)) {
                        margin = dip(8)
                    }

                    verticalLayout {
                        button(R.string.kanji_to_reading) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_TO_READING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.reading_to_kanji) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.READING_TO_KANJI) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.kanji_to_meaning) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_TO_MEANING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.meaning_to_kanji) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.MEANING_TO_KANJI) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.kanji_composition) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_COMPOSITION) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.kanji_drawing) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_DRAWING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.custom_test) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, listOf(TestType.KANJI_TO_READING, TestType.READING_TO_KANJI, TestType.KANJI_TO_MEANING, TestType.MEANING_TO_KANJI, TestType.KANJI_COMPOSITION, TestType.KANJI_DRAWING)) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        separator(this@KanjiMenuActivity).lparams(height = dip(1)) { margin = dip(8) }
                        button(R.string.kanji_selection) {
                            setOnClickListener { startActivity<ClassSelectionActivity>("mode" to ClassSelectionActivity.Mode.KANJI as Serializable) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                    }.lparams(width = matchParent, height = matchParent)
                }
            }.lparams(width = menuWidth)
        }
    }
}
