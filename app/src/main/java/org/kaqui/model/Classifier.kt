package org.kaqui.model

import android.content.Context
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
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
            is RtkIndex -> "rtk_index BETWEEN ? AND ?"
            is Rtk6Index -> "rtk6_index BETWEEN ? AND ?"
        }

fun Classifier.whereArguments() =
        when (this) {
            is JlptLevel -> arrayOf(this.level.toString())
            is RtkIndex -> arrayOf(this.from.toString(), this.to.toString())
            is Rtk6Index -> arrayOf(this.from.toString(), this.to.toString())
        }

fun Classifier.name(context: Context) =
        when (this) {
            is JlptLevel ->
                if (this.level == 0)
                    context.getString(R.string.additional_kanji)
                else
                    context.getString(R.string.jlpt_level_n, this.level.toString())
            else -> TODO("not implemented")
        }

enum class Classification {
    JlptLevel,
    RtkIndexRange,
    Rtk6IndexRange,
}

fun getClassifiers(type: Classification): List<Classifier> =
        when (type) {
            Classification.JlptLevel -> (5 downTo 0).map { JlptLevel(it) }
            else -> TODO("not implemented")
        }
