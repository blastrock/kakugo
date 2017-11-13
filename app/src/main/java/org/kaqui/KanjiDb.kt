package org.kaqui

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class KanjiDb private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_TABLE_NAME ("
                        + "id_kanji INTEGER PRIMARY KEY,"
                        + "kanji TEXT NOT NULL UNIQUE,"
                        + "jlpt_level INTEGER NOT NULL,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $HIRAGANAS_TABLE_NAME ("
                        + "id_hiragana INTEGER PRIMARY KEY,"
                        + "hiragana TEXT NOT NULL UNIQUE,"
                        + "romaji TEXT NOT NULL,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $READINGS_TABLE_NAME ("
                        + "id_reading INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji),"
                        + "reading_type TEXT NOT NULL,"
                        + "reading TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $MEANINGS_TABLE_NAME ("
                        + "id_reading INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji),"
                        + "meaning TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $SIMILARITIES_TABLE_NAME ("
                        + "id_similarity INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji),"
                        + "similar_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji)"
                        + ")")
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            database.execSQL("DROP TABLE IF EXISTS $SIMILARITIES_TABLE_NAME")
            onCreate(database)
            return
        }
        if (oldVersion < 4) {
            database.execSQL("DROP TABLE IF EXISTS tmptable")
            database.execSQL("ALTER TABLE $KANJIS_TABLE_NAME RENAME TO tmptable")
            onCreate(database)
            database.execSQL(
                    "INSERT INTO $KANJIS_TABLE_NAME (id_kanji, kanji, jlpt_level, short_score, enabled) "
                            + "SELECT id_kanji, kanji, jlpt_level, weight, enabled FROM tmptable")
            database.execSQL("DROP TABLE tmptable")
            return
        }
    }

    val empty: Boolean
        get() = DatabaseUtils.queryNumEntries(readableDatabase, KANJIS_TABLE_NAME) == 0L

    fun replaceKanjis(kanjis: List<Kanji>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(MEANINGS_TABLE_NAME, null, null)
            writableDatabase.delete(READINGS_TABLE_NAME, null, null)
            writableDatabase.delete(SIMILARITIES_TABLE_NAME, null, null)
            writableDatabase.delete(KANJIS_TABLE_NAME, null, null)
            for (kanji in kanjis) {
                val kanjiCv = ContentValues()
                kanjiCv.put("kanji", kanji.kanji)
                kanjiCv.put("jlpt_level", kanji.jlptLevel)
                val kanjiId = writableDatabase.insertOrThrow(KANJIS_TABLE_NAME, null, kanjiCv)
                for (reading in kanji.readings) {
                    val readingCv = ContentValues()
                    readingCv.put("id_kanji", kanjiId)
                    readingCv.put("reading_type", reading.readingType)
                    readingCv.put("reading", reading.reading)
                    writableDatabase.insertOrThrow(READINGS_TABLE_NAME, null, readingCv)
                }
                for (meaning in kanji.meanings) {
                    val meaningCv = ContentValues()
                    meaningCv.put("id_kanji", kanjiId)
                    meaningCv.put("meaning", meaning)
                    writableDatabase.insertOrThrow(MEANINGS_TABLE_NAME, null, meaningCv)
                }
            }

            for (kanji in kanjis) {
                for (similarity in kanji.similarities) {
                    val kanjiId = getKanjiId(kanji.kanji[0])!!
                    val similarityId = getKanjiId(similarity.kanji[0])
                    similarityId ?: continue

                    val similarityCv = ContentValues()
                    similarityCv.put("id_kanji", kanjiId)
                    similarityCv.put("similar_kanji", similarityId)
                    writableDatabase.insertOrThrow(SIMILARITIES_TABLE_NAME, null, similarityCv)
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun getKanjiId(kanji: Char): Int? {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("id_kanji"), "kanji = ?", arrayOf(kanji.toString()), null, null, null).use { cursor ->
            return if (cursor.count == 0)
                null
            else {
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }
    }

    val kanjiView: LearningDbView
            get() = LearningDbView(readableDatabase, writableDatabase, KANJIS_TABLE_NAME, "id_kanji")

    fun search(text: String): List<Int> {
        readableDatabase.rawQuery(
                """SELECT DISTINCT k.id_kanji
                FROM kanjis k
                LEFT JOIN readings r USING(id_kanji)
                LEFT JOIN meanings m USING(id_kanji)
                WHERE k.kanji = ? OR r.reading LIKE ? OR m.meaning LIKE ?""",
                arrayOf(text, "%$text%", "%$text%")).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    fun getKanji(id: Int): Kanji {
        val readings = mutableListOf<Reading>()
        val meanings = mutableListOf<String>()
        val similarities = mutableListOf<Kanji>()
        readableDatabase.query(READINGS_TABLE_NAME, arrayOf("reading_type", "reading"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                readings.add(Reading(cursor.getString(0), cursor.getString(1)))
        }
        readableDatabase.query(MEANINGS_TABLE_NAME, arrayOf("meaning"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                meanings.add(cursor.getString(0))
        }
        readableDatabase.query(SIMILARITIES_TABLE_NAME, arrayOf("similar_kanji"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Kanji(cursor.getInt(0), "", listOf(), listOf(), listOf(), 0, 0.0, 0.0, 0, false))
        }
        val ret = Kanji(id, "", readings, meanings, similarities, 0, 0.0, 0.0, 0, false)
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("kanji", "jlpt_level", "short_score", "long_score", "last_correct", "enabled"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id " + id)
            cursor.moveToFirst()
            ret.kanji = cursor.getString(0)
            ret.jlptLevel = cursor.getInt(1)
            ret.shortScore = cursor.getDouble(2)
            ret.longScore = cursor.getDouble(3)
            ret.lastCorrect = cursor.getLong(4)
            ret.enabled = cursor.getInt(5) != 0
        }
        return ret
    }

    fun setSelection(kanjis: String) {
        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues()
            cv.put("enabled", false)
            writableDatabase.update(KANJIS_TABLE_NAME, cv, null, null)
            cv.put("enabled", true)
            for (c in kanjis) {
                writableDatabase.update(KANJIS_TABLE_NAME, cv, "kanji = ?", arrayOf(c.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    data class DumpRow(val shortScore: Float, val longScore: Float, val lastCorrect: Long, val enabled: Boolean)

    fun dumpUserData(): Map<Char, DumpRow> {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("kanji", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            val ret = mutableMapOf<Char, DumpRow>()
            while (cursor.moveToNext()) {
                ret[cursor.getString(0)[0]] = DumpRow(cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0)
            }
            return ret
        }
    }

    fun restoreUserDataDump(data: Map<Char, DumpRow>) {
        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues()
            for ((kanji, row) in data) {
                cv.put("short_score", row.shortScore)
                cv.put("long_score", row.longScore)
                cv.put("last_correct", row.lastCorrect)
                cv.put("enabled", if (row.enabled) 1 else 0)
                writableDatabase.update(KANJIS_TABLE_NAME, cv, "kanji = ?", arrayOf(kanji.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    companion object {
        private const val TAG = "KanjiDb"

        private const val DATABASE_NAME = "kanjis"
        private const val DATABASE_VERSION = 4

        private const val HIRAGANAS_TABLE_NAME = "hiraganas"

        private const val KANJIS_TABLE_NAME = "kanjis"
        private const val READINGS_TABLE_NAME = "readings"
        private const val MEANINGS_TABLE_NAME = "meanings"
        private const val SIMILARITIES_TABLE_NAME = "similarities"

        private var singleton: KanjiDb? = null

        fun getInstance(context: Context): KanjiDb {
            if (singleton == null)
                singleton = KanjiDb(context)
            return singleton!!
        }
    }
}

class LearningDbView(
        private val readableDatabase: SQLiteDatabase,
        private val writableDatabase: SQLiteDatabase,
        private val tableName: String,
        private val idColumnName: String) {

    fun getItemsForLevel(level: Int): List<Int> {
        val ret = mutableListOf<Int>()
        readableDatabase.query(tableName, arrayOf(idColumnName), "jlpt_level = ?", arrayOf(level.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
        }
        return ret
    }

    fun setItemEnabled(itemId: Int, enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        writableDatabase.update(tableName, cv, idColumnName + " = ?", arrayOf(itemId.toString()))
    }

    fun setLevelEnabled(level: Int, enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        writableDatabase.update(tableName, cv, "jlpt_level = ?", arrayOf(level.toString()))
    }

    fun isItemEnabled(id: Int): Boolean {
        readableDatabase.query(tableName, arrayOf("enabled"), idColumnName + " = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) != 0
        }
    }

    fun getEnabledItemsAndScores(): List<SrsCalculator.ProbabilityData> {
        readableDatabase.query(tableName, arrayOf(idColumnName, "short_score", "long_score", "last_correct"), "enabled = 1", null, null, null, null).use { cursor ->
            val ret = mutableListOf<SrsCalculator.ProbabilityData>()
            while (cursor.moveToNext()) {
                ret.add(SrsCalculator.ProbabilityData(cursor.getInt(0), cursor.getDouble(1), 0.0, cursor.getDouble(2), 0.0, cursor.getLong(3), 0.0, 0.0))
            }
            return ret
        }
    }

    fun getMinLastCorrect(): Int {
        readableDatabase.query(tableName, arrayOf("MIN(last_correct)"), "enabled = 1", null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun getEnabledCount(): Int {
        readableDatabase.query(tableName, arrayOf("COUNT(id_kanji)"), "enabled = 1", null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun applyScoreUpdate(scoreUpdate: SrsCalculator.ScoreUpdate) {
        val cv = ContentValues()
        cv.put("short_score", scoreUpdate.shortScore)
        cv.put("long_score", scoreUpdate.longScore)
        if (scoreUpdate.lastCorrect != null)
            cv.put("last_correct", scoreUpdate.lastCorrect)
        writableDatabase.update(tableName, cv, idColumnName + " = ?", arrayOf(scoreUpdate.itemId.toString()))
    }


    data class Stats(val bad: Int, val meh: Int, val good: Int, val disabled: Int)

    fun getStats(level: Int?): Stats =
            Stats(getCountForWeight(0.0f, BAD_WEIGHT, level), getCountForWeight(BAD_WEIGHT, GOOD_WEIGHT, level), getCountForWeight(GOOD_WEIGHT, 1.0f, level), getDisabledCount(level))

    private fun getCountForWeight(from: Float, to: Float, level: Int?): Int {
        val selection = "enabled = 1 AND short_score BETWEEN ? AND ?" +
                if (level != null)
                    " AND jlpt_level = ?"
                else
                    ""
        val selectionArgsBase = arrayOf(from.toString(), to.toString())
        val selectionArgs =
                if (level != null)
                    selectionArgsBase + level.toString()
                else
                    selectionArgsBase
        readableDatabase.query(tableName, arrayOf("COUNT(*)"), selection, selectionArgs, null, null, null).use { cursor ->
            cursor.moveToNext()
            return cursor.getInt(0)
        }
    }

    private fun getDisabledCount(level: Int?): Int {
        val selection = "enabled = 0" +
                if (level != null)
                    " AND jlpt_level = ?"
                else
                    ""
        val selectionArgsBase = arrayOf<String>()
        val selectionArgs =
                if (level != null)
                    selectionArgsBase + level.toString()
                else
                    selectionArgsBase
        readableDatabase.query(tableName, arrayOf("COUNT(*)"), selection, selectionArgs, null, null, null).use { cursor ->
            cursor.moveToNext()
            return cursor.getInt(0)
        }
    }
}
