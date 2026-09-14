package org.kaqui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

// Most screens draw straight on the theme background without a Surface of their
// own, so the dark preview needs a background matching DarkColors.background,
// showBackground being white whatever the uiMode is.
@Preview(name = "light", showBackground = true)
@Preview(
    name = "dark",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class KakugoPreview
