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

fun Classifier.whereClause() =
        when (this) {
            is JlptLevel -> "jlpt_level = ?"
            is RtkIndex -> "rtk_index BETWEEN ? AND (? - 1)"
            is Rtk6Index -> "rtk6_index BETWEEN ? AND (? - 1)"
        }

fun Classifier.whereArguments() =
        when (this) {
            is JlptLevel -> arrayOf(this.level.toString())
            is RtkIndex -> arrayOf(this.from.toString(), this.to.toString())
            is Rtk6Index -> arrayOf(this.from.toString(), this.to.toString())
        }

fun Classifier.orderColumn() =
        when (this) {
            is JlptLevel -> "jlpt_level, rtk6_index" // sort also by something else because level is not enough
            is RtkIndex -> "rtk_index"
            is Rtk6Index -> "rtk6_index"
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
        }

enum class Classification {
    JlptLevel,
    RtkIndexRange,
    Rtk6IndexRange,
}

const val IndexStep = 200
const val RtkUnclassified = 0x1000000

fun getClassifiers(type: Classification): List<Classifier> =
        when (type) {
            Classification.JlptLevel -> (5 downTo 0).map { JlptLevel(it) }
            Classification.RtkIndexRange -> (0 until 3007 step IndexStep).map { RtkIndex(it, it+IndexStep) } + listOf(RtkIndex(RtkUnclassified, RtkUnclassified))
            Classification.Rtk6IndexRange -> (0 until 3000 step IndexStep).map { Rtk6Index(it, it+IndexStep) } + listOf(RtkIndex(RtkUnclassified, RtkUnclassified))
        }
