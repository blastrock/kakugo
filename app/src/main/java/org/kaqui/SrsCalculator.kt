package org.kaqui

import android.util.Log
import java.util.*

class SrsCalculator {

    data class ProbabilityData(var kanjiId: Int, var shortScore: Double, var shortWeight: Double, var longScore: Double, var longWeight: Double, var lastCorrect: Long, var daysSinceCorrect: Double, var finalProbability: Double)

    data class ProbaParamsStage1(val daysBegin: Double, val daysEnd: Double, val spreadBegin: Double, val spreadEnd: Double)
    data class ProbaParamsStage2(val shortCoefficient: Double, val longCoefficient: Double)

    data class DebugParams(var probaParamsStage1: ProbaParamsStage1, var probaParamsStage2: ProbaParamsStage2)

    data class ScoreUpdate(val kanjiId: Int, val shortScore: Float, val longScore: Float, val lastCorrect: Long?)

    companion object {
        private const val TAG = "SrsCalculator"

        private const val MIN_PROBA_SHORT_UNKNOWN = 0.1
        private const val MAX_PROBA_SHORT_UNKNOWN = 0.5
        private const val MAX_COUNT_SHORT_UNKNOWN = 30

        fun fillProbalities(kanjis: List<ProbabilityData>, minLastCorrect: Int): Pair<List<ProbabilityData>, DebugParams> {
            val probaParams = getProbaParamsStage1(minLastCorrect)
            Log.v(TAG, "probaParamsStage1: $probaParams, minLastCorrect: $minLastCorrect")
            val ret = kanjis.map({ getProbabilityDataStage1(probaParams, it) })
            val (totalShortProba, totalLongProba) = ret.fold(Pair(0.0, 0.0), { acc, v ->
                Pair(acc.first + v.shortWeight, acc.second + v.longWeight)
            })
            val countUnknown = ret.count { it.shortScore < 1.0 }
            val probaParams2 = getProbaParamsStage2(countUnknown, totalShortProba, totalLongProba)
            Log.v(TAG, "probaParamsStage2: $probaParams2, totalShortProba: $totalShortProba, totalLongProba: $totalLongProba, countUnknown: $countUnknown")
            return Pair(ret.map { getProbabilityDataStage2(probaParams2, it) }, DebugParams(probaParams, probaParams2))
        }

        private fun getProbabilityDataStage1(probaParams: ProbaParamsStage1, stage0: ProbabilityData): ProbabilityData {
            stage0.shortWeight = 1 - stage0.shortScore
            if (stage0.shortWeight !in 0..1)
                Log.wtf(TAG, "Invalid shortWeight: ${stage0.shortWeight}, shortScore: ${stage0.shortScore}")

            val now = Calendar.getInstance().timeInMillis / 1000
            stage0.daysSinceCorrect = (now - stage0.lastCorrect) / 3600.0 / 24.0

            val sigmoidArg = (stage0.daysSinceCorrect - probaParams.daysBegin - (probaParams.daysEnd - probaParams.daysBegin) * stage0.longScore) /
                    (probaParams.spreadBegin + (probaParams.spreadEnd - probaParams.spreadBegin) * stage0.longScore)
            // cap it to avoid overflow on Math.exp in the sigmoid
            stage0.longWeight = sigmoid(Math.min(sigmoidArg, 10.0))
            if (stage0.longWeight !in 0..1)
                Log.wtf(TAG, "Invalid longWeight: ${stage0.longWeight}, lastCorrect: ${stage0.lastCorrect}, now: $now, longScore: ${stage0.longScore}, probaParamsStage1: $probaParams")

            return stage0
        }

        private fun getProbabilityDataStage2(probaParams: ProbaParamsStage2, stage1: ProbabilityData): ProbabilityData {
            stage1.finalProbability = probaParams.shortCoefficient * stage1.shortWeight + probaParams.longCoefficient * stage1.longWeight
            return stage1
        }

        fun getScoreUpdate(minLastCorrect: Int, kanji: Kanji, certainty: Certainty): ScoreUpdate {
            val probaParams = getProbaParamsStage1(minLastCorrect)
            val targetScore = certaintyToWeight(certainty)

            val previousShortScore = kanji.shortScore
            // short score reduces the distance by half to target score
            var newShortScore = previousShortScore + (targetScore - previousShortScore) / 2
            if (newShortScore !in 0..1) {
                Log.wtf(TAG, "Score calculation error, previousShortScore = $previousShortScore, targetScore = $targetScore, newShortScore = $newShortScore")
            }
            if (newShortScore >= 0.92) {
                newShortScore = 1.0
            }

            val previousLongScore = kanji.longScore
            val now = Calendar.getInstance().timeInMillis / 1000
            // if lastCorrect wasn't initialized, set it to now
            val lastCorrect =
                    if (kanji.lastCorrect > 0)
                        kanji.lastCorrect
                    else
                        now
            val daysSinceCorrect = (now - lastCorrect) / 3600.0 / 24.0
            // long score goes down by one half of the distance to the target score
            // and it goes up by one half of that distance prorated by the time since the last
            // correct answer
            val newLongScore =
                    when {
                        previousLongScore < targetScore ->
                            previousLongScore + ((targetScore - previousLongScore) / 2) *
                                    Math.min(daysSinceCorrect / 2.0 /
                                            (probaParams.daysBegin + (probaParams.daysEnd - probaParams.daysBegin) * previousLongScore), 1.0)
                        previousLongScore > targetScore ->
                            previousLongScore - (-targetScore + previousLongScore) / 2
                        else ->
                            previousLongScore
                    }
            if (newLongScore !in 0..1) {
                Log.wtf(TAG, "Score calculation error, previousLongScore = $previousLongScore, daysSinceCorrect = $daysSinceCorrect, targetScore = $targetScore, probaParamsStage1: $probaParams, newLongScore = $newLongScore")
            }

            Log.v(TAG, "Score calculation: targetScore: $targetScore, daysSinceCorrect: $daysSinceCorrect, probaParamsStage1: $probaParams")
            Log.v(TAG, "Short score of $kanji going from $previousShortScore to $newShortScore")
            Log.v(TAG, "Long score of $kanji going from $previousLongScore to $newLongScore")

            return ScoreUpdate(kanji.id, newShortScore.toFloat(), newLongScore.toFloat(),
                    if (certainty != Certainty.DONTKNOW)
                        now
                    else null)
        }

        private fun certaintyToWeight(certainty: Certainty): Double =
                when (certainty) {
                    Certainty.SURE -> 1.0
                    Certainty.MAYBE -> 0.7
                    Certainty.DONTKNOW -> 0.0
                }

        private fun sigmoid(x: Double) = Math.exp(x) / (1 + Math.exp(x))

        private fun getProbaParamsStage1(minLastCorrect: Int): ProbaParamsStage1 {
            val now = Calendar.getInstance().timeInMillis / 1000

            val daysEnd = (now - minLastCorrect) / 3600.0 / 24.0
            val spreadEnd = (daysEnd * 0.8) / 7.0

            return ProbaParamsStage1(0.5, daysEnd, 0.5 / 7.0, spreadEnd)
        }

        private fun lerp(start: Double, end: Double, value: Double): Double = start + value * (end - start)

        private fun getProbaParamsStage2(countUnknown: Int, totalShortProba: Double, totalLongProba: Double): ProbaParamsStage2 {
            if (totalShortProba == 0.0 || totalLongProba == 0.0)
                return ProbaParamsStage2(1.0, 1.0)

            val neededShortProba = lerp(MIN_PROBA_SHORT_UNKNOWN, MAX_PROBA_SHORT_UNKNOWN, Math.min(countUnknown.toDouble() / MAX_COUNT_SHORT_UNKNOWN, 1.0))
            val neededLongProba = 1 - neededShortProba
            val total = totalShortProba + totalLongProba
            val shortCoefficient = neededShortProba * total / totalShortProba
            val longCoefficient = neededLongProba * total / totalLongProba
            return ProbaParamsStage2(shortCoefficient, longCoefficient)
        }
    }
}