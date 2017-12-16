package org.kaqui.data

data class RawKana(val kana: String, val romaji: String)
data class SimilarKana(val kana: String, val similar: String)

fun getHiraganas() = arrayOf(
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
        RawKana("ん", "n"),

        RawKana("が", "ga"),
        RawKana("ぎ", "gi"),
        RawKana("ぐ", "gu"),
        RawKana("げ", "ge"),
        RawKana("ご", "go"),
        RawKana("ざ", "za"),
        RawKana("じ", "ji"),
        RawKana("ず", "zu"),
        RawKana("ぜ", "ze"),
        RawKana("ぞ", "zo"),
        RawKana("だ", "da"),
        RawKana("ぢ", "ji"),
        RawKana("づ", "zu"),
        RawKana("で", "de"),
        RawKana("ど", "do"),
        RawKana("ば", "ba"),
        RawKana("び", "bi"),
        RawKana("ぶ", "bu"),
        RawKana("べ", "be"),
        RawKana("ぼ", "bo"),

        RawKana("ぱ", "pa"),
        RawKana("ぴ", "pi"),
        RawKana("ぷ", "pu"),
        RawKana("ぺ", "pe"),
        RawKana("ぽ", "po")
)

fun getSimilarHiraganas() = arrayOf(
        SimilarKana("あ", "お"),
        SimilarKana("あ", "や" /* ヤ */),

        SimilarKana("い", "り"),

        SimilarKana("う", "つ"),
        SimilarKana("う", "ら" /* ラ */),

        SimilarKana("き", "さ"),
        SimilarKana("ぎ", "ざ"),
        SimilarKana("き", "ま"),

        SimilarKana("く", "へ"),
        SimilarKana("ぐ", "べ"),

        SimilarKana("せ", "さ" /* サ */),
        SimilarKana("ぜ", "ざ" /* ザ */),

        SimilarKana("さ", "ち"),
        SimilarKana("ざ", "ぢ"),

        SimilarKana("し", "れ" /* レ */),

        SimilarKana("す", "お"),
        SimilarKana("す", "む"),

        SimilarKana("そ", "ろ"),

        SimilarKana("つ", "ふ" /* フ */),
        SimilarKana("づ", "ぶ" /* ブ */),

        SimilarKana("の", "め"),
        SimilarKana("の", "ぬ"),
        SimilarKana("め", "ぬ"),

        SimilarKana("わ", "ね"),
        SimilarKana("わ", "れ"),
        SimilarKana("ね", "れ"),

        SimilarKana("ま", "は"),
        SimilarKana("ま", "ほ"),
        SimilarKana("は", "ほ"),
        SimilarKana("ば", "ぼ"),

        SimilarKana("る", "ろ")
)

fun getKatakanas() = arrayOf(
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
        RawKana("ン", "n"),

        RawKana("ガ", "ga"),
        RawKana("ギ", "gi"),
        RawKana("グ", "gu"),
        RawKana("ゲ", "ge"),
        RawKana("ゴ", "go"),
        RawKana("ザ", "za"),
        RawKana("ジ", "ji"),
        RawKana("ズ", "zu"),
        RawKana("ゼ", "ze"),
        RawKana("ゾ", "zo"),
        RawKana("ダ", "da"),
        RawKana("ヂ", "ji"),
        RawKana("ヅ", "zu"),
        RawKana("デ", "de"),
        RawKana("ド", "do"),
        RawKana("バ", "ba"),
        RawKana("ビ", "bi"),
        RawKana("ブ", "bu"),
        RawKana("ベ", "be"),
        RawKana("ボ", "bo"),

        RawKana("パ", "pa"),
        RawKana("ピ", "pi"),
        RawKana("プ", "pu"),
        RawKana("ペ", "pe"),
        RawKana("ポ", "po")
)

fun getSimilarKatakanas() = arrayOf(
        SimilarKana("ア", "ヤ"),

        SimilarKana("ウ", "ワ"),
        SimilarKana("ラ", "ウ" /* う */),

        SimilarKana("オ", "ホ"),

        SimilarKana("ク", "ケ"),
        SimilarKana("ク", "タ"),
        SimilarKana("グ", "ゲ"),
        SimilarKana("グ", "ダ"),

        SimilarKana("コ", "ユ"),

        SimilarKana("サ", "セ" /* せ */),
        SimilarKana("ザ", "ゼ" /* ぜ */),

        SimilarKana("シ", "ツ"),
        SimilarKana("シ", "ソ"),
        SimilarKana("シ", "ノ"),
        SimilarKana("シ", "ン"),
        SimilarKana("ツ", "ソ"),
        SimilarKana("ツ", "ノ"),
        SimilarKana("ツ", "ン"),
        SimilarKana("ソ", "ノ"),
        SimilarKana("ソ", "ン"),
        SimilarKana("ノ", "ン"),

        SimilarKana("ジ", "ヅ"),
        SimilarKana("ジ", "ゾ"),
        SimilarKana("ヅ", "ゾ"),

        SimilarKana("セ", "ヒ"),
        SimilarKana("ゼ", "ビ"),

        SimilarKana("チ", "テ"),
        SimilarKana("ヂ", "デ"),

        SimilarKana("ナ", "メ"),

        SimilarKana("フ", "ツ" /* つ */),
        SimilarKana("ブ", "ヅ" /* づ */),

        SimilarKana("レ", "シ" /* し */)
)
