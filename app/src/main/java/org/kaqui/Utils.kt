package org.kaqui

fun getKanjiDescription(kanji: Kanji) =
        kanji.readings.filter { it.readingType == "ja_on" }.map { it.reading }.joinToString(", ") + "\n" +
                kanji.readings.filter { it.readingType == "ja_kun" }.map { it.reading }.joinToString(", ") + "\n" +
                kanji.meanings.joinToString(", ")
