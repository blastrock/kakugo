package org.kaqui.testactivities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FabPosition
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NavUtils
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.kaqui.BetterButton
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.StatsBar
import org.kaqui.TestEngine
import org.kaqui.TopBar
import org.kaqui.model.Certainty
import org.kaqui.model.Database
import org.kaqui.model.Item
import org.kaqui.model.Kanji
import org.kaqui.model.LearningDbView
import org.kaqui.model.TestType
import org.kaqui.model.Word
import org.kaqui.model.description
import org.kaqui.model.text
import org.kaqui.showItemProbabilityData
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes
import org.kaqui.toName

enum class HistoryItemStyle {
    GOOD, BAD, DONT_KNOW
}

data class HistoryItem(
    val item: Item,
    val probabilityData: TestEngine.DebugData?,
    val style: HistoryItemStyle,
    val prependSeparator: Boolean = false,
)

data class HistoryState(
    val items: List<HistoryItem> = listOf(),
    val lastCorrect: Item? = null,
    val lastWrong: Item? = null,
    val lastProbabilityData: TestEngine.DebugData? = null,
)

data class TestActivityUiState(
    val fragment: Class<out Fragment>? = null,
    val correctCount: Int = 0,
    val questionCount: Int = 0,
    val historyState: HistoryState = HistoryState(),
    val sheetExpanded: Boolean = false,
    val stats: LearningDbView.Stats = LearningDbView.Stats(
        good = 0,
        meh = 0,
        bad = 0,
        disabled = 0
    ),
    val title: String = ""
)

class TestViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TestActivityUiState())
    val uiState: StateFlow<TestActivityUiState> = _uiState.asStateFlow()

    lateinit var testEngine: TestEngine

    // Example of how you might initialize TestEngine if it needs application context
    fun initialize(testEngine: TestEngine) {
        this.testEngine = testEngine
        // Update initial counts from testEngine
        _uiState.update { currentState ->
            currentState.copy(
                correctCount = testEngine.correctCount,
                questionCount = testEngine.questionCount,
            )
        }
    }

    fun addGoodAnswerToHistory(
        correct: Item,
        probabilityData: TestEngine.DebugData?,
        refresh: Boolean,
    ) {
        if (refresh) {
            _uiState.update { currentState ->
                val newHistoryItems =
                    listOf(HistoryItem(correct, probabilityData, HistoryItemStyle.GOOD, true)) +
                            currentState.historyState.items.take(49)
                currentState.copy(
                    historyState = currentState.historyState.copy(
                        items = newHistoryItems,
                        lastCorrect = correct,
                        lastWrong = null,
                        lastProbabilityData = probabilityData
                    )
                )
            }
        }
    }

    fun addWrongAnswerToHistory(
        correct: Item,
        probabilityData: TestEngine.DebugData?,
        wrong: Item,
        refresh: Boolean,
    ) {
        if (refresh) {
            _uiState.update { currentState ->
                val newHistoryItems = listOf(
                    HistoryItem(correct, probabilityData, HistoryItemStyle.BAD, true),
                    HistoryItem(wrong, null, HistoryItemStyle.DONT_KNOW),
                ) + currentState.historyState.items.take(48)
                currentState.copy(
                    historyState = currentState.historyState.copy(
                        items = newHistoryItems,
                        lastCorrect = correct,
                        lastWrong = wrong,
                        lastProbabilityData = probabilityData
                    )
                )
            }
        }
    }

    fun addUnknownAnswerToHistory(
        correct: Item,
        probabilityData: TestEngine.DebugData?,
        refresh: Boolean,
    ) {
        if (refresh) {
            _uiState.update { currentState ->
                val newHistoryItems =
                    listOf(HistoryItem(correct, probabilityData, HistoryItemStyle.BAD, true)) +
                            currentState.historyState.items.take(49)
                currentState.copy(
                    historyState = currentState.historyState.copy(
                        items = newHistoryItems,
                        lastCorrect = correct,
                        lastWrong = correct,
                        lastProbabilityData = probabilityData
                    )
                )
            }
        }
    }

    fun setSheetExpanded(expanded: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(
                sheetExpanded = expanded
            )
        }
    }

    fun onAnswer(certainty: Certainty, wrong: Item?) {
        testEngine.markAnswer(certainty, wrong)
        _uiState.update {
            it.copy(
                correctCount = testEngine.correctCount,
                questionCount = testEngine.questionCount,
                stats = testEngine.itemView.getStats(),
            )
        }
    }

    fun setStats(stats: LearningDbView.Stats) {
        _uiState.update { currentState ->
            currentState.copy(
                stats = stats
            )
        }
    }

    fun setFragmentClass(testFragment: Class<out Fragment>) {
        _uiState.update { currentState ->
            currentState.copy(
                fragment = testFragment
            )
        }
    }

    fun setTitle(title: String) {
        _uiState.update { currentState ->
            currentState.copy(
                title = title
            )
        }
    }
}

class TestActivity : FragmentActivity(), TestFragmentHolder {
    override lateinit var testEngine: TestEngine

    private lateinit var testFragment: TestFragment

    private var localTestType: TestType? = null
    private var kanaWords = false

    private val viewModel: TestViewModel by viewModels()

    @Suppress("DEPRECATION")
    private val testTypes: List<TestType>
        get() = (intent.extras?.getSerializable("test_types") as? List<*>)?.filterIsInstance<TestType>()
            ?: emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        kanaWords = sharedPrefs.getBoolean("kana_words", true)

        testEngine = TestEngine(
            this,
            Database.getInstance(this),
            testTypes,
            viewModel::addGoodAnswerToHistory,
            viewModel::addWrongAnswerToHistory,
            viewModel::addUnknownAnswerToHistory
        )

        if (savedInstanceState == null) {
            nextQuestion()
        } else {
            testEngine.loadState(savedInstanceState)
            localTestType = testEngine.testType
        }

        viewModel.setStats(testEngine.itemView.getStats())

        viewModel.initialize(testEngine)

        // Setup back navigation handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val sheetExpanded = viewModel.uiState.value.sheetExpanded

                if (sheetExpanded) {
                    viewModel.setSheetExpanded(false)
                } else {
                    confirmActivityClose(false)
                }
            }
        })

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            KakugoTheme {
                Scaffold(
                    topBar = {
                        TopBar(
                            title = uiState.title,
                            onBackClick = { confirmActivityClose(true) }
                        )
                    }
                ) { paddingValues ->
                    TestScreen(
                        testFragment = uiState.fragment,
                        onFragmentUpdated = { fragment -> testFragment = fragment as TestFragment },
                        stats = uiState.stats,
                        correctCount = uiState.correctCount,
                        questionCount = uiState.questionCount,
                        historyState = uiState.historyState,
                        sheetExpanded = uiState.sheetExpanded,
                        onSheetExpandedChange = { viewModel.setSheetExpanded(it) },
                        kanaWords = kanaWords,
                        onItemClick = this::openItemInDictionary,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }

    private fun openItemInDictionary(item: Item) {
        when (val contents = item.contents) {
            is Kanji -> showItemInDict(contents)
            is Word -> showItemInDict(contents)
            else -> { /* do nothing */ }
        }
    }

    private fun showItemInDict(kanji: Kanji) {
        val intent = Intent("sk.baka.aedict3.action.ACTION_SEARCH_JMDICT")
        intent.putExtra("kanjis", kanji.kanji)
        intent.putExtra("search_in_kanjidic", true)
        intent.putExtra("showEntryDetailOnSingleResult", true)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://jisho.org/search/${kanji.kanji}%20%23kanji".toUri()
                )
            )
        }
    }

    private fun showItemInDict(word: Word) {
        val intent = Intent("sk.baka.aedict3.action.ACTION_SEARCH_JMDICT")
        intent.putExtra("kanjis", word.word)
        intent.putExtra("showEntryDetailOnSingleResult", true)
        intent.putExtra("match_jp", "Exact")
        intent.putExtra("deinflect", false)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://jisho.org/search/${word.word}".toUri()
                )
            )
        }
    }

    override fun onAnswer(button: View?, certainty: Certainty, wrong: Item?) {
        viewModel.onAnswer(certainty, wrong)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        testEngine.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun confirmActivityClose(upNavigation: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_test_stop_title)
            .setMessage(R.string.confirm_test_stop_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (upNavigation)
                    NavUtils.navigateUpFromSameTask(this)
                else
                    finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun nextQuestion() {
        testEngine.prepareNewQuestion()

        if (localTestType == testType) {
            testFragment.startNewQuestion()
            testFragment.refreshQuestion()
        } else {
            localTestType = testType
            val testFragment: Class<out Fragment> =
                when (testType) {
                    TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING, TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> QuizTestFragmentCompose::class.java
                    TestType.HIRAGANA_DRAWING, TestType.KATAKANA_DRAWING, TestType.KANJI_DRAWING -> DrawingTestFragmentCompose::class.java
                    TestType.KANJI_COMPOSITION -> CompositionTestFragmentCompose::class.java
                    TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.KATAKANA_TO_ROMAJI_TEXT -> TextTestFragmentCompose::class.java
                }

            viewModel.setFragmentClass(testFragment)

            viewModel.setTitle(getString(testType.toName()))
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TestScreen(
    testFragment: Class<out Fragment>?,
    onFragmentUpdated: (Fragment) -> Unit,
    stats: LearningDbView.Stats,
    correctCount: Int,
    questionCount: Int,
    historyState: HistoryState,
    sheetExpanded: Boolean,
    onSheetExpandedChange: (Boolean) -> Unit,
    kanaWords: Boolean,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeAttrs = LocalThemeAttributes.current
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberBottomSheetState(initialValue = BottomSheetValue.Collapsed)
    )
    val coroutineScope = rememberCoroutineScope()

    // Synchronize sheet state with UI state
    LaunchedEffect(sheetExpanded) {
        if (sheetExpanded && scaffoldState.bottomSheetState.isCollapsed) {
            scaffoldState.bottomSheetState.expand()
        } else if (!sheetExpanded && scaffoldState.bottomSheetState.isExpanded) {
            scaffoldState.bottomSheetState.collapse()
        }
    }

    // Listen to sheet state changes from user gestures
    LaunchedEffect(scaffoldState.bottomSheetState.isExpanded) {
        onSheetExpandedChange(scaffoldState.bottomSheetState.isExpanded)
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            HistoryBottomSheet(
                items = historyState.items,
                kanaWords = kanaWords,
                onItemClick = onItemClick
            )
        },
        sheetPeekHeight = 0.dp,
        sheetBackgroundColor = themeAttrs.historyBackground,
        floatingActionButton = {
            if (!sheetExpanded && historyState.items.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onSheetExpandedChange(true) },
                    modifier = Modifier
                        .offset(x = 10.dp, y = 10.dp)
                        .size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_upward),
                        contentDescription = "Show history",
                        tint = MaterialTheme.colors.onPrimary
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                StatsBar(itemsDontKnow = 0, itemsBad = stats.bad, itemsMeh = stats.meh, itemsGood = stats.good)

                Text(
                    text = LocalContext.current.getString(
                        R.string.score_string,
                        correctCount,
                        questionCount
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center
                )

                if (testFragment != null) {
                    AndroidFragment(
                        testFragment,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onUpdate = onFragmentUpdated,
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                LastItemRow(
                    lastCorrect = historyState.lastCorrect,
                    lastWrong = historyState.lastWrong,
                    lastProbabilityData = historyState.lastProbabilityData,
                    kanaWords = kanaWords,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
private fun LastItemRow(
    lastCorrect: Item?,
    lastWrong: Item?,
    lastProbabilityData: TestEngine.DebugData?,
    kanaWords: Boolean,
    onItemClick: (Item) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colors.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(8.dp))

        if (lastWrong != null) {
            ItemButton(
                item = lastWrong,
                probabilityData = null,
                style = HistoryItemStyle.BAD,
                showInfo = lastWrong.contents is Kanji || lastWrong.contents is Word,
                kanaWords = kanaWords,
                onClick = { onItemClick(lastWrong) }
            )
        }

        if (lastCorrect != null && lastCorrect != lastWrong) {
            ItemButton(
                item = lastCorrect,
                probabilityData = lastProbabilityData,
                style = HistoryItemStyle.GOOD,
                showInfo = lastCorrect.contents is Kanji || lastCorrect.contents is Word,
                kanaWords = kanaWords,
                onClick = { onItemClick(lastCorrect) }
            )
        }

        lastCorrect?.let { item ->
            Text(
                text = item.description,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
private fun HistoryBottomSheet(
    items: List<HistoryItem>,
    kanaWords: Boolean,
    onItemClick: (Item) -> Unit,
) {
    val themeAttrs = LocalThemeAttributes.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(themeAttrs.historyBackground)
    ) {
        items(items) { historyItem ->
            if (historyItem.prependSeparator)
                Separator()
            HistoryItemRow(
                historyItem = historyItem,
                kanaWords = kanaWords,
                onItemClick = onItemClick
            )
        }
    }
}

@Composable
private fun HistoryItemRow(
    historyItem: HistoryItem,
    kanaWords: Boolean,
    onItemClick: (Item) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemButton(
            item = historyItem.item,
            probabilityData = historyItem.probabilityData,
            style = historyItem.style,
            showInfo = historyItem.item.contents is Kanji || historyItem.item.contents is Word,
            kanaWords = kanaWords,
            onClick = { onItemClick(historyItem.item) }
        )

        Text(
            text = historyItem.item.description,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ItemButton(
    item: Item,
    probabilityData: TestEngine.DebugData?,
    style: HistoryItemStyle,
    showInfo: Boolean,
    kanaWords: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val themeAttrs = LocalThemeAttributes.current
    val backgroundColor = when (style) {
        HistoryItemStyle.GOOD -> themeAttrs.itemGood
        HistoryItemStyle.BAD -> themeAttrs.itemBad
        HistoryItemStyle.DONT_KNOW -> themeAttrs.backgroundDontKnow
    }
    val context = LocalContext.current

    Box(
        modifier = modifier
    ) {
        if (showInfo) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_dialog_info),
                contentDescription = "Info",
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd),
                tint = MaterialTheme.colors.primary
            )
        }

        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            BetterButton(
                onClick = { if (showInfo) onClick() },
                onLongPress = {
                    probabilityData?.let {
                        showItemProbabilityData(context, item.text(kanaWords), it)
                    }
                },
                modifier = Modifier
                    .then(
                        if (item.text(kanaWords).length > 1)
                            Modifier.height(35.dp)
                        else
                            Modifier.size(35.dp),
                    ),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = backgroundColor
                ),
                contentPadding = PaddingValues(0.dp),
                shape = CircleShape,
            ) {
                Text(
                    text = item.text(kanaWords),
                    fontSize = 25.sp,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "TestScreen Preview - Collapsed")
@Composable
fun TestScreenPreviewCollapsed() {
    val sampleHistoryState = HistoryState(
        items = listOf(),
        lastCorrect = null,
        lastWrong = null,
        lastProbabilityData = null,
    )

    KakugoTheme {
        TestScreen(
            testFragment = null,
            onFragmentUpdated = {},
            correctCount = 10,
            questionCount = 15,
            historyState = sampleHistoryState,
            sheetExpanded = false,
            onSheetExpandedChange = {},
            kanaWords = true,
            onItemClick = {},
            stats = LearningDbView.Stats(
                good = 5,
                meh = 3,
                bad = 2,
                disabled = 0
            ),
        )
    }
}

@Preview(showBackground = true, name = "TestScreen Preview - Wrong")
@Composable
fun TestScreenPreviewWrong() {
    val good = Item(
        0,
        Kanji(
            "好",
            listOf("コウ"),
            listOf("この.む", "す.く", "よ.い", "い.い"),
            listOf("fond", "pleasing", "like something"),
            listOf(),
            listOf(),
            1
        ),
        0.0,
        0.0,
        0,
        true
    )
    val bad = Item(
        0,
        Kanji(
            "人",
            listOf("ジン", "ニン"),
            listOf("ひと", "-り", "-と"),
            listOf("person"),
            listOf(),
            listOf(),
            1
        ),
        0.0,
        0.0,
        0,
        true
    )
    val sampleHistoryState = HistoryState(
        items = listOf(
            HistoryItem(
                item = good,
                probabilityData = null,
                style = HistoryItemStyle.GOOD
            ),
            HistoryItem(
                item = bad,
                probabilityData = null,
                style = HistoryItemStyle.BAD
            ),
        ),
        lastCorrect = good,
        lastWrong = bad,
        lastProbabilityData = null,
    )

    KakugoTheme {
        TestScreen(
            testFragment = null,
            onFragmentUpdated = {},
            correctCount = 10,
            questionCount = 15,
            historyState = sampleHistoryState,
            sheetExpanded = false,
            onSheetExpandedChange = {},
            kanaWords = true,
            onItemClick = {},
            stats = LearningDbView.Stats(
                good = 5,
                meh = 3,
                bad = 2,
                disabled = 0
            ),
        )
    }
}

@Preview(showBackground = true, name = "TestScreen Preview - Wrong History")
@Composable
fun TestScreenPreviewWrongHistory() {
    val good = Item(
        0,
        Kanji(
            "好き",
            listOf("コウ"),
            listOf("この.む", "す.く", "よ.い", "い.い"),
            listOf("fond", "pleasing", "like something"),
            listOf(),
            listOf(),
            1
        ),
        0.0,
        0.0,
        0,
        true
    )
    val bad = Item(
        0,
        Kanji(
            "人",
            listOf("ジン", "ニン"),
            listOf("ひと", "-り", "-と"),
            listOf("person"),
            listOf(),
            listOf(),
            1
        ),
        0.0,
        0.0,
        0,
        true
    )
    val sampleHistoryState = HistoryState(
        items = listOf(
            HistoryItem(
                item = good,
                probabilityData = null,
                style = HistoryItemStyle.GOOD
            ),
            HistoryItem(
                item = bad,
                probabilityData = null,
                style = HistoryItemStyle.BAD
            ),
        ),
        lastCorrect = good,
        lastWrong = bad,
        lastProbabilityData = null,
    )

    KakugoTheme {
        TestScreen(
            testFragment = null,
            onFragmentUpdated = {},
            correctCount = 10,
            questionCount = 15,
            historyState = sampleHistoryState,
            sheetExpanded = true,
            onSheetExpandedChange = {},
            kanaWords = true,
            onItemClick = {},
            stats = LearningDbView.Stats(
                good = 5,
                meh = 3,
                bad = 2,
                disabled = 0
            ),
        )
    }
}
