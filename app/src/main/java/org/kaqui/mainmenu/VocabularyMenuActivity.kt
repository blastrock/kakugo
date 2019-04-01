package org.kaqui.mainmenu

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.widget.TextView
import org.jetbrains.anko.*
import org.kaqui.R
import org.kaqui.menuWidth
import org.kaqui.model.TestType
import org.kaqui.settings.JlptSelectionActivity
import org.kaqui.startTest
import java.io.Serializable

class VocabularyMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        verticalLayout {
            gravity = Gravity.CENTER

            scrollView {
                verticalLayout {
                    padding = dip(8)

                    imageView(R.drawable.kakugo).lparams(width = matchParent, height = dip(80)) {
                        margin = dip(8)
                    }

                    verticalLayout {
                        button(R.string.word_reading_quizz) {
                            setOnClickListener { startTest(this@VocabularyMenuActivity, TestType.WORD_TO_READING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.reading_word_quizz) {
                            setOnClickListener { startTest(this@VocabularyMenuActivity, TestType.READING_TO_WORD) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.word_meaning_quizz) {
                            setOnClickListener { startTest(this@VocabularyMenuActivity, TestType.WORD_TO_MEANING) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.meaning_word_quizz) {
                            setOnClickListener { startTest(this@VocabularyMenuActivity, TestType.MEANING_TO_WORD) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.word_selection) {
                            setOnClickListener { startActivity<JlptSelectionActivity>("mode" to JlptSelectionActivity.Mode.WORD as Serializable) }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                    }
                }
            }.lparams(width = menuWidth)
        }
    }
}