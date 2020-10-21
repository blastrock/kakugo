package org.kaqui.mainmenu

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import org.kaqui.*
import org.kaqui.model.DatabaseUpdater
import org.kaqui.settings.MainSettingsActivity
import org.kaqui.stats.StatsActivity
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.coroutines.CoroutineContext

class MainActivity : BaseActivity(), CoroutineScope {
    companion object {
        private const val TAG = "MainActivity"
    }

    private var initProgress: ProgressDialog? = null

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleManager.updateDictionaryLocale(this)

        super.onCreate(savedInstanceState)
        job = Job()
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        verticalLayout {
            gravity = Gravity.CENTER

            scrollView {
                verticalLayout {
                    padding = dip(8)

                    appTitleImage(this@MainActivity).lparams(width = matchParent, height = dip(80)) {
                        margin = dip(8)
                    }

                    verticalLayout {
                        button(R.string.hiragana) {
                            setOnClickListener { startActivity<HiraganaMenuActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.katakana) {
                            setOnClickListener { startActivity<KatakanaMenuActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.kanji) {
                            setOnClickListener { startActivity<KanjiMenuActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.word) {
                            setOnClickListener { startActivity<VocabularyMenuActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.stats) {
                            setOnClickListener { startActivity<StatsActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                        button(R.string.settings) {
                            setOnClickListener { startActivity<MainSettingsActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(4)
                        }
                    }
                }
            }.lparams(width = menuWidth)
        }

        if (DatabaseUpdater.databaseNeedsUpdate(this)) {
            showDownloadProgressDialog()
            launch(Dispatchers.Default) {
                initDic()
            }
        }

        val lastVersionChangelog = defaultSharedPreferences.getInt("last_version_changelog", 0)
        if (lastVersionChangelog < BuildConfig.VERSION_CODE) {
            alert(HtmlCompat.fromHtml(getString(R.string.changelog_contents), HtmlCompat.FROM_HTML_MODE_COMPACT)) {
                okButton {
                    defaultSharedPreferences.edit()
                            .putInt("last_version_changelog", BuildConfig.VERSION_CODE)
                            .apply()
                }
            }.show()
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun showDownloadProgressDialog() {
        initProgress = ProgressDialog(this)
        initProgress!!.setMessage(getString(R.string.initializing_kanji_db))
        initProgress!!.setCancelable(false)
        initProgress!!.show()
    }

    private fun initDic() {
        val tmpFile = File.createTempFile("dict", "", cacheDir)
        try {
            resources.openRawResource(R.raw.dict).use { gzipStream ->
                GZIPInputStream(gzipStream, 1024).use { textStream ->
                    tmpFile.outputStream().use { outputStream ->
                        textStream.copyTo(outputStream)
                    }
                }
            }
            DatabaseUpdater.upgradeDatabase(this, tmpFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database", e)
            launch(job) {
                alert(getString(R.string.failed_to_init_db, e.message), getString(R.string.database_error)) {
                    okButton {}
                }.show()
            }
        } finally {
            tmpFile.delete()

            launch(job) {
                initProgress!!.dismiss()
                initProgress = null
            }
        }
    }
}
