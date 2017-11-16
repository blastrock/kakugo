package org.kaqui.data

data class RawKana(val kana: String, val romaji: String)
data class SimilarKana(val kana: String, val similar: String)

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

        SimilarKana("shi", "re"), // katakana

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

val Katakanas = arrayOf(
        RawKana("ア", "a"),
        RawKana("イ", "i"),
        RawKana("ウ", "u"),
        RawKana("エ", "e"),
        RawKana("オ", "o"),
        RawKana("カ", "ka"),
        RawKana("キ", "ki"),
        RawKana("ク", "ku"),
        RawKana("ケ", "ke"),
        RawKana("コ", "ko"),
        RawKana("サ", "sa"),
        RawKana("シ", "shi"),
        RawKana("ス", "su"),
        RawKana("セ", "se"),
        RawKana("ソ", "so"),
        RawKana("タ", "ta"),
        RawKana("チ", "chi"),
        RawKana("ツ", "tsu"),
        RawKana("テ", "te"),
        RawKana("ト", "to"),
        RawKana("ナ", "na"),
        RawKana("ニ", "ni"),
        RawKana("ヌ", "nu"),
        RawKana("ネ", "ne"),
        RawKana("ノ", "no"),
        RawKana("ハ", "ha"),
        RawKana("ヒ", "hi"),
        RawKana("フ", "fu"),
        RawKana("ヘ", "he"),
        RawKana("ホ", "ho"),
        RawKana("マ", "ma"),
        RawKana("ミ", "mi"),
        RawKana("ム", "mu"),
        RawKana("メ", "me"),
        RawKana("モ", "mo"),
        RawKana("ヤ", "ya"),
        RawKana("ユ", "yu"),
        RawKana("ヨ", "yo"),
        RawKana("ラ", "ra"),
        RawKana("リ", "ri"),
        RawKana("ル", "ru"),
        RawKana("レ", "re"),
        RawKana("ロ", "ro"),
        RawKana("ワ", "wa"),
        RawKana("ヲ", "wo"),
        RawKana("ン", "n")
)

val SimilarKatakanas = arrayOf(
        SimilarKana("a", "ya"),

        SimilarKana("u", "wa"),
        SimilarKana("u", "ra"), // hiragana

        SimilarKana("o", "ho"),

        SimilarKana("ku", "ke"),
        SimilarKana("ku", "ta"),

        SimilarKana("ko", "yu"),

        SimilarKana("se", "sa"), // hiragana

        SimilarKana("shi", "tsu"),
        SimilarKana("shi", "so"),
        SimilarKana("shi", "no"),
        SimilarKana("shi", "n"),
        SimilarKana("tsu", "so"),
        SimilarKana("tsu", "no"),
        SimilarKana("tsu", "n"),
        SimilarKana("so", "no"),
        SimilarKana("so", "n"),
        SimilarKana("no", "n"),

        SimilarKana("se", "hi"),

        SimilarKana("chi", "te"),

        SimilarKana("na", "me"),

        SimilarKana("fu", "tsu"), // hiragana

        SimilarKana("re", "shi") // hiragana
)
