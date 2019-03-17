package org.kaqui.mainmenu

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.widget.TextView
import org.jetbrains.anko.*
import org.kaqui.R
import org.kaqui.model.TestType
import org.kaqui.settings.ItemSelectionActivity
import org.kaqui.settings.JlptSelectionActivity
import org.kaqui.startTest
import java.io.Serializable

class KanjiMenuActivity : AppCompatActivity() {
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
                        button(R.string.kanji_reading_quizz) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_TO_READING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.reading_kanji_quizz) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.READING_TO_KANJI) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.kanji_meaning_quizz) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_TO_MEANING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.meaning_kanji_quizz) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.MEANING_TO_KANJI) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.kanji_composition_test) {
                            setOnClickListener { startTest(this@KanjiMenuActivity, TestType.KANJI_COMPOSITION) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.kanji_drawing_quizz) {
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
            }
        }
    }
}