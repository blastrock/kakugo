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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kaqui.AppScaffold
import org.kaqui.AppTitleImage
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.TopBar
import org.kaqui.model.TestType
import org.kaqui.settings.ItemSelectionActivity
import org.kaqui.settings.SelectionMode
import org.kaqui.startTest
import org.kaqui.theme.KakugoTheme
import java.io.Serializable

class KatakanaMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            KatakanaMenuScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@Composable
fun KatakanaMenuScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current

    AppScaffold(
        title = stringResource(id = R.string.katakana_title),
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
                        MenuButton(R.string.katakana_to_romaji) { startTest(context, TestType.KATAKANA_TO_ROMAJI) }
                        MenuButton(R.string.romaji_to_katakana) { startTest(context, TestType.ROMAJI_TO_KATAKANA) }
                        MenuButton(R.string.katakana_to_romaji_typing) { startTest(context, TestType.KATAKANA_TO_ROMAJI_TEXT) }
                        MenuButton(R.string.katakana_drawing) { startTest(context, TestType.KATAKANA_DRAWING) }
                        Separator(modifier = Modifier.padding(4.dp))
                        val intent = Intent(context, ItemSelectionActivity::class.java).putExtra("mode", SelectionMode.KATAKANA as Serializable)
                        MenuButton(R.string.katakana_selection) { context.startActivity(intent) }
                    }
                }
            }
        }
