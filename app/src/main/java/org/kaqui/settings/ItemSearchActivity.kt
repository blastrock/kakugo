package org.kaqui.settings

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import org.kaqui.R
import org.kaqui.model.Database
import org.kaqui.model.LearningDbView
import org.kaqui.model.description
import org.kaqui.model.text
import org.kaqui.theme.KakugoTheme

data class ItemSearchUiState(
    val searchQuery: String = "",
    val itemIds: List<Int> = emptyList(),
    val stats: LearningDbView.Stats = LearningDbView.Stats(0, 0, 0, 0),
    val kanaWords: Boolean = true,
    val cacheVersion: Int = 0
)

class ItemSearchViewModel : ViewModel() {
    var uiState by mutableStateOf(ItemSearchUiState())
        private set

    private lateinit var dbView: LearningDbView
    private var kanaWords: Boolean = true
    private lateinit var mode: SelectionMode
    private val itemCache = mutableMapOf<Int, ItemData>()

    fun getItemData(id: Int): ItemData {
        return itemCache.getOrPut(id) {
            val item = dbView.getItem(id)
            ItemData(
                id = item.id,
                text = item.text(kanaWords),
                description = item.description,
                enabled = item.enabled,
                shortScore = item.shortScore
            )
        }
    }

    fun initialize(dbView: LearningDbView, mode: SelectionMode, kanaWords: Boolean) {
        this.dbView = dbView
        this.kanaWords = kanaWords
        this.mode = mode

        uiState = uiState.copy(stats = dbView.getStats())
    }

    fun onSearchQueryChange(query: String) {
        uiState = uiState.copy(searchQuery = query)

        // Only search if query is not empty
        if (query.isNotEmpty()) {
            searchItems(query)
        } else {
            // Clear results when query is empty
            uiState = uiState.copy(
                itemIds = emptyList(),
            )
        }
    }

    private fun searchItems(query: String) {
        val itemIds = dbView.search(query)

        uiState = uiState.copy(
            itemIds = itemIds,
        )
    }

    fun onItemEnabledChange(itemId: Int, enabled: Boolean) {
        dbView.setItemEnabled(itemId, enabled)
        // Invalidate cache for this item
        itemCache.remove(itemId)
        // Update stats
        uiState = uiState.copy(
            stats = dbView.getStats(),
            cacheVersion = uiState.cacheVersion + 1
        )
    }
}

class ItemSearchActivity : ComponentActivity() {
    private val viewModel: ItemSearchViewModel by viewModels()

    private lateinit var mode: SelectionMode

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )

        mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("mode", SelectionMode::class.java)!!
        } else {
            intent.getSerializableExtra("mode") as SelectionMode
        }

        val dbView = when (mode) {
            SelectionMode.KANJI -> Database.getInstance(this).getKanjiView()
            SelectionMode.WORD -> Database.getInstance(this).getWordView()
            else -> throw IllegalArgumentException("ItemSearchActivity only supports KANJI and WORD modes")
        }

        val kanaWords = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("kana_words", true)

        viewModel.initialize(dbView, mode, kanaWords)

        setContent {
            val uiState = viewModel.uiState

            ItemSearchScreen(
                uiState = uiState,
                mode = mode,
                getItemData = viewModel::getItemData,
                onBackClick = { finish() },
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onItemEnabledChange = viewModel::onItemEnabledChange
            )
        }
    }
}

@Composable
fun ItemSearchScreen(
    uiState: ItemSearchUiState,
    mode: SelectionMode,
    getItemData: (Int) -> ItemData,
    onBackClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onItemEnabledChange: (Int, Boolean) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    KakugoTheme {
        Surface(color = MaterialTheme.colors.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        },
                        title = {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.search)
                                    )
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.clear_search),
                                                tint = MaterialTheme.colors.onPrimary
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                colors = TextFieldDefaults.textFieldColors(
                                    backgroundColor = MaterialTheme.colors.primary,
                                    textColor = MaterialTheme.colors.onPrimary,
                                    placeholderColor = MaterialTheme.colors.onPrimary.copy(alpha = 0.6f),
                                    focusedIndicatorColor = MaterialTheme.colors.onPrimary,
                                    unfocusedIndicatorColor = MaterialTheme.colors.onPrimary.copy(alpha = 0.4f),
                                    cursorColor = MaterialTheme.colors.onPrimary
                                )
                            )
                        },
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary,
                        elevation = 4.dp
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    ItemListWithStats(
                        itemIds = uiState.itemIds,
                        stats = uiState.stats,
                        cacheVersion = uiState.cacheVersion,
                        getItemData = getItemData,
                        onItemEnabledChange = onItemEnabledChange
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Item Search - Empty")
@Composable
fun PreviewItemSearchEmpty() {
    val emptyUiState = ItemSearchUiState(
        searchQuery = "",
        itemIds = emptyList(),
        stats = LearningDbView.Stats(1, 2, 3, 4),
        kanaWords = true,
        cacheVersion = 0
    )

    KakugoTheme {
        ItemSearchScreen(
            uiState = emptyUiState,
            mode = SelectionMode.KANJI,
            getItemData = { ItemData(it, "", "", false, 0.0) },
            onBackClick = { },
            onSearchQueryChange = { },
            onItemEnabledChange = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Item Search - Kanji Results")
@Composable
fun PreviewItemSearchKanjiResults() {
    val sampleItems = listOf(
        ItemData(1, "水", "water", true, 1.0),
        ItemData(2, "火", "fire", true, 0.9),
        ItemData(3, "木", "tree", true, 0.8),
        ItemData(4, "金", "gold, money", false, 0.5),
        ItemData(5, "土", "earth, soil", false, 0.3),
    )

    val sampleItemsMap = sampleItems.associateBy { it.id }

    val searchUiState = ItemSearchUiState(
        searchQuery = "五行",
        itemIds = sampleItems.map { it.id },
        stats = LearningDbView.Stats(
            bad = 1,
            meh = 1,
            good = 3,
            disabled = 0
        ),
        kanaWords = true,
        cacheVersion = 0
    )

    KakugoTheme {
        ItemSearchScreen(
            uiState = searchUiState,
            mode = SelectionMode.KANJI,
            getItemData = { id -> sampleItemsMap[id]!! },
            onBackClick = { },
            onSearchQueryChange = { },
            onItemEnabledChange = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Item Search - Word Results")
@Composable
fun PreviewItemSearchWordResults() {
    val sampleWords = listOf(
        ItemData(1, "こんにちは", "hello", true, 1.0),
        ItemData(2, "ありがとう", "thank you", true, 0.9),
        ItemData(3, "さようなら", "goodbye", true, 0.7),
        ItemData(4, "すみません", "excuse me, sorry", false, 0.4),
        ItemData(5, "おはよう", "good morning", false, 0.2),
    )

    val sampleWordsMap = sampleWords.associateBy { it.id }

    val searchUiState = ItemSearchUiState(
        searchQuery = "greeting",
        itemIds = sampleWords.map { it.id },
        stats = LearningDbView.Stats(
            bad = 1,
            meh = 1,
            good = 3,
            disabled = 0
        ),
        kanaWords = true,
        cacheVersion = 0
    )

    KakugoTheme {
        ItemSearchScreen(
            uiState = searchUiState,
            mode = SelectionMode.WORD,
            getItemData = { id -> sampleWordsMap[id]!! },
            onBackClick = { },
            onSearchQueryChange = { },
            onItemEnabledChange = { _, _ -> }
        )
    }
}
