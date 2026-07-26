package org.kaqui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.kaqui.model.Database
import org.kaqui.model.Database.Companion.KANAS_TABLE_NAME
import org.kaqui.model.DatabaseUpdater
import org.kaqui.model.Kanji
import org.kaqui.model.Word
import java.io.File
import java.util.zip.GZIPInputStream

@RunWith(AndroidJUnit4::class)
class DatabaseUpdaterTest {
    companion object {
        private lateinit var context: Context
        private lateinit var dictDb: File

        @BeforeClass
        @JvmStatic
        fun unzipDict() {
            context = InstrumentationRegistry.getInstrumentation().targetContext
            dictDb = File.createTempFile("dict", "", context.cacheDir)
            context.resources.openRawResource(R.raw.dict).use { gzipStream ->
                GZIPInputStream(gzipStream, 1024).use { textStream ->
                    dictDb.outputStream().use { outputStream ->
                        textStream.copyTo(outputStream)
                    }
                }
            }
        }

        const val HIRAGANAS_TABLE_NAME = "hiraganas"
        const val HIRAGANA_STROKES_TABLE_NAME = "hiragana_strokes"
        const val SIMILAR_HIRAGANAS_TABLE_NAME = "similar_hiraganas"

        const val KATAKANAS_TABLE_NAME = "katakanas"
        const val KATAKANA_STROKES_TABLE_NAME = "katakana_strokes"
        const val SIMILAR_KATAKANAS_TABLE_NAME = "similar_katakanas"

        const val KANJIS_TABLE_NAME = "kanjis"
        const val SIMILARITIES_TABLE_NAME = "similarities"
        const val KANJIS_SELECTION_TABLE_NAME = "kanjis_selection"
        const val KANJIS_ITEM_SELECTION_TABLE_NAME = "kanjis_item_selection"
        const val STROKES_TABLE_NAME = "strokes"
        const val KANJIS_COMPOSITION_TABLE_NAME = "kanjis_composition"

        const val WORDS_TABLE_NAME = "words"
        const val WORDS_SELECTION_TABLE_NAME = "word_selection"
        const val WORDS_ITEM_SELECTION_TABLE_NAME = "word_item_selection"

        const val ITEM_SCORES_TABLE_NAME = "item_scores"

        const val SESSIONS_TABLE_NAME = "sessions"
        const val SESSION_ITEMS_TABLE_NAME = "session_items"
        const val STATS_SNAPSHOT_TABLE_NAME = "stats_snapshots"

        const val SIMILAR_ITEMS_TABLE_NAME = "similar_items"
        const val ITEM_STROKES_TABLE_NAME = "item_strokes"
    }

    private fun createV11Tables(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_TABLE_NAME ("
                        + "id INTEGER PRIMARY KEY,"
                        + "item TEXT NOT NULL UNIQUE,"
                        + "on_readings TEXT NOT NULL DEFAULT '',"
                        + "kun_readings TEXT NOT NULL DEFAULT '',"
                        + "meanings TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_TABLE_NAME (id, item, on_readings, kun_readings, meanings, jlpt_level, short_score, long_score, last_correct, enabled)"
                        + "VALUES (50, '人', 'ジン', 'ひと', 'person', 5, 0.5, 0.4, 10, 1)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $SIMILARITIES_TABLE_NAME ("
                        + "id_similarity INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "UNIQUE(id_kanji1, id_kanji2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $WORDS_TABLE_NAME ("
                        + "id INTEGER PRIMARY KEY,"
                        + "item TEXT NOT NULL,"
                        + "reading TEXT NOT NULL DEFAULT '',"
                        + "meanings TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1,"
                        + "UNIQUE(item, reading)"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings, jlpt_level, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000010, '人', 'ひと', 'person', 5, 0.5, 0.4, 10, 1)")
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings, jlpt_level, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000020, '人', 'じん', 'person', 5, 0.5, 0.4, 10, 0)")

        initKanasV11(database, HIRAGANAS_TABLE_NAME, SIMILAR_HIRAGANAS_TABLE_NAME)
        database.execSQL(
                "INSERT INTO $HIRAGANAS_TABLE_NAME (id_kana, kana, romaji, short_score, long_score, last_correct, enabled)"
                        + "VALUES (10, 'あ', 'a', 0.5, 0.4, 10, 1)")
        initKanasV11(database, KATAKANAS_TABLE_NAME, SIMILAR_KATAKANAS_TABLE_NAME)

        database.version = 11
    }

    private fun initKanasV11(database: SQLiteDatabase, tableName: String, similarKanaTableName: String) {
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
    }

    private fun createV15Tables(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_TABLE_NAME ("
                        + "id INTEGER PRIMARY KEY,"
                        + "item TEXT NOT NULL UNIQUE,"
                        + "on_readings TEXT NOT NULL DEFAULT '',"
                        + "kun_readings TEXT NOT NULL DEFAULT '',"
                        + "meanings TEXT NOT NULL DEFAULT '',"
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
                "INSERT INTO $KANJIS_TABLE_NAME (id, item, on_readings, kun_readings, meanings, jlpt_level, kaqui_level, part_count, radical, short_score, long_score, last_correct, enabled)"
                        + "VALUES (50, '人', 'ジン', 'ひと', 'person', 5, 1, 1, 0, 0.5, 0.4, 10, 1)")
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
                "CREATE TABLE IF NOT EXISTS $KANJIS_SELECTION_TABLE_NAME ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (999, 'test selection')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_ITEM_SELECTION_TABLE_NAME ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL,"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)"
                        + "VALUES (999, 50)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $WORDS_TABLE_NAME ("
                        + "id INTEGER PRIMARY KEY,"
                        + "item TEXT NOT NULL,"
                        + "reading TEXT NOT NULL DEFAULT '',"
                        + "meanings TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "similarity_class INTEGER NOT NULL DEFAULT 0,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1,"
                        + "UNIQUE(item, reading)"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings, jlpt_level, similarity_class, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000010, '人', 'ひと', 'person', 5, 22, 0.5, 0.4, 10, 1)")
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings, jlpt_level, similarity_class, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000020, '人', 'じん', 'person', 5, 22, 0.5, 0.4, 10, 0)")

        initKanasV16(database, HIRAGANAS_TABLE_NAME, SIMILAR_HIRAGANAS_TABLE_NAME)
        database.execSQL(
                "INSERT INTO $HIRAGANAS_TABLE_NAME (id_kana, kana, romaji, short_score, long_score, last_correct, enabled)"
                        + "VALUES (10, 'あ', 'a', 0.5, 0.4, 10, 1)")
        initKanasV16(database, KATAKANAS_TABLE_NAME, SIMILAR_KATAKANAS_TABLE_NAME)

        database.version = 15
    }

    private fun createV16Tables(database: SQLiteDatabase) {
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
                "INSERT INTO $KANJIS_TABLE_NAME (id, item, on_readings, kun_readings, meanings_en, meanings_fr, jlpt_level, kaqui_level, part_count, radical, short_score, long_score, last_correct, enabled)"
                        + "VALUES (50, '人', 'ジン', 'ひと', 'person', 'personne', 5, 1, 1, 0, 0.5, 0.4, 10, 1)")
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
                "CREATE TABLE IF NOT EXISTS $KANJIS_SELECTION_TABLE_NAME ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (999, 'test selection')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_ITEM_SELECTION_TABLE_NAME ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL,"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)"
                        + "VALUES (999, 50)")
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
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000010, '人', 'ひと', 'person', 'personne', 5, 22, 0.5, 0.4, 10, 1)")
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000020, '人', 'じん', 'person', 'personne', 5, 22, 0.5, 0.4, 10, 0)")

        initKanasV16(database, HIRAGANAS_TABLE_NAME, SIMILAR_HIRAGANAS_TABLE_NAME)
        database.execSQL(
                "INSERT INTO $HIRAGANAS_TABLE_NAME (id_kana, kana, romaji, short_score, long_score, last_correct, enabled)"
                        + "VALUES (10, 'あ', 'a', 0.5, 0.4, 10, 1)")
        initKanasV16(database, KATAKANAS_TABLE_NAME, SIMILAR_KATAKANAS_TABLE_NAME)

        database.version = 16
    }

    private fun initKanasV16(database: SQLiteDatabase, tableName: String, similarKanaTableName: String) {
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
    }

    private fun createV17Tables(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_TABLE_NAME} ("
                        + "id INTEGER PRIMARY KEY NOT NULL,"
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
                "INSERT INTO $KANJIS_TABLE_NAME (id, on_readings, kun_readings, meanings_en, meanings_fr, jlpt_level, kaqui_level, part_count, radical, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x4EBA, 'ジン', 'ひと', 'person', 'personne', 5, 1, 1, 0, 0.5, 0.4, 10, 1)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${STROKES_TABLE_NAME} ("
                        + "id INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "ordinal INT NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE(id_kanji, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_COMPOSITION_TABLE_NAME} ("
                        + "id_composition INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${SIMILARITIES_TABLE_NAME} ("
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY (id_kanji1, id_kanji2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (999, 'test selection')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)"
                        + "VALUES (999, 0x4EBA)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_TABLE_NAME} ("
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
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000010, '人', 'ひと', 'person', 'personne', 5, 22, 0.5, 0.4, 10, 1)")
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000020, '人', 'じん', 'person', 'personne', 5, 22, 0.5, 0.4, 10, 0)")

        initKanasV17(database, HIRAGANAS_TABLE_NAME, SIMILAR_HIRAGANAS_TABLE_NAME, HIRAGANA_STROKES_TABLE_NAME)
        database.execSQL(
                "INSERT INTO $HIRAGANAS_TABLE_NAME (id, romaji, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x3042, 'a', 0.5, 0.4, 10, 1)")
        initKanasV17(database, KATAKANAS_TABLE_NAME, SIMILAR_KATAKANAS_TABLE_NAME, KATAKANA_STROKES_TABLE_NAME)

        database.version = 17
    }

    private fun initKanasV17(database: SQLiteDatabase, tableName: String, similarKanaTableName: String, strokesTableName: String) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $tableName ("
                        + "id INTEGER PRIMARY KEY NOT NULL,"
                        + "romaji TEXT NOT NULL DEFAULT '',"
                        + "unique_romaji TEXT NOT NULL DEFAULT '',"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $strokesTableName ("
                        + "id INTEGER PRIMARY KEY NOT NULL,"
                        + "id_kana INTEGER NOT NULL,"
                        + "ordinal INTEGER NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE (id_kana, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $similarKanaTableName ("
                        + "id_kana1 INTEGER NOT NULL REFERENCES $tableName(id),"
                        + "id_kana2 INTEGER NOT NULL REFERENCES $tableName(id),"
                        + "PRIMARY KEY (id_kana1, id_kana2)"
                        + ")")
    }

    private fun createV18Tables(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_TABLE_NAME} ("
                        + "id INTEGER PRIMARY KEY NOT NULL,"
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
                "INSERT INTO $KANJIS_TABLE_NAME (id, on_readings, kun_readings, meanings_en, meanings_fr, jlpt_level, kaqui_level, part_count, radical, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x4EBA, 'ジン', 'ひと', 'person', 'personne', 5, 1, 1, 0, 0.5, 0.4, 10, 1)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${STROKES_TABLE_NAME} ("
                        + "id INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "ordinal INT NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE(id_kanji, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_COMPOSITION_TABLE_NAME} ("
                        + "id_composition INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${SIMILARITIES_TABLE_NAME} ("
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY (id_kanji1, id_kanji2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (999, 'test selection')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)"
                        + "VALUES (999, 0x4EBA)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_TABLE_NAME} ("
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
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000010, '人', 'ひと', 'person', 'personne', 5, 22, 0.5, 0.4, 10, 1)")
        database.execSQL(
                "INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x1000020, '人', 'じん', 'person', 'personne', 5, 22, 0.8, 0.9, 15, 0)")

        initKanasV18(database, HIRAGANAS_TABLE_NAME)
        database.execSQL(
                "INSERT INTO $HIRAGANAS_TABLE_NAME (id, romaji, short_score, long_score, last_correct, enabled)"
                        + "VALUES (0x3042, 'a', 0.5, 0.4, 10, 1)")
        initKanasV18(database, KATAKANAS_TABLE_NAME)

        database.version = 18
    }

    private fun initKanasV18(database: SQLiteDatabase, tableName: String) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $tableName ("
                        + "id INTEGER PRIMARY KEY NOT NULL,"
                        + "romaji TEXT NOT NULL DEFAULT '',"
                        + "unique_romaji TEXT NOT NULL DEFAULT '',"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
    }

    private fun createV21Tables(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "on_readings TEXT NOT NULL DEFAULT '',"
                        + "kun_readings TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "meanings_es TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "rtk_index INTEGER NOT NULL DEFAULT 0,"
                        + "rtk6_index INTEGER NOT NULL DEFAULT 0,"
                        + "part_count INTEGER NOT NULL DEFAULT 0,"
                        + "radical INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_TABLE_NAME "
                        + "VALUES (0x4EBA, 'ジン', 'ひと', 'person', 'personne', 'persona', 5, 38, 39, 2, 0, 1)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${ITEM_STROKES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "id_item INTEGER NOT NULL,"
                        + "ordinal INTEGER NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE(id_item, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${SIMILAR_ITEMS_TABLE_NAME} ("
                        + "id_item1 INTEGER NOT NULL,"
                        + "id_item2 INTEGER NOT NULL,"
                        + "PRIMARY KEY (id_item1, id_item2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_COMPOSITION_TABLE_NAME} ("
                        + "id_composition INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (999, 'test selection')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)"
                        + "VALUES (999, 0x4EBA)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (800, 'test selection 2')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_word INTEGER NOT NULL REFERENCES words(id),"
                        + "PRIMARY KEY(id_selection, id_word)"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_ITEM_SELECTION_TABLE_NAME (id_selection, id_word)"
                        + "VALUES (800, 0x1000010)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "item TEXT NOT NULL,"
                        + "reading TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "meanings_es TEXT NOT NULL DEFAULT '',"
                        + "kana_alone INTEGER NOT NULL DEFAULT 0,"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "rtk_index INTEGER NOT NULL DEFAULT 0,"
                        + "rtk6_index INTEGER NOT NULL DEFAULT 0,"
                        + "similarity_class INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1,"
                        + "UNIQUE(item, reading)"
                        + ")")
        database.execSQL("""
                INSERT INTO $WORDS_TABLE_NAME VALUES
                (0x1000010, '人', 'ひと', 'person', 'personne', 'persona', 0, 5, 22, 22, 8, 1),
                (0x1000020, '人', 'じん', 'person', 'personne', 'persona', 0, 5, 22, 22, 8, 0)
        """)

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANAS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "romaji TEXT NOT NULL DEFAULT '',"
                        + "unique_romaji TEXT NOT NULL DEFAULT '',"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANAS_TABLE_NAME (id, romaji, unique_romaji, enabled)"
                        + "VALUES (0x3042, 'a', 'a', 1)")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${ITEM_SCORES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL,"
                        + "type INTEGER NOT NULL,"
                        + "short_score FLOAT NOT NULL,"
                        + "long_score FLOAT NOT NULL,"
                        + "last_correct INTEGER NOT NULL,"
                        + "PRIMARY KEY (id, type)"
                        + ")")
        database.execSQL("""
            INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES
            (0x1000010, 1, 0.5, 0.4, 10),
            (0x1000020, 1, 0.8, 0.9, 15),
            (0x4EBA, 2, 0.5, 0.4, 10),
            (0x3042, 3, 0.5, 0.4, 10)
            """)

        database.version = 21
    }

    private fun createV22Tables(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "on_readings TEXT NOT NULL DEFAULT '',"
                        + "kun_readings TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "meanings_es TEXT NOT NULL DEFAULT '',"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "rtk_index INTEGER NOT NULL DEFAULT 0,"
                        + "rtk6_index INTEGER NOT NULL DEFAULT 0,"
                        + "part_count INTEGER NOT NULL DEFAULT 0,"
                        + "radical INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_TABLE_NAME "
                        + "VALUES (0x4EBA, 'ジン', 'ひと', 'person', 'personne', 'persona', 5, 38, 39, 2, 0, 1)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${ITEM_STROKES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "id_item INTEGER NOT NULL,"
                        + "ordinal INTEGER NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE(id_item, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${SIMILAR_ITEMS_TABLE_NAME} ("
                        + "id_item1 INTEGER NOT NULL,"
                        + "id_item2 INTEGER NOT NULL,"
                        + "PRIMARY KEY (id_item1, id_item2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_COMPOSITION_TABLE_NAME} ("
                        + "id_composition INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (999, 'test selection')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)"
                        + "VALUES (999, 0x4EBA)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (800, 'test selection 2')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_word INTEGER NOT NULL REFERENCES words(id),"
                        + "PRIMARY KEY(id_selection, id_word)"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_ITEM_SELECTION_TABLE_NAME (id_selection, id_word)"
                        + "VALUES (800, 0x1000010)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "item TEXT NOT NULL,"
                        + "reading TEXT NOT NULL DEFAULT '',"
                        + "meanings_en TEXT NOT NULL DEFAULT '',"
                        + "meanings_fr TEXT NOT NULL DEFAULT '',"
                        + "meanings_es TEXT NOT NULL DEFAULT '',"
                        + "kana_alone INTEGER NOT NULL DEFAULT 0,"
                        + "jlpt_level INTEGER NOT NULL DEFAULT 0,"
                        + "rtk_index INTEGER NOT NULL DEFAULT 0,"
                        + "rtk6_index INTEGER NOT NULL DEFAULT 0,"
                        + "similarity_class INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1,"
                        + "UNIQUE(item, reading)"
                        + ")")
        database.execSQL("""
                INSERT INTO $WORDS_TABLE_NAME VALUES
                (0x1000010, '人', 'ひと', 'person', 'personne', 'persona', 0, 5, 22, 22, 8, 1),
                (0x1000020, '人', 'じん', 'person', 'personne', 'persona', 0, 5, 22, 22, 8, 0)
        """)

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANAS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "romaji TEXT NOT NULL DEFAULT '',"
                        + "unique_romaji TEXT NOT NULL DEFAULT '',"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANAS_TABLE_NAME (id, romaji, unique_romaji, enabled)"
                        + "VALUES (0x3042, 'a', 'a', 1)")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${ITEM_SCORES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL,"
                        + "type INTEGER NOT NULL,"
                        + "short_score FLOAT NOT NULL,"
                        + "long_score FLOAT NOT NULL,"
                        + "last_correct INTEGER NOT NULL,"
                        + "PRIMARY KEY (id, type)"
                        + ")")
        database.execSQL("""
            INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES
            (0x1000010, 1, 0.5, 0.4, 10),
            (0x1000020, 1, 0.8, 0.9, 15),
            (0x4EBA, 2, 0.5, 0.4, 10),
            (0x3042, 3, 0.5, 0.4, 10)
            """)

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $STATS_SNAPSHOT_TABLE_NAME ("
                        + "item_type INTEGER NOT NULL,"
                        + "knowledge_type INTEGER NOT NULL,"
                        + "time INTEGER NOT NULL,"
                        + "good_count INTEGER NOT NULL,"
                        + "meh_count INTEGER NOT NULL,"
                        + "bad_count INTEGER NOT NULL,"
                        + "long_score_partition TEXT NOT NULL,"
                        + "long_score_sum FLOAT NOT NULL,"
                        + "PRIMARY KEY (item_type, knowledge_type, time)"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $SESSIONS_TABLE_NAME ("
                        + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                        + "item_type INTEGER NOT NULL,"
                        + "test_types TEXT NOT NULL,"
                        + "start_time INTEGER NOT NULL,"
                        + "end_time INTEGER,"
                        + "item_count INTEGER,"
                        + "correct_count INTEGER"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $SESSION_ITEMS_TABLE_NAME ("
                        + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                        + "id_session INTEGER NOT NULL,"
                        + "test_type INTEGER NOT NULL,"
                        + "id_item_question INTEGER NOT NULL,"
                        + "id_item_wrong INTEGER,"
                        + "certainty INTEGER NOT NULL,"
                        + "time INTEGER NOT NULL"
                        + ")")

        database.execSQL(
                "INSERT INTO $SESSIONS_TABLE_NAME (id, item_type, test_types, start_time, end_time, item_count, correct_count)"
                        + "VALUES (5, 1, '1', 1602600658, 1602600658+300, 1, 1)")
        database.execSQL(
                "INSERT INTO $SESSION_ITEMS_TABLE_NAME (id, id_session, test_type, id_item_question, id_item_wrong, certainty, time)"
                        + "VALUES (1, 5, 1, 100, NULL, 2, 1602600658+300)")
        database.execSQL(
                "INSERT INTO $SESSIONS_TABLE_NAME (id, item_type, test_types, start_time, end_time, item_count, correct_count)"
                        + "VALUES (6, 4, '13,14', 1602610658, NULL, NULL, NULL)")
        database.execSQL(
                "INSERT INTO $SESSION_ITEMS_TABLE_NAME (id, id_session, test_type, id_item_question, id_item_wrong, certainty, time)"
                        + "VALUES (2, 6, 3, 0x1000020, 0x1000010, 0, 1602610658+20)")

        database.execSQL(
                "INSERT INTO $STATS_SNAPSHOT_TABLE_NAME (item_type, knowledge_type, time, good_count, meh_count, bad_count, long_score_partition, long_score_sum)"
                        + "VALUES (2, 2, 1602610000, 76, 12, 3, '28,11,19,9,3', 45.0)")

        database.version = 22
    }

    private fun createV24Tables(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_TABLE_NAME} ("
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
                "INSERT INTO $KANJIS_TABLE_NAME "
                        + "VALUES (0x4EBA, 'ジン', 'ひと', 'person', 'personne', 'persona', 'Person', 5, 38, 39, 2, 0, 1)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${ITEM_STROKES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "id_item INTEGER NOT NULL,"
                        + "ordinal INTEGER NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE(id_item, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${SIMILAR_ITEMS_TABLE_NAME} ("
                        + "id_item1 INTEGER NOT NULL,"
                        + "id_item2 INTEGER NOT NULL,"
                        + "PRIMARY KEY (id_item1, id_item2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_COMPOSITION_TABLE_NAME} ("
                        + "id_composition INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (999, 'test selection')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)"
                        + "VALUES (999, 0x4EBA)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (800, 'test selection 2')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_word INTEGER NOT NULL REFERENCES words(id),"
                        + "PRIMARY KEY(id_selection, id_word)"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_ITEM_SELECTION_TABLE_NAME (id_selection, id_word)"
                        + "VALUES (800, 0x1000010)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_TABLE_NAME} ("
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
        database.execSQL("""
                INSERT INTO $WORDS_TABLE_NAME VALUES
                (0x1000010, '人', 'ひと', 'person', 'personne', 'persona', 'Person', 0, 5, 22, 22, 8, 1),
                (0x1000020, '人', 'じん', 'person', 'personne', 'persona', 'Person', 0, 5, 22, 22, 8, 0)
        """)

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANAS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "romaji TEXT NOT NULL DEFAULT '',"
                        + "unique_romaji TEXT NOT NULL DEFAULT '',"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANAS_TABLE_NAME (id, romaji, unique_romaji, enabled)"
                        + "VALUES (0x3042, 'a', 'a', 1)")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${ITEM_SCORES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL,"
                        + "type INTEGER NOT NULL,"
                        + "short_score FLOAT NOT NULL,"
                        + "long_score FLOAT NOT NULL,"
                        + "last_correct INTEGER NOT NULL,"
                        + "PRIMARY KEY (id, type)"
                        + ")")
        database.execSQL("""
            INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES
            (0x1000010, 1, 0.5, 0.4, 10),
            (0x1000020, 1, 0.8, 0.9, 15),
            (0x4EBA, 2, 0.5, 0.4, 10),
            (0x3042, 3, 0.5, 0.4, 10)
            """)

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $STATS_SNAPSHOT_TABLE_NAME ("
                        + "item_type INTEGER NOT NULL,"
                        + "knowledge_type INTEGER NOT NULL,"
                        + "time INTEGER NOT NULL,"
                        + "good_count INTEGER NOT NULL,"
                        + "meh_count INTEGER NOT NULL,"
                        + "bad_count INTEGER NOT NULL,"
                        + "long_score_partition TEXT NOT NULL,"
                        + "long_score_sum FLOAT NOT NULL,"
                        + "PRIMARY KEY (item_type, knowledge_type, time)"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $SESSIONS_TABLE_NAME ("
                        + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                        + "item_type INTEGER NOT NULL,"
                        + "test_types TEXT NOT NULL,"
                        + "start_time INTEGER NOT NULL,"
                        + "end_time INTEGER,"
                        + "item_count INTEGER,"
                        + "correct_count INTEGER"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $SESSION_ITEMS_TABLE_NAME ("
                        + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                        + "id_session INTEGER NOT NULL,"
                        + "test_type INTEGER NOT NULL,"
                        + "id_item_question INTEGER NOT NULL,"
                        + "id_item_wrong INTEGER,"
                        + "certainty INTEGER NOT NULL,"
                        + "time INTEGER NOT NULL"
                        + ")")

        database.execSQL(
                "INSERT INTO $SESSIONS_TABLE_NAME (id, item_type, test_types, start_time, end_time, item_count, correct_count)"
                        + "VALUES (5, 1, '1', 1602600658, 1602600658+300, 1, 1)")
        database.execSQL(
                "INSERT INTO $SESSION_ITEMS_TABLE_NAME (id, id_session, test_type, id_item_question, id_item_wrong, certainty, time)"
                        + "VALUES (1, 5, 1, 100, NULL, 2, 1602600658+300)")
        database.execSQL(
                "INSERT INTO $SESSIONS_TABLE_NAME (id, item_type, test_types, start_time, end_time, item_count, correct_count)"
                        + "VALUES (6, 4, '13,14', 1602610658, NULL, NULL, NULL)")
        database.execSQL(
                "INSERT INTO $SESSION_ITEMS_TABLE_NAME (id, id_session, test_type, id_item_question, id_item_wrong, certainty, time)"
                        + "VALUES (2, 6, 3, 0x1000020, 0x1000010, 0, 1602610658+20)")

        database.execSQL(
                "INSERT INTO $STATS_SNAPSHOT_TABLE_NAME (item_type, knowledge_type, time, good_count, meh_count, bad_count, long_score_partition, long_score_sum)"
                        + "VALUES (2, 2, 1602610000, 76, 12, 3, '28,11,19,9,3', 45.0)")

        database.version = 24
    }

    private fun createV32Tables(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_TABLE_NAME} ("
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
                "INSERT INTO $KANJIS_TABLE_NAME "
                        + "VALUES (0x4EBA, 'ジン', 'ひと', 'person', 'personne', 'persona', 'Person', 5, 38, 39, 2, 0, 1)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${ITEM_STROKES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "id_item INTEGER NOT NULL,"
                        + "ordinal INTEGER NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE(id_item, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${SIMILAR_ITEMS_TABLE_NAME} ("
                        + "id_item1 INTEGER NOT NULL,"
                        + "id_item2 INTEGER NOT NULL,"
                        + "similarity_score FLOAT NOT NULL,"
                        + "PRIMARY KEY (id_item1, id_item2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_COMPOSITION_TABLE_NAME} ("
                        + "id_composition INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (999, 'test selection')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)"
                        + "VALUES (999, 0x4EBA)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_SELECTION_TABLE_NAME (id_selection, name)"
                        + "VALUES (800, 'test selection 2')")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_word INTEGER NOT NULL REFERENCES words(id),"
                        + "PRIMARY KEY(id_selection, id_word)"
                        + ")")
        database.execSQL(
                "INSERT INTO $WORDS_ITEM_SELECTION_TABLE_NAME (id_selection, id_word)"
                        + "VALUES (800, 0x1000010)")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${WORDS_TABLE_NAME} ("
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
        database.execSQL("""
                INSERT INTO $WORDS_TABLE_NAME VALUES
                (0x1000010, '人', 'ひと', 'person', 'personne', 'persona', 'Person', 0, 5, 22, 22, 8, 1),
                (0x1000020, '人', 'じん', 'person', 'personne', 'persona', 'Person', 0, 5, 22, 22, 8, 0)
        """)

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANAS_TABLE_NAME} ("
                        + "id INTEGER NOT NULL PRIMARY KEY,"
                        + "romaji TEXT NOT NULL DEFAULT '',"
                        + "unique_romaji TEXT NOT NULL DEFAULT '',"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "INSERT INTO $KANAS_TABLE_NAME (id, romaji, unique_romaji, enabled)"
                        + "VALUES (0x3042, 'a', 'a', 1)")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${ITEM_SCORES_TABLE_NAME} ("
                        + "id INTEGER NOT NULL,"
                        + "type INTEGER NOT NULL,"
                        + "short_score FLOAT NOT NULL,"
                        + "long_score FLOAT NOT NULL,"
                        + "last_correct INTEGER NOT NULL,"
                        + "PRIMARY KEY (id, type)"
                        + ")")
        database.execSQL("""
            INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES
            (0x1000010, 1, 0.5, 0.4, 10),
            (0x1000020, 1, 0.8, 0.9, 15),
            (0x4EBA, 2, 0.5, 0.4, 10),
            (0x3042, 3, 0.5, 0.4, 10)
            """)

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $STATS_SNAPSHOT_TABLE_NAME ("
                        + "item_type INTEGER NOT NULL,"
                        + "knowledge_type INTEGER NOT NULL,"
                        + "time INTEGER NOT NULL,"
                        + "good_count INTEGER NOT NULL,"
                        + "meh_count INTEGER NOT NULL,"
                        + "bad_count INTEGER NOT NULL,"
                        + "long_score_partition TEXT NOT NULL,"
                        + "long_score_sum FLOAT NOT NULL,"
                        + "PRIMARY KEY (item_type, knowledge_type, time)"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $SESSIONS_TABLE_NAME ("
                        + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                        + "item_type INTEGER NOT NULL,"
                        + "test_types TEXT NOT NULL,"
                        + "start_time INTEGER NOT NULL,"
                        + "end_time INTEGER,"
                        + "item_count INTEGER,"
                        + "correct_count INTEGER"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $SESSION_ITEMS_TABLE_NAME ("
                        + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
                        + "id_session INTEGER NOT NULL,"
                        + "test_type INTEGER NOT NULL,"
                        + "id_item_question INTEGER NOT NULL,"
                        + "id_item_wrong INTEGER,"
                        + "certainty INTEGER NOT NULL,"
                        + "time INTEGER NOT NULL"
                        + ")")

        database.execSQL(
                "INSERT INTO $SESSIONS_TABLE_NAME (id, item_type, test_types, start_time, end_time, item_count, correct_count)"
                        + "VALUES (5, 1, '1', 1602600658, 1602600658+300, 1, 1)")
        database.execSQL(
                "INSERT INTO $SESSION_ITEMS_TABLE_NAME (id, id_session, test_type, id_item_question, id_item_wrong, certainty, time)"
                        + "VALUES (1, 5, 1, 100, NULL, 2, 1602600658+300)")
        database.execSQL(
                "INSERT INTO $SESSIONS_TABLE_NAME (id, item_type, test_types, start_time, end_time, item_count, correct_count)"
                        + "VALUES (6, 4, '13,14', 1602610658, NULL, NULL, NULL)")
        database.execSQL(
                "INSERT INTO $SESSION_ITEMS_TABLE_NAME (id, id_session, test_type, id_item_question, id_item_wrong, certainty, time)"
                        + "VALUES (2, 6, 3, 0x1000020, 0x1000010, 0, 1602610658+20)")

        database.execSQL(
                "INSERT INTO $STATS_SNAPSHOT_TABLE_NAME (item_type, knowledge_type, time, good_count, meh_count, bad_count, long_score_partition, long_score_sum)"
                        + "VALUES (2, 2, 1602610000, 76, 12, 3, '28,11,19,9,3', 45.0)")

        database.version = 32
    }

    // Builds a v38 database: the words table now has one row per sense with an ent_seq column and
    // no UNIQUE(item, reading). Seeds 彼処 and は as the old (pre-split) dictionary bump stored them,
    // where all of an entry's foreign translations were lumped onto a single row. Individual scores
    // and selections are added by each test before migrating.
    private fun createV38Tables(database: SQLiteDatabase) {
        createV32Tables(database)
        database.execSQL("DELETE FROM $ITEM_SCORES_TABLE_NAME WHERE id >= 0x1000000")
        database.execSQL("DROP TABLE $WORDS_TABLE_NAME")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $WORDS_TABLE_NAME ("
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
                        + "ent_seq INTEGER NOT NULL DEFAULT 0,"
                        + "freq INTEGER NOT NULL DEFAULT 0,"
                        + "expr TEXT NOT NULL DEFAULT '',"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        // 彼処/あそこ: three senses sharing ent_seq 1000320, French only on the last ("that far") row
        database.execSQL("""
                INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings_en, meanings_fr, ent_seq) VALUES
                (0x1000100, '彼処', 'あそこ', 'there_over there_that place_yonder_you-know-where', '', 1000320),
                (0x1000101, '彼処', 'あそこ', 'genitals_private parts_nether regions', '', 1000320),
                (0x1000102, '彼処', 'あそこ', 'that far_that much_that point', 'là-bas_cet endroit-là_là', 1000320)
        """)
        // は/は: two different JMdict entries share the same (item, reading); French only on the
        // interjection entry (ent_seq 2069620), never on the topic-particle entry (ent_seq 2028920)
        database.execSQL("""
                INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings_en, meanings_fr, ent_seq) VALUES
                (0x1000200, 'は', 'は', 'indicates sentence topic_indicates contrast with another option (stated or unstated)_adds emphasis', '', 2028920),
                (0x1000201, 'は', 'は', 'yes_indeed_well_ha!_what?_huh?_sigh', 'oui_en effet_bien_ha !_quoi ?_hein ?_soupir', 2069620)
        """)
        database.version = 38
    }

    private fun createV39Tables(database: SQLiteDatabase) {
        createV38Tables(database)
        // A session asking the same kana twice, wrong then right, plus another kana never answered
        database.execSQL(
                "INSERT INTO $SESSIONS_TABLE_NAME (id, item_type, test_types, start_time, end_time, item_count, correct_count)"
                        + "VALUES (7, 1, '1', 1602620658, NULL, NULL, NULL)")
        database.execSQL(
                "INSERT INTO $SESSION_ITEMS_TABLE_NAME (id, id_session, test_type, id_item_question, id_item_wrong, certainty, time) VALUES"
                        + "(3, 7, 1, 100, 101, 0, 1602620658+10),"
                        + "(4, 7, 1, 100, NULL, 2, 1602620658+20),"
                        + "(5, 7, 1, 101, NULL, 0, 1602620658+30)")
        database.version = 39
    }

    // id in item_scores of the word matching (item, reading, meanings_en) in the migrated database
    private fun wordId(database: SQLiteDatabase, item: String, reading: String, meaningsEn: String): Int =
            database.rawQuery("SELECT id FROM $WORDS_TABLE_NAME WHERE item = ? AND reading = ? AND meanings_en = ?", arrayOf(item, reading, meaningsEn)).use { cursor ->
                assertTrue(cursor.moveToNext())
                cursor.getInt(0)
            }

    private fun wordScoreIds(database: SQLiteDatabase): List<Int> =
            database.rawQuery("SELECT id FROM $ITEM_SCORES_TABLE_NAME WHERE id >= 0x1000000 ORDER BY id", null).use { cursor ->
                val ids = mutableListOf<Int>()
                while (cursor.moveToNext())
                    ids.add(cursor.getInt(0))
                ids
            }

    private fun enabledWordIds(database: SQLiteDatabase, item: String, reading: String): List<Int> =
            database.rawQuery("SELECT id FROM $WORDS_TABLE_NAME WHERE enabled = 1 AND item = ? AND reading = ? ORDER BY id", arrayOf(item, reading)).use { cursor ->
                val ids = mutableListOf<Int>()
                while (cursor.moveToNext())
                    ids.add(cursor.getInt(0))
                ids
            }

    @Test
    fun v38RemapsLoneLumpRowToFirstSense() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV38Tables(db)
            // a curated user who narrowed 彼処 down to just the French-bearing (lump) sense, and also
            // has a score on it
            db.execSQL("UPDATE $WORDS_TABLE_NAME SET enabled = 0 WHERE id != 0x1000102")
            db.execSQL("INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES (0x1000102, 1, 0.5, 0.4, 10)")
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db, "fr").doUpgrade(dictDb.absolutePath)
            // both the enabled flag and the score should land on the sense they were reading in
            // French ("there"), not the sense whose English happened to sit on the lump row
            val there = wordId(db, "彼処", "あそこ", "there_over there_that place_yonder_you-know-where")
            assertEquals(listOf(there), enabledWordIds(db, "彼処", "あそこ"))
            assertEquals(listOf(there), wordScoreIds(db))
        }
    }

    @Test
    fun v38RemapsLoneEnabledLumpWithoutAnyScore() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV38Tables(db)
            // a curated user who enabled only the lump sense and never took a test
            db.execSQL("UPDATE $WORDS_TABLE_NAME SET enabled = 0 WHERE id != 0x1000102")
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db, "fr").doUpgrade(dictDb.absolutePath)
            val there = wordId(db, "彼処", "あそこ", "there_over there_that place_yonder_you-know-where")
            assertEquals(listOf(there), enabledWordIds(db, "彼処", "あそこ"))
        }
    }

    @Test
    fun v38KeepsMultipleReferencedSensesDistinct() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV38Tables(db)
            // a curated user who enabled two distinct senses of 彼処 on purpose
            db.execSQL("UPDATE $WORDS_TABLE_NAME SET enabled = 0 WHERE id NOT IN (0x1000100, 0x1000102)")
            db.execSQL("INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES (0x1000100, 1, 0.5, 0.4, 10)")
            db.execSQL("INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES (0x1000102, 1, 0.8, 0.9, 15)")
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db, "fr").doUpgrade(dictDb.absolutePath)
            // referencing several senses means no remap: each keeps its own meanings_en match
            val there = wordId(db, "彼処", "あそこ", "there_over there_that place_yonder_you-know-where")
            val thatFar = wordId(db, "彼処", "あそこ", "that far_that much_that point")
            assertEquals(listOf(there, thatFar).sorted(), wordScoreIds(db).sorted())
        }
    }

    @Test
    fun v38EnglishLocaleIsUnchanged() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV38Tables(db)
            db.execSQL("UPDATE $WORDS_TABLE_NAME SET enabled = 0 WHERE id != 0x1000102")
            db.execSQL("INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES (0x1000102, 1, 0.5, 0.4, 10)")
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db, "en").doUpgrade(dictDb.absolutePath)
            // an English user is matched purely on meanings_en, so the lump row keeps its own sense
            val thatFar = wordId(db, "彼処", "あそこ", "that far_that much_that point")
            assertEquals(listOf(thatFar), enabledWordIds(db, "彼処", "あそこ"))
            assertEquals(listOf(thatFar), wordScoreIds(db))
        }
    }

    @Test
    fun v39CuratedNonFirstSenseIsNotRemapped() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV38Tables(db)
            // an already-migrated (v39) user who curated 彼処 down to just its third sense
            db.execSQL("UPDATE $WORDS_TABLE_NAME SET enabled = 0 WHERE id != 0x1000102")
            db.version = 39
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db, "fr").doUpgrade(dictDb.absolutePath)
            // re-dumping an already-split database must not remap: "that far" stays "that far"
            val thatFar = wordId(db, "彼処", "あそこ", "that far_that much_that point")
            assertEquals(listOf(thatFar), enabledWordIds(db, "彼処", "あそこ"))
        }
    }

    @Test
    fun v38SurvivesFirstIdFallbackCollision() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV38Tables(db)
            // two senses whose English no longer matches any new row both fall back to the first
            // 彼処 row in findWordId; without a dedup guard they would violate the (id, type) PK
            db.execSQL("""
                    INSERT INTO $WORDS_TABLE_NAME (id, item, reading, meanings_en, meanings_fr, ent_seq) VALUES
                    (0x1000103, '彼処', 'あそこ', 'obsolete meaning one', '', 1000320),
                    (0x1000104, '彼処', 'あそこ', 'obsolete meaning two', '', 1000320)
            """)
            db.execSQL("INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES (0x1000103, 1, 0.5, 0.4, 10)")
            db.execSQL("INSERT INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES (0x1000104, 1, 0.8, 0.9, 15)")
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db, "fr").doUpgrade(dictDb.absolutePath)
            val there = wordId(db, "彼処", "あそこ", "there_over there_that place_yonder_you-know-where")
            // both collapsed onto the first 彼処 row, kept once, no crash
            assertEquals(listOf(there), wordScoreIds(db))
        }
    }

    private fun doChecksV20(database: SQLiteDatabase) {
        database.query(KANJIS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(0x4EBA, cursor.getInt(0))
            assertFalse(cursor.moveToNext())
        }
        database.query(ITEM_SCORES_TABLE_NAME, arrayOf("type", "short_score", "long_score", "last_correct"), "id = 0x4EBA", null, null, null, "type").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertTrue(cursor.moveToNext())
            assertEquals(3, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertFalse(cursor.moveToNext())
        }
        database.query(KANAS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(0x3042, cursor.getInt(0))
            assertFalse(cursor.moveToNext())
        }
        database.query(ITEM_SCORES_TABLE_NAME, arrayOf("type", "short_score", "long_score", "last_correct"), "id = 0x3042", null, null, null, "type").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertTrue(cursor.moveToNext())
            assertEquals(3, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertFalse(cursor.moveToNext())
        }
        var id = 0
        database.query(WORDS_TABLE_NAME, arrayOf("id, item"), "enabled = 1", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            id = cursor.getInt(0)
            assertEquals("人", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
        database.query(ITEM_SCORES_TABLE_NAME, arrayOf("type", "short_score", "long_score", "last_correct"), "id = $id", null, null, null, "type").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun doChecksV21(database: SQLiteDatabase) {
        database.query(KANJIS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(0x4EBA, cursor.getInt(0))
            assertFalse(cursor.moveToNext())
        }
        database.query(ITEM_SCORES_TABLE_NAME, arrayOf("type", "short_score", "long_score", "last_correct"), "id = 0x4EBA", null, null, null, "type").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertFalse(cursor.moveToNext())
        }
        database.query(KANAS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(0x3042, cursor.getInt(0))
            assertFalse(cursor.moveToNext())
        }
        database.query(ITEM_SCORES_TABLE_NAME, arrayOf("type", "short_score", "long_score", "last_correct"), "id = 0x3042", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(3, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertFalse(cursor.moveToNext())
        }
        database.query(KANJIS_ITEM_SELECTION_TABLE_NAME, arrayOf("COUNT(*)"), "id_kanji = 0x4EBA", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertFalse(cursor.moveToNext())
        }
        var id = 0
        database.query(WORDS_TABLE_NAME, arrayOf("id, item"), "enabled = 1", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            id = cursor.getInt(0)
            assertEquals("人", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
        database.query(ITEM_SCORES_TABLE_NAME, arrayOf("type", "short_score", "long_score", "last_correct"), "id = $id", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertEquals(0.5f, cursor.getFloat(1))
            assertEquals(0.4f, cursor.getFloat(2))
            assertEquals(10L, cursor.getLong(3))
            assertFalse(cursor.moveToNext())
        }
        database.query(WORDS_ITEM_SELECTION_TABLE_NAME, arrayOf("COUNT(*)"), "id_word = $id", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun doChecksV22(database: SQLiteDatabase) {
        database.query(SESSIONS_TABLE_NAME, arrayOf("id", "item_type", "test_types", "start_time", "end_time", "item_count", "correct_count", "unique_item_count", "unique_correct_count"), null, null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(1))
            assertEquals("1", cursor.getString(2))
            assertEquals(1602600658, cursor.getLong(3))
            assertEquals(1602600658+300, cursor.getLong(4))
            assertEquals(1, cursor.getInt(5))
            assertEquals(1, cursor.getInt(6))
            assertEquals(1, cursor.getInt(7))
            assertEquals(1, cursor.getInt(8))
            assertTrue(cursor.moveToNext())
            assertEquals(4, cursor.getInt(1))
            assertEquals("13,14", cursor.getString(2))
            assertEquals(1602610658, cursor.getLong(3))
            assertEquals(1602610658+20, cursor.getLong(4))
            assertEquals(1, cursor.getInt(5))
            assertEquals(0, cursor.getInt(6))
            assertEquals(1, cursor.getInt(7))
            assertEquals(0, cursor.getInt(8))
            assertFalse(cursor.moveToNext())
        }
        database.query(SESSION_ITEMS_TABLE_NAME, arrayOf("id", "id_session", "test_type", "id_item_question", "id_item_wrong", "certainty", "time"), "id == 1", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getLong(0))
            assertEquals(1, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(100, cursor.getInt(3))
            assertTrue(cursor.isNull(4))
            assertEquals(2, cursor.getInt(5))
            assertEquals(1602600658+300, cursor.getLong(6))
            assertFalse(cursor.moveToNext())
        }
        val hito = database.query(WORDS_TABLE_NAME, arrayOf("id"), "item = '人' AND reading = 'ひと'", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            cursor.getLong(0)
        }
        val jin = database.query(WORDS_TABLE_NAME, arrayOf("id"), "item = '人' AND reading = 'じん'", null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            cursor.getLong(0)
        }
        database.rawQuery("""
                SELECT id, id_session, test_type, id_item_question, id_item_wrong, certainty, time
                FROM $SESSION_ITEMS_TABLE_NAME
                WHERE id == 2
            """, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getLong(0))
            assertEquals(2, cursor.getLong(1))
            assertEquals(3, cursor.getInt(2))
            assertEquals(jin, cursor.getLong(3))
            assertEquals(hito, cursor.getLong(4))
            assertEquals(0, cursor.getInt(5))
            assertEquals(1602610658+20, cursor.getLong(6))
            assertFalse(cursor.moveToNext())
        }
        database.query(STATS_SNAPSHOT_TABLE_NAME, arrayOf("item_type", "knowledge_type", "time", "good_count", "meh_count", "bad_count", "long_score_partition", "long_score_sum"), null, null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals(2, cursor.getInt(1))
            assertEquals(1602610000, cursor.getLong(2))
            assertEquals(76, cursor.getInt(3))
            assertEquals(12, cursor.getInt(4))
            assertEquals(3, cursor.getInt(5))
            assertEquals("28,11,19,9,3", cursor.getString(6))
            assertEquals(45.0f, cursor.getFloat(7))
            assertFalse(cursor.moveToNext())
        }
    }


    @Test
    fun createFromV11() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV11Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            doChecksV20(db)
        }
    }

    @Test
    fun createFromV15() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV15Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            doChecksV20(db)
        }
    }

    @Test
    fun createFromV16() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV16Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            doChecksV20(db)
        }
    }

    @Test
    fun createFromV17() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV17Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            doChecksV20(db)
        }
    }

    @Test
    fun createFromV18() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV18Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            doChecksV20(db)
        }
    }

    @Test
    fun createFromV21() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV21Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            doChecksV21(db)
        }
    }

    @Test
    fun createFromV22() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV22Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            Database(InstrumentationRegistry.getInstrumentation().context, db).commitAllSessions()
            doChecksV21(db)
            doChecksV22(db)
        }
    }

    @Test
    fun createFromV24() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV24Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            Database(InstrumentationRegistry.getInstrumentation().context, db).commitAllSessions()
            doChecksV21(db)
            doChecksV22(db)
        }
    }

    @Test
    fun createFromV32() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV32Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            Database(InstrumentationRegistry.getInstrumentation().context, db).commitAllSessions()
            doChecksV21(db)
            doChecksV22(db)
        }
    }

    @Test
    fun createFromV39() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            createV39Tables(db)
        }
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            Database(InstrumentationRegistry.getInstrumentation().context, db).commitAllSessions()

            // The word session of the base fixture is dropped by the v38 word split, its item does
            // not exist anymore
            db.query(SESSIONS_TABLE_NAME, arrayOf("start_time", "end_time", "item_count", "correct_count", "unique_item_count", "unique_correct_count"), null, null, null, null, "start_time").use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(1602600658L, cursor.getLong(0))
                assertEquals(1602600658L+300, cursor.getLong(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals(1, cursor.getInt(5))
                // Three questions on two items, the one asked twice ends up counted as correct
                assertTrue(cursor.moveToNext())
                assertEquals(1602620658L, cursor.getLong(0))
                assertEquals(1602620658L+30, cursor.getLong(1))
                assertEquals(3, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(2, cursor.getInt(4))
                assertEquals(1, cursor.getInt(5))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    @Test
    fun localizedMeaningsFallBackToEnglish() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        val appContext = InstrumentationRegistry.getInstrumentation().context
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        prefs.edit().putString("dictionary_language", "fr").commit()
        LocaleManager.updateDictionaryLocale(appContext)
        try {
            SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
                DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
                val database = Database(appContext, db)

                val (wordWithoutFr, wordMeaningsEn) = db.rawQuery("SELECT id, meanings_en FROM $WORDS_TABLE_NAME WHERE meanings_fr IS NULL LIMIT 1", null).use { cursor ->
                    assertTrue(cursor.moveToNext())
                    Pair(cursor.getInt(0), cursor.getString(1))
                }
                val (wordWithFr, wordMeaningsFr) = db.rawQuery("SELECT id, meanings_fr FROM $WORDS_TABLE_NAME WHERE meanings_fr IS NOT NULL LIMIT 1", null).use { cursor ->
                    assertTrue(cursor.moveToNext())
                    Pair(cursor.getInt(0), cursor.getString(1))
                }
                val wordView = database.getWordView()
                assertEquals(wordMeaningsEn.split('_'), (wordView.getItem(wordWithoutFr).contents as Word).meanings)
                assertEquals(wordMeaningsFr.split('_'), (wordView.getItem(wordWithFr).contents as Word).meanings)
                assertTrue(wordView.search(wordMeaningsEn.split('_')[0]).contains(wordWithoutFr))

                val (kanjiWithoutFr, kanjiMeaningsEn) = db.rawQuery("SELECT id, meanings_en FROM $KANJIS_TABLE_NAME WHERE meanings_fr IS NULL AND radical = 0 LIMIT 1", null).use { cursor ->
                    assertTrue(cursor.moveToNext())
                    Pair(cursor.getInt(0), cursor.getString(1))
                }
                val (kanjiWithFr, kanjiMeaningsFr) = db.rawQuery("SELECT id, meanings_fr FROM $KANJIS_TABLE_NAME WHERE meanings_fr IS NOT NULL AND radical = 0 LIMIT 1", null).use { cursor ->
                    assertTrue(cursor.moveToNext())
                    Pair(cursor.getInt(0), cursor.getString(1))
                }
                val kanjiView = database.getKanjiView()
                assertEquals(kanjiMeaningsEn.split('_'), (kanjiView.getItem(kanjiWithoutFr).contents as Kanji).meanings)
                assertEquals(kanjiMeaningsFr.split('_'), (kanjiView.getItem(kanjiWithFr).contents as Kanji).meanings)
                assertTrue(kanjiView.search(kanjiMeaningsEn.split('_')[0]).contains(kanjiWithoutFr))
            }
        } finally {
            prefs.edit().remove("dictionary_language").commit()
            LocaleManager.updateDictionaryLocale(appContext)
        }
    }

    @Test
    fun createFromScratch() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
        }
    }

    @Test
    fun dumpAndRestore() {
        val newDb = File.createTempFile("testdb", "", context.cacheDir)

        // Step 1: Create db from scratch and insert user data
        val dump: DatabaseUpdater.Dump
        SQLiteDatabase.openDatabase(newDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)

            // Insert item scores
            db.execSQL("""
                INSERT OR REPLACE INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES
                (0x4EBA, 2, 0.5, 0.4, 10),
                (0x3042, 3, 0.6, 0.7, 20)
            """)
            val hito = db.query(WORDS_TABLE_NAME, arrayOf("id"), "item = '人' AND reading = 'ひと'", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                cursor.getLong(0)
            }
            val jin = db.query(WORDS_TABLE_NAME, arrayOf("id"), "item = '人' AND reading = 'じん'", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                cursor.getLong(0)
            }
            db.execSQL("INSERT OR REPLACE INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES ($hito, 1, 0.8, 0.9, 30)")
            db.execSQL("INSERT OR REPLACE INTO $ITEM_SCORES_TABLE_NAME (id, type, short_score, long_score, last_correct) VALUES ($jin, 1, 0.3, 0.2, 40)")

            // Set enabled flags: disable all, then enable specific items
            db.execSQL("UPDATE $KANJIS_TABLE_NAME SET enabled = 0")
            db.execSQL("UPDATE $KANJIS_TABLE_NAME SET enabled = 1 WHERE id = 0x4EBA")
            db.execSQL("UPDATE ${KANAS_TABLE_NAME} SET enabled = 0")
            db.execSQL("UPDATE ${KANAS_TABLE_NAME} SET enabled = 1 WHERE id = 0x3042")
            db.execSQL("UPDATE $WORDS_TABLE_NAME SET enabled = 0")
            db.execSQL("UPDATE $WORDS_TABLE_NAME SET enabled = 1 WHERE id = $hito")

            // Kanji selection
            db.execSQL("INSERT INTO $KANJIS_SELECTION_TABLE_NAME (name) VALUES ('test selection')")
            val kanjiSelId = db.query(KANJIS_SELECTION_TABLE_NAME, arrayOf("id_selection"), "name = 'test selection'", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                cursor.getLong(0)
            }
            db.execSQL("INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji) VALUES ($kanjiSelId, 0x4EBA)")

            // Word selection
            db.execSQL("INSERT INTO $WORDS_SELECTION_TABLE_NAME (name) VALUES ('test selection 2')")
            val wordSelId = db.query(WORDS_SELECTION_TABLE_NAME, arrayOf("id_selection"), "name = 'test selection 2'", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                cursor.getLong(0)
            }
            db.execSQL("INSERT INTO $WORDS_ITEM_SELECTION_TABLE_NAME (id_selection, id_word) VALUES ($wordSelId, $hito)")

            // Session 1: finished, kana item
            db.execSQL("INSERT INTO $SESSIONS_TABLE_NAME (item_type, test_types, start_time, end_time, item_count, correct_count) VALUES (1, '1', 1602600658, 1602600958, 1, 1)")
            val session1Id = db.query(SESSIONS_TABLE_NAME, arrayOf("MAX(id)"), null, null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                cursor.getLong(0)
            }
            db.execSQL("INSERT INTO $SESSION_ITEMS_TABLE_NAME (id_session, test_type, id_item_question, id_item_wrong, certainty, time) VALUES ($session1Id, 1, 100, NULL, 2, 1602600958)")

            // Session 2: unfinished, word item
            db.execSQL("INSERT INTO $SESSIONS_TABLE_NAME (item_type, test_types, start_time, end_time, item_count, correct_count) VALUES (4, '13,14', 1602610658, NULL, NULL, NULL)")
            val session2Id = db.query(SESSIONS_TABLE_NAME, arrayOf("MAX(id)"), null, null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                cursor.getLong(0)
            }
            db.execSQL("INSERT INTO $SESSION_ITEMS_TABLE_NAME (id_session, test_type, id_item_question, id_item_wrong, certainty, time) VALUES ($session2Id, 3, $jin, $hito, 0, 1602610678)")

            // Stats snapshot
            db.execSQL("INSERT INTO $STATS_SNAPSHOT_TABLE_NAME (item_type, knowledge_type, time, good_count, meh_count, bad_count, long_score_partition, long_score_sum) VALUES (2, 2, 1602610000, 76, 12, 3, '28,11,19,9,3', 45.0)")

            // Step 2: Dump
            dump = DatabaseUpdater(db).dumpUserData()!!
        }

        // Step 3: Create fresh db and restore
        val newDb2 = File.createTempFile("testdb2", "", context.cacheDir)
        SQLiteDatabase.openDatabase(newDb2.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
            DatabaseUpdater(db).doUpgrade(dictDb.absolutePath)
            DatabaseUpdater(db).restoreUserData(dump)
            Database(InstrumentationRegistry.getInstrumentation().context, db).commitAllSessions()

            // Assert scores
            db.query(ITEM_SCORES_TABLE_NAME, arrayOf("short_score", "long_score", "last_correct"), "id = 0x4EBA AND type = 2", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(0.5f, cursor.getFloat(0))
                assertEquals(0.4f, cursor.getFloat(1))
                assertEquals(10L, cursor.getLong(2))
                assertFalse(cursor.moveToNext())
            }
            db.query(ITEM_SCORES_TABLE_NAME, arrayOf("short_score", "long_score", "last_correct"), "id = 0x3042 AND type = 3", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(0.6f, cursor.getFloat(0))
                assertEquals(0.7f, cursor.getFloat(1))
                assertEquals(20L, cursor.getLong(2))
                assertFalse(cursor.moveToNext())
            }
            val hito = db.query(WORDS_TABLE_NAME, arrayOf("id"), "item = '人' AND reading = 'ひと'", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                cursor.getLong(0)
            }
            val jin = db.query(WORDS_TABLE_NAME, arrayOf("id"), "item = '人' AND reading = 'じん'", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                cursor.getLong(0)
            }
            db.query(ITEM_SCORES_TABLE_NAME, arrayOf("short_score", "long_score", "last_correct"), "id = $hito AND type = 1", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(0.8f, cursor.getFloat(0))
                assertEquals(0.9f, cursor.getFloat(1))
                assertEquals(30L, cursor.getLong(2))
                assertFalse(cursor.moveToNext())
            }
            db.query(ITEM_SCORES_TABLE_NAME, arrayOf("short_score", "long_score", "last_correct"), "id = $jin AND type = 1", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(0.3f, cursor.getFloat(0))
                assertEquals(0.2f, cursor.getFloat(1))
                assertEquals(40L, cursor.getLong(2))
                assertFalse(cursor.moveToNext())
            }

            // Assert enabled flags
            db.query(KANJIS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(0x4EBA, cursor.getInt(0))
                assertFalse(cursor.moveToNext())
            }
            db.query(KANAS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(0x3042, cursor.getInt(0))
                assertFalse(cursor.moveToNext())
            }
            db.query(WORDS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(hito, cursor.getLong(0))
                assertFalse(cursor.moveToNext())
            }

            // Assert kanji selection
            db.rawQuery("""
                SELECT kis.id_kanji FROM $KANJIS_SELECTION_TABLE_NAME ks
                JOIN $KANJIS_ITEM_SELECTION_TABLE_NAME kis USING(id_selection)
                WHERE ks.name = 'test selection'
            """, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(0x4EBA, cursor.getInt(0))
                assertFalse(cursor.moveToNext())
            }

            // Assert word selection
            db.rawQuery("""
                SELECT wis.id_word FROM $WORDS_SELECTION_TABLE_NAME ws
                JOIN $WORDS_ITEM_SELECTION_TABLE_NAME wis USING(id_selection)
                WHERE ws.name = 'test selection 2'
            """, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(hito, cursor.getLong(0))
                assertFalse(cursor.moveToNext())
            }

            // Assert sessions (after commitAllSessions)
            db.query(SESSIONS_TABLE_NAME, arrayOf("item_type", "test_types", "start_time", "end_time", "item_count", "correct_count", "unique_item_count", "unique_correct_count"), null, null, null, null, "start_time").use { cursor ->
                // Session 1: finished kana session
                assertTrue(cursor.moveToNext())
                assertEquals(1, cursor.getInt(0))
                assertEquals("1", cursor.getString(1))
                assertEquals(1602600658L, cursor.getLong(2))
                assertEquals(1602600958L, cursor.getLong(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals(1, cursor.getInt(5))
                assertEquals(1, cursor.getInt(6))
                assertEquals(1, cursor.getInt(7))
                // Session 2: was unfinished, now committed
                assertTrue(cursor.moveToNext())
                assertEquals(4, cursor.getInt(0))
                assertEquals("13,14", cursor.getString(1))
                assertEquals(1602610658L, cursor.getLong(2))
                assertEquals(1602610678L, cursor.getLong(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
                assertEquals(1, cursor.getInt(6))
                assertEquals(0, cursor.getInt(7))
                assertFalse(cursor.moveToNext())
            }

            // Assert session items
            db.query(SESSION_ITEMS_TABLE_NAME, arrayOf("test_type", "id_item_question", "id_item_wrong", "certainty", "time"), null, null, null, null, "time").use { cursor ->
                // Session 1 item: kana, SURE
                assertTrue(cursor.moveToNext())
                assertEquals(1, cursor.getInt(0))
                assertEquals(100, cursor.getInt(1))
                assertTrue(cursor.isNull(2))
                assertEquals(2, cursor.getInt(3))
                assertEquals(1602600958L, cursor.getLong(4))
                // Session 2 item: word, DONTKNOW with wrong answer
                assertTrue(cursor.moveToNext())
                assertEquals(3, cursor.getInt(0))
                assertEquals(jin, cursor.getLong(1))
                assertEquals(hito, cursor.getLong(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(1602610678L, cursor.getLong(4))
                assertFalse(cursor.moveToNext())
            }

            // Assert stats snapshot
            db.query(STATS_SNAPSHOT_TABLE_NAME, arrayOf("item_type", "knowledge_type", "time", "good_count", "meh_count", "bad_count", "long_score_partition", "long_score_sum"), null, null, null, null, null).use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(2, cursor.getInt(0))
                assertEquals(2, cursor.getInt(1))
                assertEquals(1602610000L, cursor.getLong(2))
                assertEquals(76, cursor.getInt(3))
                assertEquals(12, cursor.getInt(4))
                assertEquals(3, cursor.getInt(5))
                assertEquals("28,11,19,9,3", cursor.getString(6))
                assertEquals(45.0f, cursor.getFloat(7))
                assertFalse(cursor.moveToNext())
            }
        }
    }
}
