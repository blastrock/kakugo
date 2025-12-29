package org.kaqui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.kaqui.theme.LocalThemeAttributes

data class ItemData(
    val id: Int,
    val text: String,
    val description: String,
    val enabled: Boolean,
    val shortScore: Double
)

@Composable
fun ItemRow(
    itemData: ItemData,
    onEnabledChange: (Int, Boolean) -> Unit
) {
    val themeColors = LocalThemeAttributes.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = itemData.enabled,
            onCheckedChange = { checked ->
                onEnabledChange(itemData.id, checked)
            },
            modifier = Modifier.padding(8.dp)
        )

        val backgroundColor = themeColors.getColorFromScore(itemData.shortScore)

        Box(
            modifier = Modifier
                .defaultMinSize(if (itemData.text.length > 1) 50.dp else 35.dp, 35.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .padding(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = itemData.text,
                fontSize = 25.sp,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = itemData.description,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            fontSize = 16.sp,
            style = MaterialTheme.typography.body2,
            lineHeight = 1.1.em,
        )
    }
}
