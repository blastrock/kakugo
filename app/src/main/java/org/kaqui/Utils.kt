package org.kaqui

fun getKanjiDescription(kanji: Kanji) =
        kanji.readings.filter { it.readingType == "ja_on" }.joinToString(", ", transform = { it.reading }) + "\n" +
                kanji.readings.filter { it.readingType == "ja_kun" }.joinToString(", ", transform = { it.reading }) + "\n" +
                kanji.meanings.joinToString(", ")

fun getKanjiReadings(kanji: Kanji) =
        kanji.readings.filter { it.readingType == "ja_on" }.joinToString(", ", transform = { it.reading }) + "\n" +
                kanji.readings.filter { it.readingType == "ja_kun" }.joinToString(", ", transform = { it.reading })

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

fun getBackgroundFromScore(score: Double) =
        when (score) {
            in 0.0f..BAD_WEIGHT -> R.drawable.round_red
            in BAD_WEIGHT..GOOD_WEIGHT -> R.drawable.round_yellow
            in GOOD_WEIGHT..1.0f -> R.drawable.round_green
            else -> R.drawable.round_red
        }