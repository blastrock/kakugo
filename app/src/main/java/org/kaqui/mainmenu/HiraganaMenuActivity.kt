package org.kaqui.mainmenu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.kaqui.AppScaffold
import org.kaqui.AppTitleImage
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.model.TestType
import org.kaqui.settings.ItemSelectionActivity
import org.kaqui.settings.SelectionMode
import org.kaqui.startTest
import java.io.Serializable

class HiraganaMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            HiraganaMenuScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@Composable
fun HiraganaMenuScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current

    AppScaffold(
        title = stringResource(id = R.string.hiragana_title),
        onBackClick = onBackClick
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
                MenuButton(R.string.hiragana_to_romaji) {
                    startTest(
                        context,
                        TestType.HIRAGANA_TO_ROMAJI
                    )
                }
                MenuButton(R.string.romaji_to_hiragana) {
                    startTest(
                        context,
                        TestType.ROMAJI_TO_HIRAGANA
                    )
                }
                MenuButton(R.string.hiragana_to_romaji_typing) {
                    startTest(
                        context,
                        TestType.HIRAGANA_TO_ROMAJI_TEXT
                    )
                }
                MenuButton(R.string.hiragana_drawing) {
                    startTest(
                        context,
                        TestType.HIRAGANA_DRAWING
                    )
                }
                Separator(modifier = Modifier.padding(4.dp))
                val intent = Intent(context, ItemSelectionActivity::class.java).putExtra(
                    "mode",
                    SelectionMode.HIRAGANA as Serializable
                )
                MenuButton(R.string.hiragana_selection) { context.startActivity(intent) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHiraganaMenuScreen() {
    HiraganaMenuScreen()
}