package org.kaqui

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.kaqui.R
import org.kaqui.settings.SettingsActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private var downloadProgress: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)

        start_kanji_reading_quizz.setOnClickListener {
            val intent = Intent(this, QuizzActivity::class.java)
            intent.putExtra("kanji_reading", true)
            startActivity(intent)
        }
        start_reading_kanji_quizz.setOnClickListener {
            val intent = Intent(this, QuizzActivity::class.java)
            intent.putExtra("kanji_reading", false)
            startActivity(intent)
        }

        settings_button.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        download_kanjidic_button.setOnClickListener {
            showDownloadProgressDialog()
            async(CommonPool) {
                downloadKanjiDic()
            }
        }

        updateButtonStatuses()
    }

    private fun updateButtonStatuses() {
        val db = KanjiDb.getInstance(this)
        if (db.empty) {
            start_kanji_reading_quizz.isEnabled = false
            start_reading_kanji_quizz.isEnabled = false
            settings_button.isEnabled = false
        } else {
            start_kanji_reading_quizz.isEnabled = true
            start_reading_kanji_quizz.isEnabled = true
            settings_button.isEnabled = true
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
                    val db = KanjiDb.getInstance(this)
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

