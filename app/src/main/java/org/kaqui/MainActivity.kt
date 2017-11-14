package org.kaqui

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.kaqui.model.KaquiDb
import org.kaqui.model.QuizzType
import org.kaqui.model.parseFile
import org.kaqui.settings.KanaSelectionActivity
import org.kaqui.settings.KanjiSelectionActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private enum class Mode {
        MAIN,
        HIRAGANA,
        KANJI,
    }

    private var downloadProgress: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)

        hiragana_quizz.transformationMethod = null
        kanji_quizz.transformationMethod = null

        hiragana_quizz.setOnClickListener { setMode(Mode.HIRAGANA) }
        kanji_quizz.setOnClickListener { setMode(Mode.KANJI) }
        start_hiragana_to_romaji_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.HIRAGANA_TO_ROMAJI)))
        start_romaji_to_hiragana_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.ROMAJI_TO_HIRAGANA)))
        start_kanji_reading_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.KANJI_TO_READING)))
        start_reading_kanji_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.READING_TO_KANJI)))
        start_kanji_meaning_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.KANJI_TO_MEANING)))
        start_meaning_kanji_quizz.setOnClickListener(View.OnClickListener(makeQuizzLauncher(QuizzType.MEANING_TO_KANJI)))

        hiragana_selection_button.setOnClickListener {
            startActivity(Intent(this, KanaSelectionActivity::class.java))
        }
        kanji_selection_button.setOnClickListener {
            startActivity(Intent(this, KanjiSelectionActivity::class.java))
        }
        download_kanjidic_button.setOnClickListener {
            showDownloadProgressDialog()
            async(CommonPool) {
                downloadKanjiDic()
            }
        }

        updateButtonStatuses()
        setMode(Mode.MAIN)
    }

    private fun setMode(mode: Mode) {
        main_layout.visibility = if (mode == Mode.MAIN) View.VISIBLE else View.GONE
        hiragana_layout.visibility = if (mode == Mode.HIRAGANA) View.VISIBLE else View.GONE
        kanji_layout.visibility = if (mode == Mode.KANJI) View.VISIBLE else View.GONE
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
            if (db.kanjiView.getEnabledCount() < 10) {
                Toast.makeText(this, "You must enable at least 10 kanjis in settings", Toast.LENGTH_LONG).show()
            } else {
                val intent = Intent(this, QuizzActivity::class.java)
                intent.putExtra("quizz_type", type)
                startActivity(intent)
            }
        }
    }

    private fun updateButtonStatuses() {
        val db = KaquiDb.getInstance(this)
        if (db.empty) {
            start_kanji_reading_quizz.isEnabled = false
            start_reading_kanji_quizz.isEnabled = false
            start_kanji_meaning_quizz.isEnabled = false
            start_meaning_kanji_quizz.isEnabled = false
            kanji_selection_button.isEnabled = false
        } else {
            start_kanji_reading_quizz.isEnabled = true
            start_reading_kanji_quizz.isEnabled = true
            start_kanji_meaning_quizz.isEnabled = true
            start_meaning_kanji_quizz.isEnabled = true
            kanji_selection_button.isEnabled = true
        }
    }

    private fun showDownloadProgressDialog() {
        downloadProgress = ProgressDialog(this)
        downloadProgress!!.setMessage("Downloading kanjidic database")
        downloadProgress!!.setCancelable(false)
        downloadProgress!!.show()
    }

    private fun downloadKanjiDic() {
        try {
            Log.v(TAG, "Downloading kanjidic")
            val url = URL("https://axanux.net/kanjidic_.gz")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.connect()

            urlConnection.inputStream.use { gzipStream ->
                GZIPInputStream(gzipStream, 1024).use { textStream ->
                    val db = KaquiDb.getInstance(this)
                    val dump = db.dumpUserData()
                    db.replaceKanjis(parseFile(textStream.bufferedReader()))
                    db.restoreUserDataDump(dump)
                }
            }
            Log.v(TAG, "Finished downloading kanjidic")
            async(UI) {
                downloadProgress!!.dismiss()
                downloadProgress = null
                updateButtonStatuses()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and parse kanjidic", e)
            async(UI) {
                Toast.makeText(this@MainActivity, "Failed to download and parse kanjidic: " + e.message, Toast.LENGTH_LONG).show()
                downloadProgress!!.dismiss()
                downloadProgress = null
                updateButtonStatuses()
            }
        }
    }

}

