package org.kaqui.model

sealed class ItemContents

data class Kanji(
        var kanji: String,
        var readings: List<Reading>,
        var meanings: List<String>,
        var similarities: List<Item>,
        var jlptLevel: Int
) : ItemContents()

data class Reading(var readingType: String, var reading: String)

data class Kana(
        var kana: String,
        var romaji: String
) : ItemContents()

data class Item(
        var id: Int,
        var contents: ItemContents,
        var shortScore: Double,
        var longScore: Double,
        var lastCorrect: Long,
        var enabled: Boolean
)

val Item.similarities: List<Item>
    get() = when (contents) {
        is Kana -> listOf()
        is Kanji -> (contents as Kanji).similarities
    }