package org.kaqui

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.coroutines.*
import org.kaqui.model.KaquiDb
import org.kaqui.model.QuizzType
import org.kaqui.settings.JlptSelectionActivity
import org.kaqui.settings.ItemSelectionActivity
import java.io.File
import java.io.Serializable
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

    lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        setContentView(R.layout.main_activity)

        hiragana_quizz.transformationMethod = null
        katakana_quizz.transformationMethod = null
        kanji_quizz.transformationMethod = null
        word_quizz.transformationMethod = null

        hiragana_quizz.setOnClickListener { setMode(Mode.HIRAGANA) }
        katakana_quizz.setOnClickListener { setMode(Mode.KATAKANA) }
        kanji_quizz.setOnClickListener { setMode(Mode.KANJI) }
        word_quizz.setOnClickListener { setMode(Mode.WORD) }

        start_hiragana_to_romaji_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.HIRAGANA_TO_ROMAJI)))
        start_romaji_to_hiragana_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.ROMAJI_TO_HIRAGANA)))

        start_katakana_to_romaji_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.KATAKANA_TO_ROMAJI)))
        start_romaji_to_katakana_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.ROMAJI_TO_KATAKANA)))

        start_kanji_reading_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.KANJI_TO_READING)))
        start_reading_kanji_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.READING_TO_KANJI)))
        start_kanji_meaning_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.KANJI_TO_MEANING)))
        start_meaning_kanji_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.MEANING_TO_KANJI)))
        start_kanji_drawing_quizz.setOnClickListener(View.OnClickListener(makeDrawQuizzLauncher()))

        start_word_reading_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.WORD_TO_READING)))
        start_reading_word_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.READING_TO_WORD)))
        start_word_meaning_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.WORD_TO_MEANING)))
        start_meaning_word_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.MEANING_TO_WORD)))

        hiragana_selection_button.setOnClickListener {
            startActivity(Intent(this, ItemSelectionActivity::class.java).putExtra("mode", ItemSelectionActivity.Mode.HIRAGANA as Serializable))
        }
        katakana_selection_button.setOnClickListener {
            startActivity(Intent(this, ItemSelectionActivity::class.java).putExtra("mode", ItemSelectionActivity.Mode.KATAKANA as Serializable))
        }
        kanji_selection_button.setOnClickListener {
            startActivity(Intent(this, JlptSelectionActivity::class.java).putExtra("mode", JlptSelectionActivity.Mode.KANJI as Serializable))
        }
        word_selection_button.setOnClickListener {
            startActivity(Intent(this, JlptSelectionActivity::class.java).putExtra("mode", JlptSelectionActivity.Mode.WORD as Serializable))
        }

        setMode(Mode.MAIN)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun setMode(mode: Mode) {
        main_layout.visibility = if (mode == Mode.MAIN) View.VISIBLE else View.GONE
        hiragana_layout.visibility = if (mode == Mode.HIRAGANA) View.VISIBLE else View.GONE
        katakana_layout.visibility = if (mode == Mode.KATAKANA) View.VISIBLE else View.GONE
        kanji_layout.visibility = if (mode == Mode.KANJI) View.VISIBLE else View.GONE
        word_layout.visibility = if (mode == Mode.WORD) View.VISIBLE else View.GONE

        if (mode == Mode.KANJI || mode == Mode.WORD) {
            val db = KaquiDb.getInstance(this)
            if (db.needsInit) {
                showDownloadProgressDialog()
                launch(Dispatchers.Default) {
                    initDic()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (main_layout.visibility != View.VISIBLE)
            setMode(Mode.MAIN)
        else
            super.onBackPressed()
    }

    private fun makeQuizzLauncher(type: QuizzType): (View) -> Unit {
        return {
            val db = KaquiDb.getInstance(this)
            if (QuizzEngine.getItemView(db, type).getEnabledCount() < 10) {
                Toast.makeText(this, R.string.enable_a_few_items, Toast.LENGTH_LONG).show()
            } else {
                val intent = Intent(this, QuizzActivity::class.java)
                intent.putExtra("quizz_type", type)
                startActivity(intent)
            }
        }
    }

    private fun makeDrawQuizzLauncher(): (View) -> Unit {
        return {
            val db = KaquiDb.getInstance(this)
            if (db.kanjiView.getEnabledCount() < 10) {
                Toast.makeText(this, R.string.enable_a_few_items, Toast.LENGTH_LONG).show()
            } else {
                val intent = Intent(this, WritingQuizzActivity::class.java)
                startActivity(intent)
            }
        }
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
        }

        launch(job) {
            initProgress!!.dismiss()
            initProgress = null
        }
    }
}
