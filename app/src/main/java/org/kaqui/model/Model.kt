package org.kaqui.model

import android.graphics.Path

sealed class ItemContents

data class Kanji(
        var kanji: String,
        var on_readings: List<String>,
        var kun_readings: List<String>,
        var meanings: List<String>,
        var strokes: List<Path>,
        var similarities: List<Item>,
        var jlptLevel: Int
) : ItemContents()

data class Kana(
        var kana: String,
        var romaji: String,
        var similarities: List<Item>
) : ItemContents()

data class Word(
        var word: String,
        var reading: String,
        var meanings: List<String>
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
        is Word -> listOf()
    }

fun Item.getQuestionText(quizzType: QuizzType): String =
        when (quizzType) {
            QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.KATAKANA_TO_ROMAJI -> (contents as Kana).kana
            QuizzType.ROMAJI_TO_HIRAGANA, QuizzType.ROMAJI_TO_KATAKANA -> (contents as Kana).romaji

            QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING -> (contents as Kanji).kanji
            QuizzType.READING_TO_KANJI -> (contents as Kanji).readingsText
            QuizzType.MEANING_TO_KANJI -> (contents as Kanji).meaningsText

            QuizzType.WORD_TO_READING, QuizzType.WORD_TO_MEANING -> (contents as Word).word
            QuizzType.READING_TO_WORD -> (contents as Word).reading
            QuizzType.MEANING_TO_WORD -> (contents as Word).meaningsText
            QuizzType.KANJI_WRITING -> "${(contents as Kanji).readingsText}\n${(contents as Kanji).meaningsText}"
        }

fun Item.getAnswerText(quizzType: QuizzType): String =
        when (quizzType) {
            QuizzType.HIRAGANA_TO_ROMAJI, QuizzType.KATAKANA_TO_ROMAJI -> (contents as Kana).romaji
            QuizzType.ROMAJI_TO_HIRAGANA, QuizzType.ROMAJI_TO_KATAKANA -> (contents as Kana).kana

            QuizzType.KANJI_TO_READING -> (contents as Kanji).readingsText
            QuizzType.KANJI_TO_MEANING -> (contents as Kanji).meaningsText
            QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI -> (contents as Kanji).kanji

            QuizzType.WORD_TO_READING -> (contents as Word).reading
            QuizzType.WORD_TO_MEANING -> (contents as Word).meaningsText
            QuizzType.READING_TO_WORD, QuizzType.MEANING_TO_WORD -> (contents as Word).word
            QuizzType.KANJI_WRITING -> throw RuntimeException("No answer text for kanji writing")
        }

val Kanji.readingsText: String
    get() = on_readings.joinToString(", ") + "\n" +
            kun_readings.joinToString(", ")

val Kanji.meaningsText: String
    get() = meanings.joinToString(", ")

val Word.meaningsText: String
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
        is Word -> {
            val word = contents as Word
            word.word
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
        is Word -> {
            val word = contents as Word
            word.reading + "\n" +
                    word.meanings.joinToString(", ")
        }
    }
