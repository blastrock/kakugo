package org.kaqui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.longToast
import org.kaqui.*
import org.kaqui.model.Database
import org.kaqui.model.DatabaseUpdater
import java.io.File
import java.util.Date


class MainSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        linearLayout {
            frameLayout {
                id = R.id.settings_main_view
            }.lparams(width = matchParent, height = matchParent)
        }

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_main_view, SettingsFragment())
                .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            findPreference<Preference>("pickCustomFont")!!.setOnPreferenceClickListener {
                pickCustomFont()
                true
            }
            findPreference<Preference>("exportBackup")!!.setOnPreferenceClickListener {
                pickBackupFolder()
                true
            }
            findPreference<Preference>("importBackup")!!.setOnPreferenceClickListener {
                pickBackup()
                true
            }
            findPreference<SwitchPreferenceCompat>("useCustomFont")!!.setOnPreferenceClickListener {
                if (!(it as SwitchPreferenceCompat).isChecked) {
                    setCustomFontPath(null)
                    true
                } else {
                    false
                }
            }
            findPreference<Preference>("showChangelog")!!.setOnPreferenceClickListener {
                requireContext().alert(HtmlCompat.fromHtml(getString(R.string.changelog_contents), HtmlCompat.FROM_HTML_MODE_COMPACT)) {
                    okButton { }
                }.show()
                true
            }
        }

        override fun onStart() {
            super.onStart()
            preferenceManager.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onStop() {
            preferenceManager.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
            super.onStop()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "dictionary_language")
                LocaleManager.updateDictionaryLocale(requireContext())
        }

        private fun pickBackupFolder() {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addCategory(Intent.CATEGORY_DEFAULT);
            startActivityForResult(Intent.createChooser(i, getString(R.string.choose_backup_idr)), PICK_BACKUP_FOLDER);
        }

        private fun pickBackup() {
            var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
            chooseFile.setType("*/*")
            chooseFile = Intent.createChooser(chooseFile, "Choose a file")
            startActivityForResult(chooseFile, PICK_BACKUP)
        }

        private fun pickCustomFont() {
            if (Build.VERSION.SDK_INT >= 23) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
                    return
                }
            }

            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            startActivityForResult(intent, PICK_CUSTOM_FONT)
        }

        @Deprecated("Deprecated in Java")
        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED })
                pickCustomFont()
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            when (requestCode) {
                PICK_CUSTOM_FONT -> {
                    if (resultCode != RESULT_OK || data == null)
                        return
                    importFont(data.data!!)
                }
                PICK_BACKUP_FOLDER -> {
                    if (resultCode != RESULT_OK || data == null)
                        return
                    exportBackup(data.data!!)
                }
                PICK_BACKUP -> {
                    if (resultCode != RESULT_OK || data == null)
                        return
                    importBackup(data.data!!)
                }
                else -> return super.onActivityResult(requestCode, resultCode, data)
            }
        }

        private fun exportBackup(path: Uri) {
            val datetime = android.text.format.DateFormat.format("yyyy-MM-dd_HH-mm-ss", Date())
            val documentFile = DocumentFile.fromTreeUri(requireContext(), path)!!
            val file = documentFile.createFile("application/data", "kakugo-backup-$datetime.sqlite3")!!

            File(Database.getDatabasePath(requireContext())).inputStream().use { input ->
                requireContext().contentResolver.openOutputStream(file.uri)!!.use { output ->
                    input.copyTo(output)
                }
            }
        }

        private fun importBackup(path: Uri) {
            try {
                val tmpFile = File.createTempFile("import", "", requireContext().cacheDir)
                requireContext().contentResolver.openInputStream(path).use { input ->
                    if (input == null)
                        throw RuntimeException("failed to open database")
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val dataDump = SQLiteDatabase.openDatabase(
                    tmpFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
                ).use { db ->
                    DatabaseUpdater(db).dumpUserData() ?: throw RuntimeException("invalid database version")
                }

                DatabaseUpdater(Database.getInstance(requireContext()).database).restoreUserData(dataDump)
                longToast(R.string.backup_import_completed)
            } catch (e: Exception) {
                longToast(getString(R.string.import_backup_failure, e.message))
            }
        }

        @SuppressLint("ApplySharedPref")
        private fun importFont(path: Uri) {
            requireContext().contentResolver.openInputStream(path).use { input ->
                requireContext().openFileOutput(CUSTOM_FONT_NAME, Context.MODE_PRIVATE)
                    .use { output ->
                        input?.copyTo(output)
                    }
            }

            setCustomFontPath(File(requireContext().filesDir, CUSTOM_FONT_NAME).absolutePath)
        }

        private fun setCustomFontPath(path: String?) {
            defaultSharedPreferences.edit()
                    .putString("custom_font", path)
                    .commit()
            TypefaceManager.updateTypeface(requireContext())
        }

        companion object {
            const val PICK_CUSTOM_FONT = 1
            const val PICK_BACKUP_FOLDER = 2
            const val PICK_BACKUP = 3
            const val CUSTOM_FONT_NAME = "custom-font.ttf"
        }
    }
}
