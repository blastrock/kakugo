package org.kaqui.stats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
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
import org.kaqui.roundToPreviousDay
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes
import java.text.DateFormat
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max

private val ChartHeight = 280.dp
private const val ColumnThickness = 4
private const val ColumnSpacing = 2
private const val YLabelCount = 5

// Beyond this many days, date labels are too dense to be readable one per day
private const val DailyLabelDayLimit = 14
private const val SparseLabelSpacing = 7

private fun nextDayTimestamp(): Long {
    val calendar = Calendar.getInstance()
    calendar.roundToPreviousDay()
    calendar.roll(Calendar.DAY_OF_MONTH, true)
    return calendar.timeInMillis / 1000
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

    val statsData = remember {
        val rawDayStats = Database.getInstance(context).getAskedItem()

        val dayStats = rawDayStats.groupBy {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.timestamp * 1000
            calendar.roundToPreviousDay()
            calendar.timeInMillis / 1000
        }.map { Database.DayStatistics(it.key, it.value.sumOf { it.askedCount }, it.value.sumOf { it.correctCount }) }

        val nextDay = nextDayTimestamp()

        val days = dayStats.map { stat ->
            DayStat(
                ((stat.timestamp - nextDay) / 24 / 3600).toInt(),
                stat.correctCount,
                stat.askedCount - stat.correctCount
            )
        }.toMutableList()

        val today = run {
            val cal = Calendar.getInstance()
            cal.roundToPreviousDay()
            cal.timeInMillis / 1000
        }
        if (rawDayStats.isNotEmpty() && rawDayStats.last().timestamp != today)
            days.add(DayStat(((today - nextDay) / 24 / 3600).toInt(), 0, 0))

        StatsData(days.sortedBy { it.dayOffset }, nextDay)
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(statsData) {
        modelProducer.runTransaction { statsColumns(statsData) }
    }

    StatsScreen(statsData, modelProducer, onBackClick)
}

@Composable
private fun StatsScreen(
    statsData: StatsData,
    modelProducer: CartesianChartModelProducer,
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.stats_items_answered),
                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (statsData.days.isNotEmpty()) {
                val themeAttributes = LocalThemeAttributes.current
                StatsChart(
                    modelProducer = modelProducer,
                    data = statsData,
                    correctColor = themeAttributes.statsItemsGood,
                    wrongColor = themeAttributes.statsItemsBad,
                    correctLabel = stringResource(R.string.stats_correct_answers),
                    wrongLabel = stringResource(R.string.stats_wrong_answers),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ChartHeight)
                )
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
}

private fun CartesianChartModelProducer.Transaction.statsColumns(data: StatsData) {
    columnModel {
        series(data.days.map { it.dayOffset }, data.days.map { it.correct })
        series(data.days.map { it.dayOffset }, data.days.map { it.wrong })
    }
}

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
    val dateFormatter = remember(data.nextDay) {
        val calendar = Calendar.getInstance()
        val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
        CartesianValueFormatter { _, value, _ ->
            calendar.timeInMillis = (value.toLong() * 24 * 3600 + data.nextDay) * 1000
            dateFormat.format(calendar.time)
        }
    }

    val labelSpacing =
        if (data.days.size > DailyLabelDayLimit) SparseLabelSpacing else 1
    val yStep = remember(data) {
        val maxTotal = data.days.maxOf { it.correct + it.wrong }
        max(1.0, ceil(maxTotal.toDouble() / YLabelCount))
    }

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
                    padding = Insets(top = 8.dp)
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

data class DayStat(
    val dayOffset: Int,
    val correct: Int,
    val wrong: Int
)

data class StatsData(
    val days: List<DayStat>,
    val nextDay: Long
)

private fun previewStatsData(dayCount: Int) = StatsData(
    days = (0 until dayCount).map { day ->
        DayStat(day - dayCount, 12 + day * 7 % 28, day * 5 % 11)
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
private fun StatsChartPreview(data: StatsData) {
    val modelProducer = previewModelProducer(data)

    KakugoTheme {
        val themeAttributes = LocalThemeAttributes.current
        StatsChart(
            modelProducer = modelProducer,
            data = data,
            correctColor = themeAttributes.statsItemsGood,
            wrongColor = themeAttributes.statsItemsBad,
            correctLabel = stringResource(R.string.stats_correct_answers),
            wrongLabel = stringResource(R.string.stats_wrong_answers),
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatsScreenWithDataPreview() {
    val data = previewStatsData(30)
    StatsScreen(data, previewModelProducer(data), onBackClick = {})
}

@Preview(showBackground = true)
@Composable
fun StatsScreenNoDataPreview() {
    StatsScreen(
        StatsData(days = emptyList(), nextDay = nextDayTimestamp()),
        remember { CartesianChartModelProducer() },
        onBackClick = {}
    )
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
