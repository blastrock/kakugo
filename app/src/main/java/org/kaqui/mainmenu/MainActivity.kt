package org.kaqui.mainmenu

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import org.kaqui.*
import org.kaqui.model.KaquiDb
import org.kaqui.model.TestType
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    companion object {
        private const val TAG = "MainActivity"
    }

    private enum class Mode {
        MAIN,
        HIRAGANA,
        KATAKANA,
        KANJI,
        WORD,
    }

    private var initProgress: ProgressDialog? = null

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()

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
                        button(R.string.hiragana) {
                            setOnClickListener { startActivity<HiraganaMenuActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.katakana) {
                            setOnClickListener { startActivity<KatakanaMenuActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.kanji) {
                            setOnClickListener { startActivity<KanjiMenuActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                        button(R.string.word) {
                            setOnClickListener { startActivity<VocabularyMenuActivity>() }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                    }
                }
            }
        }

        if (KaquiDb.databaseNeedsUpdate(this)) {
            showDownloadProgressDialog()
            launch(Dispatchers.Default) {
                initDic()
            }
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
            val db = KaquiDb.getInstance(this)
            resources.openRawResource(R.raw.dict).use { gzipStream ->
                GZIPInputStream(gzipStream, 1024).use { textStream ->
                    tmpFile.outputStream().use { outputStream ->
                        textStream.copyTo(outputStream)
                    }
                }
            }
            db.replaceKanjis(tmpFile.absolutePath)
            db.replaceWords(tmpFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database", e)
            launch(job) {
                Toast.makeText(this@MainActivity, getString(R.string.failed_to_init_db, e.message), Toast.LENGTH_LONG).show()
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
