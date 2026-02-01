package org.kaqui.testactivities

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FabPosition
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.app.NavUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.kaqui.AppScaffold
import org.kaqui.BetterButton
import org.kaqui.R
import org.kaqui.Separator
import org.kaqui.StatsBar
import org.kaqui.TestEngine
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
import org.kaqui.showKanjiInDict
import org.kaqui.startActivity
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
    val forceFragmentRefresh: Int = 0,
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
    ) {
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

    fun addWrongAnswerToHistory(
        correct: Item,
        probabilityData: TestEngine.DebugData?,
        wrong: Item,
    ) {
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

    fun addUnknownAnswerToHistory(
        correct: Item,
        probabilityData: TestEngine.DebugData?,
    ) {
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

    fun resetHistory() {
        _uiState.update { currentState ->
            currentState.copy(
                historyState = HistoryState()
            )
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
                fragment = testFragment,
                forceFragmentRefresh = _uiState.value.forceFragmentRefresh + 1
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

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                Color.TRANSPARENT
            )
        )

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        kanaWords = sharedPrefs.getBoolean("kana_words", true)

        if (sharedPrefs.getBoolean("keep_on", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        testEngine = TestEngine(
            this,
            Database.getInstance(this),
            testTypes,
            viewModel::addGoodAnswerToHistory,
            viewModel::addWrongAnswerToHistory,
            viewModel::addUnknownAnswerToHistory
        )

        viewModel.resetHistory()

        if (savedInstanceState == null) {
            nextQuestion()
        } else {
            testEngine.loadState(savedInstanceState)
            localTestType = testEngine.testType
            refreshFragment()
        }

        viewModel.setStats(testEngine.itemView.getStats())

        viewModel.initialize(testEngine)

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            TestScreen(
                title = uiState.title,
                testFragment = uiState.fragment,
                forceFragmentRefresh = uiState.forceFragmentRefresh,
                onFragmentUpdated = { fragment ->
                    testFragment = fragment as TestFragment
                },
                stats = uiState.stats,
                correctCount = uiState.correctCount,
                questionCount = uiState.questionCount,
                historyState = uiState.historyState,
                sheetExpanded = uiState.sheetExpanded,
                onSheetExpandedChange = { viewModel.setSheetExpanded(it) },
                kanaWords = kanaWords,
                onItemClick = this::openItemInDictionary,
                onBackClick = { confirmActivityClose() },
            )
        }
    }

    private fun openItemInDictionary(item: Item) {
        when (item.contents) {
            is Kanji -> startActivity<org.kaqui.itemdetails.KanjiDisplayActivity>("kanji_id" to item.id)
            is Word -> startActivity<org.kaqui.itemdetails.WordDisplayActivity>("word_id" to item.id)
            else -> { /* do nothing */
            }
        }
    }

    override fun onAnswer(button: View?, certainty: Certainty, wrong: Item?) {
        viewModel.onAnswer(certainty, wrong)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        testEngine.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun confirmActivityClose() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_test_stop_title)
            .setMessage(R.string.confirm_test_stop_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
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
            refreshFragment()
            viewModel.setStats(testEngine.itemView.getStats())
        }
    }

    private fun refreshFragment() {
        val testFragmentClass: Class<out Fragment> =
            when (testType) {
                TestType.WORD_TO_READING, TestType.WORD_TO_MEANING, TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING, TestType.READING_TO_WORD, TestType.MEANING_TO_WORD, TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.HIRAGANA_TO_ROMAJI, TestType.ROMAJI_TO_HIRAGANA, TestType.KATAKANA_TO_ROMAJI, TestType.ROMAJI_TO_KATAKANA -> QuizTestFragmentCompose::class.java
                TestType.HIRAGANA_DRAWING, TestType.KATAKANA_DRAWING, TestType.KANJI_DRAWING -> DrawingTestFragmentCompose::class.java
                TestType.KANJI_COMPOSITION -> CompositionTestFragmentCompose::class.java
                TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.KATAKANA_TO_ROMAJI_TEXT -> TextTestFragmentCompose::class.java
            }

        viewModel.setTitle(getString(testType.toName()))
        viewModel.setFragmentClass(testFragmentClass)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setStats(testEngine.itemView.getStats())
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TestScreen(
    title: String,
    testFragment: Class<out Fragment>?,
    forceFragmentRefresh: Int,
    onFragmentUpdated: (Fragment) -> Unit,
    stats: LearningDbView.Stats,
    correctCount: Int,
    questionCount: Int,
    historyState: HistoryState,
    sheetExpanded: Boolean,
    onSheetExpandedChange: (Boolean) -> Unit,
    kanaWords: Boolean,
    onItemClick: (Item) -> Unit,
    onBackClick: () -> Unit,
) {
    val themeAttrs = LocalThemeAttributes.current
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberBottomSheetState(initialValue = BottomSheetValue.Collapsed)
    )

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

    // Handle back gesture for sheet collapse
    BackHandler(enabled = sheetExpanded) {
        onSheetExpandedChange(false)
    }

    // Handle back gesture for activity close
    BackHandler(enabled = !sheetExpanded) {
        onBackClick()
    }

    AppScaffold(
        title = title,
        onBackClick = onBackClick
    ) { paddingValues ->
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
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
                        val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
                        val layoutDirection = LocalLayoutDirection.current

                        FloatingActionButton(
                            onClick = { onSheetExpandedChange(true) },
                            modifier =
                                Modifier
                                    .offset(
                                        x = 10.dp - safeDrawing.calculateEndPadding(layoutDirection),
                                        y = 10.dp - safeDrawing.calculateBottomPadding()
                                    )
                                    .size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_upward),
                                contentDescription = stringResource(R.string.show_history),
                                tint = MaterialTheme.colors.onPrimary
                            )
                        }
                    }
                },
                floatingActionButtonPosition = FabPosition.End,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    StatsBar(
                        itemsDontKnow = 0,
                        itemsBad = stats.bad,
                        itemsMeh = stats.meh,
                        itemsGood = stats.good
                    )

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

                    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
                    val layoutDirection = LocalLayoutDirection.current

                    Column(
                        modifier = Modifier
                            .padding(
                                start = safeDrawing.calculateStartPadding(layoutDirection),
                                end = safeDrawing.calculateEndPadding(layoutDirection),
                            )
                            .weight(1f),
                    )
                    {
                        if (testFragment != null) {
                            key(forceFragmentRefresh) {
                                AndroidFragment(
                                    testFragment,
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    onUpdate = onFragmentUpdated,
                                )
                            }
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                        }
                    }

                    LastItemRow(
                        lastCorrect = historyState.lastCorrect,
                        lastWrong = historyState.lastWrong,
                        lastProbabilityData = historyState.lastProbabilityData,
                        kanaWords = kanaWords,
                        onItemClick = onItemClick,
                    )
                }
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
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp + safeDrawing.calculateBottomPadding())
            .background(MaterialTheme.colors.surface)
            .padding(bottom = safeDrawing.calculateBottomPadding()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = lastCorrect to lastWrong,
            transitionSpec = {
                slideInVertically(
                    animationSpec = tween(100),
                    initialOffsetY = { -it }
                ) togetherWith ExitTransition.None
            },
            label = "LastItemAnimation"
        ) { (animatedCorrect, animatedWrong) ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(8.dp))

                if (animatedWrong != null) {
                    ItemButton(
                        item = animatedWrong,
                        probabilityData =
                            if (animatedCorrect == animatedWrong)
                                lastProbabilityData
                            else
                                null,
                        style = HistoryItemStyle.BAD,
                        showInfo = animatedWrong.contents is Kanji || animatedWrong.contents is Word,
                        kanaWords = kanaWords,
                        onClick = { onItemClick(animatedWrong) }
                    )
                }

                if (animatedCorrect != null && animatedCorrect != animatedWrong) {
                    ItemButton(
                        item = animatedCorrect,
                        probabilityData = lastProbabilityData,
                        style = HistoryItemStyle.GOOD,
                        showInfo = animatedCorrect.contents is Kanji || animatedCorrect.contents is Word,
                        kanaWords = kanaWords,
                        onClick = { onItemClick(animatedCorrect) }
                    )
                }

                animatedCorrect?.let { item ->
                    Text(
                        text = item.description,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f),
                        style = MaterialTheme.typography.body2,
                        lineHeight = 1.1.em,
                    )
                }
            }
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
            modifier = Modifier.weight(1f),
            lineHeight = 1.1.em,
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
        HistoryItemStyle.DONT_KNOW -> themeAttrs.itemBad2
    }
    val context = LocalContext.current

    Box(
        modifier = modifier
    ) {
        if (showInfo) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_dialog_info),
                contentDescription = stringResource(R.string.info),
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
            title = "Test",
            testFragment = null,
            forceFragmentRefresh = 0,
            onFragmentUpdated = {},
            correctCount = 10,
            questionCount = 15,
            historyState = sampleHistoryState,
            sheetExpanded = false,
            onSheetExpandedChange = {},
            kanaWords = true,
            onItemClick = {},
            onBackClick = {},
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
            title = "Test",
            testFragment = null,
            forceFragmentRefresh = 0,
            onFragmentUpdated = {},
            correctCount = 10,
            questionCount = 15,
            historyState = sampleHistoryState,
            sheetExpanded = false,
            onSheetExpandedChange = {},
            kanaWords = true,
            onItemClick = {},
            onBackClick = {},
            stats = LearningDbView.Stats(
                good = 5,
                meh = 3,
                bad = 2,
                disabled = 0
            ),
        )
    }
}

@Preview(showBackground = true, name = "TestScreen Preview - Long text")
@Composable
fun TestScreenPreviewLongText() {
    val good = Item(
        0,
        Word(
            "好き",
            "すき",
            listOf(
                "fond",
                "pleasing",
                "like something",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah",
                "blah"
            ),
            listOf(),
            false,
            "",
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
        ),
        lastCorrect = good,
        lastWrong = null,
        lastProbabilityData = null,
    )

    KakugoTheme {
        TestScreen(
            title = "Test",
            testFragment = null,
            forceFragmentRefresh = 0,
            onFragmentUpdated = {},
            correctCount = 10,
            questionCount = 15,
            historyState = sampleHistoryState,
            sheetExpanded = false,
            onSheetExpandedChange = {},
            kanaWords = true,
            onItemClick = {},
            onBackClick = {},
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
        Word(
            "好き",
            "すき",
            listOf("fond", "pleasing", "like something"),
            listOf(),
            false,
            ""
        ),
        0.0,
        0.0,
        0,
        true
    )
    val bad = Item(
        0,
        Word(
            "人",
            "ひと",
            listOf("person"),
            listOf(),
            false,
            ""
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
                style = HistoryItemStyle.GOOD,
            ),
            HistoryItem(
                item = bad,
                probabilityData = null,
                style = HistoryItemStyle.BAD,
                prependSeparator = true,
            ),
            HistoryItem(
                item = good,
                probabilityData = null,
                style = HistoryItemStyle.DONT_KNOW,
                prependSeparator = false,
            ),
        ),
        lastCorrect = good,
        lastWrong = bad,
        lastProbabilityData = null,
    )

    KakugoTheme {
        TestScreen(
            title = "Test",
            testFragment = null,
            forceFragmentRefresh = 0,
            onFragmentUpdated = {},
            correctCount = 10,
            questionCount = 15,
            historyState = sampleHistoryState,
            sheetExpanded = true,
            onSheetExpandedChange = {},
            kanaWords = true,
            onItemClick = {},
            onBackClick = {},
            stats = LearningDbView.Stats(
                good = 5,
                meh = 3,
                bad = 2,
                disabled = 0
            ),
        )
    }
}
