package org.kaqui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

private const val LANDSCAPE_DEVICE = "spec:width=891dp,height=411dp,dpi=420"

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class KakugoPreview

@Preview(name = "landscape light", showBackground = true, device = LANDSCAPE_DEVICE)
@Preview(
    name = "landscape dark",
    showBackground = true,
    device = LANDSCAPE_DEVICE,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class KakugoLandscapePreview
