package org.kaqui

fun getItemText(item: Item) =
        when (item.contents) {
            is Kana -> {
                val kana = item.contents as Kana
                kana.kana
            }
            is Kanji -> {
                val kanji = item.contents as Kanji
                kanji.kanji
            }
        }

fun getItemDescription(item: Item) =
        when (item.contents) {
            is Kana -> {
                val kana = item.contents as Kana
                kana.romaji
            }
            is Kanji -> {
                val kanji = item.contents as Kanji
                kanji.readings.filter { it.readingType == "ja_on" }.joinToString(", ", transform = { it.reading }) + "\n" +
                        kanji.readings.filter { it.readingType == "ja_kun" }.joinToString(", ", transform = { it.reading }) + "\n" +
                        kanji.meanings.joinToString(", ")
            }
        }

fun getKanjiReadings(kanji: Kanji) =
        kanji.readings.filter { it.readingType == "ja_on" }.joinToString(", ", transform = { it.reading }) + "\n" +
                kanji.readings.filter { it.readingType == "ja_kun" }.joinToString(", ", transform = { it.reading })

fun getKanjiMeanings(kanji: Kanji) =
        kanji.meanings.joinToString(", ")

fun Item.getQuestionText(quizzType: QuizzType): String =
        when (quizzType) {
            QuizzType.HIRAGANA_TO_ROMAJI -> (this.contents as Kana).kana
            QuizzType.ROMAJI_TO_HIRAGANA -> (this.contents as Kana).romaji
            QuizzType.KANJI_TO_READING, QuizzType.KANJI_TO_MEANING -> (this.contents as Kanji).kanji
            QuizzType.READING_TO_KANJI -> getKanjiReadings(this.contents as Kanji)
            QuizzType.MEANING_TO_KANJI -> getKanjiMeanings(this.contents as Kanji)
        }

fun Item.getAnswerText(quizzType: QuizzType): String =
        when (quizzType) {
            QuizzType.HIRAGANA_TO_ROMAJI -> (this.contents as Kana).romaji
            QuizzType.ROMAJI_TO_HIRAGANA -> (this.contents as Kana).kana
            QuizzType.KANJI_TO_READING -> getKanjiReadings(this.contents as Kanji)
            QuizzType.KANJI_TO_MEANING -> getKanjiMeanings(this.contents as Kanji)
            QuizzType.READING_TO_KANJI, QuizzType.MEANING_TO_KANJI -> (this.contents as Kanji).kanji
        }

fun getBackgroundFromScore(score: Double) =
        when (score) {
            in 0.0f..BAD_WEIGHT -> R.drawable.round_red
            in BAD_WEIGHT..GOOD_WEIGHT -> R.drawable.round_yellow
            in GOOD_WEIGHT..1.0f -> R.drawable.round_green
            else -> R.drawable.round_red
        }