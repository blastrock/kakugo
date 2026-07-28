package org.kaqui.stats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LegendItem
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.rememberVerticalLegend
import com.patrykandpatrick.vico.compose.common.vicoTheme
import com.patrykandpatrick.vico.compose.m2.common.rememberM2VicoTheme
import kotlinx.coroutines.runBlocking
import org.kaqui.AppScaffold
import org.kaqui.R
import org.kaqui.model.Database
import org.kaqui.model.ItemType
import org.kaqui.roundToPreviousDay
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes
import java.text.DateFormat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private val ChartHeight = 280.dp
private const val ColumnThickness = 4
private const val ColumnSpacing = 2
private const val YLabelCount = 5
private const val YStepRounding = 10
private const val AreaFillAlpha = 0.25f

// Beyond this many days, date labels are too dense to be readable one per day
private const val DailyLabelDayLimit = 14
private const val SparseLabelSpacing = 7

// Charts that start fully zoomed out show their whole history at once, so their labels have to be
// spread over it instead of using a fixed spacing
private const val MaxDateLabels = 4

private fun nextDayTimestamp(): Long {
    val calendar = Calendar.getInstance()
    calendar.roundToPreviousDay()
    calendar.roll(Calendar.DAY_OF_MONTH, true)
    return calendar.timeInMillis / 1000
}

// Snapshot timestamps are UTC midnights while the charts bucket days in local time, so the day has
// to be carried over as a date rather than as an offset in seconds
private fun snapshotDayOffset(snapshotTime: Long, nextDay: Long): Int {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = snapshotTime * 1000

    val local = Calendar.getInstance()
    local.roundToPreviousDay()
    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))

    return ((local.timeInMillis / 1000 - nextDay) / 24 / 3600).toInt()
}

// Fills the days between two snapshots by linear interpolation, then holds the last value up to lastDay
private fun densify(points: List<DayCount>, lastDay: Int): Map<Int, Int> {
    val counts = mutableMapOf<Int, Int>()
    for ((previous, next) in points.zipWithNext())
        for (day in previous.dayOffset until next.dayOffset) {
            val ratio = (day - previous.dayOffset).toDouble() / (next.dayOffset - previous.dayOffset)
            counts[day] = (previous.count + (next.count - previous.count) * ratio).roundToInt()
        }
    for (day in points.last().dayOffset..lastDay)
        counts[day] = points.last().count
    return counts
}

private fun learnedItemsData(snapshots: List<Database.LongScoreSnapshot>, nextDay: Long, todayOffset: Int): CountData {
    if (snapshots.isEmpty())
        return CountData(emptyList(), nextDay)

    // A snapshot is only taken for the knowledge types of the session that is starting, so each
    // knowledge type has to be completed on its own, otherwise the max of a day on which only one
    // of them was tested would drop down to that one
    val perKnowledgeType = snapshots.groupBy { it.knowledgeType }.map { (_, typeSnapshots) ->
        typeSnapshots
            .map { DayCount(snapshotDayOffset(it.timestamp, nextDay), it.itemCount) }
            .associateBy { it.dayOffset }
            .values.sortedBy { it.dayOffset }
    }

    val firstDay = perKnowledgeType.minOf { it.first().dayOffset }
    val lastDay = maxOf(todayOffset, perKnowledgeType.maxOf { it.last().dayOffset })

    val completed = perKnowledgeType.map { densify(it, lastDay) }
    val days = (firstDay..lastDay).map { day ->
        DayCount(day, completed.mapNotNull { it[day] }.max())
    }
    return CountData(days, nextDay)
}

class StatsActivity : ComponentActivity() {
    companion object {
        const val TAG = "StatsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            StatsScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@Composable
fun StatsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val stats = remember {
        val database = Database.getInstance(context)
        val rawDayStats = database.getAskedItem()

        val dayStats = rawDayStats.groupBy {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.timestamp * 1000
            calendar.roundToPreviousDay()
            calendar.timeInMillis / 1000
        }.map {
            Database.DayStatistics(
                it.key,
                it.value.sumOf { it.askedCount },
                it.value.sumOf { it.correctCount },
                it.value.sumOf { it.uniqueAskedCount },
                it.value.sumOf { it.uniqueCorrectCount }
            )
        }

        val nextDay = nextDayTimestamp()

        val today = run {
            val cal = Calendar.getInstance()
            cal.roundToPreviousDay()
            cal.timeInMillis / 1000
        }
        val padToday = rawDayStats.isNotEmpty() && rawDayStats.last().timestamp != today

        fun statsData(asked: (Database.DayStatistics) -> Int, correct: (Database.DayStatistics) -> Int): StatsData {
            val days = dayStats.map { stat ->
                DayStat(
                    ((stat.timestamp - nextDay) / 24 / 3600).toInt(),
                    correct(stat),
                    asked(stat) - correct(stat)
                )
            }.toMutableList()

            if (padToday)
                days.add(DayStat(((today - nextDay) / 24 / 3600).toInt(), 0, 0))

            return StatsData(days.sortedBy { it.dayOffset }, nextDay)
        }

        val todayOffset = ((today - nextDay) / 24 / 3600).toInt()

        Stats(
            answers = statsData({ it.askedCount }, { it.correctCount }),
            uniqueItems = statsData({ it.uniqueAskedCount }, { it.uniqueCorrectCount }),
            learnedKanji = learnedItemsData(database.getLongScoreSnapshots(ItemType.Kanji), nextDay, todayOffset),
            learnedWords = learnedItemsData(database.getLongScoreSnapshots(ItemType.Word), nextDay, todayOffset)
        )
    }

    val models = remember {
        StatsModels(
            CartesianChartModelProducer(),
            CartesianChartModelProducer(),
            CartesianChartModelProducer(),
            CartesianChartModelProducer()
        )
    }

    LaunchedEffect(stats.answers) {
        models.answers.runTransaction { statsColumns(stats.answers) }
    }
    LaunchedEffect(stats.uniqueItems) {
        models.uniqueItems.runTransaction { statsColumns(stats.uniqueItems) }
    }
    LaunchedEffect(stats.learnedKanji) {
        models.learnedKanji.runTransaction { learnedItemsLine(stats.learnedKanji) }
    }
    LaunchedEffect(stats.learnedWords) {
        models.learnedWords.runTransaction { learnedItemsLine(stats.learnedWords) }
    }

    StatsScreen(stats, models, onBackClick)
}

@Composable
private fun StatsScreen(
    stats: Stats,
    models: StatsModels,
    onBackClick: () -> Unit
) {
    AppScaffold(
        title = stringResource(R.string.title_stats),
        onBackClick = onBackClick
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AnswersSection(
                title = stringResource(R.string.stats_items_answered),
                statsData = stats.answers,
                modelProducer = models.answers,
                correctLabel = stringResource(R.string.stats_correct_answers),
                wrongLabel = stringResource(R.string.stats_wrong_answers)
            )

            AnswersSection(
                title = stringResource(R.string.stats_unique_items_answered),
                statsData = stats.uniqueItems,
                modelProducer = models.uniqueItems,
                correctLabel = stringResource(R.string.stats_correct_items),
                wrongLabel = stringResource(R.string.stats_wrong_items)
            )

            LearnedItemsSection(
                title = stringResource(R.string.stats_learned_kanji),
                countData = stats.learnedKanji,
                modelProducer = models.learnedKanji
            )

            LearnedItemsSection(
                title = stringResource(R.string.stats_learned_words),
                countData = stats.learnedWords,
                modelProducer = models.learnedWords
            )
        }
    }
}

@Composable
private fun AnswersSection(
    title: String,
    statsData: StatsData,
    modelProducer: CartesianChartModelProducer,
    correctLabel: String,
    wrongLabel: String
) {
    StatsSection(title, statsData.days.isNotEmpty()) {
        val themeAttributes = LocalThemeAttributes.current
        StatsChart(
            modelProducer = modelProducer,
            data = statsData,
            correctColor = themeAttributes.statsItemsGood,
            wrongColor = themeAttributes.statsItemsBad,
            correctLabel = correctLabel,
            wrongLabel = wrongLabel,
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
        )
    }
}

@Composable
private fun LearnedItemsSection(
    title: String,
    countData: CountData,
    modelProducer: CartesianChartModelProducer
) {
    StatsSection(title, countData.days.isNotEmpty()) {
        LearnedItemsChart(
            modelProducer = modelProducer,
            data = countData,
            color = LocalThemeAttributes.current.statsLearnedItems,
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
        )
    }
}

@Composable
private fun StatsSection(
    title: String,
    hasData: Boolean,
    chart: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (hasData) {
            chart()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.stats_no_data),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body1
                )
            }
        }
    }
}

private fun CartesianChartModelProducer.Transaction.statsColumns(data: StatsData) {
    columnModel {
        series(data.days.map { it.dayOffset }, data.days.map { it.correct })
        series(data.days.map { it.dayOffset }, data.days.map { it.wrong })
    }
}

private fun CartesianChartModelProducer.Transaction.learnedItemsLine(data: CountData) {
    lineModel {
        series(data.days.map { it.dayOffset }, data.days.map { it.count })
    }
}

@Composable
private fun rememberDayFormatter(nextDay: Long) = remember(nextDay) {
    val calendar = Calendar.getInstance()
    val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
    CartesianValueFormatter { _, value, _ ->
        calendar.timeInMillis = (value.toLong() * 24 * 3600 + nextDay) * 1000
        dateFormat.format(calendar.time)
    }
}

private fun labelSpacing(dayCount: Int) =
    if (dayCount > DailyLabelDayLimit) SparseLabelSpacing else 1

// Kept at 2 at the least so that the labels can be offset by half a spacing, see LearnedItemsChart
private fun fittedLabelSpacing(dayCount: Int) =
    max(2, ceil(dayCount.toDouble() / MaxDateLabels).toInt())

private fun yStep(maxValue: Int) = max(1.0, ceil(maxValue.toDouble() / YLabelCount))

// Item counts are large enough that a step of less than ten, or one that is not round, only makes
// the labels harder to read
private fun roundedYStep(maxValue: Int) =
    ceil(yStep(maxValue) / YStepRounding) * YStepRounding

@Composable
private fun StatsChart(
    modelProducer: CartesianChartModelProducer,
    data: StatsData,
    correctColor: Color,
    wrongColor: Color,
    correctLabel: String,
    wrongLabel: String,
    modifier: Modifier = Modifier
) {
    val dateFormatter = rememberDayFormatter(data.nextDay)
    val labelSpacing = labelSpacing(data.days.size)
    val yStep = remember(data) { yStep(data.days.maxOf { it.correct + it.wrong }) }

    ProvideVicoTheme(rememberM2VicoTheme()) {
        val legendLabel = rememberTextComponent(TextStyle(vicoTheme.textColor))
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(Fill(correctColor), ColumnThickness.dp),
                        rememberLineComponent(Fill(wrongColor), ColumnThickness.dp)
                    ),
                    columnCollectionSpacing = ColumnSpacing.dp,
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked }
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = remember { CartesianValueFormatter.decimal(decimalCount = 0) },
                    itemPlacer = remember(yStep) { VerticalAxis.ItemPlacer.step({ yStep }) }
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = dateFormatter,
                    itemPlacer = remember(labelSpacing) {
                        // Extreme label padding would inset the line by half a date label to keep
                        // the first and last labels from being clipped, detaching it from the axis.
                        // Without it the labels have to be kept away from the edges by hand.
                        HorizontalAxis.ItemPlacer.aligned(
                            spacing = { labelSpacing },
                            offset = { labelSpacing / 2 },
                            addExtremeLabelPadding = false
                        )
                    }
                ),
                legend = rememberVerticalLegend(
                    items = {
                        add(
                            LegendItem(
                                ShapeComponent(Fill(correctColor), CircleShape),
                                legendLabel,
                                correctLabel
                            )
                        )
                        add(
                            LegendItem(
                                ShapeComponent(Fill(wrongColor), CircleShape),
                                legendLabel,
                                wrongLabel
                            )
                        )
                    },
                    padding = Insets(top = 8.dp),
                    rowSpacing = 4.dp,
                )
            ),
            modelProducer = modelProducer,
            modifier = modifier,
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
            zoomState = rememberVicoZoomState(
                zoomEnabled = false,
                initialZoom = Zoom.max(Zoom.Content, Zoom.fixed())
            )
        )
    }
}

@Composable
private fun LearnedItemsChart(
    modelProducer: CartesianChartModelProducer,
    data: CountData,
    color: Color,
    modifier: Modifier = Modifier
) {
    val dateFormatter = rememberDayFormatter(data.nextDay)
    val labelSpacing = fittedLabelSpacing(data.days.size)
    val yStep = remember(data) { roundedYStep(data.days.maxOf { it.count }) }

    ProvideVicoTheme(rememberM2VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(color)),
                            areaFill = remember(color) {
                                LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = AreaFillAlpha)))
                            }
                        )
                    ),
                    rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0) }
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = remember { CartesianValueFormatter.decimal(decimalCount = 0) },
                    itemPlacer = remember(yStep) { VerticalAxis.ItemPlacer.step({ yStep }) }
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = dateFormatter,
                    itemPlacer = remember(labelSpacing) {
                        // Extreme label padding would inset the line by half a date label to keep
                        // the first and last labels from being clipped, detaching it from the axis.
                        // Without it the labels have to be kept away from the edges by hand.
                        HorizontalAxis.ItemPlacer.aligned(
                            spacing = { labelSpacing },
                            offset = { labelSpacing / 2 },
                            addExtremeLabelPadding = false
                        )
                    }
                )
            ),
            modelProducer = modelProducer,
            modifier = modifier,
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
            // Zoom.Content shows the whole history at once, and is also the default minimum zoom,
            // so the chart starts fully zoomed out and can only be zoomed in from there
            zoomState = rememberVicoZoomState(initialZoom = Zoom.Content)
        )
    }
}

data class DayStat(
    val dayOffset: Int,
    val correct: Int,
    val wrong: Int
)

data class StatsData(
    val days: List<DayStat>,
    val nextDay: Long
)

data class DayCount(
    val dayOffset: Int,
    val count: Int
)

data class CountData(
    val days: List<DayCount>,
    val nextDay: Long
)

data class Stats(
    val answers: StatsData,
    val uniqueItems: StatsData,
    val learnedKanji: CountData,
    val learnedWords: CountData
)

private data class StatsModels(
    val answers: CartesianChartModelProducer,
    val uniqueItems: CartesianChartModelProducer,
    val learnedKanji: CartesianChartModelProducer,
    val learnedWords: CartesianChartModelProducer
)

private fun previewStatsData(dayCount: Int) = StatsData(
    days = (0 until dayCount).map { day ->
        DayStat(day - dayCount, 12 + day * 7 % 28, day * 5 % 11)
    },
    nextDay = nextDayTimestamp()
)

private fun previewUniqueStatsData(dayCount: Int) = StatsData(
    days = (0 until dayCount).map { day ->
        DayStat(day - dayCount, 7 + day * 3 % 15, day * 2 % 7)
    },
    nextDay = nextDayTimestamp()
)

private fun previewCountData(dayCount: Int, dailyGrowth: Int) = CountData(
    days = (0 until dayCount).map { day ->
        DayCount(day - dayCount, day * dailyGrowth + day % 5)
    },
    nextDay = nextDayTimestamp()
)

// Previews don't run effects, so the model has to be built before rendering
@Composable
private fun previewModelProducer(data: StatsData) =
    remember { CartesianChartModelProducer() }.also {
        runBlocking { it.runTransaction { statsColumns(data) } }
    }

@Composable
private fun previewModelProducer(data: CountData) =
    remember { CartesianChartModelProducer() }.also {
        runBlocking { it.runTransaction { learnedItemsLine(data) } }
    }

@Composable
private fun previewStatsModels(stats: Stats) = StatsModels(
    previewModelProducer(stats.answers),
    previewModelProducer(stats.uniqueItems),
    previewModelProducer(stats.learnedKanji),
    previewModelProducer(stats.learnedWords)
)

@Composable
private fun StatsChartPreview(
    data: StatsData,
    correctLabel: String = stringResource(R.string.stats_correct_answers),
    wrongLabel: String = stringResource(R.string.stats_wrong_answers)
) {
    val modelProducer = previewModelProducer(data)

    KakugoTheme {
        val themeAttributes = LocalThemeAttributes.current
        StatsChart(
            modelProducer = modelProducer,
            data = data,
            correctColor = themeAttributes.statsItemsGood,
            wrongColor = themeAttributes.statsItemsBad,
            correctLabel = correctLabel,
            wrongLabel = wrongLabel,
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
        )
    }
}

@Composable
private fun LearnedItemsChartPreview(data: CountData) {
    val modelProducer = previewModelProducer(data)

    KakugoTheme {
        LearnedItemsChart(
            modelProducer = modelProducer,
            data = data,
            color = LocalThemeAttributes.current.statsLearnedItems,
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatsScreenWithDataPreview() {
    val stats = Stats(
        previewStatsData(30),
        previewUniqueStatsData(30),
        previewCountData(30, 8),
        previewCountData(30, 3)
    )
    StatsScreen(stats, previewStatsModels(stats), onBackClick = {})
}

@Preview(showBackground = true)
@Composable
fun StatsScreenNoDataPreview() {
    val empty = StatsData(days = emptyList(), nextDay = nextDayTimestamp())
    val emptyCounts = CountData(days = emptyList(), nextDay = nextDayTimestamp())
    val models = remember {
        StatsModels(
            CartesianChartModelProducer(),
            CartesianChartModelProducer(),
            CartesianChartModelProducer(),
            CartesianChartModelProducer()
        )
    }
    StatsScreen(Stats(empty, empty, emptyCounts, emptyCounts), models, onBackClick = {})
}

@Preview(showBackground = true)
@Composable
fun StatsChartShortHistoryPreview() {
    StatsChartPreview(previewStatsData(10))
}

@Preview(showBackground = true)
@Composable
fun StatsChartLongHistoryPreview() {
    StatsChartPreview(previewStatsData(60))
}

@Preview(showBackground = true)
@Composable
fun LearnedItemsChartShortHistoryPreview() {
    LearnedItemsChartPreview(previewCountData(10, 8))
}

@Preview(showBackground = true)
@Composable
fun LearnedItemsChartLongHistoryPreview() {
    LearnedItemsChartPreview(previewCountData(60, 8))
}
