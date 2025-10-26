package org.kaqui.mainmenu

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import org.kaqui.AppTitleImage
import org.kaqui.BuildConfig
import org.kaqui.LocaleManager
import org.kaqui.R
import org.kaqui.TopBar
import org.kaqui.model.DatabaseUpdater
import org.kaqui.settings.MainSettingsActivity
import org.kaqui.startActivity
import org.kaqui.stats.StatsActivity
import org.kaqui.theme.KakugoTheme
import java.io.File
import java.util.zip.GZIPInputStream

class MainActivity : ComponentActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleManager.updateDictionaryLocale(this)
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val lastVersionChangelog = sharedPrefs.getInt("last_version_changelog", 0)
        if (lastVersionChangelog < BuildConfig.VERSION_CODE) {
            AlertDialog.Builder(this)
                .setMessage(HtmlCompat.fromHtml(getString(R.string.changelog_contents), HtmlCompat.FROM_HTML_MODE_COMPACT))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    sharedPrefs.edit()
                        .putInt("last_version_changelog", BuildConfig.VERSION_CODE)
                        .apply()
                }
                .show()
        }

        setContent {
            MainScreen(
                onDatabaseInitRequired = { initDic() },
            )
        }
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
        } finally {
            tmpFile.delete()
        }
    }
}

@Composable
fun MainScreen(
    onDatabaseInitRequired: () -> Unit,
) {
    val context = LocalContext.current
    var showProgress by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (DatabaseUpdater.databaseNeedsUpdate(context)) {
            showProgress = true
            try {
                onDatabaseInitRequired()
            } catch (e: Exception) {
                Log.e(MainActivity.TAG, "Database initialization failed", e)
                errorMessage = context.getString(R.string.failed_to_init_db, e.message)
                errorTitle = context.getString(R.string.database_error)
            } finally {
                showProgress = false
            }
        }
    }

    KakugoTheme {
        Surface(color = MaterialTheme.colors.background) {
            Scaffold(
                topBar = {
                    TopBar(title = stringResource(R.string.app_name))
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(500.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppTitleImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(8.dp)
                        )

                        MenuButton(R.string.hiragana) { context.startActivity<HiraganaMenuActivity>() }
                        MenuButton(R.string.katakana) { context.startActivity<KatakanaMenuActivity>() }
                        MenuButton(R.string.kanji) { context.startActivity<KanjiMenuActivity>() }
                        MenuButton(R.string.word) { context.startActivity<VocabularyMenuActivity>() }
                        MenuButton(R.string.stats) { context.startActivity<StatsActivity>() }
                        MenuButton(R.string.settings) { context.startActivity<MainSettingsActivity>() }
                    }
                }

                if (showProgress) {
                    LoadingDialog()
                }

                if (errorMessage != null) {
                    ErrorDialog(
                        title = errorTitle!!,
                        message = errorMessage!!,
                        onDismiss = { errorMessage = null }
                    )
                }
            }
        }
    }
}

@Composable
fun MenuButton(textRes: Int, onClick: () -> Unit) {
    val surfaceColor = MaterialTheme.colors.surface.toArgb()
    val onSurfaceColor = MaterialTheme.colors.onSurface.toArgb()
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        factory = { context ->
            android.widget.Button(context).apply {
                this.setText(textRes)
                this.setBackgroundColor(surfaceColor)
                this.setTextColor(onSurfaceColor)
                this.setOnClickListener { onClick() }
            }
        }
    )
}

@Composable
fun LoadingDialog() {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.initializing_kanji_db)) },
        text = { CircularProgressIndicator() },
        buttons = { }
    )
}

@Composable
fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen(
        onDatabaseInitRequired = {}
    )
}

@Preview(showBackground = true)
@Composable
fun LoadingDialogPreview() {
    KakugoTheme {
        LoadingDialog()
    }
}

@Preview(showBackground = true)
@Composable
fun ErrorDialogPreview() {
    KakugoTheme {
        ErrorDialog(
            title = "Error",
            message = "An error occurred while processing your request",
            onDismiss = {}
        )
    }
}