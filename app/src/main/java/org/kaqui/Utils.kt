package org.kaqui

fun getKanjiDescription(kanji: Kanji) =
        kanji.readings.filter { it.readingType == "ja_on" }.map { it.reading }.joinToString(", ") + "\n" +
                kanji.readings.filter { it.readingType == "ja_kun" }.map { it.reading }.joinToString(", ") + "\n" +
                kanji.meanings.joinToString(", ")

fun getKanjiReadings(kanji: Kanji) =
        kanji.readings.filter { it.readingType == "ja_on" }.map { it.reading }.joinToString(", ") + "\n" +
                kanji.readings.filter { it.readingType == "ja_kun" }.map { it.reading }.joinToString(", ")

fun getKanjiMeanings(kanji: Kanji) =
        kanji.meanings.joinToString(", ")

fun Kanji.getQuestionText(quizzType: QuizzType): String =
        when (quizzType) {
            QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING -> this.kanji
            QuizzType.READING_TO_KANJI -> getKanjiReadings(this)
            QuizzType.MEANING_TO_KANJI -> getKanjiMeanings(this)
        }

fun Kanji.getAnswerText(quizzType: QuizzType): String =
        when (quizzType) {
            QuizzType.KANJI_TO_READING -> getKanjiReadings(this)
            QuizzType.KANJI_TO_MEANING -> getKanjiMeanings(this)
            QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI -> this.kanji
        }
