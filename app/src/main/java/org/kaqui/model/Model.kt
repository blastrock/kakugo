package org.kaqui.model

sealed class ItemContents

data class Kanji(
        var kanji: String,
        var on_readings: List<String>,
        var kun_readings: List<String>,
        var meanings: List<String>,
        var similarities: List<Item>,
        var jlptLevel: Int
) : ItemContents()

data class Kana(
        var kana: String,
        var romaji: String,
        var similarities: List<Item>
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
        is Kana -> (contents as Kana).similarities
        is Kanji -> (contents as Kanji).similarities
    }

fun Item.getQuestionText(quizzType: QuizzType): String =
        when (quizzType) {
            QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.KATAKANA_TO_ROMAJI -> (contents as Kana).kana
            QuizzType.ROMAJI_TO_HIRAGANA, QuizzType.ROMAJI_TO_KATAKANA -> (contents as Kana).romaji
            QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING -> (contents as Kanji).kanji
            QuizzType.READING_TO_KANJI -> (contents as Kanji).readingsText
            QuizzType.MEANING_TO_KANJI -> (contents as Kanji).meaningsText
        }

fun Item.getAnswerText(quizzType: QuizzType): String =
        when (quizzType) {
            QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.KATAKANA_TO_ROMAJI -> (contents as Kana).romaji
            QuizzType.ROMAJI_TO_HIRAGANA, QuizzType.ROMAJI_TO_KATAKANA -> (contents as Kana).kana
            QuizzType.KANJI_TO_READING -> (contents as Kanji).readingsText
            QuizzType.KANJI_TO_MEANING -> (contents as Kanji).meaningsText
            QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI -> (contents as Kanji).kanji
        }

private val Kanji.readingsText: String
    get() = on_readings.joinToString(", ") + "\n" +
            kun_readings.joinToString(", ")

private val Kanji.meaningsText: String
    get() = meanings.joinToString(", ")

val Item.text: String
    get() = when (contents) {
        is Kana -> {
            val kana = contents as Kana
            kana.kana
        }
        is Kanji -> {
            val kanji = contents as Kanji
            kanji.kanji
        }
    }

val Item.description: String
    get() = when (contents) {
        is Kana -> {
            val kana = contents as Kana
            kana.romaji
        }
        is Kanji -> {
            val kanji = contents as Kanji
            kanji.on_readings.joinToString(", ") + "\n" +
                    kanji.kun_readings.joinToString(", ") + "\n" +
                    kanji.meanings.joinToString(", ")
        }
    }
