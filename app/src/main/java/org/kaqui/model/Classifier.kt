package org.kaqui.model

sealed class Classifier

data class JlptLevel(val level: Int) : Classifier()
data class RtkIndex(val from: Int, val to: Int) : Classifier()
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
