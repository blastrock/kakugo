package org.kaqui.stats

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import org.kaqui.R
import org.kaqui.TopBar
import org.kaqui.model.Database
import org.kaqui.roundToPreviousDay
import org.kaqui.theme.KakugoTheme
import org.kaqui.theme.LocalThemeAttributes
import java.text.DateFormat
import java.util.Calendar
import kotlin.math.min

private const val AppBarOverhead = 100

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
    val themeAttributes = LocalThemeAttributes.current
    val configuration = LocalConfiguration.current

    // Calculate chart dimensions based on orientation
    val (chartWidth, chartHeight) = remember(configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val height = configuration.screenHeightDp - AppBarOverhead
            val width = min(configuration.screenWidthDp, height * 16 / 9)
            Pair(width.dp, height.dp)
        } else {
            val height = min(configuration.screenHeightDp - AppBarOverhead, configuration.screenWidthDp)
            Pair(-1.dp, height.dp) // -1 means match parent
        }
    }

    // Load data
    val statsData = remember {
        val rawDayStats = Database.getInstance(context).getAskedItem()

        val dayStats = rawDayStats.groupBy {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.timestamp * 1000
            calendar.roundToPreviousDay()
            calendar.timeInMillis / 1000
        }.map { Database.DayStatistics(it.key, it.value.sumOf { it.askedCount }, it.value.sumOf { it.correctCount }) }

        val nextDay = run {
            val calendar = Calendar.getInstance()
            calendar.roundToPreviousDay()
            calendar.roll(Calendar.DAY_OF_MONTH, true)
            calendar.timeInMillis / 1000
        }

        val values = dayStats.map { stat ->
            BarEntry(
                ((stat.timestamp - nextDay) / 24 / 3600).toFloat(),
                floatArrayOf(
                    stat.correctCount.toFloat(),
                    (stat.askedCount - stat.correctCount).toFloat()
                )
            )
        }.toMutableList()

        val today = run {
            val cal = Calendar.getInstance()
            cal.roundToPreviousDay()
            cal.timeInMillis / 1000
        }
        if (rawDayStats.isNotEmpty() && rawDayStats.last().timestamp != today)
            values.add(BarEntry(((today - nextDay) / 24 / 3600).toFloat(), floatArrayOf(0f, 0f)))

        StatsData(values, nextDay)
    }

    KakugoTheme {
        Surface(color = MaterialTheme.colors.background) {
            Scaffold(
                topBar = {
                    TopBar(
                        title = stringResource(R.string.title_stats),
                        onBackClick = onBackClick
                    )
                },
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

                    if (statsData.values.isNotEmpty()) {
                        StatsChart(
                            data = statsData,
                            chartWidth = chartWidth,
                            chartHeight = chartHeight,
                            correctColor = themeAttributes.statsItemsGood.toArgb(),
                            wrongColor = themeAttributes.statsItemsBad.toArgb(),
                            textColor = MaterialTheme.colors.onBackground.toArgb(),
                            correctLabel = stringResource(R.string.stats_correct_answers),
                            wrongLabel = stringResource(R.string.stats_wrong_answers)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(chartHeight),
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
    }
}

@Composable
fun StatsChart(
    data: StatsData,
    chartWidth: androidx.compose.ui.unit.Dp,
    chartHeight: androidx.compose.ui.unit.Dp,
    correctColor: Int,
    wrongColor: Int,
    textColor: Int,
    correctLabel: String,
    wrongLabel: String
) {
    AndroidView(
        modifier = Modifier
            .then(
                if (chartWidth > 0.dp)
                    Modifier.width(chartWidth)
                else
                    Modifier.fillMaxWidth()
            )
            .height(chartHeight)
            .padding(bottom = 20.dp),
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                setDrawValueAboveBar(false)
                axisRight.isEnabled = false
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                setPinchZoom(false)
                isDoubleTapToZoomEnabled = false
                setScaleEnabled(false)
                setDrawBorders(true)
                isHighlightPerTapEnabled = false
                isHighlightPerDragEnabled = false

                legend.isEnabled = true
                legend.orientation = Legend.LegendOrientation.VERTICAL
                legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER

                xAxis.valueFormatter = object : ValueFormatter() {
                    val calendar = Calendar.getInstance()

                    override fun getAxisLabel(value: Float, axis: AxisBase): String {
                        calendar.timeInMillis = (value.toLong() * 24 * 3600 + data.nextDay) * 1000
                        return DateFormat.getDateInstance(DateFormat.SHORT).format(calendar.time)
                    }
                }
                xAxis.granularity = 1f
                axisLeft.axisMinimum = 0f
                axisLeft.granularity = 1f

                xAxis.textColor = textColor
                axisLeft.textColor = textColor
                legend.textColor = textColor
            }
        },
        update = { chart ->
            val dataSet = BarDataSet(data.values, "")
            dataSet.colors = listOf(correctColor, wrongColor)
            dataSet.stackLabels = arrayOf(correctLabel, wrongLabel)

            val barData = BarData(dataSet)
            barData.setDrawValues(false)

            chart.data = barData
            chart.xAxis.textColor = textColor
            chart.axisLeft.textColor = textColor
            chart.legend.textColor = textColor
            chart.visibility = View.VISIBLE
            chart.invalidate()
        }
    )
}

data class StatsData(
    val values: List<BarEntry>,
    val nextDay: Long
)
