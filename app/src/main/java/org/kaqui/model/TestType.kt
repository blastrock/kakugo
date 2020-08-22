package org.kaqui.model

enum class TestType(val value: Int) {
    HIRAGANA_TO_ROMAJI(1),
    HIRAGANA_TO_ROMAJI_TEXT(2),
    ROMAJI_TO_HIRAGANA(3),

    HIRAGANA_DRAWING(4),

    KATAKANA_TO_ROMAJI(5),
    KATAKANA_TO_ROMAJI_TEXT(6),
    ROMAJI_TO_KATAKANA(7),

    KATAKANA_DRAWING(8),

    KANJI_TO_READING(9),
    READING_TO_KANJI(10),
    KANJI_TO_MEANING(11),
    MEANING_TO_KANJI(12),

    WORD_TO_READING(13),
    READING_TO_WORD(14),
    WORD_TO_MEANING(15),
    MEANING_TO_WORD(16),

    KANJI_DRAWING(17),

    KANJI_COMPOSITION(18);

    companion object {
        private val map = values().associateBy(TestType::value)
        fun fromInt(type: Int) = map.getValue(type)
    }
}
