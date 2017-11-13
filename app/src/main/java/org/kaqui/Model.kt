package org.kaqui

data class Kanji(
        var id: Int,
        var kanji: String,
        var readings: List<Reading>,
        var meanings: List<String>,
        var similarities: List<Kanji>,
        var jlptLevel: Int,
        var shortScore: Double,
        var longScore: Double,
        var lastCorrect: Long,
        var enabled: Boolean
)

data class Reading(var readingType: String, var reading: String)

data class Kana(
        var id: Int,
        var kana: String,
        var romaji: String,
        var shortScore: Double,
        var longScore: Double,
        var lastCorrect: Long,
        var enabled: Boolean
)