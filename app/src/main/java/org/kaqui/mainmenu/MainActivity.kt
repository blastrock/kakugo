package org.kaqui.mainmenu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import org.kaqui.AppScaffold
import org.kaqui.AppTitleImage
import org.kaqui.BuildConfig
import org.kaqui.LocaleManager
import org.kaqui.R
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

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val lastVersionChangelog = sharedPrefs.getInt("last_version_changelog", 0)
        if (lastVersionChangelog < BuildConfig.VERSION_CODE) {
            AlertDialog.Builder(this)
                .setMessage(
                    HtmlCompat.fromHtml(
                        getString(R.string.changelog_contents),
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    )
                )
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    sharedPrefs.edit {
                        putInt("last_version_changelog", BuildConfig.VERSION_CODE)
                    }
                }
                .show()
        }

        setContent {
            MainScreen(
                onDatabaseInitRequired = { initDic() }
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
    onDatabaseInitRequired: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    var showProgress by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cardDismissed by remember {
        mutableStateOf(sharedPrefs.getBoolean("hide_keep_android_open_card", false))
    }

    LaunchedEffect(Unit) {
        if (DatabaseUpdater.databaseNeedsUpdate(context)) {
            showProgress = true
            try {
                withContext(Dispatchers.IO) {
                    onDatabaseInitRequired()
                }
            } catch (e: Exception) {
                Log.e(MainActivity.TAG, "Database initialization failed", e)
                errorMessage = context.getString(R.string.failed_to_init_db, e.message)
                errorTitle = context.getString(R.string.database_error)
            } finally {
                showProgress = false
            }
        }
    }

    AppScaffold(
        title = stringResource(R.string.app_name)
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
                if (!cardDismissed) {
                    KeepAndroidOpenCard(
                        onDismiss = {
                            cardDismissed = true
                            sharedPrefs.edit { putBoolean("hide_keep_android_open_card", true) }
                        }
                    )
                }

                AppTitleImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(horizontal = 64.dp, vertical = 8.dp)
                )

                MenuButton(R.string.hiragana) { context.startActivity<HiraganaMenuActivity>() }
                MenuButton(R.string.katakana) { context.startActivity<KatakanaMenuActivity>() }
                MenuButton(R.string.kanji) { context.startActivity<KanjiMenuActivity>() }
                MenuButton(R.string.word) { context.startActivity<VocabularyMenuActivity>() }
                MenuButton(R.string.stats) { context.startActivity<StatsActivity>() }
                MenuButton(R.string.settings) { context.startActivity<MainSettingsActivity>() }
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
fun KeepAndroidOpenCard(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Card(
        backgroundColor = MaterialTheme.colors.error,
        contentColor = MaterialTheme.colors.onError,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "✕",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable { onDismiss() }
                    .padding(end = 4.dp)
            )
            Column(
                modifier = Modifier.padding(end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.keep_android_open_message))
                Text(
                    text = stringResource(R.string.keep_android_open_link),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://keepandroidopen.org/"))
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun LoadingDialog() {
    Dialog( onDismissRequest = { })
    {
        Card {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.initializing_kanji_db))
            }
        }
    }
}

@Composable
fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
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