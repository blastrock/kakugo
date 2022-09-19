package org.kaqui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.kaqui.*
import java.io.File

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

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            if (key == "dictionary_language")
                LocaleManager.updateDictionaryLocale(requireContext())
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
            if (requestCode != PICK_CUSTOM_FONT)
                return super.onActivityResult(requestCode, resultCode, data)

            if (resultCode != RESULT_OK || data == null)
                return

            requireContext().contentResolver.openInputStream(data.data!!).use { input ->
                requireContext().openFileOutput(CUSTOM_FONT_NAME, Context.MODE_PRIVATE).use { output ->
                    input?.copyTo(output)
                }
            }
            setCustomFontPath(File(requireContext().filesDir, CUSTOM_FONT_NAME).absolutePath)
        }

        @SuppressLint("ApplySharedPref")
        private fun setCustomFontPath(path: String?) {
            defaultSharedPreferences.edit()
                    .putString("custom_font", path)
                    .commit()
            TypefaceManager.updateTypeface(requireContext())
        }

        companion object {
            const val PICK_CUSTOM_FONT = 1
            const val CUSTOM_FONT_NAME = "custom-font.ttf"
        }
    }
}
