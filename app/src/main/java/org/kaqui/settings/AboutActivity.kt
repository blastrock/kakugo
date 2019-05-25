package org.kaqui.settings

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import org.jetbrains.anko.*
import org.kaqui.BaseActivity
import org.kaqui.R
import org.kaqui.appTitleImage

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scrollView {
            verticalLayout {
                appTitleImage(context).lparams(width = matchParent, height = dip(80)) {
                    margin = dip(8)
                }

                textView {
                    text = Html.fromHtml(getString(R.string.about_text))
                    movementMethod = LinkMovementMethod.getInstance()
                }.lparams {
                    margin = dip(16)
                }
            }
        }
    }
}