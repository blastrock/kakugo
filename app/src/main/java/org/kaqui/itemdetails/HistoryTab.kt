package org.kaqui.itemdetails

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.kaqui.HistoryItem
import org.kaqui.HistoryItemRow
import org.kaqui.HistoryItemStyle
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.model.Certainty
import org.kaqui.model.Database
import org.kaqui.model.Item
import org.kaqui.model.Kanji
import org.kaqui.model.TestType
import org.kaqui.toName

data class HistoryEntryUi(
    val timeSeconds: Long,
    val testType: TestType,
    val rows: List<HistoryItem>,
)

// Turns raw session-history entries into display rows, mirroring the good/wrong/unknown styling
// used by the test screen's history. resolveItem maps an item id to its Item.
fun buildHistoryEntries(
    history: List<Database.ItemHistoryEntry>,
    resolveItem: (Int) -> Item,
): List<HistoryEntryUi> =
    history.map { entry ->
        val question = resolveItem(entry.questionItemId)
        val rows = when {
            entry.wrongItemId != null -> listOf(
                HistoryItem(question, null, HistoryItemStyle.BAD, prependSeparator = true),
                HistoryItem(resolveItem(entry.wrongItemId), null, HistoryItemStyle.DONT_KNOW),
            )
            entry.certainty != Certainty.DONTKNOW ->
                listOf(HistoryItem(question, null, HistoryItemStyle.GOOD))
            else ->
                listOf(HistoryItem(question, null, HistoryItemStyle.BAD))
        }
        HistoryEntryUi(entry.time, entry.testType, rows)
    }

@Composable
fun HistoryTabContent(
    entries: List<HistoryEntryUi>,
    kanaWords: Boolean,
    onItemClick: (Item) -> Unit,
    contentPadding: PaddingValues,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.body1,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        items(entries) { entry ->
            Separator()
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    entry.rows.forEach { row ->
                        HistoryItemRow(
                            historyItem = row,
                            kanaWords = kanaWords,
                            onItemClick = onItemClick,
                        )
                    }
                }
                Column(
                    modifier = Modifier.padding(end = 16.dp, top = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(
                            entry.timeSeconds * 1000,
                            System.currentTimeMillis(),
                            DateUtils.DAY_IN_MILLIS
                        ).toString(),
                        style = MaterialTheme.typography.caption,
                    )
                    Text(
                        text = stringResource(entry.testType.toName()),
                        style = MaterialTheme.typography.caption,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private fun previewKanjiItem(
    id: Int,
    kanji: String,
    onReadings: List<String>,
    kunReadings: List<String>,
    meaning: String,
) = Item(
    id = id,
    contents = Kanji(kanji, onReadings, kunReadings, listOf(meaning), emptyList(), emptyList(), 5),
    shortScore = 0.5,
    longScore = 0.3,
    lastAsked = 0L,
    enabled = true,
)

@Preview(showBackground = true, name = "History Tab")
@Composable
fun PreviewHistoryTabContent() {
    val items = mapOf(
        1 to previewKanjiItem(1, "水", listOf("スイ"), listOf("みず"), "water"),
        2 to previewKanjiItem(2, "火", listOf("カ"), listOf("ひ", "ほ"), "fire"),
        3 to previewKanjiItem(3, "山", listOf("サン"), listOf("やま"), "mountain"),
        4 to previewKanjiItem(4, "川", listOf("セン"), listOf("かわ"), "river"),
    )

    val now = System.currentTimeMillis() / 1000
    val day = 24 * 3600L
    val history = listOf(
        Database.ItemHistoryEntry(now, TestType.KANJI_TO_READING, 1, null, Certainty.SURE),
        Database.ItemHistoryEntry(now - day, TestType.KANJI_TO_MEANING, 2, null, Certainty.MAYBE),
        Database.ItemHistoryEntry(now - 2 * day, TestType.MEANING_TO_KANJI, 3, 4, Certainty.DONTKNOW),
        Database.ItemHistoryEntry(now - 3 * day, TestType.KANJI_TO_READING, 4, null, Certainty.DONTKNOW),
    )

    HistoryTabContent(
        entries = buildHistoryEntries(history) { id -> items.getValue(id) },
        kanaWords = true,
        onItemClick = {},
        contentPadding = PaddingValues(0.dp),
    )
}

@Preview(showBackground = true, name = "History Tab - Empty")
@Composable
fun PreviewHistoryTabContentEmpty() {
    HistoryTabContent(
        entries = emptyList(),
        kanaWords = true,
        onItemClick = {},
        contentPadding = PaddingValues(0.dp),
    )
}
