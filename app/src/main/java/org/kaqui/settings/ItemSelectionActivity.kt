package org.kaqui.settings

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import org.kaqui.AppScaffold
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.StatsBar
import org.kaqui.model.Classifier
import org.kaqui.itemdetails.KanjiDisplayActivity
import org.kaqui.itemdetails.WordDisplayActivity
import org.kaqui.model.Database
import org.kaqui.model.LearningDbView
import org.kaqui.startActivity
import org.kaqui.model.description
import org.kaqui.model.text
import org.kaqui.theme.KakugoTheme

data class ItemSelectionUiState(
    val mode: SelectionMode = SelectionMode.HIRAGANA,
    val title: String = "",
    val itemIds: List<Int> = emptyList(),
    val stats: LearningDbView.Stats = LearningDbView.Stats(0, 0, 0, 0),
    val cacheVersion: Int = 0,
)

class ItemSelectionViewModel : ViewModel() {
    var uiState by mutableStateOf(ItemSelectionUiState())
        private set

    private lateinit var dbView: LearningDbView
    private var kanaWords: Boolean = true
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
        uiState = uiState.copy(
            mode = mode,
        )
        loadItems()
    }

    private fun loadItems() {
        val itemIds = dbView.getAllItems()
        val stats = dbView.getStats()

        uiState = uiState.copy(
            itemIds = itemIds,
            stats = stats
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

    fun selectAll() {
        dbView.setAllEnabled(true)
        // Clear cache since all items changed
        itemCache.clear()
        uiState = uiState.copy(
            stats = dbView.getStats(),
            cacheVersion = uiState.cacheVersion + 1
        )
    }

    fun selectNone() {
        dbView.setAllEnabled(false)
        // Clear cache since all items changed
        itemCache.clear()
        uiState = uiState.copy(
            stats = dbView.getStats(),
            cacheVersion = uiState.cacheVersion + 1
        )
    }
}

class ItemSelectionActivity : ComponentActivity() {
    private val viewModel: ItemSelectionViewModel by viewModels()

    private lateinit var mode: SelectionMode
    private var classifier: Classifier? = null

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

        if (mode == SelectionMode.KANJI || mode == SelectionMode.WORD) {
            classifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("classifier", Classifier::class.java)
            } else {
                intent.getParcelableExtra("classifier")
            }
        }

        val dbView = when (mode) {
            SelectionMode.HIRAGANA -> Database.getInstance(this).getHiraganaView()
            SelectionMode.KATAKANA -> Database.getInstance(this).getKatakanaView()
            SelectionMode.KANJI -> Database.getInstance(this).getKanjiView(classifier = classifier!!)
            SelectionMode.WORD -> Database.getInstance(this).getWordView(classifier = classifier!!)
        }

        val kanaWords = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("kana_words", true)

        viewModel.initialize(dbView, mode, kanaWords)

        setContent {
            val uiState = viewModel.uiState

            ItemSelectionScreen(
                uiState = uiState,
                getItemData = viewModel::getItemData,
                onBackClick = { finish() },
                onItemEnabledChange = viewModel::onItemEnabledChange,
                onItemClick = when (mode) {
                    SelectionMode.KANJI -> { id: Int -> startActivity<KanjiDisplayActivity>("kanji_id" to id) }
                    SelectionMode.WORD -> { id: Int -> startActivity<WordDisplayActivity>("word_id" to id) }
                    else -> null
                },
                onSelectAll = viewModel::selectAll,
                onSelectNone = viewModel::selectNone
            )
        }
    }

    companion object {
        private const val TAG = "ItemSelectionActivity"
    }
}

@Composable
fun ItemSelectionScreen(
    uiState: ItemSelectionUiState,
    getItemData: (Int) -> ItemData,
    onBackClick: () -> Unit,
    onItemEnabledChange: (Int, Boolean) -> Unit,
    onItemClick: ((Int) -> Unit)? = null,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit
) {
    // Get title for the mode
    val title = when (uiState.mode) {
        SelectionMode.HIRAGANA -> stringResource(R.string.hiragana_selection)
        SelectionMode.KATAKANA -> stringResource(R.string.katakana_selection)
        SelectionMode.KANJI -> stringResource(R.string.kanji_selection)
        SelectionMode.WORD -> stringResource(R.string.word_selection)
    }

    var showMenu by remember { mutableStateOf(false) }

    AppScaffold(
        title = title,
        onBackClick = onBackClick,
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(onClick = {
                    showMenu = false
                    onSelectAll()
                }) {
                    Text(stringResource(R.string.select_all))
                }
                DropdownMenuItem(onClick = {
                    showMenu = false
                    onSelectNone()
                }) {
                    Text(stringResource(R.string.select_none))
                }
            }
        }
    ) { paddingValues ->
                Box(
                    modifier = Modifier .fillMaxSize()
                ) {
                    ItemListWithStats(
                        itemIds = uiState.itemIds,
                        stats = uiState.stats,
                        cacheVersion = uiState.cacheVersion,
                        getItemData = getItemData,
                        onItemEnabledChange = onItemEnabledChange,
                        onItemClick = onItemClick,
                        contentPadding = paddingValues,
                    )
                }
            }
        }

@Composable
fun ItemListWithStats(
    itemIds: List<Int>,
    stats: LearningDbView.Stats,
    cacheVersion: Int,
    getItemData: (Int) -> ItemData,
    onItemEnabledChange: (Int, Boolean) -> Unit,
    onItemClick: ((Int) -> Unit)? = null,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        StatsBar(
            itemsDontKnow = stats.disabled,
            itemsBad = stats.bad,
            itemsMeh = stats.meh,
            itemsGood = stats.good
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(
                items = itemIds,
                key = { it }  // Stable keys for performance
            ) { id ->
                val itemData = remember(id, cacheVersion) { getItemData(id) }
                ItemRow(
                    itemData = itemData,
                    onEnabledChange = onItemEnabledChange,
                    onClick = if (onItemClick != null) {{ onItemClick(itemData.id) }} else null
                )
                Separator()
            }
        }
    }
}

@Preview(showBackground = true, name = "Item Selection Screen - Kanji")
@Composable
fun PreviewItemSelectionScreenKanji() {
    val sampleItems = listOf(
        ItemData(1, "一", "one", true, 1.0),
        ItemData(2, "二", "two", true, 0.9),
        ItemData(3, "三", "three", true, 0.8),
        ItemData(4, "四", "four", true, 0.6),
        ItemData(5, "五", "five", false, 0.3),
        ItemData(6, "六", "six", false, 0.2),
        ItemData(7, "七", "seven", false, 0.0),
        ItemData(8, "八", "eight", false, 0.0),
        ItemData(9, "九", "nine", false, 0.0),
        ItemData(10, "十九八", "ten", false, 0.0),
    )

    val sampleUiState = ItemSelectionUiState(
        mode = SelectionMode.KANJI,
        itemIds = sampleItems.map { it.id },
        stats = LearningDbView.Stats(
            bad = 1,
            meh = 1,
            good = 2,
            disabled = 6
        ),
        cacheVersion = 0
    )

    val sampleItemsMap = sampleItems.associateBy { it.id }

    KakugoTheme {
        ItemSelectionScreen(
            uiState = sampleUiState,
            getItemData = { id -> sampleItemsMap[id]!! },
            onBackClick = { },
            onItemEnabledChange = { _, _ -> },
            onSelectAll = { },
            onSelectNone = { }
        )
    }
}
