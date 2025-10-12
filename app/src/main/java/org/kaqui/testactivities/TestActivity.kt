package org.kaqui.testactivities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.AttrRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NavUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kaqui.*
import org.kaqui.R
import org.kaqui.model.*
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes
import kotlinx.coroutines.flow.update
import androidx.core.net.toUri
import androidx.fragment.compose.AndroidFragment

data class HistoryItem(
    val item: Item,
    val probabilityData: TestEngine.DebugData?,
    @AttrRes val style: Int,
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
    val sheetState: Int = BottomSheetBehavior.STATE_COLLAPSED,
    val stats: LearningDbView.Stats = LearningDbView.Stats(
        good = 0,
        meh = 0,
        bad = 0,
        disabled = 0
    )
)
//val kanaWords: Boolean = true)

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
                    listOf(HistoryItem(correct, probabilityData, R.attr.itemGood, true)) +
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
                    HistoryItem(correct, probabilityData, R.attr.itemBad, true),
                    HistoryItem(wrong, null, R.attr.backgroundDontKnow),
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
                    listOf(HistoryItem(correct, probabilityData, R.attr.itemBad, true)) +
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

    fun setSheetState(sheetState: Int) {
        _uiState.update { currentState ->
            currentState.copy(
                sheetState = sheetState
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
}

class TestActivity : BaseActivity(), TestFragmentHolder {
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

        kanaWords =
            PreferenceManager.getDefaultSharedPreferences(this).getBoolean("kana_words", true)

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

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            TestScreen(
                testFragment = uiState.fragment,
                onFragmentUpdated = { fragment -> testFragment = fragment as TestFragment },
                stats = uiState.stats,
                correctCount = uiState.correctCount,
                questionCount = uiState.questionCount,
                historyState = uiState.historyState,
                sheetState = uiState.sheetState,
                onSheetStateChange = { viewModel.setSheetState(it) },
                kanaWords = kanaWords,
                onItemClick = this::openItemInDictionary
            )
        }

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
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

    override fun onStart() {
        super.onStart()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        testEngine.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val sheetState = viewModel.uiState.value.sheetState

        if (sheetState == BottomSheetBehavior.STATE_EXPANDED) {
            viewModel.setSheetState(BottomSheetBehavior.STATE_COLLAPSED)
        } else {
            confirmActivityClose(false)
        }
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                confirmActivityClose(true)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
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

            title = getString(testType.toName())
        }
    }
}

@Composable
fun TestScreen(
    testFragment: Class<out Fragment>?,
    onFragmentUpdated: (Fragment) -> Unit,
    stats: LearningDbView.Stats,
    correctCount: Int,
    questionCount: Int,
    historyState: HistoryState,
    sheetState: Int,
    onSheetStateChange: (Int) -> Unit,
    kanaWords: Boolean,
    onItemClick: (Item) -> Unit,
) {
    KakugoTheme {
        Surface(color = MaterialTheme.colors.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    StatsBar(itemsDontKnow = 0, itemsBad = stats.bad, itemsMeh = stats.meh, itemsGood = stats.good)

                    Text(
                        text = LocalContext.current.getString(
                            R.string.score_string,
                            correctCount,
                            questionCount
                        ),
                        modifier = Modifier
                            .fillMaxWidth(),
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

                if (sheetState == BottomSheetBehavior.STATE_COLLAPSED) {
                    FloatingActionButton(
                        onClick = { onSheetStateChange(BottomSheetBehavior.STATE_EXPANDED) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(40.dp),
                        backgroundColor = MaterialTheme.colors.primary
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_upward),
                            contentDescription = "Show history",
                            tint = MaterialTheme.colors.onPrimary
                        )
                    }
                }

                HistoryBottomSheet(
                    items = historyState.items,
                    sheetState = sheetState,
                    onStateChange = onSheetStateChange,
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
                style = R.attr.itemBad,
                showInfo = lastWrong.contents is Kanji || lastWrong.contents is Word,
                kanaWords = kanaWords,
                onClick = { onItemClick(lastWrong) }
            )
        }

        if (lastCorrect != null && lastCorrect != lastWrong) {
            ItemButton(
                item = lastCorrect,
                probabilityData = lastProbabilityData,
                style = R.attr.itemGood,
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
    sheetState: Int,
    onStateChange: (Int) -> Unit,
    kanaWords: Boolean,
    onItemClick: (Item) -> Unit,
) {
    val themeAttrs = LocalThemeAttributes.current

    ModalBottomSheetLayout(
        sheetState = rememberModalBottomSheetState(
            initialValue = if (sheetState == BottomSheetBehavior.STATE_EXPANDED)
                ModalBottomSheetValue.Expanded else ModalBottomSheetValue.Hidden,
            confirmValueChange = {
                onStateChange(
                    if (it == ModalBottomSheetValue.Expanded)
                        BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
                )
                true
            }
        ),
        sheetContent = {
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
        },
        sheetBackgroundColor = themeAttrs.historyBackground
    ) { }
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
    @AttrRes style: Int,
    showInfo: Boolean,
    kanaWords: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val themeAttrs = LocalThemeAttributes.current
    val backgroundColor = when (style) {
        R.attr.itemGood -> themeAttrs.itemGood
        R.attr.itemBad -> themeAttrs.itemBad
        else -> MaterialTheme.colors.surface
    }

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
                tint = MaterialTheme.colors.secondary
            )
        }

        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Button(
                onClick = { if (showInfo) onClick() },
                shape = CircleShape,
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
            sheetState = BottomSheetBehavior.STATE_HIDDEN,
            onSheetStateChange = {},
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
                style = R.attr.itemGood
            ),
            HistoryItem(
                item = bad,
                probabilityData = null,
                style = R.attr.itemBad
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
            sheetState = BottomSheetBehavior.STATE_COLLAPSED,
            onSheetStateChange = {},
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
                style = R.attr.itemGood
            ),
            HistoryItem(
                item = bad,
                probabilityData = null,
                style = R.attr.itemBad
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
            sheetState = BottomSheetBehavior.STATE_EXPANDED,
            onSheetStateChange = {},
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
