package org.kaqui.model

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Path
import org.kaqui.LocaleManager
import org.kaqui.asUnicodeCodePoint

class Database private constructor(context: Context, private val database: SQLiteDatabase) {
    private val locale: String get() = LocaleManager.getDictionaryLocale()

    init {
        // the app can be restored from android without going through the main activity,
        // we need a locale here though and we can't try to fetch it every time we need it
        if (!LocaleManager.isReady())
            LocaleManager.updateDictionaryLocale(context)
    }

    fun getHiraganaView(knowledgeType: KnowledgeType? = null) =
            LearningDbView(database, KANAS_TABLE_NAME, knowledgeType, filter = "$KANAS_TABLE_NAME.id BETWEEN ${HiraganaRange.first} AND ${HiraganaRange.last}", itemGetter = this::getKana)

    fun getKatakanaView(knowledgeType: KnowledgeType? = null) =
            LearningDbView(database, KANAS_TABLE_NAME, knowledgeType, filter = "$KANAS_TABLE_NAME.id BETWEEN ${KatakanaRange.first} AND ${KatakanaRange.last}", itemGetter = this::getKana)

    fun getKanjiView(knowledgeType: KnowledgeType? = null, classifier: Classifier? = null): LearningDbView =
            LearningDbView(database, KANJIS_TABLE_NAME, knowledgeType, filter = "radical = 0", classifier = classifier, itemGetter = this::getKanji, itemSearcher = this::searchKanji)

    fun getWordView(knowledgeType: KnowledgeType? = null, classifier: Classifier? = null): LearningDbView =
            LearningDbView(database, WORDS_TABLE_NAME, knowledgeType, classifier = classifier, itemGetter = this::getWord, itemSearcher = this::searchWord)

    fun getOtherCompositionAnswerIds(kanjiId: Int): List<Int> {
        database.rawQuery("""
            SELECT c3.id_kanji2
            FROM $KANJIS_COMPOSITION_TABLE_NAME c1
            JOIN $KANJIS_COMPOSITION_TABLE_NAME c2 ON c1.id_kanji2 = c2.id_kanji2
            JOIN $KANJIS_TABLE_NAME k2 ON c2.id_kanji1 = k2.id AND k2.enabled = 1
            JOIN $KANJIS_COMPOSITION_TABLE_NAME c3 ON c2.id_kanji1 = c3.id_kanji1
            WHERE c1.id_kanji1 = ?
                UNION
            SELECT c.id_kanji1
            FROM $KANJIS_COMPOSITION_TABLE_NAME c
            JOIN $KANJIS_TABLE_NAME k ON c.id_kanji1 = k.id AND k.enabled = 1
            WHERE c.id_kanji2 = ?
            """, arrayOf(kanjiId.toString(), kanjiId.toString())).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    fun getSimilarCompositionAnswerIds(kanjiId: Int): List<Int> {
        database.rawQuery("""
            SELECT DISTINCT c.id_kanji2
            FROM $SIMILAR_ITEMS_TABLE_NAME s
            JOIN $KANJIS_TABLE_NAME sk ON s.id_item2 = sk.id AND sk.enabled = 1
            JOIN $KANJIS_COMPOSITION_TABLE_NAME c ON c.id_kanji1 = s.id_item2
            WHERE s.id_item1 = ?
            """, arrayOf(kanjiId.toString())).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    private fun searchKanji(text: String): List<Int> {
        val firstCodePoint =
                if (text.isNotEmpty())
                    text.codePointAt(0).toString()
                else
                    ""
        database.rawQuery(
                """SELECT id
                FROM $KANJIS_TABLE_NAME
                WHERE (id = ? OR on_readings LIKE ? OR kun_readings LIKE ? OR (meanings_$locale <> '' AND meanings_$locale LIKE ? OR meanings_$locale == '' AND meanings_en LIKE ?)) AND radical = 0""",
                arrayOf(firstCodePoint, "%$text%", "%$text%", "%$text%", "%$text%")).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    private fun searchWord(text: String): List<Int> {
        database.rawQuery(
                """SELECT id
                FROM $WORDS_TABLE_NAME
                WHERE item LIKE ? OR reading LIKE ? OR (meanings_$locale <> '' AND meanings_$locale LIKE ? OR meanings_$locale == '' AND meanings_en LIKE ?)""",
                arrayOf("%$text%", "%$text%", "%$text%", "%$text%")).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    fun getStrokes(id: Int): List<Path> {
        val strokes = mutableListOf<Path>()
        database.query(ITEM_STROKES_TABLE_NAME, arrayOf("path"), "id_item = ?", arrayOf(id.toString()), null, null, "ordinal").use { cursor ->
            val PathParser = Class.forName("androidx.core.graphics.PathParser")
            val createPathFromPathData = PathParser.getMethod("createPathFromPathData", String::class.java)
            while (cursor.moveToNext()) {
                strokes.add(createPathFromPathData.invoke(null, cursor.getString(0)) as Path)
            }
        }
        return strokes
    }

    private fun getOnClause(knowledgeType: KnowledgeType?) =
            if (knowledgeType != null)
                " AND s.type = ${knowledgeType.value} "
            else
                ""

    private fun getKana(id: Int, knowledgeType: KnowledgeType?): Item {
        val similarities = mutableListOf<Item>()
        database.query(SIMILAR_ITEMS_TABLE_NAME, arrayOf("id_item2"), "id_item1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kana("", "", "", listOf()), 0.0, 0.0, 0, false))
        }
        val contents = Kana("", "", "", similarities)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        database.rawQuery("""
            SELECT romaji, MAX(ifnull(short_score, 0.0)), MAX(ifnull(long_score, 0.0)), ifnull(last_correct, 0), enabled, unique_romaji
            FROM $KANAS_TABLE_NAME k
            LEFT JOIN $ITEM_SCORES_TABLE_NAME s ON k.id = s.id ${getOnClause(knowledgeType)}
            WHERE k.id = $id
            GROUP BY k.id
            """, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kana with id $id")
            cursor.moveToFirst()
            contents.kana = id.asUnicodeCodePoint()
            contents.romaji = cursor.getString(0)
            contents.uniqueRomaji = cursor.getString(5)
            item.shortScore = cursor.getDouble(1)
            item.longScore = cursor.getDouble(2)
            item.lastAsked = cursor.getLong(3)
            item.enabled = cursor.getInt(4) != 0
        }
        return item
    }

    fun getKanji(id: Int, knowledgeType: KnowledgeType?): Item {
        val similarities = mutableListOf<Item>()
        database.query(SIMILAR_ITEMS_TABLE_NAME, arrayOf("id_item2"), "id_item1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kanji("", listOf(), listOf(), listOf(), listOf(), listOf(), 0), 0.0, 0.0, 0, false))
        }
        val parts = mutableListOf<Item>()
        database.query(KANJIS_COMPOSITION_TABLE_NAME, arrayOf("id_kanji2"), "id_kanji1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                parts.add(Item(cursor.getInt(0), Kanji("", listOf(), listOf(), listOf(), listOf(), listOf(), 0), 0.0, 0.0, 0, false))
        }
        val contents = Kanji("", listOf(), listOf(), listOf(), similarities, parts, 0)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        database.rawQuery("""
            SELECT jlpt_level, MAX(ifnull(short_score, 0.0)), MAX(ifnull(long_score, 0.0)), ifnull(last_correct, 0), enabled, on_readings, kun_readings, meanings_$locale, meanings_en
            FROM $KANJIS_TABLE_NAME k
            LEFT JOIN $ITEM_SCORES_TABLE_NAME s ON k.id = s.id ${getOnClause(knowledgeType)}
            WHERE k.id = $id
            GROUP BY k.id
            """, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id $id")
            cursor.moveToFirst()
            contents.kanji = id.asUnicodeCodePoint()
            contents.on_readings = cursor.getString(5).split('_')
            contents.kun_readings = cursor.getString(6).split('_')
            val localMeaning = cursor.getString(7)
            if (localMeaning != "")
                contents.meanings = localMeaning.split('_')
            else
                contents.meanings = cursor.getString(8).split('_')
            contents.jlptLevel = cursor.getInt(0)
            item.shortScore = cursor.getDouble(1)
            item.longScore = cursor.getDouble(2)
            item.lastAsked = cursor.getLong(3)
            item.enabled = cursor.getInt(4) != 0
        }
        return item
    }

    fun getWord(id: Int, knowledgeType: KnowledgeType?): Item {
        val contents = Word("", "", listOf(), listOf())
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        var similarityClass = 0
        database.rawQuery("""
            SELECT item, reading, meanings_$locale, MAX(ifnull(short_score, 0.0)), MAX(ifnull(long_score, 0.0)), ifnull(last_correct, 0), enabled, similarity_class, meanings_en
            FROM $WORDS_TABLE_NAME k
            LEFT JOIN $ITEM_SCORES_TABLE_NAME s ON k.id = s.id ${getOnClause(knowledgeType)}
            WHERE k.id = $id
            GROUP BY k.id
            """, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find word with id $id")
            cursor.moveToFirst()
            contents.word = cursor.getString(0)
            contents.reading = cursor.getString(1)
            val localMeaning = cursor.getString(2)
            if (localMeaning != "")
                contents.meanings = localMeaning.split('_')
            else
                contents.meanings = cursor.getString(8).split('_')
            item.shortScore = cursor.getDouble(3)
            item.longScore = cursor.getDouble(4)
            item.lastAsked = cursor.getLong(5)
            item.enabled = cursor.getInt(6) != 0
            similarityClass = cursor.getInt(7)
        }
        val similarWords = mutableListOf<Item>()
        database.query(WORDS_TABLE_NAME, arrayOf("id"),
                "similarity_class = ? AND id <> ?", arrayOf(similarityClass.toString(), id.toString()),
                null, null, "RANDOM()", "20").use { cursor ->
            while (cursor.moveToNext())
                similarWords.add(Item(cursor.getInt(0), Word("", "", listOf(), listOf()), 0.0, 0.0, 0, false))
        }
        contents.similarities = similarWords
        return item
    }

    fun setSelection(kanjis: String) {
        database.beginTransaction()
        try {
            val cv = ContentValues()
            cv.put("enabled", false)
            database.update(KANJIS_TABLE_NAME, cv, null, null)
            cv.put("enabled", true)
            for (i in 0 until kanjis.codePointCount(0, kanjis.length)) {
                database.update(KANJIS_TABLE_NAME, cv, "id = ?", arrayOf(kanjis.codePointAt(i).toString()))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun autoSelectWords() {
        val enabledKanjis = HashSet<Char>()
        database.query(KANJIS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                enabledKanjis.add(Character.toChars(cursor.getInt(0))[0])
            }
        }
        var allWords =
                database.query(WORDS_TABLE_NAME, arrayOf("id, item"), null, null, null, null, null).use { cursor ->
                    val ret = mutableListOf<Pair<Long, String>>()
                    while (cursor.moveToNext()) {
                        ret.add(Pair(cursor.getLong(0), cursor.getString(1)))
                    }
                    ret.toList()
                }
        allWords = allWords.map { Pair(it.first, it.second.filter { isKanji(it) }) }
        allWords = allWords.filter { it.second.all { it in enabledKanjis } }

        database.beginTransaction()
        try {
            val cv = ContentValues()
            cv.put("enabled", false)
            database.update(WORDS_TABLE_NAME, cv, null, null)
            cv.put("enabled", true)
            for (word in allWords) {
                database.update(WORDS_TABLE_NAME, cv, "id = ?", arrayOf(word.first.toString()))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    data class KanjiSelection(
            val id: Long,
            val name: String,
            val count: Int
    )

    fun listKanjiSelections(): List<KanjiSelection> {
        val out = mutableListOf<KanjiSelection>()
        database.rawQuery("""
            SELECT id_selection, name, (
                SELECT COUNT(*)
                FROM $KANJIS_ITEM_SELECTION_TABLE_NAME ss WHERE ss.id_selection = s.id_selection
            )
            FROM $KANJIS_SELECTION_TABLE_NAME s
            """, arrayOf()).use { cursor ->
            while (cursor.moveToNext())
                out.add(KanjiSelection(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)))
        }
        return out
    }

    fun saveKanjiSelectionTo(name: String) {
        database.beginTransaction()
        try {
            val idSelection = database.query(KANJIS_SELECTION_TABLE_NAME, arrayOf("id_selection"), "name = ?", arrayOf(name), null, null, null).use { cursor ->
                if (cursor.count == 0) {
                    val cv = ContentValues()
                    cv.put("name", name)
                    return@use database.insert(KANJIS_SELECTION_TABLE_NAME, null, cv)
                } else {
                    cursor.moveToFirst()
                    return@use cursor.getInt(0)
                }
            }
            database.delete(KANJIS_ITEM_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            database.execSQL("""
            INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)
            SELECT ?, id FROM $KANJIS_TABLE_NAME WHERE enabled = 1
            """, arrayOf(idSelection.toString()))
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun restoreKanjiSelectionFrom(idSelection: Long) {
        database.beginTransaction()
        try {
            database.execSQL("""
                UPDATE $KANJIS_TABLE_NAME
                SET enabled = 0
                """)
            database.execSQL("""
                UPDATE $KANJIS_TABLE_NAME
                SET enabled = 1
                WHERE id IN (
                    SELECT id_kanji
                    FROM $KANJIS_ITEM_SELECTION_TABLE_NAME
                    WHERE id_selection = ?
                )
                """, arrayOf(idSelection.toString()))
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun deleteKanjiSelection(idSelection: Long) {
        database.beginTransaction()
        try {
            database.delete(KANJIS_ITEM_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            database.delete(KANJIS_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    companion object {
        private const val TAG = "Database"

        const val DATABASE_NAME = "kanjis"

        const val KANAS_TABLE_NAME = "kanas"

        const val KANJIS_TABLE_NAME = "kanjis"
        const val KANJIS_COMPOSITION_TABLE_NAME = "kanjis_composition"

        const val SIMILAR_ITEMS_TABLE_NAME = "similar_items"
        const val ITEM_STROKES_TABLE_NAME = "item_strokes"

        const val ITEM_SCORES_TABLE_NAME = "item_scores"

        const val KANJIS_SELECTION_TABLE_NAME = "kanjis_selection"
        const val KANJIS_ITEM_SELECTION_TABLE_NAME = "kanjis_item_selection"

        const val WORDS_TABLE_NAME = "words"

        private var singleton: Database? = null

        private fun isKanji(c: Char): Boolean {
            // This is the hiragana/katakana range
            return c.toInt() !in 0x3040..0x3100
        }

        fun getInstance(context: Context): Database {
            if (singleton == null) {
                val db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                singleton = Database(context, db)
            }
            return singleton!!
        }
    }
}
