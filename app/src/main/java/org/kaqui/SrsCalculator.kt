package org.kaqui

import android.util.Log
import org.kaqui.model.Certainty
import org.kaqui.model.GOOD_WEIGHT
import org.kaqui.model.Item
import java.util.*

class SrsCalculator {

    data class ProbabilityData(@JvmField var itemId: Int, @JvmField var shortScore: Double, @JvmField var shortWeight: Double, @JvmField var longScore: Double, @JvmField var longWeight: Double, @JvmField var lastCorrect: Long, @JvmField var daysSinceCorrect: Double, @JvmField var finalProbability: Double)

    data class ProbaParamsStage1(@JvmField val daysBegin: Double, @JvmField val daysEnd: Double, @JvmField val spreadBegin: Double, @JvmField val spreadEnd: Double)
    data class ProbaParamsStage2(@JvmField val shortCoefficient: Double, @JvmField val longCoefficient: Double)

    data class DebugParams(var probaParamsStage1: ProbaParamsStage1, var probaParamsStage2: ProbaParamsStage2)

    data class ScoreUpdate(val itemId: Int, val shortScore: Float, val longScore: Float, val lastCorrect: Long, val minLastCorrect: Int)

    private data class Stage1Stats(@JvmField val totalShortWeight: Double, @JvmField val totalLongWeight: Double, @JvmField val countUnknown: Int)

    companion object {
        private const val TAG = "SrsCalculator"

        private const val MIN_PROBA_SHORT_UNKNOWN = 0.2
        private const val MAX_PROBA_SHORT_UNKNOWN = 0.9
        private const val MAX_COUNT_SHORT_UNKNOWN = 30

        fun fillProbalities(items: List<ProbabilityData>, minLastCorrect: Int): Pair<List<ProbabilityData>, DebugParams> {
            val now = Calendar.getInstance().timeInMillis / 1000

            val probaParams = getProbaParamsStage1(now, minLastCorrect)
            Log.v(TAG, "probaParamsStage1: $probaParams, minLastCorrect: $minLastCorrect")
            val ret = items.map { getProbabilityDataStage1(now, probaParams, it) }
            val stage1Stats = getStage1Stats(ret)
            val probaParams2 = getProbaParamsStage2(stage1Stats)
            Log.v(TAG, "probaParamsStage2: $probaParams2, stage1Stats: $stage1Stats")
            return Pair(ret.map { getProbabilityDataStage2(probaParams2, it) }, DebugParams(probaParams, probaParams2))
        }

        private fun getProbabilityDataStage1(now: Long, probaParams: ProbaParamsStage1, stage0: ProbabilityData): ProbabilityData {
            stage0.shortWeight = 1 - stage0.shortScore
            if (stage0.shortWeight < 0 || stage0.shortWeight > 1)
                Log.wtf(TAG, "Invalid shortWeight: ${stage0.shortWeight}, shortScore: ${stage0.shortScore}")

            stage0.daysSinceCorrect = (now - stage0.lastCorrect) / 3600.0 / 24.0

            val sigmoidArg = (stage0.daysSinceCorrect - probaParams.daysBegin - (probaParams.daysEnd - probaParams.daysBegin) * stage0.longScore) /
                    (probaParams.spreadBegin + (probaParams.spreadEnd - probaParams.spreadBegin) * stage0.longScore)
            // cap it to avoid overflow on Math.exp in the sigmoid
            stage0.longWeight = sigmoid(Math.min(sigmoidArg, 10.0))
            if (stage0.longWeight < 0 || stage0.longWeight > 1)
                Log.wtf(TAG, "Invalid longWeight: ${stage0.longWeight}, lastCorrect: ${stage0.lastCorrect}, now: $now, longScore: ${stage0.longScore}, probaParamsStage1: $probaParams")

            // square long weight to increase gaps
            stage0.longWeight *= stage0.longWeight

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

        fun getScoreUpdate(minLastCorrect: Int, item: Item, certainty: Certainty): ScoreUpdate {
            val now = Calendar.getInstance().timeInMillis / 1000

            val probaParams = getProbaParamsStage1(now, minLastCorrect)
            val targetScore = certaintyToWeight(certainty)

            val previousShortScore = item.shortScore
            // short score reduces the distance by half to target score
            var newShortScore = previousShortScore + (targetScore - previousShortScore) / 2
            if (newShortScore !in 0f..1f) {
                Log.wtf(TAG, "Score calculation error, previousShortScore = $previousShortScore, targetScore = $targetScore, newShortScore = $newShortScore")
            }
            if (newShortScore >= GOOD_WEIGHT) {
                newShortScore = 1.0
            }

            val previousLongScore = item.longScore
            // if lastCorrect wasn't initialized, set it to now
            val lastCorrect =
                    if (item.lastCorrect > 0)
                        item.lastCorrect
                    else
                        now
            val daysSinceCorrect = secondsToDays(now - lastCorrect)
            // long score goes down by one half of the distance to the target score
            // and it goes up by one half of that distance prorated by the time since the last
            // correct answer
            val newLongScore =
                    when {
                        previousLongScore < targetScore ->
                            Math.min(1.0, previousLongScore + (1.0 / 10.0 *
                                    Math.min(daysSinceCorrect / 2.0 /
                                            (probaParams.daysBegin + (probaParams.daysEnd - probaParams.daysBegin) * previousLongScore), 1.0)))
                        previousLongScore > targetScore ->
                            previousLongScore - (-targetScore + previousLongScore) / 2
                        else ->
                            previousLongScore
                    }
            if (newLongScore !in 0f..1f) {
                Log.wtf(TAG, "Score calculation error, previousLongScore = $previousLongScore, daysSinceCorrect = $daysSinceCorrect, targetScore = $targetScore, probaParamsStage1: $probaParams, newLongScore = $newLongScore")
            }

            Log.v(TAG, "Score calculation: targetScore: $targetScore, daysSinceCorrect: $daysSinceCorrect, probaParamsStage1: $probaParams")
            Log.v(TAG, "Short score of $item going from $previousShortScore to $newShortScore")
            Log.v(TAG, "Long score of $item going from $previousLongScore to $newLongScore")

            return ScoreUpdate(item.id, newShortScore.toFloat(), newLongScore.toFloat(), now, minLastCorrect)
        }

        private fun certaintyToWeight(certainty: Certainty): Double =
                when (certainty) {
                    Certainty.SURE -> 1.0
                    Certainty.MAYBE -> 0.7
                    Certainty.DONTKNOW -> 0.0
                }

        private fun sigmoid(x: Double) = Math.exp(x) / (1 + Math.exp(x))

        private fun getProbaParamsStage1(now: Long, minLastCorrect: Int): ProbaParamsStage1 {
            val daysEnd = (now - minLastCorrect) / 3600.0 / 24.0
            val spreadEnd = (daysEnd * 0.5) / 7.0

            return ProbaParamsStage1(0.5, daysEnd, 0.5 / 7.0, spreadEnd)
        }

        private fun lerp(start: Double, end: Double, value: Double): Double = start + value * (end - start)

        private fun getProbaParamsStage2(stage1Stats: Stage1Stats): ProbaParamsStage2 {
            if (stage1Stats.totalShortWeight == 0.0 || stage1Stats.totalLongWeight == 0.0)
                return ProbaParamsStage2(1.0, 1.0)

            val neededShortWeight = lerp(MIN_PROBA_SHORT_UNKNOWN, MAX_PROBA_SHORT_UNKNOWN, Math.min(stage1Stats.countUnknown.toDouble() / MAX_COUNT_SHORT_UNKNOWN, 1.0))
            val totalWeight = stage1Stats.totalShortWeight + stage1Stats.totalLongWeight
            val shortCoefficient = neededShortWeight * totalWeight / stage1Stats.totalShortWeight
            return ProbaParamsStage2(shortCoefficient, 1.0)
        }
    }
}
