package org.kaqui.settings

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import org.kaqui.AppTitleImage
import org.kaqui.BuildConfig
import org.kaqui.R
import org.kaqui.TopBar
import org.kaqui.theme.KakugoTheme

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            AboutScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@Composable
fun AboutScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val aboutText: String = context.getString(
        R.string.about_text, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE
    )

    KakugoTheme {
        Surface(color = MaterialTheme.colors.background) {
            Scaffold(
                topBar = {
                    TopBar(
                        title = stringResource(id = R.string.title_about),
                        onBackClick = onBackClick
                    )
                },
                content = { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AppTitleImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(8.dp)
                        )

                        val onBackgroundColor = MaterialTheme.colors.onBackground.toArgb()

                        AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    text = HtmlCompat.fromHtml(
                                        aboutText, HtmlCompat.FROM_HTML_MODE_LEGACY
                                    )
                                    movementMethod = LinkMovementMethod.getInstance()
                                    setTextColor(onBackgroundColor)
                                }
                            },
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AboutScreenPreview() {
    AboutScreen()
}