package org.kaqui.itemdetails

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import org.kaqui.AppScaffold
import org.kaqui.Separator
import org.kaqui.model.Database
import org.kaqui.model.HiraganaRange
import org.kaqui.model.Item
import org.kaqui.model.Kanji
import org.kaqui.model.KatakanaRange
import org.kaqui.model.KnowledgeType
import org.kaqui.model.Word
import org.kaqui.settings.ItemData
import org.kaqui.settings.ItemRow
import org.kaqui.showKanjiInDict
import org.kaqui.showWordInDict
import org.kaqui.startActivity
import org.kaqui.SrsCalculator
import org.kaqui.theme.LocalThemeAttributes
import org.kaqui.theme.ThemeAttributes
import java.util.Calendar
import kotlinx.coroutines.launch

enum class WordDisplayTab {
    WORD, KANJI
}

data class MemorizationStatus(
    val text: String,
    val color: Color
)

data class WordData(
    val word: String,
    val reading: String,
    val meanings: List<String>,
    val readingShortScore: Double,
    val readingLongScore: Double,
    val meaningShortScore: Double,
    val meaningLongScore: Double,
    val wordObject: Word,
    val wordId: Int,
) {
    companion object {
        fun default() = WordData(
            word = "",
            reading = "",
            meanings = emptyList(),
            readingShortScore = 0.0,
            readingLongScore = 0.0,
            meaningShortScore = 0.0,
            meaningLongScore = 0.0,
            wordObject = Word("", "", emptyList(), emptyList(), false),
            wordId = -1
        )
    }
}

data class WordDisplayUiState(
    val selectedTab: WordDisplayTab = WordDisplayTab.WORD,
    val wordData: WordData = WordData.default(),
    val kanjiList: List<Item> = emptyList(),
)

class WordDisplayViewModel : ViewModel() {
    var uiState by mutableStateOf(WordDisplayUiState())
        private set

    fun initialize(context: Context, wordId: Int) {
        val database = Database.getInstance(context)

        // Load word with both knowledge types
        val wordReading = database.getWord(wordId, KnowledgeType.Reading)
        val wordMeaning = database.getWord(wordId, KnowledgeType.Meaning)
        val wordContents = wordReading.contents as Word

        val wordData = WordData(
            word = wordContents.word,
            reading = wordContents.reading,
            meanings = wordContents.meanings,
            readingShortScore = wordReading.shortScore,
            readingLongScore = wordReading.longScore,
            meaningShortScore = wordMeaning.shortScore,
            meaningLongScore = wordMeaning.longScore,
            wordObject = wordContents,
            wordId = wordId
        )

        // Extract and load kanji - return Items to get IDs for navigation
        val kanjiList = extractKanjiFromWord(wordContents.word).mapNotNull { char ->
            database.getKanjiByCharacter(char)
        }

        uiState = uiState.copy(
            wordData = wordData,
            kanjiList = kanjiList
        )
    }

    fun onTabSelected(tab: WordDisplayTab) {
        uiState = uiState.copy(selectedTab = tab)
    }

    fun updateScore(context: Context, knowledgeType: KnowledgeType, increase: Boolean) {
        val database = Database.getInstance(context)
        val wordView = database.getWordView(knowledgeType)

        val currentShortScore = when (knowledgeType) {
            KnowledgeType.Reading -> uiState.wordData.readingShortScore
            KnowledgeType.Meaning -> uiState.wordData.meaningShortScore
            else -> throw IllegalArgumentException("Invalid knowledge type for word: $knowledgeType")
        }

        val currentLongScore = when (knowledgeType) {
            KnowledgeType.Reading -> uiState.wordData.readingLongScore
            KnowledgeType.Meaning -> uiState.wordData.meaningLongScore
            else -> throw IllegalArgumentException("Invalid knowledge type for word: $knowledgeType")
        }

        val scoreUpdate = SrsCalculator.createManualScoreUpdate(
            itemId = uiState.wordData.wordId,
            currentShortScore = currentShortScore,
            currentLongScore = currentLongScore,
            increase = increase
        )

        wordView.applyScoreUpdate(scoreUpdate)
        initialize(context, uiState.wordData.wordId)
    }

    private fun extractKanjiFromWord(word: String): List<String> {
        return word.toList()
            .filter { c -> c.code !in HiraganaRange && c.code !in KatakanaRange }
            .map { it.toString() }
            .distinct()
    }
}

class WordDisplayActivity : ComponentActivity() {
    private val viewModel: WordDisplayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))

        val wordId = intent.getIntExtra("word_id", -1)
        if (wordId == -1) {
            finish()
            return
        }

        viewModel.initialize(this, wordId)

        setContent {
            WordDisplayScreen(
                uiState = viewModel.uiState,
                onTabSelected = viewModel::onTabSelected,
                onBackClick = { finish() },
                onUpdateScore = { knowledgeType, increase ->
                    viewModel.updateScore(this@WordDisplayActivity, knowledgeType, increase)
                }
            )
        }
    }
}

@Composable
fun WordDisplayScreen(
    uiState: WordDisplayUiState,
    onTabSelected: (WordDisplayTab) -> Unit,
    onBackClick: () -> Unit,
    onUpdateScore: (KnowledgeType, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val hasKanji = uiState.kanjiList.isNotEmpty()
    val pageCount = if (hasKanji) 2 else 1
    val pagerState = rememberPagerState(initialPage = uiState.selectedTab.ordinal, pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val targetTab = when (page) {
                0 -> WordDisplayTab.WORD
                1 -> WordDisplayTab.KANJI
                else -> WordDisplayTab.WORD
            }
            onTabSelected(targetTab)
        }
    }

    AppScaffold(
        title = uiState.wordData.word,
        onBackClick = onBackClick,
        actions = {
            IconButton(onClick = { showWordInDict(context, uiState.wordData.wordObject) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(org.kaqui.R.string.open_in_external_dictionary)
                )
            }
        },
        belowAppBar = {
            // Tab selector (only show if there are kanji)
            if (hasKanji) {
                val tabs = listOf(
                    stringResource(org.kaqui.R.string.word_tab),
                    stringResource(org.kaqui.R.string.kanji_tab)
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
        }
    ) { paddingValues ->
        val wordData = uiState.wordData

        // Content area with swipeable pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> WordTabContent(
                    wordData = wordData,
                    contentPadding = paddingValues,
                    onUpdateScore = onUpdateScore
                )
                1 -> KanjiTabContent(
                    kanjiList = uiState.kanjiList,
                    onKanjiClick = { kanjiItem ->
                        context.startActivity<KanjiDisplayActivity>("kanji_id" to kanjiItem.id)
                    },
                    contentPadding = paddingValues
                )
            }
        }
    }
}

@Composable
fun WordTabContent(
    wordData: WordData,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onUpdateScore: (KnowledgeType, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Word text in large font
        item {
            Text(
                text = wordData.word,
                fontSize = 48.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Readings section
        item {
            Text(
                text = stringResource(org.kaqui.R.string.reading_label) + ":",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = wordData.reading,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Definitions section
        item {
            Text(
                text = stringResource(org.kaqui.R.string.definitions_label) + ":",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        items(wordData.meanings) { meaning ->
            Text(
                text = meaning,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Memorization status
        item {
            Text(
                text = stringResource(org.kaqui.R.string.learning_status_label) + ":",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            val themeColors = LocalThemeAttributes.current
            val readingStatus = getMemorizationStatus(
                wordData.readingShortScore,
                wordData.readingLongScore,
                themeColors
            )

            MemorizationStatusRow(
                label = stringResource(org.kaqui.R.string.reading_label),
                status = readingStatus.text,
                color = readingStatus.color,
                shortScore = wordData.readingShortScore,
                onButtonClick = {
                    val increase = wordData.readingShortScore != 1.0
                    onUpdateScore(KnowledgeType.Reading, increase)
                }
            )
        }

        item {
            val themeColors = LocalThemeAttributes.current
            val meaningStatus = getMemorizationStatus(
                wordData.meaningShortScore,
                wordData.meaningLongScore,
                themeColors
            )

            MemorizationStatusRow(
                label = stringResource(org.kaqui.R.string.meaning_label),
                status = meaningStatus.text,
                color = meaningStatus.color,
                shortScore = wordData.meaningShortScore,
                onButtonClick = {
                    val increase = wordData.meaningShortScore != 1.0
                    onUpdateScore(KnowledgeType.Meaning, increase)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MemorizationStatusRow(
    label: String,
    status: String,
    color: Color,
    shortScore: Double,
    onButtonClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(20f)
        )
        StatusTag(
            text = status,
            backgroundColor = color,
            modifier = Modifier.width(150.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (shortScore == 1.0) {
            TextButton(
                onClick = onButtonClick,
                modifier = Modifier.weight(30f)
            ) {
                Text(stringResource(org.kaqui.R.string.review_sooner), textAlign = TextAlign.Center, fontSize = 12.sp)
            }
        } else {
            TextButton(
                onClick = onButtonClick,
                modifier = Modifier.weight(30f)
            ) {
                Text(stringResource(org.kaqui.R.string.skip_next_review), textAlign = TextAlign.Center, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun StatusTag(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun getMemorizationStatus(
    shortScore: Double,
    longScore: Double,
    themeColors: ThemeAttributes
): MemorizationStatus {
    return when {
        shortScore == 0.0 -> MemorizationStatus(stringResource(org.kaqui.R.string.memorization_status_not_known), themeColors.itemBad)
        shortScore > 0 && longScore == 0.0 -> MemorizationStatus(stringResource(org.kaqui.R.string.memorization_status_learning), themeColors.itemMeh)
        longScore > 0 && longScore <= 0.2 -> MemorizationStatus(stringResource(org.kaqui.R.string.memorization_status_memorizing), themeColors.itemLearn)
        longScore > 0.2 && longScore <= 0.8 -> MemorizationStatus(stringResource(org.kaqui.R.string.memorization_status_well_known), themeColors.itemGood)
        longScore > 0.8 -> MemorizationStatus(stringResource(org.kaqui.R.string.memorization_status_perfectly_known), themeColors.itemPerfect)
        else -> MemorizationStatus(stringResource(org.kaqui.R.string.memorization_status_unknown), themeColors.itemBad)
    }
}

@Composable
fun KanjiTabContent(
    kanjiList: List<Item>,
    onKanjiClick: (Item) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        items(kanjiList) { kanjiItem ->
            val kanji = kanjiItem.contents as Kanji
            val hasData = kanji.on_readings.isNotEmpty() || kanji.kun_readings.isNotEmpty() || kanji.meanings.isNotEmpty()
            val description = kanji.on_readings.joinToString(", ") + "\n" +
                    kanji.kun_readings.joinToString(", ") + "\n" +
                    kanji.meanings.joinToString(", ")

            ItemRow(
                itemData = ItemData(
                    id = kanjiItem.id,
                    text = kanji.kanji,
                    description = description,
                    enabled = kanjiItem.enabled,
                    shortScore = kanjiItem.shortScore
                ),
                onClick = if (hasData) {{ onKanjiClick(kanjiItem) }} else null
            )
            Separator()
        }
    }
}

@Preview(showBackground = true, name = "Word Tab - With Kanji")
@Composable
fun PreviewWordDisplayScreenWordTab() {
    val sampleWordData = WordData(
        word = "食べる",
        reading = "たべる",
        meanings = listOf("to eat", "to consume"),
        readingShortScore = 0.5,
        readingLongScore = 0.0,
        meaningShortScore = 1.0,
        meaningLongScore = 0.1,
        wordObject = Word("食べる", "たべる", listOf("to eat", "to consume"), listOf(), false),
        wordId = 123
    )

    val sampleKanjiList = listOf(
        Item(
            39135,
            Kanji(
                "食",
                listOf("ショク", "ジキ"),
                listOf("た.べる", "く.う"),
                listOf("eat", "food"),
                listOf(),
                listOf(),
                5
            ),
            0.5, 0.3, 0, true
        )
    )

    WordDisplayScreen(
        uiState = WordDisplayUiState(
            selectedTab = WordDisplayTab.WORD,
            wordData = sampleWordData,
            kanjiList = sampleKanjiList
        ),
        onTabSelected = {},
        onBackClick = {},
        onUpdateScore = { _, _ -> }
    )
}

@Preview(showBackground = true, name = "Kanji Tab")
@Composable
fun PreviewWordDisplayScreenKanjiTab() {
    val sampleWordData = WordData(
        word = "食べる",
        reading = "たべる",
        meanings = listOf("to eat", "to consume"),
        readingShortScore = 0.5,
        readingLongScore = 0.75,
        meaningShortScore = 0.3,
        meaningLongScore = 0.45,
        wordObject = Word("食べる", "たべる", listOf("to eat", "to consume"), listOf(), false),
        wordId = 123
    )

    val sampleKanjiList = listOf(
        Item(
            39135,
            Kanji(
                "食",
                listOf("ショク", "ジキ"),
                listOf("た.べる", "く.う"),
                listOf("eat", "food"),
                listOf(),
                listOf(),
                5
            ),
            0.5, 0.3, 0, true
        )
    )

    WordDisplayScreen(
        uiState = WordDisplayUiState(
            selectedTab = WordDisplayTab.KANJI,
            wordData = sampleWordData,
            kanjiList = sampleKanjiList
        ),
        onTabSelected = {},
        onBackClick = {},
        onUpdateScore = { _, _ -> }
    )
}

@Preview(showBackground = true, name = "Word Tab - Kana Only")
@Composable
fun PreviewWordDisplayScreenKanaOnly() {
    val sampleWordData = WordData(
        word = "ひらがな",
        reading = "ひらがな",
        meanings = listOf("hiragana", "Japanese syllabary"),
        readingShortScore = 1.0,
        readingLongScore = 0.5,
        meaningShortScore = 0.7,
        meaningLongScore = 1.0,
        wordObject = Word(
            "ひらがな",
            "ひらがな",
            listOf("hiragana", "Japanese syllabary"),
            listOf(),
            true
        ),
        wordId = 123
    )

    WordDisplayScreen(
        uiState = WordDisplayUiState(
            selectedTab = WordDisplayTab.WORD,
            wordData = sampleWordData,
            kanjiList = emptyList()
        ),
        onTabSelected = {},
        onBackClick = {},
        onUpdateScore = { _, _ -> }
    )
}

@Preview(showBackground = true, name = "Multiple Kanji")
@Composable
fun PreviewWordDisplayScreenMultipleKanji() {
    val sampleWordData = WordData(
        word = "日本語",
        reading = "にほんご",
        meanings = listOf("Japanese language"),
        readingShortScore = 1.0,
        readingLongScore = 0.95,
        meaningShortScore = 1.0,
        meaningLongScore = 0.9,
        wordObject = Word("日本語", "にほんご", listOf("Japanese language"), listOf(), false),
        wordId = 123
    )

    val sampleKanjiList = listOf(
        Item(
            26085,
            Kanji(
                "日",
                listOf("ニチ", "ジツ"),
                listOf("ひ", "か"),
                listOf("day", "sun", "Japan"),
                listOf(),
                listOf(),
                5
            ),
            0.8, 0.6, 0, true
        ),
        Item(
            26412,
            Kanji(
                "本",
                listOf("ホン"),
                listOf("もと"),
                listOf("book", "origin"),
                listOf(),
                listOf(),
                5
            ),
            0.7, 0.5, 0, true
        ),
        Item(
            35486,
            Kanji(
                "語",
                listOf("ゴ"),
                listOf("かた.る", "かた.らう"),
                listOf("language", "word"),
                listOf(),
                listOf(),
                3
            ),
            0.6, 0.4, 0, true
        )
    )

    WordDisplayScreen(
        uiState = WordDisplayUiState(
            selectedTab = WordDisplayTab.KANJI,
            wordData = sampleWordData,
            kanjiList = sampleKanjiList
        ),
        onTabSelected = {},
        onBackClick = {},
        onUpdateScore = { _, _ -> }
    )
}
