package org.kaqui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.kaqui.theme.KakugoTheme
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
    onEnabledChange: ((Int, Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val themeColors = LocalThemeAttributes.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .let {
                if (onEnabledChange != null)
                    it.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                else
                    it.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onEnabledChange != null) {
            Checkbox(
                checked = itemData.enabled,
                onCheckedChange = { checked ->
                    onEnabledChange(itemData.id, checked)
                },
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        val backgroundColor = themeColors.getColorFromScore(itemData.shortScore)

        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .defaultMinSize(35.dp, 35.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .padding(horizontal = 4.dp),
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
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            fontSize = 16.sp,
            style = MaterialTheme.typography.body2,
            lineHeight = 1.1.em,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemRowPreview() {
    KakugoTheme {
        ItemRow(
            itemData = ItemData(
                id = 1,
                text = "漢",
                description = "Kan,\nHan;\nSino-, China",
                enabled = true,
                shortScore = 0.75
            ),
            onEnabledChange = { _, _ -> },
            onClick = { }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemRowWordPreview() {
    KakugoTheme {
        ItemRow(
            itemData = ItemData(
                id = 1,
                text = "食べる",
                description = "食べる\neat",
                enabled = true,
                shortScore = 0.75
            ),
            onEnabledChange = { _, _ -> },
            onClick = { }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemRowNoCheckboxPreview() {
    KakugoTheme {
        ItemRow(
            itemData = ItemData(
                id = 2,
                text = "あ",
                description = "a",
                enabled = false,
                shortScore = 0.3
            ),
            onClick = { }
        )
    }
}
