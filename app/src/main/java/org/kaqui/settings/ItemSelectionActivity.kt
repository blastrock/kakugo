package org.kaqui.settings

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import org.kaqui.BaseActivity
import org.kaqui.R
import org.kaqui.TopBar
import org.kaqui.Separator
import org.kaqui.StatsBar
import org.kaqui.getColorFromScore
import org.kaqui.getColorFromAttr
import org.kaqui.theme.KakugoTheme
import org.kaqui.model.Classifier
import org.kaqui.model.Database
import org.kaqui.model.LearningDbView
import org.kaqui.model.Item
import org.kaqui.model.text
import org.kaqui.model.description

data class ItemData(
    val id: Int,
    val text: String,
    val description: String,
    val enabled: Boolean,
    val shortScore: Double
)

data class ItemSelectionUiState(
    val mode: SelectionMode = SelectionMode.HIRAGANA,
    val title: String = "",
    val items: List<ItemData> = emptyList(),
    val stats: LearningDbView.Stats = LearningDbView.Stats(0, 0, 0, 0),
)

class ItemSelectionViewModel : ViewModel() {
    var uiState by mutableStateOf(ItemSelectionUiState())
        private set

    private lateinit var dbView: LearningDbView
    private var kanaWords: Boolean = true

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
        val items = itemIds.map { id ->
            val item = dbView.getItem(id)
            ItemData(
                id = item.id,
                text = item.text(kanaWords),
                description = item.description,
                enabled = item.enabled,
                shortScore = item.shortScore
            )
        }
        val stats = dbView.getStats()

        uiState = uiState.copy(
            items = items,
            stats = stats
        )
    }

    fun onItemEnabledChange(itemId: Int, enabled: Boolean) {
        dbView.setItemEnabled(itemId, enabled)
        // Update local state
        uiState = uiState.copy(
            items = uiState.items.map {
                if (it.id == itemId) it.copy(enabled = enabled) else it
            },
            stats = dbView.getStats()
        )
    }

    fun selectAll() {
        dbView.setAllEnabled(true)
        uiState = uiState.copy(
            items = uiState.items.map { it.copy(enabled = true) },
            stats = dbView.getStats()
        )
    }

    fun selectNone() {
        dbView.setAllEnabled(false)
        uiState = uiState.copy(
            items = uiState.items.map { it.copy(enabled = false) },
            stats = dbView.getStats()
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
                onBackClick = { finish() },
                onItemEnabledChange = viewModel::onItemEnabledChange,
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
    onBackClick: () -> Unit,
    onItemEnabledChange: (Int, Boolean) -> Unit,
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

    KakugoTheme {
        Surface(color = MaterialTheme.colors.background) {
            Scaffold(
                topBar = {
                    TopBar(
                        title = title,
                        onBackClick = onBackClick,
                        actions = {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options"
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
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    ItemListWithStats(
                        items = uiState.items,
                        stats = uiState.stats,
                        onItemEnabledChange = onItemEnabledChange
                    )
                }
            }
        }
    }
}

@Composable
fun ItemListWithStats(
    items: List<ItemData>,
    stats: LearningDbView.Stats,
    onItemEnabledChange: (Int, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Stats bar
        StatsBar(
            itemsDontKnow = stats.disabled,
            itemsBad = stats.bad,
            itemsMeh = stats.meh,
            itemsGood = stats.good
        )

        // Items list
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = items,
                key = { it.id }  // Stable keys for performance
            ) { itemData ->
                ItemRow(
                    itemData = itemData,
                    onEnabledChange = onItemEnabledChange
                )
                Separator()
            }
        }
    }
}

@Composable
fun ItemRow(
    itemData: ItemData,
    onEnabledChange: (Int, Boolean) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Checkbox(
            checked = itemData.enabled,
            onCheckedChange = { checked ->
                onEnabledChange(itemData.id, checked)
            },
            modifier = Modifier.padding(8.dp)
        )

        // Item text with colored background
        val colorAttr = getColorFromScore(itemData.shortScore)
        val backgroundColor = Color(context.getColorFromAttr(colorAttr))

        Box(
            modifier = Modifier
                .defaultMinSize(if (itemData.text.length > 1) 50.dp else 35.dp, 35.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .padding(0.dp),
            contentAlignment = Alignment.Center,
            //contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = itemData.text,
                fontSize = 25.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Description text
        Text(
            text = itemData.description,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            fontSize = 16.sp
        )
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
        items = sampleItems,
        stats = LearningDbView.Stats(
            bad = 1,
            meh = 1,
            good = 2,
            disabled = 6
        ),
    )

    KakugoTheme {
        ItemSelectionScreen(
            uiState = sampleUiState,
            onBackClick = { },
            onItemEnabledChange = { _, _ -> },
            onSelectAll = { },
            onSelectNone = { }
        )
    }
}
