package org.kaqui.mainmenu

import android.os.Bundle
import android.view.Gravity
import org.jetbrains.anko.*
import org.kaqui.*
import org.kaqui.model.TestType
import org.kaqui.settings.JlptSelectionActivity
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
                        button(R.string.kanji_reading_quiz) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_TO_READING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.reading_kanji_quiz) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.READING_TO_KANJI) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.kanji_meaning_quiz) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_TO_MEANING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.meaning_kanji_quiz) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.MEANING_TO_KANJI) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.kanji_composition_test) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_COMPOSITION) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.kanji_drawing_quiz) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_WRITING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.kanji_selection) {
                            setOnClickListener { startActivity<JlptSelectionActivity>("mode" to JlptSelectionActivity.Mode.KANJI as Serializable) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                    }.lparams(width = matchParent, height = matchParent)
                }
            }.lparams(width = menuWidth)
        }
    }
}
