package org.kaqui.data

data class RawKana(val kana: String, val romaji: String)

val Hiraganas = arrayOf(
        RawKana("あ", "a"),
        RawKana("い", "i"),
        RawKana("う", "u"),
        RawKana("え", "e"),
        RawKana("お", "o"),
        RawKana("か", "ka"),
        RawKana("き", "ki"),
        RawKana("く", "ku"),
        RawKana("け", "ke"),
        RawKana("こ", "ko"),
        RawKana("さ", "sa"),
        RawKana("し", "shi"),
        RawKana("す", "su"),
        RawKana("せ", "se"),
        RawKana("そ", "so"),
        RawKana("た", "ta"),
        RawKana("ち", "chi"),
        RawKana("つ", "tsu"),
        RawKana("て", "te"),
        RawKana("と", "to"),
        RawKana("な", "na"),
        RawKana("に", "ni"),
        RawKana("ぬ", "nu"),
        RawKana("ね", "ne"),
        RawKana("の", "no"),
        RawKana("は", "ha"),
        RawKana("ひ", "hi"),
        RawKana("ふ", "fu"),
        RawKana("へ", "he"),
        RawKana("ほ", "ho"),
        RawKana("ま", "ma"),
        RawKana("み", "mi"),
        RawKana("む", "mu"),
        RawKana("め", "me"),
        RawKana("も", "mo"),
        RawKana("や", "ya"),
        RawKana("ゆ", "yu"),
        RawKana("よ", "yo"),
        RawKana("ら", "ra"),
        RawKana("り", "ri"),
        RawKana("る", "ru"),
        RawKana("れ", "re"),
        RawKana("ろ", "ro"),
        RawKana("わ", "wa"),
        RawKana("を", "wo"),
        RawKana("ん", "n")
)

data class SimilarKana(val kana: String, val similar: String)

val SimilarHiraganas = arrayOf(
        SimilarKana("a", "o"),
        SimilarKana("a", "ya"), // katakana

        SimilarKana("i", "ri"),

        SimilarKana("u", "tsu"),
        SimilarKana("u", "ra"), // katakana

        SimilarKana("ki", "sa"),
        SimilarKana("ki", "ma"),

        SimilarKana("ku", "he"),

        SimilarKana("se", "sa"), // katakana

        SimilarKana("sa", "chi"),

        SimilarKana("su", "o"),
        SimilarKana("su", "mu"),

        SimilarKana("so", "ro"),

        SimilarKana("no", "me"),
        SimilarKana("no", "nu"),
        SimilarKana("me", "nu"),

        SimilarKana("wa", "ne"),
        SimilarKana("wa", "re"),
        SimilarKana("ne", "re"),

        SimilarKana("fu", "tsu"), // katakana

        SimilarKana("ma", "ha"),
        SimilarKana("ma", "ho"),
        SimilarKana("ha", "ho"),

        SimilarKana("ru", "ro")
)