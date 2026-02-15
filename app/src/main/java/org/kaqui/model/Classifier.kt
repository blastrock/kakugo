package org.kaqui.model

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.kaqui.R

sealed class Classifier : Parcelable

@Parcelize
data class JlptLevel(val level: Int) : Classifier()
@Parcelize
data class RtkIndex(val from: Int, val to: Int) : Classifier()
@Parcelize
data class Rtk6Index(val from: Int, val to: Int) : Classifier()
@Parcelize
data class FreqRange(val from: Int, val to: Int, val classification: Classification) : Classifier()

fun Classifier.whereClause() =
        when (this) {
            is JlptLevel -> "jlpt_level = ?"
            is RtkIndex -> "rtk_index BETWEEN ? AND (? - 1)"
            is Rtk6Index -> "rtk6_index BETWEEN ? AND (? - 1)"
            is FreqRange -> when (this.classification) {
                Classification.JlptLevel -> "jlpt_level = 0 AND freq BETWEEN ? AND ?"
                Classification.RtkIndexRange -> "rtk_index = $RtkUnclassified AND freq BETWEEN ? AND ?"
                Classification.Rtk6IndexRange -> "rtk6_index = $RtkUnclassified AND freq BETWEEN ? AND ?"
            }
        }

fun Classifier.whereArguments() =
        when (this) {
            is JlptLevel -> arrayOf(this.level.toString())
            is RtkIndex -> arrayOf(this.from.toString(), this.to.toString())
            is Rtk6Index -> arrayOf(this.from.toString(), this.to.toString())
            is FreqRange -> arrayOf(this.from.toString(), this.to.toString())
        }

fun Classifier.orderColumn() =
        when (this) {
            is JlptLevel -> "jlpt_level, rtk6_index" // sort also by something else because level is not enough
            is RtkIndex -> "rtk_index"
            is Rtk6Index -> "rtk6_index"
            is FreqRange -> "freq"
        }

fun Classifier.name(context: Context): String =
        when (this) {
            is JlptLevel ->
                if (this.level == 0)
                    context.getString(R.string.additional_kanji)
                else
                    context.getString(R.string.jlpt_level_n, this.level.toString())
            is RtkIndex ->
                if (this.from == RtkUnclassified)
                    context.getString(R.string.additional_kanji)
                else
                    context.getString(R.string.rtk_index_range, this.from.toString())
            is Rtk6Index ->
                if (this.from == RtkUnclassified)
                    context.getString(R.string.additional_kanji)
                else
                    context.getString(R.string.rtk6_index_range, this.from.toString())
            is FreqRange ->
                context.getString(R.string.additional_words_range, this.from.toString(), this.to.toString())
        }

enum class Classification {
    JlptLevel,
    RtkIndexRange,
    Rtk6IndexRange,
}

const val IndexStep = 200
const val RtkUnclassified = 0x1000000
const val FreqStep = 5000

private fun freqRanges(maxFreq: Int, classification: Classification) =
        (0 until maxFreq step FreqStep).map {
            FreqRange(it + 1, it + FreqStep, classification)
        }

fun getClassifiers(type: Classification, maxFreq: Int): List<Classifier> =
        when (type) {
            Classification.JlptLevel -> {
                val jlptLevels = (5 downTo 1).map { JlptLevel(it) }
                if (maxFreq > 0)
                    jlptLevels + freqRanges(maxFreq, Classification.JlptLevel)
                else
                    jlptLevels + listOf(JlptLevel(0))
            }
            Classification.RtkIndexRange -> {
                val rtkRanges = (0 until 3007 step IndexStep).map { RtkIndex(it, it + IndexStep) }
                if (maxFreq > 0)
                    rtkRanges + freqRanges(maxFreq, Classification.RtkIndexRange)
                else
                    rtkRanges + listOf(RtkIndex(RtkUnclassified, RtkUnclassified))
            }
            Classification.Rtk6IndexRange -> {
                val rtk6Ranges = (0 until 3000 step IndexStep).map { Rtk6Index(it, it + IndexStep) }
                if (maxFreq > 0)
                    rtk6Ranges + freqRanges(maxFreq, Classification.Rtk6IndexRange)
                else
                    rtk6Ranges + listOf(RtkIndex(RtkUnclassified, RtkUnclassified))
            }
        }
