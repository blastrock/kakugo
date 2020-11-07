package org.kaqui.stats

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.UI
import org.kaqui.R
import org.kaqui.barChart
import org.kaqui.getColorFromAttr
import org.kaqui.model.Database
import org.kaqui.roundToPreviousDay
import java.text.DateFormat
import java.util.*

class TestStatsFragment: Fragment() {
    companion object {
        const val TAG = "TestStatsFragment"
    }

    private val nextDay = run {
        val calendar = Calendar.getInstance()
        calendar.roundToPreviousDay()
        calendar.roll(Calendar.DAY_OF_MONTH, true)

        calendar.timeInMillis / 1000
    }

    private lateinit var testItemsChart: BarChart
    private lateinit var noDataLabel: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        return UI {
            scrollView {
                verticalLayout {
                    textView(R.string.stats_items_answered) {
                        setTypeface(null, Typeface.BOLD)
                    }.lparams {
                        gravity = Gravity.CENTER
                        verticalMargin = dip(4)
                    }

                    testItemsChart = barChart {
                        data = barData

                        description.isEnabled = false
                        setDrawValueAboveBar(false)
                        axisRight.isEnabled = false
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        legend.isEnabled = false
                        setPinchZoom(false)
                        isDoubleTapToZoomEnabled = false
                        setScaleEnabled(false)
                        setDrawBorders(true)
                        isHighlightPerTapEnabled = false
                        isHighlightPerDragEnabled = false

                        xAxis.valueFormatter = object : ValueFormatter() {
                            val calendar = Calendar.getInstance()

                            override fun getAxisLabel(value: Float, axis: AxisBase): String {
                                calendar.timeInMillis = (value.toLong() * 24 * 3600 + nextDay) * 1000
                                return DateFormat.getDateInstance(DateFormat.SHORT).format(calendar.time)
                            }
                        }
                        xAxis.granularity = 1f
                        axisLeft.axisMinimum = 0f
                        axisLeft.granularity = 1f

                        xAxis.textColor = context.getColorFromAttr(android.R.attr.textColorPrimary)
                        axisLeft.textColor = context.getColorFromAttr(android.R.attr.textColorPrimary)
                    }.lparams(width = matchParent, height = dip(400)) {
                        bottomPadding = dip(20)
                    }

                    noDataLabel = textView(R.string.stats_no_data) {
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    }.lparams {
                        gravity = Gravity.CENTER
                    }.lparams(width = matchParent, height = dip(400))
                }.lparams(width = matchParent) {
                    margin = dip(16)
                }
            }
        }.view
    }

    override fun onStart() {
        super.onStart()

        refreshGraph()
    }

    private fun refreshGraph() {
        val rawDayStats = Database.getInstance(requireContext()).getAskedItem()

        val dayStats = rawDayStats.groupBy {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.timestamp * 1000
            calendar.roundToPreviousDay()
            calendar.timeInMillis / 1000
        }.map { Database.DayStatistics(it.key, it.value.map { it.askedCount }.sum()) }

        val values = dayStats.map { stat ->
            // Save time relative to a point close to now to keep the maximum precision
            // Also, use a date in the future so that we don't have values both positive and negative
            // as it values close to 0, positive or negative, would both round down to 0
            BarEntry(((stat.timestamp - nextDay) / 24 / 3600).toFloat(), stat.askedCount.toFloat())
        }.toMutableList()

        val today = run {
            val cal = Calendar.getInstance()
            cal.roundToPreviousDay()
            cal.timeInMillis / 1000
        }
        if (rawDayStats.isNotEmpty() && rawDayStats.last().timestamp != today)
            values.add(BarEntry(((today - nextDay) / 24 / 3600).toFloat(), 0f))

        val dataSet = BarDataSet(values, getString(R.string.stats_items_answered))

        val barData = BarData(dataSet)
        barData.setDrawValues(false)
        barData.dataSetLabels

        if (values.isNotEmpty()) {
            testItemsChart.data = barData
            testItemsChart.visibility = View.VISIBLE
            noDataLabel.visibility = View.GONE
        } else {
            testItemsChart.visibility = View.GONE
            noDataLabel.visibility = View.VISIBLE
        }
    }
}
