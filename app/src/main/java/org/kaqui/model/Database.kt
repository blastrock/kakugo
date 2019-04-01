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

class Database private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
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

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_TABLE_NAME ("
                        + "id INTEGER PRIMARY KEY,"
                        + "item TEXT NOT NULL UNIQUE,"
                        + "on_readings TEXT NOT NULL DEFAULT '',"
                        + "kun_readings TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "kaqui_level INTEGER NOT NULL DEFAULT 0,"
                        + "part_count INTEGER NOT NULL DEFAULT 0,"
                        + "radical INTEGER NOT NULL DEFAULT 0,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $STROKES_TABLE_NAME ("
                        + "id INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL,"
                        + "ordinal INT NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE(id_kanji, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_COMPOSITION_TABLE_NAME ("
                        + "id_composition INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL,"
                        + "id_kanji2 INTEGER NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $SIMILARITIES_TABLE_NAME ("
                        + "id_similarity INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "UNIQUE(id_kanji1, id_kanji2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_ITEM_SELECTION_TABLE_NAME ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL,"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_SELECTION_TABLE_NAME ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $WORDS_TABLE_NAME ("
                        + "id INTEGER PRIMARY KEY,"
                        + "item TEXT NOT NULL,"
                        + "reading TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "similarity_class INTEGER NOT NULL DEFAULT 0,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1,"
                        + "UNIQUE(item, reading)"
                        + ")")

        initKanas(database, HIRAGANAS_TABLE_NAME, SIMILAR_HIRAGANAS_TABLE_NAME, getHiraganas(), getSimilarHiraganas())
        initKanas(database, KATAKANAS_TABLE_NAME, SIMILAR_KATAKANAS_TABLE_NAME, getKatakanas(), getSimilarKatakanas())
    }

    private fun initKanas(database: SQLiteDatabase, tableName: String, similarKanaTableName: String, kanas: Array<RawKana>, similarKanas: Array<SimilarKana>) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $tableName ("
                        + "id_kana INTEGER PRIMARY KEY,"
                        + "kana TEXT NOT NULL UNIQUE,"
                        + "romaji TEXT NOT NULL,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $similarKanaTableName ("
                        + "id_similar_kana INTEGER PRIMARY KEY,"
                        + "id_kana INTEGER NOT NULL REFERENCES $tableName(id_kana),"
                        + "similar_kana INTEGER NOT NULL REFERENCES $tableName(id_kana),"
                        + "UNIQUE (id_kana, similar_kana)"
                        + ")")

        val kanaCount = database.query(tableName, arrayOf("COUNT(*)"), null, null, null, null, null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        if (kanaCount == 0) {
            for (kana in kanas) {
                val cv = ContentValues()
                cv.put("kana", kana.kana)
                cv.put("romaji", kana.romaji)
                database.insertOrThrow(tableName, null, cv)
            }
        }
        val similarKanaCount = database.query(similarKanaTableName, arrayOf("COUNT(*)"), null, null, null, null, null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        if (similarKanaCount == 0) {
            for (similarKana in similarKanas) {
                val id1 = database.query(tableName, arrayOf("id_kana"), "kana = ?", arrayOf(similarKana.kana), null, null, null).use {
                    it.moveToFirst()
                    it.getInt(0)
                }
                val id2 = database.query(tableName, arrayOf("id_kana"), "kana = ?", arrayOf(similarKana.similar), null, null, null).use {
                    it.moveToFirst()
                    it.getInt(0)
                }

                val cv = ContentValues()
                cv.put("id_kana", id1)
                cv.put("similar_kana", id2)
                database.insertOrThrow(similarKanaTableName, null, cv)
                cv.put("id_kana", id2)
                cv.put("similar_kana", id1)
                database.insertOrThrow(similarKanaTableName, null, cv)
            }
        }

    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val dump =
                when {
                    oldVersion < 10 -> dumpUserDataV9(database)
                    oldVersion < 13 -> dumpUserDataV12(database)
                    else -> dumpUserData(database)
                }
        database.execSQL("DROP TABLE IF EXISTS meanings")
        database.execSQL("DROP TABLE IF EXISTS readings")
        database.execSQL("DROP TABLE IF EXISTS $SIMILARITIES_TABLE_NAME")
        database.execSQL("DROP TABLE IF EXISTS $STROKES_TABLE_NAME")
        database.execSQL("DROP TABLE IF EXISTS $KANJIS_ITEM_SELECTION_TABLE_NAME")
        database.execSQL("DROP TABLE IF EXISTS $KANJIS_SELECTION_TABLE_NAME")
        database.execSQL("DROP TABLE IF EXISTS $KANJIS_COMPOSITION_TABLE_NAME")
        database.execSQL("DROP TABLE IF EXISTS $KANJIS_TABLE_NAME")
        database.execSQL("DROP TABLE IF EXISTS hiraganas")
        database.execSQL("DROP TABLE IF EXISTS similar_hiraganas")
        database.execSQL("DROP TABLE IF EXISTS katakanas")
        database.execSQL("DROP TABLE IF EXISTS similar_katakanas")
        database.execSQL("DROP TABLE IF EXISTS $WORDS_TABLE_NAME")
        onCreate(database)
        restoreUserData(database, dump)
    }

    fun replaceKanjis(dictDb: String) {
        writableDatabase.execSQL("ATTACH DATABASE ? AS dict", arrayOf(dictDb))
        writableDatabase.beginTransaction()
        try {
            val dump = dumpUserData()
            writableDatabase.delete(SIMILARITIES_TABLE_NAME, null, null)
            writableDatabase.delete(STROKES_TABLE_NAME, null, null)
            writableDatabase.delete(KANJIS_COMPOSITION_TABLE_NAME, null, null)
            writableDatabase.delete(KANJIS_TABLE_NAME, null, null)
            writableDatabase.execSQL(
                    "INSERT INTO $KANJIS_TABLE_NAME "
                            + "(id, item, on_readings, kun_readings, meanings_en, meanings_fr, jlpt_level, kaqui_level, part_count, radical, enabled) "
                            + "SELECT id, item, on_readings, kun_readings, meanings_en, meanings_fr, jlpt_level, kaqui_level, part_count, radical, jlpt_level = 5 "
                            + "FROM dict.kanjis"
            )
            writableDatabase.execSQL(
                    "INSERT INTO $STROKES_TABLE_NAME "
                            + "(id, id_kanji, ordinal, path) "
                            + "SELECT id, id_kanji, ordinal, path "
                            + "FROM dict.strokes"
            )
            writableDatabase.execSQL(
                    "INSERT INTO $SIMILARITIES_TABLE_NAME "
                            + "(id_kanji1, id_kanji2) "
                            + "SELECT id_kanji1, id_kanji2 "
                            + "FROM dict.kanjis_similars "
            )
            writableDatabase.execSQL(
                    "INSERT INTO $KANJIS_COMPOSITION_TABLE_NAME "
                            + "(id_kanji1, id_kanji2) "
                            + "SELECT id_kanji1, id_kanji2 "
                            + "FROM dict.kanjis_composition "
            )
            restoreUserData(dump)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
            writableDatabase.execSQL("DETACH DATABASE dict")
        }
    }

    fun replaceWords(dictDb: String) {
        writableDatabase.execSQL("ATTACH DATABASE ? AS dict", arrayOf(dictDb))
        writableDatabase.beginTransaction()
        try {
            val dump = dumpUserData()
            writableDatabase.delete(WORDS_TABLE_NAME, null, null)
            writableDatabase.execSQL(
                    "INSERT INTO $WORDS_TABLE_NAME "
                            + "(id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class) "
                            + "SELECT id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class "
                            + "FROM dict.words"
            )
            restoreUserData(dump)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
            writableDatabase.execSQL("DETACH DATABASE dict")
        }
    }

    val hiraganaView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, HIRAGANAS_TABLE_NAME, "id_kana", itemGetter = this::getHiragana)
    val katakanaView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, KATAKANAS_TABLE_NAME, "id_kana", itemGetter = this::getKatakana)
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
        readableDatabase.rawQuery(
                """SELECT id
                FROM $KANJIS_TABLE_NAME
                WHERE item = ? OR on_readings LIKE ? OR kun_readings LIKE ? OR  (meanings_$locale <> '' AND meanings_$locale LIKE ? OR meanings_$locale == '' AND meanings_en LIKE ?) AND radical = 0""",
                arrayOf(text, "%$text%", "%$text%", "%$text%", "%$text%")).use { cursor ->
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
        readableDatabase.query(similarKanaTableName, arrayOf("similar_kana"), "id_kana = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kana("", "", listOf()), 0.0, 0.0, 0, false))
        }
        val contents = Kana("", "", similarities)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        readableDatabase.query(tableName, arrayOf("kana", "romaji", "short_score", "long_score", "last_correct", "enabled"), "id_kana = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kana with id $id in $tableName")
            cursor.moveToFirst()
            contents.kana = cursor.getString(0)
            contents.romaji = cursor.getString(1)
            item.shortScore = cursor.getDouble(2)
            item.longScore = cursor.getDouble(3)
            item.lastCorrect = cursor.getLong(4)
            item.enabled = cursor.getInt(5) != 0
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
                arrayOf("item", "jlpt_level", "short_score", "long_score", "last_correct", "enabled", "on_readings", "kun_readings", "meanings_$locale", "meanings_en"),
                "id = ?", arrayOf(id.toString()),
                null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id $id")
            cursor.moveToFirst()
            contents.kanji = cursor.getString(0)
            contents.on_readings = cursor.getString(6).split('_')
            contents.kun_readings = cursor.getString(7).split('_')
            val localMeaning = cursor.getString(8)
            if (localMeaning != "")
                contents.meanings = localMeaning.split('_')
            else
                contents.meanings = cursor.getString(9).split('_')
            contents.jlptLevel = cursor.getInt(1)
            item.shortScore = cursor.getDouble(2)
            item.longScore = cursor.getDouble(3)
            item.lastCorrect = cursor.getLong(4)
            item.enabled = cursor.getInt(5) != 0
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
            for (c in kanjis) {
                writableDatabase.update(KANJIS_TABLE_NAME, cv, "item = ?", arrayOf(c.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun autoSelectWords() {
        val enabledKanjis = HashSet<Char>()
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("item"), "enabled = 1", null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                enabledKanjis.add(cursor.getString(0)[0])
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

    data class Dump(val hiraganas: List<DumpRow>, val katakanas: List<DumpRow>, val kanjis: List<DumpRow>, val words: List<DumpRow>, val kanjiSelections: Map<String, List<String>>)
    data class DumpRow(val item: String, val shortScore: Float, val longScore: Float, val lastCorrect: Long, val enabled: Boolean)

    private fun dumpUserData(): Dump = dumpUserData(readableDatabase)
    private fun restoreUserData(data: Dump) = restoreUserData(writableDatabase, data)

    companion object {
        private const val TAG = "Database"

        private const val DATABASE_NAME = "kanjis"
        private const val DATABASE_VERSION = 16

        private const val HIRAGANAS_TABLE_NAME = "hiraganas"
        private const val SIMILAR_HIRAGANAS_TABLE_NAME = "similar_hiraganas"

        private const val KATAKANAS_TABLE_NAME = "katakanas"
        private const val SIMILAR_KATAKANAS_TABLE_NAME = "similar_katakanas"

        private const val KANJIS_TABLE_NAME = "kanjis"
        private const val SIMILARITIES_TABLE_NAME = "similarities"
        private const val KANJIS_SELECTION_TABLE_NAME = "kanjis_selection"
        private const val KANJIS_ITEM_SELECTION_TABLE_NAME = "kanjis_item_selection"
        private const val STROKES_TABLE_NAME = "strokes"
        private const val KANJIS_COMPOSITION_TABLE_NAME = "kanjis_composition"

        private const val WORDS_TABLE_NAME = "words"

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

        fun databaseNeedsUpdate(context: Context): Boolean {
            try {
                SQLiteDatabase.openDatabase(context.getDatabasePath(Database.DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    if (db.version != DATABASE_VERSION)
                        return true

                    db.query(KANJIS_TABLE_NAME, arrayOf("COUNT(*)"), "on_readings <> ''", null, null, null, null).use { cursor ->
                        cursor.moveToFirst()
                        if (cursor.getInt(0) == 0)
                            return true
                    }
                    db.query(STROKES_TABLE_NAME, arrayOf("COUNT(*)"), null, null, null, null, null).use { cursor ->
                        cursor.moveToFirst()
                        if (cursor.getInt(0) == 0)
                            return true
                    }
                    db.query(KANJIS_COMPOSITION_TABLE_NAME, arrayOf("COUNT(*)"), null, null, null, null, null).use { cursor ->
                        cursor.moveToFirst()
                        if (cursor.getInt(0) == 0)
                            return true
                    }
                    db.query(WORDS_TABLE_NAME, arrayOf("COUNT(*)"), "reading <> ''", null, null, null, null).use { cursor ->
                        cursor.moveToFirst()
                        if (cursor.getInt(0) == 0)
                            return true
                    }
                    db.query(WORDS_TABLE_NAME, arrayOf("COUNT(*)"), "jlpt_level <> 0", null, null, null, null).use { cursor ->
                        cursor.moveToFirst()
                        if (cursor.getInt(0) == 0)
                            return true
                    }
                    return false
                }
            } catch (e: SQLiteException) {
                Log.i(TAG, "Failed to open database", e)
                return true
            }
        }

        private fun dumpUserDataV9(database: SQLiteDatabase): Dump {
            val hiraganas = mutableListOf<DumpRow>()
            database.query(HIRAGANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    hiraganas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val katakanas = mutableListOf<DumpRow>()
            database.query(KATAKANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    katakanas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val kanjis = mutableListOf<DumpRow>()
            database.query(KANJIS_TABLE_NAME, arrayOf("kanji", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    kanjis.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            return Dump(hiraganas, katakanas, kanjis, listOf(), mapOf())
        }

        private fun dumpUserDataV12(database: SQLiteDatabase): Dump {
            val hiraganas = mutableListOf<DumpRow>()
            database.query(HIRAGANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    hiraganas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val katakanas = mutableListOf<DumpRow>()
            database.query(KATAKANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    katakanas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val kanjis = mutableListOf<DumpRow>()
            database.query(KANJIS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    kanjis.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val words = mutableListOf<DumpRow>()
            database.query(WORDS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    words.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            return Dump(hiraganas, katakanas, kanjis, words, mapOf())
        }

        private fun dumpUserData(database: SQLiteDatabase): Dump {
            val hiraganas = mutableListOf<DumpRow>()
            database.query(HIRAGANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    hiraganas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val katakanas = mutableListOf<DumpRow>()
            database.query(KATAKANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    katakanas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val kanjis = mutableListOf<DumpRow>()
            database.query(KANJIS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    kanjis.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val kanjiSelections = mutableMapOf<String, MutableList<String>>()
            database.rawQuery("""
                SELECT ks.name, k.item
                FROM $KANJIS_SELECTION_TABLE_NAME ks
                LEFT JOIN $KANJIS_ITEM_SELECTION_TABLE_NAME kis USING(id_selection)
                LEFT JOIN $KANJIS_TABLE_NAME k ON kis.id_kanji = k.id
            """, null).use { cursor ->
                while (cursor.moveToNext())
                    kanjiSelections.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getString(1))
            }
            val words = mutableListOf<DumpRow>()
            database.query(WORDS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    words.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            return Dump(hiraganas, katakanas, kanjis, words, kanjiSelections)
        }

        private fun restoreUserData(database: SQLiteDatabase, data: Dump) {
            database.beginTransaction()
            try {
                run {
                    val cv = ContentValues()
                    for (row in data.hiraganas) {
                        cv.put("short_score", row.shortScore)
                        cv.put("long_score", row.longScore)
                        cv.put("last_correct", row.lastCorrect)
                        cv.put("enabled", if (row.enabled) 1 else 0)
                        database.update(HIRAGANAS_TABLE_NAME, cv, "kana = ?", arrayOf(row.item))
                    }
                }
                run {
                    val cv = ContentValues()
                    for (row in data.katakanas) {
                        cv.put("short_score", row.shortScore)
                        cv.put("long_score", row.longScore)
                        cv.put("last_correct", row.lastCorrect)
                        cv.put("enabled", if (row.enabled) 1 else 0)
                        database.update(KATAKANAS_TABLE_NAME, cv, "kana = ?", arrayOf(row.item))
                    }
                }
                run {
                    for (row in data.kanjis) {
                        val cv = ContentValues()
                        cv.put("short_score", row.shortScore)
                        cv.put("long_score", row.longScore)
                        cv.put("last_correct", row.lastCorrect)
                        cv.put("enabled", if (row.enabled) 1 else 0)
                        if (database.update(KANJIS_TABLE_NAME, cv, "item = ?", arrayOf(row.item)) == 0) {
                            cv.put("item", row.item)
                            database.insert(KANJIS_TABLE_NAME, null, cv)
                        }
                    }
                }
                run {
                    database.delete(KANJIS_ITEM_SELECTION_TABLE_NAME, null, null)
                    database.delete(KANJIS_SELECTION_TABLE_NAME, null, null)
                    for ((selectionName, selectionItems) in data.kanjiSelections) {
                        val cv = ContentValues()
                        cv.put("name", selectionName)
                        val selectionId = database.insert(KANJIS_SELECTION_TABLE_NAME, null, cv)
                        for (item in selectionItems) {
                            val kanjiId = database.rawQuery(
                                    """SELECT id
                                    FROM $KANJIS_TABLE_NAME
                                    WHERE item = ?""",
                                    arrayOf(item)).use { cursor ->
                                if (cursor.count == 0)
                                    return@use null
                                cursor.moveToFirst()
                                return@use cursor.getInt(0)
                            } ?: continue

                            val cv = ContentValues()
                            cv.put("id_selection", selectionId)
                            cv.put("id_kanji", kanjiId)
                            database.insert(KANJIS_ITEM_SELECTION_TABLE_NAME, null, cv)
                        }
                    }
                }
                run {
                    for (row in data.words) {
                        val cv = ContentValues()
                        cv.put("short_score", row.shortScore)
                        cv.put("long_score", row.longScore)
                        cv.put("last_correct", row.lastCorrect)
                        cv.put("enabled", if (row.enabled) 1 else 0)
                        if (database.update(WORDS_TABLE_NAME, cv, "item = ?", arrayOf(row.item)) == 0) {
                            cv.put("item", row.item)
                            database.insert(WORDS_TABLE_NAME, null, cv)
                        }
                    }
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }
}
