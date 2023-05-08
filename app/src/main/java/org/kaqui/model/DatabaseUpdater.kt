package org.kaqui.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.core.database.sqlite.transaction

class DatabaseUpdater(private val database: SQLiteDatabase, private val dictDb: String) {
    data class Dump(
            val enabledKanas: List<Int>,
            val enabledKanjis: List<Int>,
            val enabledWords: List<WordId>,
            val scores: List<DumpScore>,
            val wordScores: List<DumpWordScore>,
            val kanjiSelections: Map<String, List<Int>>,
            val wordSelections: Map<String, List<WordId>>,
            val statsSnapshots: List<DumpStatsSnapshot>,
            val sessions: List<DumpSession>)

    data class DumpScore(val id: Int, val knowledgeType: KnowledgeType, val shortScore: Float, val longScore: Float, val lastCorrect: Long)
    data class DumpWordScore(val item: String, val reading: String, val knowledgeType: KnowledgeType, val shortScore: Float, val longScore: Float, val lastCorrect: Long)
    data class DumpStatsSnapshot(val itemType: ItemType, val knowledgeType: KnowledgeType, val time: Long, val goodCount: Int, val mehCount: Int, val badCount: Int, val longPartition: String, val longSum: Float)
    data class DumpSession(val itemType: ItemType, val testTypes: String, val startTime: Long, val items: List<DumpSessionItem>)
    data class DumpSessionItem(val testType: TestType, val content: DumpSessionItemContent, val certainty: Certainty, val time: Long)
    data class WordId(val item: String, val reading: String)
    sealed class DumpSessionItemContent {
        data class Normal(val question: Long, val wrong: Long?) : DumpSessionItemContent()
        data class Word(val question: WordId, val wrong: WordId?) : DumpSessionItemContent()
    }

    private fun createDatabase() {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANJIS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "on_readings TEXT NOT NULL DEFAULT '',"
                        + "kun_readings TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "meanings_es TEXT NOT NULL DEFAULT '',"
                        + "meanings_de TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "rtk_index INTEGER NOT NULL DEFAULT 0,"
                        + "rtk6_index INTEGER NOT NULL DEFAULT 0,"
                        + "part_count INTEGER NOT NULL DEFAULT 0,"
                        + "radical INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.ITEM_STROKES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "id_item INTEGER NOT NULL,"
                        + "ordinal INTEGER NOT NULL,"
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
                        + "id_selection INTEGER NOT NULL PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.WORDS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_word INTEGER NOT NULL REFERENCES words(id),"
                        + "PRIMARY KEY(id_selection, id_word)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.WORDS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.WORDS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "item TEXT NOT NULL,"
                        + "reading TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "meanings_es TEXT NOT NULL DEFAULT '',"
                        + "meanings_de TEXT NOT NULL DEFAULT '',"
                        + "kana_alone INTEGER NOT NULL DEFAULT 0,"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "rtk_index INTEGER NOT NULL DEFAULT 0,"
                        + "rtk6_index INTEGER NOT NULL DEFAULT 0,"
                        + "similarity_class INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1,"
                        + "UNIQUE(item, reading)"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANAS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "romaji TEXT NOT NULL DEFAULT '',"
                        + "unique_romaji TEXT NOT NULL DEFAULT '',"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.ITEM_SCORES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL,"
                        + "type INTEGER NOT NULL,"
                        + "short_score FLOAT NOT NULL,"
                        + "long_score FLOAT NOT NULL,"
                        + "last_correct INTEGER NOT NULL,"
                        + "PRIMARY KEY (id, type)"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.STATS_SNAPSHOT_TABLE_NAME} ("
                        + "item_type INTEGER NOT NULL,"
                        + "knowledge_type INTEGER NOT NULL,"
                        + "time INTEGER NOT NULL," // timestamp rounded to day in UTC
                        + "good_count INTEGER NOT NULL,"
                        + "meh_count INTEGER NOT NULL,"
                        + "bad_count INTEGER NOT NULL,"
                        + "long_score_partition TEXT NOT NULL,"
                        + "long_score_sum FLOAT NOT NULL,"
                        + "PRIMARY KEY (item_type, knowledge_type, time)"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.SESSIONS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                        + "item_type INTEGER NOT NULL,"
                        + "test_types TEXT NOT NULL," // separated by ','
                        + "start_time INTEGER NOT NULL,"
                        + "end_time INTEGER,"
                        + "item_count INTEGER,"
                        + "correct_count INTEGER"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.SESSION_ITEMS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                        + "id_session INTEGER NOT NULL,"
                        + "test_type INTEGER NOT NULL,"
                        + "id_item_question INTEGER NOT NULL,"
                        + "id_item_wrong INTEGER,"
                        + "certainty INTEGER NOT NULL,"
                        + "time INTEGER NOT NULL"
                        + ")")
    }

    private fun doUpgrade() {
        database.execSQL("ATTACH DATABASE ? AS dict", arrayOf(dictDb))
        database.transaction {
            val oldVersion = database.version

            val dump =
                    when {
                        oldVersion == 0 -> null
                        oldVersion < 10 -> dumpUserDataV9()
                        oldVersion < 13 -> dumpUserDataV12()
                        oldVersion < 17 -> dumpUserDataV16()
                        oldVersion < 19 -> dumpUserDataV18()
                        oldVersion < 21 -> dumpUserDataV20()
                        oldVersion < 22 -> dumpUserDataV21()
                        oldVersion > DATABASE_VERSION -> throw RuntimeException("reverting to an old version of the app is not supported")
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
            database.execSQL("DROP TABLE IF EXISTS main.${Database.WORDS_ITEM_SELECTION_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.WORDS_SELECTION_TABLE_NAME}")
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
            database.execSQL("DROP TABLE IF EXISTS main.${Database.STATS_SNAPSHOT_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.SESSIONS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.SESSION_ITEMS_TABLE_NAME}")
            createDatabase()

            replaceDict()
            if (dump != null)
                restoreUserData(dump)
            database.version = DATABASE_VERSION
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
                        + "(id, on_readings, kun_readings, meanings_en, meanings_fr, meanings_es, meanings_de, jlpt_level, rtk_index, rtk6_index, part_count, radical, enabled) "
                        + "SELECT id, on_readings, kun_readings, meanings_en, meanings_fr, meanings_es, meanings_de, jlpt_level, rtk_index, rtk6_index, part_count, radical, jlpt_level = 5 "
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
                        + "(id, item, reading, meanings_en, meanings_fr, meanings_es, meanings_de, kana_alone, jlpt_level, rtk_index, rtk6_index, similarity_class) "
                        + "SELECT id, item, reading, meanings_en, meanings_fr, meanings_es, meanings_de, kana_alone, jlpt_level, rtk_index, rtk6_index, similarity_class "
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

    private fun dumpWordScores(scores: MutableList<DumpWordScore>, enabledItems: MutableList<WordId>, cursor: Cursor) {
        while (cursor.moveToNext()) {
            val item = cursor.getString(0)
            val reading = cursor.getString(1)
            val enabled = cursor.getInt(5) != 0
            scores.addAll(convertWordScore(item, reading, cursor.getFloat(2), cursor.getFloat(3), cursor.getLong(4)))
            if (enabled)
                enabledItems.add(WordId(item, reading))
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
        return Dump(enabledKanas, enabledKanjis, listOf(), scores, listOf(), mapOf(), mapOf(), listOf(), listOf())
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
        val enabledWords = mutableListOf<WordId>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpWordScores(wordScores, enabledWords, cursor)
        }
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, mapOf(), mapOf(), listOf(), listOf())
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
        val enabledWords = mutableListOf<WordId>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpWordScores(wordScores, enabledWords, cursor)
        }
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, kanjiSelections, mapOf(), listOf(), listOf())
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
        val enabledWords = mutableListOf<WordId>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "short_score", "long_score", "last_correct", "enabled"), "last_correct > 0", null, null, null, null).use { cursor ->
            dumpWordScores(wordScores, enabledWords, cursor)
        }
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, kanjiSelections, mapOf(), listOf(), listOf())
    }

    private fun dumpUserDataV20(): Dump {
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
        val enabledWords = mutableListOf<WordId>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                if (cursor.getInt(2) != 0)
                    enabledWords.add(WordId(cursor.getString(0), cursor.getString(1)))
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
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, kanjiSelections, mapOf(), listOf(), listOf())
    }

    private fun dumpUserDataV21(): Dump {
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
        val enabledWords = mutableListOf<WordId>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                if (cursor.getInt(2) != 0)
                    enabledWords.add(WordId(cursor.getString(0), cursor.getString(1)))
        }
        val wordSelections = mutableMapOf<String, MutableList<WordId>>()
        database.rawQuery("""
                SELECT ws.name, w.item, w.reading
                FROM ${Database.WORDS_SELECTION_TABLE_NAME} ws
                LEFT JOIN ${Database.WORDS_ITEM_SELECTION_TABLE_NAME} wis USING(id_selection)
                JOIN ${Database.WORDS_TABLE_NAME} w ON w.id = wis.id_word
            """, null).use { cursor ->
            while (cursor.moveToNext())
                wordSelections.getOrPut(cursor.getString(0)) { mutableListOf() }.add(
                        WordId(cursor.getString(1), cursor.getString(2)))
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
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, kanjiSelections, wordSelections, listOf(), listOf())
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
        val enabledWords = mutableListOf<WordId>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "reading", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                if (cursor.getInt(2) != 0)
                    enabledWords.add(WordId(cursor.getString(0), cursor.getString(1)))
        }
        val wordSelections = mutableMapOf<String, MutableList<WordId>>()
        database.rawQuery("""
                SELECT ws.name, w.item, w.reading
                FROM ${Database.WORDS_SELECTION_TABLE_NAME} ws
                LEFT JOIN ${Database.WORDS_ITEM_SELECTION_TABLE_NAME} wis USING(id_selection)
                JOIN ${Database.WORDS_TABLE_NAME} w ON w.id = wis.id_word
            """, null).use { cursor ->
            while (cursor.moveToNext())
                wordSelections.getOrPut(cursor.getString(0)) { mutableListOf() }.add(
                        WordId(cursor.getString(1), cursor.getString(2)))
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
        val sessionItems = mutableMapOf<Long, MutableList<DumpSessionItem>>()
        database.rawQuery("""
                SELECT si.id_session, si.test_type, si.id_item_question, si.id_item_wrong, si.certainty, si.time
                FROM ${Database.SESSION_ITEMS_TABLE_NAME} si
                WHERE id_item_question < $WordBaseId
            """, null).use { cursor ->
            while (cursor.moveToNext())
                sessionItems.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(DumpSessionItem(
                        TestType.fromInt(cursor.getInt(1)),
                        DumpSessionItemContent.Normal(
                                cursor.getLong(2),
                                if (cursor.isNull(3)) null
                                else cursor.getLong(3)),
                        Certainty.fromInt(cursor.getInt(4)),
                        cursor.getLong(5)
                ))
        }
        database.rawQuery("""
                SELECT si.id_session, si.test_type, qw.item, ww.item, si.certainty, si.time, qw.reading, ww.reading
                FROM ${Database.SESSION_ITEMS_TABLE_NAME} si
                JOIN ${Database.WORDS_TABLE_NAME} qw ON qw.id = si.id_item_question
                LEFT JOIN ${Database.WORDS_TABLE_NAME} ww ON ww.id = si.id_item_wrong
                WHERE id_item_question >= $WordBaseId
            """, null).use { cursor ->
            while (cursor.moveToNext())
                sessionItems.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(DumpSessionItem(
                        TestType.fromInt(cursor.getInt(1)),
                        DumpSessionItemContent.Word(
                                WordId(cursor.getString(2), cursor.getString(6)),
                                if (cursor.isNull(3)) null
                                else WordId(cursor.getString(3), cursor.getString(7))),
                        Certainty.fromInt(cursor.getInt(4)),
                        cursor.getLong(5)
                ))
        }
        val sessions = mutableListOf<DumpSession>()
        database.rawQuery("""
                SELECT s.id, s.item_type, s.test_types, s.start_time
                FROM ${Database.SESSIONS_TABLE_NAME} s
            """, null).use { cursor ->
            while (cursor.moveToNext()) {
                val items = sessionItems[cursor.getLong(0)] ?: continue
                sessions.add(DumpSession(
                        ItemType.fromInt(cursor.getInt(1)),
                        cursor.getString(2),
                        cursor.getLong(3),
                        items,
                ))
            }
        }
        val snapshots = mutableListOf<DumpStatsSnapshot>()
        database.rawQuery("""
                SELECT s.item_type, s.knowledge_type, s.time, s.good_count, s.meh_count, s.bad_count, s.long_score_partition, s.long_score_sum
                FROM ${Database.STATS_SNAPSHOT_TABLE_NAME} s
            """, null).use { cursor ->
            while (cursor.moveToNext()) {
                snapshots.add(DumpStatsSnapshot(
                        ItemType.fromInt(cursor.getInt(0)),
                        KnowledgeType.fromInt(cursor.getInt(1)),
                        cursor.getLong(2),
                        cursor.getInt(3),
                        cursor.getInt(4),
                        cursor.getInt(5),
                        cursor.getString(6),
                        cursor.getFloat(7),
                ))
            }
        }
        return Dump(enabledKanas, enabledKanjis, enabledWords, scores, wordScores, kanjiSelections, wordSelections, snapshots, sessions)
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

    private fun enableOnlyWords(tableName: String, toEnable: List<WordId>) {
        run {
            val cv = ContentValues()
            cv.put("enabled", 0)
            database.update(tableName, cv, null, null)
        }
        for (row in toEnable) {
            val cv = ContentValues()
            cv.put("enabled", 1)
            database.update(tableName, cv, "item = ? AND reading = ?", arrayOf(row.item, row.reading))
        }
    }

    private fun restoreUserData(data: Dump) {
        database.transaction {
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
            run {
                database.delete(Database.WORDS_ITEM_SELECTION_TABLE_NAME, null, null)
                database.delete(Database.WORDS_SELECTION_TABLE_NAME, null, null)
                for ((selectionName, selectionItems) in data.wordSelections) {
                    val cv = ContentValues()
                    cv.put("name", selectionName)
                    val selectionId = database.insertOrThrow(Database.WORDS_SELECTION_TABLE_NAME, null, cv)
                    for (item in selectionItems) {
                        val wordId = database.rawQuery(
                                """SELECT id
                                 FROM ${Database.WORDS_TABLE_NAME}
                                 WHERE item = ? AND reading = ?""",
                                arrayOf(item.item, item.reading)).use { cursor ->
                            if (cursor.count == 0)
                                return@use null
                            cursor.moveToFirst()
                            return@use cursor.getInt(0)
                        } ?: continue

                        val cv = ContentValues()
                        cv.put("id_selection", selectionId)
                        cv.put("id_word", wordId)
                        database.insertOrThrow(Database.WORDS_ITEM_SELECTION_TABLE_NAME, null, cv)
                    }
                }
            }
            run {
                database.delete(Database.STATS_SNAPSHOT_TABLE_NAME, null, null)
                for (snapshot in data.statsSnapshots) {
                    val cv = ContentValues()
                    cv.put("item_type", snapshot.itemType.value)
                    cv.put("knowledge_type", snapshot.knowledgeType.value)
                    cv.put("time", snapshot.time)
                    cv.put("good_count", snapshot.goodCount)
                    cv.put("meh_count", snapshot.mehCount)
                    cv.put("bad_count", snapshot.badCount)
                    cv.put("long_score_partition", snapshot.longPartition)
                    cv.put("long_score_sum", snapshot.longSum)
                    database.insertOrThrow(Database.STATS_SNAPSHOT_TABLE_NAME, null, cv)
                }
            }
            run {
                database.delete(Database.SESSIONS_TABLE_NAME, null, null)
                database.delete(Database.SESSION_ITEMS_TABLE_NAME, null, null)
                for (session in data.sessions) {
                    val cv = ContentValues()
                    cv.put("item_type", session.itemType.value)
                    cv.put("test_types", session.testTypes)
                    cv.put("start_time", session.startTime)
                    val sessionId = database.insertOrThrow(Database.SESSIONS_TABLE_NAME, null, cv)
                    for (item in session.items) {
                        val cv = ContentValues()
                        cv.put("id_session", sessionId)
                        cv.put("test_type", item.testType.value)
                        cv.put("certainty", item.certainty.value)
                        cv.put("time", item.time)
                        when (item.content) {
                            is DumpSessionItemContent.Normal -> {
                                cv.put("id_item_question", item.content.question)
                                cv.put("id_item_wrong", item.content.wrong)
                            }
                            is DumpSessionItemContent.Word -> {
                                val wordId = database.rawQuery(
                                        """SELECT id
                                             FROM ${Database.WORDS_TABLE_NAME}
                                             WHERE item = ? AND reading = ?""",
                                        arrayOf(item.content.question.item, item.content.question.reading)).use { cursor ->
                                    if (cursor.count == 0)
                                        return@use null
                                    cursor.moveToFirst()
                                    return@use cursor.getInt(0)
                                } ?: continue
                                cv.put("id_item_question", wordId)
                                if (item.content.wrong != null) {
                                    val wrongWordId = database.rawQuery(
                                            """SELECT id
                                             FROM ${Database.WORDS_TABLE_NAME}
                                             WHERE item = ? AND reading = ?""",
                                            arrayOf(item.content.wrong.item, item.content.wrong.reading)).use { cursor ->
                                        if (cursor.count == 0)
                                            return@use null
                                        cursor.moveToFirst()
                                        return@use cursor.getInt(0)
                                    }
                                    cv.put("id_item_wrong", wrongWordId)
                                }
                            }
                        }
                        database.insertOrThrow(Database.SESSION_ITEMS_TABLE_NAME, null, cv)
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "DatabaseUpdater"
        const val DATABASE_VERSION = 25

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
            context.getDatabasePath(Database.DATABASE_NAME).parentFile!!.mkdirs()
            SQLiteDatabase.openDatabase(context.getDatabasePath(Database.DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
                DatabaseUpdater(db, dictDb).doUpgrade()
            }
        }
    }
}
