package org.kaqui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class KakugoPreview
