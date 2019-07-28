package org.kaqui.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log

class DatabaseUpdater(private val database: SQLiteDatabase, private val dictDb: String) {
    data class Dump(val enabledKanas: List<Int>, val enabledKanjis: List<Int>, val enabledWords: List<Pair<String, String>>, val scores: List<DumpScore>, val wordScores: List<DumpWordScore>, val kanjiSelections: Map<String, List<Int>>)
    data class DumpScore(val id: Int, val knowledgeType: KnowledgeType, val shortScore: Float, val longScore: Float, val lastCorrect: Long)
    data class DumpWordScore(val item: String, val reading: String, val knowledgeType: KnowledgeType, val shortScore: Float, val longScore: Float, val lastCorrect: Long)

    private fun createDatabase() {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANJIS_TABLE_NAME} ("
                        + "id INTEGER PRIMARY KEY NOT NULL,"
                        + "on_readings TEXT NOT NULL DEFAULT '',"
                        + "kun_readings TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "rtk_index INTEGER NOT NULL DEFAULT 0,"
                        + "rtk6_index INTEGER NOT NULL DEFAULT 0,"
                        + "part_count INTEGER NOT NULL DEFAULT 0,"
                        + "radical INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.ITEM_STROKES_TABLE_NAME} ("
                        + "id INTEGER PRIMARY KEY,"
                        + "id_item INTEGER NOT NULL,"
                        + "ordinal INT NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE(id_item, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.SIMILAR_ITEMS_TABLE_NAME} ("
                        + "id_item1 INTEGER NOT NULL,"
                        + "id_item2 INTEGER NOT NULL,"
                        + "PRIMARY KEY (id_item1, id_item2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANJIS_COMPOSITION_TABLE_NAME} ("
                        + "id_composition INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANJIS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANJIS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.WORDS_TABLE_NAME} ("
                        + "id INTEGER PRIMARY KEY,"
                        + "item TEXT NOT NULL,"
                        + "reading TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "rtk_index INTEGER NOT NULL DEFAULT 0,"
                        + "rtk6_index INTEGER NOT NULL DEFAULT 0,"
                        + "similarity_class INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1,"
                        + "UNIQUE(item, reading)"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANAS_TABLE_NAME} ("
                        + "id INTEGER PRIMARY KEY NOT NULL,"
                        + "romaji TEXT NOT NULL DEFAULT '',"
                        + "unique_romaji TEXT NOT NULL DEFAULT '',"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.ITEM_SCORES_TABLE_NAME} ("
                        + "id INTEGER,"
                        + "type INTEGER NOT NULL,"
                        + "short_score FLOAT NOT NULL,"
                        + "long_score FLOAT NOT NULL,"
                        + "last_correct INTEGER NOT NULL,"
                        + "PRIMARY KEY (id, type)"
                        + ")")
    }

    private fun doUpgrade() {
        database.execSQL("ATTACH DATABASE ? AS dict", arrayOf(dictDb))
        database.beginTransaction()
        try {
            val oldVersion = database.version

            val dump =
                    when {
                        oldVersion == 0 -> null
                        oldVersion < 10 -> dumpUserDataV9()
                        oldVersion < 13 -> dumpUserDataV12()
                        oldVersion < 17 -> dumpUserDataV16()
                        oldVersion < 19 -> dumpUserDataV18()
                        else -> dumpUserData()
                    }
            database.execSQL("DROP TABLE IF EXISTS main.meanings")
            database.execSQL("DROP TABLE IF EXISTS main.readings")
            database.execSQL("DROP TABLE IF EXISTS main.similarities")
            database.execSQL("DROP TABLE IF EXISTS main.strokes")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.SIMILAR_ITEMS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.ITEM_STROKES_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KANJIS_ITEM_SELECTION_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KANJIS_SELECTION_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KANJIS_COMPOSITION_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KANJIS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KANAS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.hiraganas")
            database.execSQL("DROP TABLE IF EXISTS main.similar_hiraganas")
            database.execSQL("DROP TABLE IF EXISTS main.hiragana_strokes")
            database.execSQL("DROP TABLE IF EXISTS main.katakanas")
            database.execSQL("DROP TABLE IF EXISTS main.similar_katakanas")
            database.execSQL("DROP TABLE IF EXISTS main.katakana_strokes")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.WORDS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.ITEM_SCORES_TABLE_NAME}")
            createDatabase()

            replaceDict()
            if (dump != null)
                restoreUserData(dump)
            database.version = DATABASE_VERSION
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
            database.execSQL("DETACH DATABASE dict")
        }
    }

    private fun replaceDict() {
        database.delete(Database.KANAS_TABLE_NAME, null, null)
        database.execSQL(
                "INSERT INTO ${Database.KANAS_TABLE_NAME} "
                        + "(id, romaji, unique_romaji) "
                        + "SELECT id, romaji, unique_romaji "
                        + "FROM dict.${Database.KANAS_TABLE_NAME}"
        )

        database.delete(Database.SIMILAR_ITEMS_TABLE_NAME, null, null)
        database.delete(Database.ITEM_STROKES_TABLE_NAME, null, null)
        database.delete(Database.KANJIS_COMPOSITION_TABLE_NAME, null, null)
        database.delete(Database.KANJIS_TABLE_NAME, null, null)
        database.execSQL(
                "INSERT INTO ${Database.KANJIS_TABLE_NAME} "
                        + "(id, on_readings, kun_readings, meanings_en, meanings_fr, jlpt_level, rtk_index, rtk6_index, part_count, radical, enabled) "
                        + "SELECT id, on_readings, kun_readings, meanings_en, meanings_fr, jlpt_level, rtk_index, rtk6_index, part_count, radical, jlpt_level = 5 "
                        + "FROM dict.kanjis"
        )
        database.execSQL(
                "INSERT INTO ${Database.ITEM_STROKES_TABLE_NAME} "
                        + "(id, id_item, ordinal, path) "
                        + "SELECT id, id_item, ordinal, path "
                        + "FROM dict.item_strokes"
        )
        database.execSQL(
                "INSERT INTO ${Database.SIMILAR_ITEMS_TABLE_NAME} "
                        + "(id_item1, id_item2) "
                        + "SELECT id_item1, id_item2 "
                        + "FROM dict.similar_items "
        )
        database.execSQL(
                "INSERT INTO ${Database.KANJIS_COMPOSITION_TABLE_NAME} "
                        + "(id_kanji1, id_kanji2) "
                        + "SELECT id_kanji1, id_kanji2 "
                        + "FROM dict.kanjis_composition "
        )

        database.delete(Database.WORDS_TABLE_NAME, null, null)
        database.execSQL(
                "INSERT INTO ${Database.WORDS_TABLE_NAME} "
                        + "(id, item, reading, meanings_en, meanings_fr, jlpt_level, rtk_index, rtk6_index, similarity_class) "
                        + "SELECT id, item, reading, meanings_en, meanings_fr, jlpt_level, rtk_index, rtk6_index, similarity_class "
                        + "FROM dict.words"
        )
    }

    private fun convertKanaScore(id: Int, shortScore: Float, longScore: Float, lastCorrect: Long) =
            listOf(KnowledgeType.Reading, KnowledgeType.Strokes).map { knowledgeType ->
                DumpScore(id, knowledgeType, shortScore, longScore, lastCorrect)
            }

    private fun convertKanjiScore(id: Int, shortScore: Float, longScore: Float, lastCorrect: Long) =
            listOf(KnowledgeType.Reading, KnowledgeType.Meaning, KnowledgeType.Strokes).map { knowledgeType ->
                DumpScore(id, knowledgeType, shortScore, longScore, lastCorrect)
            }

    private fun convertWordScore(item: String, reading: String, shortScore: Float, longScore: Float, lastCorrect: Long) =
            listOf(KnowledgeType.Reading, KnowledgeType.Meaning).map { knowledgeType ->
                DumpWordScore(item, reading, knowledgeType, shortScore, longScore, lastCorrect)
            }

    private fun dumpScoresV16(scores: MutableList<DumpScore>, enabledItems: MutableList<Int>, converter: (Int, Float, Float, Long) -> Iterable<DumpScore>, cursor: Cursor) {
        while (cursor.moveToNext()) {
            val id = cursor.getString(0).codePointAt(0)
            val enabled = cursor.getInt(4) != 0
            scores.addAll(converter(id, cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3)))
            if (enabled)
                enabledItems.add(id)
        }
    }

    private fun dumpScores(scores: MutableList<DumpScore>, enabledItems: MutableList<Int>, converter: (Int, Float, Float, Long) -> Iterable<DumpScore>, cursor: Cursor) {
        while (cursor.moveToNext()) {
            val id = cursor.getInt(0)
            val enabled = cursor.getInt(4) != 0
            scores.addAll(converter(id, cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3)))
            if (enabled)
                enabledItems.add(id)
        }
    }

    private fun dumpWordScores(scores: MutableList<DumpWordScore>, enabledItems: MutableList<Pair<String, String>>, cursor: Cursor) {
        while (cursor.moveToNext()) {
            val item = cursor.getString(0)
            val reading = cursor.getString(1)
            val enabled = cursor.getInt(5) != 0
            scores.addAll(convertWordScore(item, reading, cursor.getFloat(2), cursor.getFloat(3), cursor.getLong(4)))
            if (enabled)
                enabledItems.add(Pair(item, reading))
        }
    }

    private fun dumpUserDataV9(): Dump {
        val scores = mutableListOf<DumpScore>()
        val enabledKanas = mutableListOf<Int>()
        database.query("hiraganas", arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScoresV16(scores, enabledKanas, this::convertKanaScore, cursor)
        }
        database.query("katakanas", arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScoresV16(scores, enabledKanas, this::convertKanaScore, cursor)
        }
        val enabledKanjis = mutableListOf<Int>()
        database.query(Database.KANJIS_TABLE_NAME, arrayOf("kanji", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0 AND radical = 0", null, null, null, null).use { cursor ->
            dumpScoresV16(scores, enabledKanjis, this::convertKanjiScore, cursor)
        }
        return Dump(enabledKanas, enabledKanjis, listOf(), scores, listOf(), mapOf())
    }

    private fun dumpUserDataV12(): Dump {
        val scores = mutableListOf<DumpScore>()
        val enabledKanas = mutableListOf<Int>()
        database.query("hiraganas", arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScoresV16(scores, enabledKanas, this::convertKanaScore, cursor)
        }
        database.query("katakanas", arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScoresV16(scores, enabledKanas, this::convertKanaScore, cursor)
        }
        val enabledKanjis = mutableListOf<Int>()
        database.query(Database.KANJIS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScoresV16(scores, enabledKanjis, this::convertKanjiScore, cursor)
        }
        val wordScores = mutableListOf<DumpWordScore>()
        val enabledWords = mutableListOf<Pair<String, String>>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpWordScores(wordScores, enabledWords, cursor)
        }
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, mapOf())
    }

    private fun dumpUserDataV16(): Dump {
        val scores = mutableListOf<DumpScore>()
        val enabledKanas = mutableListOf<Int>()
        database.query("hiraganas", arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScoresV16(scores, enabledKanas, this::convertKanaScore, cursor)
        }
        database.query("katakanas", arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScoresV16(scores, enabledKanas, this::convertKanaScore, cursor)
        }
        val enabledKanjis = mutableListOf<Int>()
        database.query(Database.KANJIS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScoresV16(scores, enabledKanjis, this::convertKanjiScore, cursor)
        }
        val kanjiSelections = mutableMapOf<String, MutableList<Int>>()
        database.rawQuery("""
                SELECT ks.name, k.item
                FROM ${Database.KANJIS_SELECTION_TABLE_NAME} ks
                LEFT JOIN ${Database.KANJIS_ITEM_SELECTION_TABLE_NAME} kis USING(id_selection)
                LEFT JOIN ${Database.KANJIS_TABLE_NAME} k ON kis.id_kanji = k.id
            """, null).use { cursor ->
            while (cursor.moveToNext())
                kanjiSelections.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getString(1).codePointAt(0))
        }
        val wordScores = mutableListOf<DumpWordScore>()
        val enabledWords = mutableListOf<Pair<String, String>>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpWordScores(wordScores, enabledWords, cursor)
        }
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, kanjiSelections)
    }

    private fun dumpUserDataV18(): Dump {
        val scores = mutableListOf<DumpScore>()
        val enabledKanas = mutableListOf<Int>()
        database.query("hiraganas", arrayOf("id", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScores(scores, enabledKanas, this::convertKanaScore, cursor)
        }
        database.query("katakanas", arrayOf("id", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpScores(scores, enabledKanas, this::convertKanaScore, cursor)
        }
        val enabledKanjis = mutableListOf<Int>()
        database.query(Database.KANJIS_TABLE_NAME, arrayOf("id", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0 AND radical = 0", null, null, null, null).use { cursor ->
            dumpScores(scores, enabledKanjis, this::convertKanjiScore, cursor)
        }
        val kanjiSelections = mutableMapOf<String, MutableList<Int>>()
        database.rawQuery("""
                SELECT ks.name, kis.id_kanji
                FROM ${Database.KANJIS_SELECTION_TABLE_NAME} ks
                LEFT JOIN ${Database.KANJIS_ITEM_SELECTION_TABLE_NAME} kis USING(id_selection)
            """, null).use { cursor ->
            while (cursor.moveToNext())
                kanjiSelections.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getInt(1))
        }
        val wordScores = mutableListOf<DumpWordScore>()
        val enabledWords = mutableListOf<Pair<String, String>>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpWordScores(wordScores, enabledWords, cursor)
        }
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, kanjiSelections)
    }

    private fun dumpUserData(): Dump {
        val enabledKanas = mutableListOf<Int>()
        database.query(Database.KANAS_TABLE_NAME, arrayOf("id", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                if (cursor.getInt(1) != 0)
                    enabledKanas.add(cursor.getInt(0))
        }
        val enabledKanjis = mutableListOf<Int>()
        database.query(Database.KANJIS_TABLE_NAME, arrayOf("id", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                if (cursor.getInt(1) != 0)
                    enabledKanjis.add(cursor.getInt(0))
        }
        val kanjiSelections = mutableMapOf<String, MutableList<Int>>()
        database.rawQuery("""
                SELECT ks.name, kis.id_kanji
                FROM ${Database.KANJIS_SELECTION_TABLE_NAME} ks
                LEFT JOIN ${Database.KANJIS_ITEM_SELECTION_TABLE_NAME} kis USING(id_selection)
            """, null).use { cursor ->
            while (cursor.moveToNext())
                kanjiSelections.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getInt(1))
        }
        val enabledWords = mutableListOf<Pair<String, String>>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                if (cursor.getInt(2) != 0)
                    enabledWords.add(Pair(cursor.getString(0), cursor.getString(1)))
        }
        val scores = mutableListOf<DumpScore>()
        database.query(Database.ITEM_SCORES_TABLE_NAME, arrayOf("id", "type", "short_score", "long_score", "last_correct"), "id < $WordBaseId", null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                scores.add(DumpScore(cursor.getInt(0), KnowledgeType.fromInt(cursor.getInt(1)), cursor.getFloat(2), cursor.getFloat(3), cursor.getLong(4)))
        }
        val wordScores = mutableListOf<DumpWordScore>()
        database.rawQuery("""
                SELECT w.item, w.reading, s.type, s.short_score, s.long_score, s.last_correct
                FROM ${Database.ITEM_SCORES_TABLE_NAME} s
                JOIN ${Database.WORDS_TABLE_NAME} w USING(id)
            """, null).use { cursor ->
            while (cursor.moveToNext())
                wordScores.add(DumpWordScore(cursor.getString(0), cursor.getString(1), KnowledgeType.fromInt(cursor.getInt(2)), cursor.getFloat(3), cursor.getFloat(4), cursor.getLong(5)))
        }
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, kanjiSelections)
    }

    private fun enableOnly(tableName: String, toEnable: List<Int>) {
        run {
            val cv = ContentValues()
            cv.put("enabled", 0)
            database.update(tableName, cv, null, null)
        }
        for (row in toEnable) {
            val cv = ContentValues()
            cv.put("enabled", 1)
            database.update(tableName, cv, "id = ?", arrayOf(row.toString()))
        }
    }

    private fun enableOnlyWords(tableName: String, toEnable: List<Pair<String, String>>) {
        run {
            val cv = ContentValues()
            cv.put("enabled", 0)
            database.update(tableName, cv, null, null)
        }
        for (row in toEnable) {
            val cv = ContentValues()
            cv.put("enabled", 1)
            database.update(tableName, cv, "item = ? AND reading = ?", arrayOf(row.first, row.second))
        }
    }

    private fun restoreUserData(data: Dump) {
        database.beginTransaction()
        try {
            database.delete(Database.ITEM_SCORES_TABLE_NAME, null, null)
            run {
                val cv = ContentValues()
                for (row in data.scores) {
                    cv.put("id", row.id)
                    cv.put("type", row.knowledgeType.value)
                    cv.put("short_score", row.shortScore)
                    cv.put("long_score", row.longScore)
                    cv.put("last_correct", row.lastCorrect)
                    database.insertOrThrow(Database.ITEM_SCORES_TABLE_NAME, null, cv)
                }
            }
            run {
                for (row in data.wordScores) {
                    val wordId = database.rawQuery(
                            """SELECT id
                                 FROM ${Database.WORDS_TABLE_NAME}
                                 WHERE item = ? AND reading = ?""",
                            arrayOf(row.item, row.reading)).use { cursor ->
                        if (cursor.count == 0)
                            return@use null
                        cursor.moveToFirst()
                        return@use cursor.getInt(0)
                    } ?: continue

                    val cv = ContentValues()
                    cv.put("id", wordId)
                    cv.put("type", row.knowledgeType.value)
                    cv.put("short_score", row.shortScore)
                    cv.put("long_score", row.longScore)
                    cv.put("last_correct", row.lastCorrect)
                    database.insertOrThrow(Database.ITEM_SCORES_TABLE_NAME, null, cv)
                }
            }
            enableOnly(Database.KANAS_TABLE_NAME, data.enabledKanas)
            enableOnly(Database.KANJIS_TABLE_NAME, data.enabledKanjis)
            enableOnlyWords(Database.WORDS_TABLE_NAME, data.enabledWords)
            run {
                database.delete(Database.KANJIS_ITEM_SELECTION_TABLE_NAME, null, null)
                database.delete(Database.KANJIS_SELECTION_TABLE_NAME, null, null)
                for ((selectionName, selectionItems) in data.kanjiSelections) {
                    val cv = ContentValues()
                    cv.put("name", selectionName)
                    val selectionId = database.insertOrThrow(Database.KANJIS_SELECTION_TABLE_NAME, null, cv)
                    for (item in selectionItems) {
                        val cv = ContentValues()
                        cv.put("id_selection", selectionId)
                        cv.put("id_kanji", item)
                        database.insertOrThrow(Database.KANJIS_ITEM_SELECTION_TABLE_NAME, null, cv)
                    }
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    companion object {
        const val TAG = "DatabaseUpdater"
        const val DATABASE_VERSION = 20

        fun databaseNeedsUpdate(context: Context): Boolean {
            try {
                SQLiteDatabase.openDatabase(context.getDatabasePath(Database.DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    if (db.version != DATABASE_VERSION)
                        return true

                    return false
                }
            } catch (e: SQLiteException) {
                Log.i(TAG, "Failed to open database", e)
                return true
            }
        }

        fun upgradeDatabase(context: Context, dictDb: String) {
            // the databases folder may not exist on older androids
            context.getDatabasePath(Database.DATABASE_NAME).parentFile.mkdirs()
            SQLiteDatabase.openDatabase(context.getDatabasePath(Database.DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
                DatabaseUpdater(db, dictDb).doUpgrade()
            }
        }
    }
}
