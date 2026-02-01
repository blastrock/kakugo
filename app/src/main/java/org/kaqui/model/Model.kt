package org.kaqui.model

val HiraganaRange = 0x3040..0x309F
val KatakanaRange = 0x30A0..0x30FF
const val WordBaseId = 0x1000000

sealed class ItemContents

data class Kanji(
        var kanji: String,
        var on_readings: List<String>,
        var kun_readings: List<String>,
        var meanings: List<String>,
        var similarities: List<Item>,
        var parts: List<Item>,
        var jlptLevel: Int
) : ItemContents()

data class Kana(
        var kana: String,
        var romaji: String,
        var uniqueRomaji: String,
        var similarities: List<Item>
) : ItemContents()

data class Word(
        var word: String,
        var reading: String,
        var meanings: List<String>,
        var similarities: List<Item>,
        var kanaAlone: Boolean,
        var expr: String,
) : ItemContents()

data class Item(
        var id: Int,
        var contents: ItemContents,
        var shortScore: Double,
        var longScore: Double,
        var lastAsked: Long,
        var enabled: Boolean
)

val Item.similarities: List<Item>
    get() = when (contents) {
        is Kana -> (contents as Kana).similarities
        is Kanji -> (contents as Kanji).similarities
        is Word -> (contents as Word).similarities
    }

enum class ItemType(val value: Int) {
    Hiragana(1),
    Katakana(2),
    Kanji(3),
    Word(4);

    companion object {
        private val map = entries.associateBy(ItemType::value)
        fun fromInt(type: Int) = map.getValue(type)
    }
}

fun getItemType(testType: TestType) =
        when (testType) {
            TestType.HIRAGANA_TO_ROMAJI, TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_HIRAGANA, TestType.HIRAGANA_DRAWING,
            -> ItemType.Hiragana

            TestType.KATAKANA_TO_ROMAJI, TestType.KATAKANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_KATAKANA, TestType.KATAKANA_DRAWING,
            -> ItemType.Katakana

            TestType.KANJI_TO_READING, TestType.READING_TO_KANJI,
            TestType.KANJI_TO_MEANING, TestType.MEANING_TO_KANJI,
            TestType.KANJI_DRAWING, TestType.KANJI_COMPOSITION,
            -> ItemType.Kanji

            TestType.WORD_TO_READING, TestType.READING_TO_WORD, TestType.WORD_TO_MEANING, TestType.MEANING_TO_WORD
            -> ItemType.Word
        }

enum class KnowledgeType(val value: Int) {
    Reading(1),
    Meaning(2),
    Strokes(3);

    companion object {
        private val map = entries.associateBy(KnowledgeType::value)
        fun fromInt(type: Int) = map.getValue(type)
    }
}

fun getKnowledgeType(testType: TestType) =
        when (testType) {
            TestType.HIRAGANA_TO_ROMAJI, TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_HIRAGANA,
            TestType.KATAKANA_TO_ROMAJI, TestType.KATAKANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_KATAKANA,
            TestType.KANJI_TO_READING, TestType.READING_TO_KANJI,
            TestType.WORD_TO_READING, TestType.READING_TO_WORD -> KnowledgeType.Reading

            TestType.HIRAGANA_DRAWING, TestType.KATAKANA_DRAWING, TestType.KANJI_DRAWING, TestType.KANJI_COMPOSITION -> KnowledgeType.Strokes

            TestType.KANJI_TO_MEANING, TestType.MEANING_TO_KANJI,
            TestType.WORD_TO_MEANING, TestType.MEANING_TO_WORD -> KnowledgeType.Meaning
        }

fun itemAndKnowledgeTypeToTestType(itemType: ItemType, knowledgeType: KnowledgeType): List<TestType> =
    when (itemType) {
        ItemType.Hiragana ->
            when (knowledgeType) {
                KnowledgeType.Reading -> listOf(TestType.HIRAGANA_TO_ROMAJI, TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_HIRAGANA)
                KnowledgeType.Meaning -> throw RuntimeException("invalid itemType/knowledgeType combination ($itemType, $knowledgeType)")
                KnowledgeType.Strokes -> listOf(TestType.HIRAGANA_DRAWING)
            }
        ItemType.Katakana ->
            when (knowledgeType) {
                KnowledgeType.Reading -> listOf(TestType.KATAKANA_TO_ROMAJI, TestType.KATAKANA_TO_ROMAJI_TEXT, TestType.ROMAJI_TO_KATAKANA)
                KnowledgeType.Meaning -> throw RuntimeException("invalid itemType/knowledgeType combination ($itemType, $knowledgeType)")
                KnowledgeType.Strokes -> listOf(TestType.KATAKANA_DRAWING)
            }
        ItemType.Kanji ->
            when (knowledgeType) {
                KnowledgeType.Reading -> listOf(TestType.KANJI_TO_READING, TestType.READING_TO_KANJI)
                KnowledgeType.Meaning -> listOf(TestType.KANJI_TO_MEANING, TestType.MEANING_TO_KANJI)
                KnowledgeType.Strokes -> listOf(TestType.KANJI_DRAWING, TestType.KANJI_COMPOSITION)
            }
        ItemType.Word ->
            when (knowledgeType) {
                KnowledgeType.Reading -> listOf(TestType.WORD_TO_READING, TestType.READING_TO_WORD)
                KnowledgeType.Meaning -> listOf(TestType.WORD_TO_MEANING, TestType.MEANING_TO_WORD)
                KnowledgeType.Strokes -> throw RuntimeException("invalid itemType/knowledgeType combination ($itemType, $knowledgeType)")
            }
    }

fun Item.getQuestionText(testType: TestType, kanaWords: Boolean): String =
        when (testType) {
            TestType.HIRAGANA_TO_ROMAJI, TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.KATAKANA_TO_ROMAJI, TestType.KATAKANA_TO_ROMAJI_TEXT -> (contents as Kana).kana
            TestType.ROMAJI_TO_HIRAGANA, TestType.ROMAJI_TO_KATAKANA -> (contents as Kana).romaji
            TestType.HIRAGANA_DRAWING, TestType.KATAKANA_DRAWING -> {
                val kana = contents as Kana
                if (kana.romaji in arrayOf("ji", "zu"))
                    "${kana.romaji} (${kana.uniqueRomaji})"
                else
                    kana.romaji
            }

            TestType.KANJI_TO_READING, TestType.KANJI_TO_MEANING -> (contents as Kanji).kanji
            TestType.READING_TO_KANJI -> (contents as Kanji).readingsText
            TestType.MEANING_TO_KANJI -> (contents as Kanji).meaningsText

            TestType.WORD_TO_READING, TestType.WORD_TO_MEANING -> this.text(kanaWords)
            TestType.READING_TO_WORD -> (contents as Word).reading
            TestType.MEANING_TO_WORD -> (contents as Word).meaningsText

            TestType.KANJI_DRAWING, TestType.KANJI_COMPOSITION -> "${(contents as Kanji).readingsText}\n${(contents as Kanji).meaningsText}"
        }

fun Item.getAnswerText(testType: TestType, kanaWords: Boolean): String =
        when (testType) {
            TestType.HIRAGANA_TO_ROMAJI, TestType.HIRAGANA_TO_ROMAJI_TEXT, TestType.KATAKANA_TO_ROMAJI, TestType.KATAKANA_TO_ROMAJI_TEXT -> (contents as Kana).romaji
            TestType.ROMAJI_TO_HIRAGANA, TestType.ROMAJI_TO_KATAKANA -> (contents as Kana).kana

            TestType.KANJI_TO_READING -> (contents as Kanji).readingsText
            TestType.KANJI_TO_MEANING -> (contents as Kanji).meaningsText
            TestType.READING_TO_KANJI, TestType.MEANING_TO_KANJI, TestType.KANJI_COMPOSITION -> (contents as Kanji).kanji

            TestType.WORD_TO_READING -> (contents as Word).reading
            TestType.WORD_TO_MEANING -> (contents as Word).meaningsText
            TestType.READING_TO_WORD, TestType.MEANING_TO_WORD -> this.text(kanaWords)

            TestType.KANJI_DRAWING, TestType.HIRAGANA_DRAWING, TestType.KATAKANA_DRAWING -> throw RuntimeException("No answer text for writing test")
        }

val Kana.text: String
    get() = kana

val Kana.description: String
    get() = romaji

val Kanji.readingsText: String
    get() = """
        ${on_readings.joinToString(", ")}
        ${kun_readings.joinToString(", ")}""".trimIndent()

val Kanji.meaningsText: String
    get() = meanings.joinToString(", ")

val Kanji.text: String
    get() = kanji

val Kanji.description: String
    get() = """
        ${on_readings.joinToString(", ")}
        ${kun_readings.joinToString(", ")}
        ${meanings.joinToString(", ")}""".trimIndent()

val Word.meaningsText: String
    get() = meanings.joinToString(", ")

fun Word.text(kanaWords: Boolean): String =
    if (kanaAlone && kanaWords)
        reading
    else
        word

val Word.description: String
    get() = """
        $reading
        ${meanings.joinToString(", ")}""".trimIndent()

fun Item.text(kanaWords: Boolean): String =
    when (val contents = contents) {
        is Kana -> {
            contents.text
        }
        is Kanji -> {
            contents.text
        }
        is Word -> {
            contents.text(kanaWords)
        }
    }

val Item.description: String
    get() = when (val contents = contents) {
        is Kana -> {
            contents.description
        }
        is Kanji -> {
            contents.description
        }
        is Word -> {
            contents.description
        }
    }

fun getAnswerCount(testType: TestType) =
        when (testType) {
            TestType.KANJI_COMPOSITION -> 9
            else -> 6
        }

