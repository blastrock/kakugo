package org.kaqui

import android.util.Log
import org.kaqui.model.Certainty
import org.kaqui.model.Item
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

class SrsCalculator {

    data class ProbabilityData(@JvmField var itemId: Int, @JvmField var shortScore: Double, @JvmField var shortWeight: Double, @JvmField var longScore: Double, @JvmField var longWeight: Double, @JvmField var lastAsked: Long, @JvmField var daysSinceAsked: Double, @JvmField var finalProbability: Double)

    data class ProbaParamsStage1(@JvmField val daysEnd: Double)
    data class ProbaParamsStage2(@JvmField val minProbaShort: Double, @JvmField val shortCoefficient: Double, @JvmField val longCoefficient: Double)

    data class DebugParams(var probaParamsStage1: ProbaParamsStage1, var probaParamsStage2: ProbaParamsStage2)

    data class ScoreUpdate(val itemId: Int, val shortScore: Float, val longScore: Float, val lastAsked: Long, val minLastAsked: Long)

    private data class Stage1Stats(@JvmField val totalShortWeight: Double, @JvmField val totalLongWeight: Double, @JvmField val countUnknown: Int)

    companion object {
        private const val TAG = "SrsCalculator"

        private const val SHORT_SCORE_UPDATE = 0.34
        private const val MIN_PROBA_SHORT_UNKNOWN = 0.2
        private const val MAX_PROBA_SHORT_UNKNOWN = 0.9
        private const val MAX_COUNT_SHORT_UNKNOWN = 30
        private const val MIN_SHORT_RATIO_LERP = 1.0/4
        private const val MAX_SHORT_RATIO_LERP = 1.0/3
        private const val MIN_LONG_WEIGHT = 0.1
        private const val MAX_LONG_SCORE_UPDATE_INCREMENT = 0.125

        fun fillProbalities(items: List<ProbabilityData>, minLastAsked: Long): Pair<List<ProbabilityData>, DebugParams> {
            val now = Calendar.getInstance().timeInMillis / 1000

            val probaParams = getProbaParamsStage1(now, minLastAsked)
            Log.v(TAG, "probaParamsStage1: $probaParams, minLastAsked: $minLastAsked")
            val ret = items.map { getProbabilityDataStage1(now, probaParams, it) }
            val stage1Stats = getStage1Stats(ret)
            val probaParams2 = getProbaParamsStage2(stage1Stats, items.size)
            Log.v(TAG, "probaParamsStage2: $probaParams2, stage1Stats: $stage1Stats")
            return Pair(ret.map { getProbabilityDataStage2(probaParams2, it) }, DebugParams(probaParams, probaParams2))
        }

        private fun getProbabilityDataStage1(now: Long, probaParams: ProbaParamsStage1, stage0: ProbabilityData): ProbabilityData {
            stage0.shortWeight = 1 - stage0.shortScore
            if (stage0.shortWeight !in 0.0..1.0)
                Log.wtf(TAG, "Invalid shortWeight: ${stage0.shortWeight}, shortScore: ${stage0.shortScore}")

            stage0.daysSinceAsked = (now - stage0.lastAsked) / 3600.0 / 24.0

            stage0.longWeight =
                if (stage0.shortWeight == 0.0)
                    unitStep(stage0.daysSinceAsked - (probaParams.daysEnd * 0.99) * stage0.longScore) * lerp(MIN_LONG_WEIGHT, 1.0, (1 - stage0.longScore))
                else
                    0.0
            if (stage0.longWeight !in 0.0..1.0)
                Log.wtf(TAG, "Invalid longWeight: ${stage0.longWeight}, lastAsked: ${stage0.lastAsked}, now: $now, longScore: ${stage0.longScore}, probaParamsStage1: $probaParams")

            return stage0
        }

        private fun getStage1Stats(items: List<ProbabilityData>): Stage1Stats {
            var totalShortWeight = 0.0
            var totalLongWeight = 0.0
            var countUnknown = 0
            for (item in items) {
                totalShortWeight += item.shortWeight
                totalLongWeight += item.longWeight
                if (item.shortScore < 1.0)
                    countUnknown += 1
            }
            return Stage1Stats(totalShortWeight, totalLongWeight, countUnknown)
        }

        private fun getProbabilityDataStage2(probaParams: ProbaParamsStage2, stage1: ProbabilityData): ProbabilityData {
            stage1.finalProbability = probaParams.shortCoefficient * stage1.shortWeight + probaParams.longCoefficient * stage1.longWeight
            if (stage1.finalProbability < 0)
                Log.wtf(TAG, "Invalid finalProbability: ${stage1.finalProbability}, shortCoefficient: ${probaParams.shortCoefficient}, longCoefficient: ${probaParams.longCoefficient}, shortWeight: ${stage1.shortWeight}, longWeight: ${stage1.longWeight}")
            return stage1
        }

        fun getScoreUpdate(minLastAsked: Long, item: Item, certainty: Certainty): ScoreUpdate {
            val now = Calendar.getInstance().timeInMillis / 1000
            val probaParams = getProbaParamsStage1(now, minLastAsked)

            val previousShortScore = item.shortScore
            // short score reduces the distance by half to target score
            val newShortScore = when (certainty) {
                Certainty.SURE -> min(1.0, previousShortScore + SHORT_SCORE_UPDATE)
                Certainty.MAYBE -> min(0.7, previousShortScore + SHORT_SCORE_UPDATE/2)
                Certainty.DONTKNOW -> max(0.0, min(item.longScore, previousShortScore - SHORT_SCORE_UPDATE * 0.99))
            }
            if (newShortScore !in 0f..1f) {
                Log.wtf(TAG, "Score calculation error, previousShortScore = $previousShortScore, certainty = $certainty, newShortScore = $newShortScore")
            }

            val previousLongScore = item.longScore
            // if lastAsked wasn't initialized, set it to now
            val lastAsked =
                    if (item.lastAsked > 0)
                        item.lastAsked
                    else
                        now
            val daysSinceAsked = secondsToDays(now - lastAsked)
            val newLongScore =
                    when {
                        certainty == Certainty.MAYBE ->
                            previousLongScore / 2
                        certainty == Certainty.DONTKNOW ->
                            previousLongScore / lerp(4.0, 2.0, previousLongScore)
                        previousShortScore < 1.0 ->
                            if (newShortScore < 1.0)
                                previousLongScore
                            else
                                max(previousLongScore, 0.01)
                        certainty == Certainty.SURE -> {
                            val stepCompletion = min(daysSinceAsked / (probaParams.daysEnd * 0.99 * previousLongScore), 2.0)
                            min(1.0, min(max(previousLongScore, 0.01) * lerp(1.0, 2.0, stepCompletion), previousLongScore + MAX_LONG_SCORE_UPDATE_INCREMENT))
                        }
                        else ->
                            throw RuntimeException("Unknown certainty $certainty")
                    }
            if (newLongScore !in 0f..1f) {
                Log.wtf(TAG, "Score calculation error, previousLongScore = $previousLongScore, daysSinceAsked = $daysSinceAsked, probaParamsStage1: $probaParams, newLongScore = $newLongScore")
            }

            Log.v(TAG, "Score calculation: certainty: $certainty, daysSinceAsked: $daysSinceAsked, probaParamsStage1: $probaParams")
            Log.v(TAG, "Short score of $item going from $previousShortScore to $newShortScore")
            Log.v(TAG, "Long score of $item going from $previousLongScore to $newLongScore")

            return ScoreUpdate(item.id, newShortScore.toFloat(), newLongScore.toFloat(), now, minLastAsked)
        }

        private fun getProbaParamsStage1(now: Long, minLastAsked: Long): ProbaParamsStage1 {
            val daysEnd = (now - minLastAsked) / 3600.0 / 24.0

            return ProbaParamsStage1(daysEnd)
        }

        private fun getProbaParamsStage2(stage1Stats: Stage1Stats, totalCount: Int): ProbaParamsStage2 {
            if (stage1Stats.totalShortWeight == 0.0 || stage1Stats.totalLongWeight == 0.0)
                return ProbaParamsStage2(MIN_PROBA_SHORT_UNKNOWN, 1.0, 1.0)

            val minProbaCoeff = invLerp(MIN_SHORT_RATIO_LERP, MAX_SHORT_RATIO_LERP, stage1Stats.countUnknown.toDouble() / totalCount).coerceIn(0.0, 1.0)
            val minProbaShort = lerp(MIN_PROBA_SHORT_UNKNOWN, MAX_PROBA_SHORT_UNKNOWN, minProbaCoeff)
            val neededShortWeight = lerp(minProbaShort, MAX_PROBA_SHORT_UNKNOWN, min(stage1Stats.countUnknown.toDouble() / MAX_COUNT_SHORT_UNKNOWN, 1.0))
            val totalWeight = stage1Stats.totalShortWeight + stage1Stats.totalLongWeight
            val shortCoefficient = neededShortWeight * totalWeight / stage1Stats.totalShortWeight
            return ProbaParamsStage2(minProbaShort, shortCoefficient, 1.0)
        }
    }
}
