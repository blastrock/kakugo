package org.kaqui.model

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Path
import android.os.Build
import android.util.Log
import org.kaqui.data.*

class Database private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DatabaseUpdater.DATABASE_VERSION) {
    private val locale: String by lazy {
        val locale =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    context.resources.configuration.locales.getFirstMatch(arrayOf("fr-FR"))?.language
                else
                    context.resources.configuration.locale.language
        if (locale == "fr")
            "fr"
        else
            "en"
    }

    override fun onCreate(db: SQLiteDatabase?) {
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    val hiraganaView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, HIRAGANAS_TABLE_NAME, "id", itemGetter = this::getHiragana)
    val katakanaView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, KATAKANAS_TABLE_NAME, "id", itemGetter = this::getKatakana)
    val kanjiView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, KANJIS_TABLE_NAME, "id", "radical = 0", itemGetter = this::getKanji, itemSearcher = this::searchKanji)
    val composedKanjiView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, KANJIS_TABLE_NAME, "id", filter = "radical = 0", itemGetter = this::getKanji, itemSearcher = this::searchKanji)

    fun getKanjiView(level: Int?): LearningDbView =
            LearningDbView(readableDatabase, writableDatabase, KANJIS_TABLE_NAME, "id", "radical = 0", level = level, itemGetter = this::getKanji, itemSearcher = this::searchKanji)

    val wordView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, WORDS_TABLE_NAME, "id", itemGetter = this::getWord, itemSearcher = this::searchWord)

    fun getWordView(level: Int?): LearningDbView =
            LearningDbView(readableDatabase, writableDatabase, WORDS_TABLE_NAME, "id", "1", level = level, itemGetter = this::getWord, itemSearcher = this::searchWord)

    fun getCompositionAnswerIds(kanjiId: Int): List<Int> {
        readableDatabase.rawQuery("""
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

    private fun searchKanji(text: String): List<Int> {
        val firstCodePoint =
                if (!text.isEmpty())
                    text.codePointAt(0).toString()
                else
                    ""
        readableDatabase.rawQuery(
                """SELECT id
                FROM $KANJIS_TABLE_NAME
                WHERE id_kanji = ? OR on_readings LIKE ? OR kun_readings LIKE ? OR  (meanings_$locale <> '' AND meanings_$locale LIKE ? OR meanings_$locale == '' AND meanings_en LIKE ?) AND radical = 0""",
                arrayOf(firstCodePoint, "%$text%", "%$text%", "%$text%", "%$text%")).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    private fun searchWord(text: String): List<Int> {
        readableDatabase.rawQuery(
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

    fun getHiragana(id: Int): Item = getKana(HIRAGANAS_TABLE_NAME, SIMILAR_HIRAGANAS_TABLE_NAME, id)
    fun getKatakana(id: Int): Item = getKana(KATAKANAS_TABLE_NAME, SIMILAR_KATAKANAS_TABLE_NAME, id)

    private fun getKana(tableName: String, similarKanaTableName: String, id: Int): Item {
        val similarities = mutableListOf<Item>()
        readableDatabase.query(similarKanaTableName, arrayOf("id_kana2"), "id_kana1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kana("", "", listOf()), 0.0, 0.0, 0, false))
        }
        val contents = Kana("", "", similarities)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        readableDatabase.query(tableName, arrayOf("romaji", "short_score", "long_score", "last_correct", "enabled"), "id = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kana with id $id in $tableName")
            cursor.moveToFirst()
            contents.kana = Character.toChars(id).joinToString()
            contents.romaji = cursor.getString(0)
            item.shortScore = cursor.getDouble(1)
            item.longScore = cursor.getDouble(2)
            item.lastCorrect = cursor.getLong(3)
            item.enabled = cursor.getInt(4) != 0
        }
        return item
    }

    fun getKanji(id: Int): Item {
        val strokes = mutableListOf<Path>()
        readableDatabase.query(STROKES_TABLE_NAME, arrayOf("path"), "id_kanji = ?", arrayOf(id.toString()), null, null, "ordinal").use { cursor ->
            val PathParser = Class.forName("android.support.v4.graphics.PathParser")
            val createPathFromPathData = PathParser.getMethod("createPathFromPathData", String::class.java)
            while (cursor.moveToNext()) {
                strokes.add(createPathFromPathData.invoke(null, cursor.getString(0)) as Path)
            }
        }
        val similarities = mutableListOf<Item>()
        readableDatabase.query(SIMILARITIES_TABLE_NAME, arrayOf("id_kanji2"), "id_kanji1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kanji("", listOf(), listOf(), listOf(), listOf(), listOf(), listOf(), 0), 0.0, 0.0, 0, false))
        }
        val parts = mutableListOf<Item>()
        readableDatabase.query(KANJIS_COMPOSITION_TABLE_NAME, arrayOf("id_kanji2"), "id_kanji1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                parts.add(Item(cursor.getInt(0), Kanji("", listOf(), listOf(), listOf(), listOf(), listOf(), listOf(), 0), 0.0, 0.0, 0, false))
        }
        val contents = Kanji("", listOf(), listOf(), listOf(), strokes, similarities, parts, 0)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        readableDatabase.query(KANJIS_TABLE_NAME,
                arrayOf("jlpt_level", "short_score", "long_score", "last_correct", "enabled", "on_readings", "kun_readings", "meanings_$locale", "meanings_en"),
                "id = ?", arrayOf(id.toString()),
                null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id $id")
            cursor.moveToFirst()
            contents.kanji = Character.toChars(id).joinToString()
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
            item.lastCorrect = cursor.getLong(3)
            item.enabled = cursor.getInt(4) != 0
        }
        return item
    }

    fun getEnabledWholeKanjiRatio(): Float {
        val wholeKanjis = readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("COUNT(*)"), "enabled = 1 AND part_count = 1", null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        val enabledKanjis = readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("COUNT(*)"), "enabled = 1", null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

        return wholeKanjis.toFloat() / enabledKanjis.toFloat()
    }

    fun getWord(id: Int): Item {
        val contents = Word("", "", listOf(), listOf())
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        var similarityClass = 0
        readableDatabase.query(WORDS_TABLE_NAME,
                arrayOf("item", "reading", "meanings_$locale", "short_score", "long_score", "last_correct", "enabled", "similarity_class", "meanings_en"),
                "id = ?", arrayOf(id.toString()),
                null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id $id")
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
            item.lastCorrect = cursor.getLong(5)
            item.enabled = cursor.getInt(6) != 0
            similarityClass = cursor.getInt(7)
        }
        val similarWords = mutableListOf<Item>()
        readableDatabase.query(WORDS_TABLE_NAME, arrayOf("id"),
                "similarity_class = ? AND id <> ?", arrayOf(similarityClass.toString(), id.toString()),
                null, null, "RANDOM()", "20").use { cursor ->
            while (cursor.moveToNext())
                similarWords.add(Item(cursor.getInt(0), Word("", "", listOf(), listOf()), 0.0, 0.0, 0, false))
        }
        contents.similarities = similarWords
        return item
    }

    fun setSelection(kanjis: String) {
        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues()
            cv.put("enabled", false)
            writableDatabase.update(KANJIS_TABLE_NAME, cv, null, null)
            cv.put("enabled", true)
            for (i in 0..kanjis.codePointCount(0, kanjis.length)) {
                writableDatabase.update(KANJIS_TABLE_NAME, cv, "item = ?", arrayOf(kanjis.codePointAt(i).toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun autoSelectWords() {
        val enabledKanjis = HashSet<Char>()
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                enabledKanjis.add(Character.toChars(cursor.getInt(0))[0])
            }
        }
        var allWords =
                readableDatabase.query(WORDS_TABLE_NAME, arrayOf("id, item"), null, null, null, null, null).use { cursor ->
                    val ret = mutableListOf<Pair<Long, String>>()
                    while (cursor.moveToNext()) {
                        ret.add(Pair(cursor.getLong(0), cursor.getString(1)))
                    }
                    ret.toList()
                }
        allWords = allWords.map { Pair(it.first, it.second.filter { isKanji(it) }) }
        allWords = allWords.filter { it.second.all { it in enabledKanjis } }

        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues()
            cv.put("enabled", false)
            writableDatabase.update(WORDS_TABLE_NAME, cv, null, null)
            cv.put("enabled", true)
            for (word in allWords) {
                writableDatabase.update(WORDS_TABLE_NAME, cv, "id = ?", arrayOf(word.first.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    data class KanjiSelection(
            val id: Long,
            val name: String
    )

    fun listKanjiSelections(): List<KanjiSelection> {
        val out = mutableListOf<KanjiSelection>()
        readableDatabase.query(KANJIS_SELECTION_TABLE_NAME, arrayOf("id_selection", "name"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                out.add(KanjiSelection(cursor.getLong(0), cursor.getString(1)))
        }
        return out
    }

    fun saveKanjiSelectionTo(name: String) {
        writableDatabase.beginTransaction()
        try {
            val idSelection = readableDatabase.query(KANJIS_SELECTION_TABLE_NAME, arrayOf("id_selection"), "name = ?", arrayOf(name), null, null, null).use { cursor ->
                if (cursor.count == 0) {
                    val cv = ContentValues()
                    cv.put("name", name)
                    return@use writableDatabase.insert(KANJIS_SELECTION_TABLE_NAME, null, cv)
                } else {
                    cursor.moveToFirst()
                    return@use cursor.getInt(0)
                }
            }
            writableDatabase.delete(KANJIS_ITEM_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            writableDatabase.execSQL("""
            INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)
            SELECT ?, id FROM $KANJIS_TABLE_NAME WHERE enabled = 1
            """, arrayOf(idSelection.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun restoreKanjiSelectionFrom(idSelection: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.execSQL("""
                UPDATE $KANJIS_TABLE_NAME
                SET enabled = 0
                """)
            writableDatabase.execSQL("""
                UPDATE $KANJIS_TABLE_NAME
                SET enabled = 1
                WHERE id IN (
                    SELECT id_kanji
                    FROM $KANJIS_ITEM_SELECTION_TABLE_NAME
                    WHERE id_selection = ?
                )
                """, arrayOf(idSelection.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun deleteKanjiSelection(idSelection: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(KANJIS_ITEM_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            writableDatabase.delete(KANJIS_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    companion object {
        private const val TAG = "Database"

        const val DATABASE_NAME = "kanjis"

        const val HIRAGANAS_TABLE_NAME = "hiraganas"
        const val SIMILAR_HIRAGANAS_TABLE_NAME = "similar_hiraganas"

        const val KATAKANAS_TABLE_NAME = "katakanas"
        const val SIMILAR_KATAKANAS_TABLE_NAME = "similar_katakanas"

        const val KANJIS_TABLE_NAME = "kanjis"
        const val SIMILARITIES_TABLE_NAME = "similarities"
        const val KANJIS_SELECTION_TABLE_NAME = "kanjis_selection"
        const val KANJIS_ITEM_SELECTION_TABLE_NAME = "kanjis_item_selection"
        const val STROKES_TABLE_NAME = "strokes"
        const val KANJIS_COMPOSITION_TABLE_NAME = "kanjis_composition"

        const val WORDS_TABLE_NAME = "words"

        private var singleton: Database? = null

        private fun isKanji(c: Char): Boolean {
            // This is the hiragana/katakana range
            return c.toInt() !in 0x3040..0x3100
        }

        fun getInstance(context: Context): Database {
            if (singleton == null)
                singleton = Database(context)
            return singleton!!
        }
    }
}