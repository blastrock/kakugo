package org.kaqui.itemdetails

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import org.kaqui.AppScaffold
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.SrsCalculator
import org.kaqui.TypefaceManager
import org.kaqui.model.Database
import org.kaqui.model.Item
import org.kaqui.model.Kanji
import org.kaqui.model.KnowledgeType
import org.kaqui.model.Word
import org.kaqui.settings.ItemData
import org.kaqui.settings.ItemRow
import org.kaqui.showKanjiInDict
import org.kaqui.startActivity
import org.kaqui.theme.LocalThemeAttributes
import kotlinx.coroutines.launch

enum class KanjiDisplayTab {
    KANJI, WORDS, HISTORY
}

data class KanjiData(
    val kanji: String,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val meanings: List<String>,
    val jlptLevel: Int,
    val readingShortScore: Double,
    val readingLongScore: Double,
    val meaningShortScore: Double,
    val meaningLongScore: Double,
    val strokesShortScore: Double,
    val strokesLongScore: Double,
    val kanjiObject: Kanji,
    val kanjiId: Int,
) {
    companion object {
        fun default() = KanjiData(
            kanji = "",
            onReadings = emptyList(),
            kunReadings = emptyList(),
            meanings = emptyList(),
            jlptLevel = 0,
            readingShortScore = 0.0,
            readingLongScore = 0.0,
            meaningShortScore = 0.0,
            meaningLongScore = 0.0,
            strokesShortScore = 0.0,
            strokesLongScore = 0.0,
            kanjiObject = Kanji("", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0),
            kanjiId = -1
        )
    }
}

data class KanjiDisplayUiState(
    val selectedTab: KanjiDisplayTab = KanjiDisplayTab.KANJI,
    val kanjiData: KanjiData = KanjiData.default(),
    val wordList: List<Item> = emptyList(),
    val historyEntries: List<HistoryEntryUi> = emptyList(),
)

class KanjiDisplayViewModel : ViewModel() {
    var uiState by mutableStateOf(KanjiDisplayUiState())
        private set

    fun initialize(context: Context, kanjiId: Int) {
        val database = Database.getInstance(context)

        // Load kanji with all three knowledge types
        val kanjiReading = database.getKanji(kanjiId, KnowledgeType.Reading)
        val kanjiMeaning = database.getKanji(kanjiId, KnowledgeType.Meaning)
        val kanjiStrokes = database.getKanji(kanjiId, KnowledgeType.Strokes)
        val kanjiContents = kanjiReading.contents as Kanji

        val kanjiData = KanjiData(
            kanji = kanjiContents.kanji,
            onReadings = kanjiContents.on_readings.filter { it.isNotBlank() },
            kunReadings = kanjiContents.kun_readings.filter { it.isNotBlank() },
            meanings = kanjiContents.meanings.filter { it.isNotBlank() },
            jlptLevel = kanjiContents.jlptLevel,
            readingShortScore = kanjiReading.shortScore,
            readingLongScore = kanjiReading.longScore,
            meaningShortScore = kanjiMeaning.shortScore,
            meaningLongScore = kanjiMeaning.longScore,
            strokesShortScore = kanjiStrokes.shortScore,
            strokesLongScore = kanjiStrokes.longScore,
            kanjiObject = kanjiContents,
            kanjiId = kanjiId
        )

        // Load words containing this kanji
        val wordList = database.getWordsByKanji(kanjiContents.kanji)
            .sortedByDescending { it.shortScore }

        // Load the question history for this kanji (asked, or picked as a wrong answer)
        val historyEntries = buildHistoryEntries(database.getItemHistory(kanjiId)) { id ->
            database.getKanji(id, KnowledgeType.Reading)
        }

        uiState = uiState.copy(
            kanjiData = kanjiData,
            wordList = wordList,
            historyEntries = historyEntries
        )
    }

    fun onTabSelected(tab: KanjiDisplayTab) {
        uiState = uiState.copy(selectedTab = tab)
    }

    fun updateScore(context: Context, knowledgeType: KnowledgeType, increase: Boolean) {
        val database = Database.getInstance(context)
        val kanjiView = database.getKanjiView(knowledgeType)

        val currentShortScore = when (knowledgeType) {
            KnowledgeType.Reading -> uiState.kanjiData.readingShortScore
            KnowledgeType.Meaning -> uiState.kanjiData.meaningShortScore
            KnowledgeType.Strokes -> uiState.kanjiData.strokesShortScore
        }

        val currentLongScore = when (knowledgeType) {
            KnowledgeType.Reading -> uiState.kanjiData.readingLongScore
            KnowledgeType.Meaning -> uiState.kanjiData.meaningLongScore
            KnowledgeType.Strokes -> uiState.kanjiData.strokesLongScore
        }

        val scoreUpdate = SrsCalculator.createManualScoreUpdate(
            itemId = uiState.kanjiData.kanjiId,
            currentShortScore = currentShortScore,
            currentLongScore = currentLongScore,
            increase = increase
        )

        kanjiView.applyScoreUpdate(scoreUpdate)
        initialize(context, uiState.kanjiData.kanjiId)
    }
}

class KanjiDisplayActivity : ComponentActivity() {
    private val viewModel: KanjiDisplayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))

        val kanjiId = intent.getIntExtra("kanji_id", -1)
        if (kanjiId == -1) {
            finish()
            return
        }

        viewModel.initialize(this, kanjiId)

        setContent {
            KanjiDisplayScreen(
                uiState = viewModel.uiState,
                onTabSelected = viewModel::onTabSelected,
                onBackClick = { finish() },
                onUpdateScore = { knowledgeType, increase ->
                    viewModel.updateScore(this@KanjiDisplayActivity, knowledgeType, increase)
                },
                onWordClick = { wordItem ->
                    startActivity<WordDisplayActivity>("word_id" to wordItem.id)
                },
                onHistoryItemClick = { item ->
                    startActivity<KanjiDisplayActivity>("kanji_id" to item.id)
                }
            )
        }
    }
}

@Composable
fun KanjiDisplayScreen(
    uiState: KanjiDisplayUiState,
    onTabSelected: (KanjiDisplayTab) -> Unit,
    onBackClick: () -> Unit,
    onUpdateScore: (KnowledgeType, Boolean) -> Unit,
    onWordClick: (Item) -> Unit,
    onHistoryItemClick: (Item) -> Unit,
) {
    val context = LocalContext.current
    val kanaWords = remember {
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("kana_words", true)
    }
    val pageCount = 3
    val pagerState = rememberPagerState(initialPage = uiState.selectedTab.ordinal, pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val targetTab = when (page) {
                0 -> KanjiDisplayTab.KANJI
                1 -> KanjiDisplayTab.WORDS
                2 -> KanjiDisplayTab.HISTORY
                else -> KanjiDisplayTab.KANJI
            }
            onTabSelected(targetTab)
        }
    }

    AppScaffold(
        title = uiState.kanjiData.kanji,
        onBackClick = onBackClick,
        actions = {
            IconButton(onClick = { showKanjiInDict(context, uiState.kanjiData.kanjiObject) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.open_in_external_dictionary)
                )
            }
        },
        belowAppBar = {
            val tabs = listOf(
                stringResource(R.string.kanji_tab),
                stringResource(R.string.words_tab),
                stringResource(R.string.history_tab)
            )
            val selectedIndex = uiState.selectedTab.ordinal

            TabRow(
                selectedTabIndex = selectedIndex,
                backgroundColor = MaterialTheme.colors.primary,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedIndex == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        val kanjiData = uiState.kanjiData

        // Content area with swipeable pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> KanjiTabContent(
                    kanjiData = kanjiData,
                    contentPadding = paddingValues,
                    onUpdateScore = onUpdateScore
                )
                1 -> WordsTabContent(
                    wordList = uiState.wordList,
                    onWordClick = onWordClick,
                    contentPadding = paddingValues
                )
                2 -> HistoryTabContent(
                    entries = uiState.historyEntries,
                    kanaWords = kanaWords,
                    onItemClick = onHistoryItemClick,
                    contentPadding = paddingValues
                )
            }
        }
    }
}

@Composable
fun KanjiTabContent(
    kanjiData: KanjiData,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onUpdateScore: (KnowledgeType, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val fontFamily = TypefaceManager.getTypeface(context)?.let { FontFamily(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Kanji character in large font
        item {
            Text(
                text = kanjiData.kanji,
                fontSize = 72.sp,
                fontFamily = fontFamily,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("kanji", kanjiData.kanji)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // On'yomi section
        if (kanjiData.onReadings.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.on_readings_label) + ":",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(kanjiData.onReadings) { reading ->
                Text(
                    text = reading,
                    style = MaterialTheme.typography.body1,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Kun'yomi section
        if (kanjiData.kunReadings.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.kun_readings_label) + ":",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(kanjiData.kunReadings) { reading ->
                Text(
                    text = reading,
                    style = MaterialTheme.typography.body1,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Meanings section
        if (kanjiData.meanings.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.definitions_label) + ":",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(kanjiData.meanings) { meaning ->
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Learning status
        item {
            Text(
                text = stringResource(R.string.learning_status_label) + ":",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Reading status
        item {
            val themeColors = LocalThemeAttributes.current
            val readingStatus = getMemorizationStatus(
                kanjiData.readingShortScore,
                kanjiData.readingLongScore,
                themeColors
            )

            MemorizationStatusRow(
                label = stringResource(R.string.reading_label),
                status = readingStatus.text,
                color = readingStatus.color,
                shortScore = kanjiData.readingShortScore,
                onButtonClick = {
                    val increase = kanjiData.readingShortScore != 1.0
                    onUpdateScore(KnowledgeType.Reading, increase)
                }
            )
        }

        // Meaning status
        item {
            val themeColors = LocalThemeAttributes.current
            val meaningStatus = getMemorizationStatus(
                kanjiData.meaningShortScore,
                kanjiData.meaningLongScore,
                themeColors
            )

            MemorizationStatusRow(
                label = stringResource(R.string.meaning_label),
                status = meaningStatus.text,
                color = meaningStatus.color,
                shortScore = kanjiData.meaningShortScore,
                onButtonClick = {
                    val increase = kanjiData.meaningShortScore != 1.0
                    onUpdateScore(KnowledgeType.Meaning, increase)
                }
            )
        }

        // Strokes status
        item {
            val themeColors = LocalThemeAttributes.current
            val strokesStatus = getMemorizationStatus(
                kanjiData.strokesShortScore,
                kanjiData.strokesLongScore,
                themeColors
            )

            MemorizationStatusRow(
                label = stringResource(R.string.strokes_label),
                status = strokesStatus.text,
                color = strokesStatus.color,
                shortScore = kanjiData.strokesShortScore,
                onButtonClick = {
                    val increase = kanjiData.strokesShortScore != 1.0
                    onUpdateScore(KnowledgeType.Strokes, increase)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun WordsTabContent(
    wordList: List<Item>,
    onWordClick: (Item) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        items(wordList) { wordItem ->
            val word = wordItem.contents as Word
            val description = word.reading + "\n" + word.meanings.joinToString(", ")

            ItemRow(
                itemData = ItemData(
                    id = wordItem.id,
                    text = word.word,
                    description = description,
                    enabled = wordItem.enabled,
                    shortScore = wordItem.shortScore
                ),
                onClick = { onWordClick(wordItem) }
            )
            Separator()
        }
    }
}

@Preview(showBackground = true, name = "Kanji Tab")
@Composable
fun PreviewKanjiDisplayScreenKanjiTab() {
    val sampleKanjiData = KanjiData(
        kanji = "食",
        onReadings = listOf("ショク", "ジキ"),
        kunReadings = listOf("た.べる", "く.う"),
        meanings = listOf("eat", "food"),
        jlptLevel = 5,
        readingShortScore = 0.5,
        readingLongScore = 0.0,
        meaningShortScore = 1.0,
        meaningLongScore = 0.1,
        strokesShortScore = 0.0,
        strokesLongScore = 0.0,
        kanjiObject = Kanji(
            "食",
            listOf("ショク", "ジキ"),
            listOf("た.べる", "く.う"),
            listOf("eat", "food"),
            listOf(),
            listOf(),
            5
        ),
        kanjiId = 39135
    )

    val sampleWordList = listOf(
        Item(1, Word("食べる", "たべる", listOf("to eat"), listOf(), false, ""), 0.5, 0.3, 0, true),
        Item(2, Word("食事", "しょくじ", listOf("meal"), listOf(), false, ""), 0.8, 0.6, 0, true)
    )

    KanjiDisplayScreen(
        uiState = KanjiDisplayUiState(
            selectedTab = KanjiDisplayTab.KANJI,
            kanjiData = sampleKanjiData,
            wordList = sampleWordList
        ),
        onTabSelected = {},
        onBackClick = {},
        onUpdateScore = { _, _ -> },
        onWordClick = {},
        onHistoryItemClick = {}
    )
}

@Preview(showBackground = true, name = "Words Tab")
@Composable
fun PreviewKanjiDisplayScreenWordsTab() {
    val sampleKanjiData = KanjiData(
        kanji = "食",
        onReadings = listOf("ショク", "ジキ"),
        kunReadings = listOf("た.べる", "く.う"),
        meanings = listOf("eat", "food"),
        jlptLevel = 5,
        readingShortScore = 0.5,
        readingLongScore = 0.0,
        meaningShortScore = 1.0,
        meaningLongScore = 0.1,
        strokesShortScore = 0.0,
        strokesLongScore = 0.0,
        kanjiObject = Kanji(
            "食",
            listOf("ショク", "ジキ"),
            listOf("た.べる", "く.う"),
            listOf("eat", "food"),
            listOf(),
            listOf(),
            5
        ),
        kanjiId = 39135
    )

    val sampleWordList = listOf(
        Item(1, Word("食べる", "たべる", listOf("to eat"), listOf(), false, ""), 0.5, 0.3, 0, true),
        Item(2, Word("食事", "しょくじ", listOf("meal"), listOf(), false, ""), 0.8, 0.6, 0, true),
        Item(3, Word("食堂", "しょくどう", listOf("dining hall", "cafeteria"), listOf(), false, ""), 0.3, 0.1, 0, true)
    )

    KanjiDisplayScreen(
        uiState = KanjiDisplayUiState(
            selectedTab = KanjiDisplayTab.WORDS,
            kanjiData = sampleKanjiData,
            wordList = sampleWordList
        ),
        onTabSelected = {},
        onBackClick = {},
        onUpdateScore = { _, _ -> },
        onWordClick = {},
        onHistoryItemClick = {}
    )
}
