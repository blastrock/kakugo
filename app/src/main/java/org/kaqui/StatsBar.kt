package org.kaqui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.max

@Composable
fun StatsBar(
    itemsDontKnow: Int,
    itemsBad: Int,
    itemsMeh: Int,
    itemsGood: Int,
) {
    val totalWeight = (itemsDontKnow + itemsBad + itemsMeh + itemsGood).toFloat()
    fun weightMin(weight: Float): Float {
        return max(weight, 0.1f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        if (itemsDontKnow > 0)
            StatsText(
                text = "$itemsDontKnow",
                backgroundColor = Color(LocalContext.current.getColorFromAttr(R.attr.backgroundDontKnow)),
                modifier = Modifier.weight(weightMin(itemsDontKnow / totalWeight))
            )
        if (itemsBad > 0)
            StatsText(
                text = "$itemsBad",
                backgroundColor = Color(LocalContext.current.getColorFromAttr(R.attr.itemBad)),
                modifier = Modifier.weight(weightMin(itemsBad / totalWeight))
            )
        if (itemsMeh > 0)
            StatsText(
                text = "$itemsMeh",
                backgroundColor = Color(LocalContext.current.getColorFromAttr(R.attr.itemMeh)),
                modifier = Modifier.weight(weightMin(itemsMeh / totalWeight))
            )
        if (itemsGood > 0)
            StatsText(
                text = "$itemsGood",
                backgroundColor = Color(
                    LocalContext.current.getColorFromAttr(R.attr.itemGood)
                ),
                modifier = Modifier.weight(weightMin(itemsGood / totalWeight))
            )
    }
}

@Composable
fun StatsText(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(backgroundColor),
        textAlign = TextAlign.Center
    )
}

@Preview(showBackground = true)
@Composable
fun StatsScreenPreview() {
    StatsBar(1, 1, 2, 3)
}

@Preview(showBackground = true)
@Composable
fun StatsScreenPreviewUnbalanced() {
    StatsBar(100, 1, 2, 5000)
}
