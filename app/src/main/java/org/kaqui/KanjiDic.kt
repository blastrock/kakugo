package org.kaqui

import java.io.BufferedReader

data class Kanji(
        var id: Int,
        var kanji: String,
        var readings: List<Reading>,
        var meanings: List<String>,
        var similarities: List<Kanji>,
        var jlptLevel: Int,
        var weight: Double,
        var enabled: Boolean
)

data class Reading(var readingType: String, var reading: String)

private enum class PartType {
    Unknown,
    Kanji,
    KatakanaReading,
    HiraganaReading,
    Meaning,
    Similarities,
    Frequency,
}

private fun letterToPartType(letter: Char): PartType {
    return when (letter) {
        'F' -> PartType.Frequency
        'a' -> PartType.Kanji
        'k' -> PartType.KatakanaReading
        'h' -> PartType.HiraganaReading
        'm' -> PartType.Meaning
        's' -> PartType.Similarities
        else -> PartType.Unknown
    }
}

fun getJlptLevel(jlptLevels: Map<Int, String>, kanji: Char): Int {
    var jlptLevel = 0
    for ((level, kanjis) in jlptLevels) {
        if (kanji in kanjis) {
            jlptLevel = level
            break
        }
    }
    return jlptLevel
}

fun lineToKanji(levels: Map<Int, String>, line: String): Kanji? {
    if (line[0] == '#')
        return null

    val parts = line.split(" ").map { part ->
        letterToPartType(part[0]) to part.slice(1..(part.length - 1))
    }

    if (parts.filter { it.first == PartType.Frequency }.count() == 0)
        return null

    val literal = parts.filter { it.first == PartType.Kanji }.first().second.first()

    return Kanji(
            0,
            literal.toString(),
            parts.filter { it.first == PartType.KatakanaReading || it.first == PartType.HiraganaReading }
                    .map { Reading(if (it.first == PartType.HiraganaReading) "ja_kun" else "ja_on", it.second) },
            parts.filter { it.first == PartType.Meaning }.map { it.second.replace('_', ' ') },
            parts.filter { it.first == PartType.Similarities }.map { Kanji(0, it.second[0].toString(), listOf(), listOf(), listOf(), 0, 0.0, false) },
            getJlptLevel(levels, literal),
            0.0,
            true)
}

fun parseFile(stream: BufferedReader): List<Kanji> {
    val levels = getJlptLevels()
    return stream.lineSequence().map { lineToKanji(levels, it) }.filterNotNull().toList()
}
