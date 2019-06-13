package org.kaqui.settings

import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.core.text.HtmlCompat
import org.jetbrains.anko.*
import org.kaqui.BaseActivity
import org.kaqui.BuildConfig
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
                    text = HtmlCompat.fromHtml(getString(R.string.about_text, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), HtmlCompat.FROM_HTML_MODE_LEGACY)
                    movementMethod = LinkMovementMethod.getInstance()
                }.lparams {
                    margin = dip(16)
                }
            }
        }
    }
}