package org.kaqui

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class KanjiDb private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS $KANJIS_TABLE_NAME ("
                        + "id_kanji INTEGER PRIMARY KEY,"
                        + "kanji TEXT NOT NULL UNIQUE,"
                        + "jlpt_level INTEGER NOT NULL,"
                        + "weight FLOAT NOT NULL DEFAULT 0.0,"
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
        }
    }

    val empty: Boolean
        get() = DatabaseUtils.queryNumEntries(readableDatabase, KANJIS_TABLE_NAME) == 0L

    fun replaceKanjis(kanjis: List<Kanji>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(MEANINGS_TABLE_NAME, null, null)
            writableDatabase.delete(READINGS_TABLE_NAME, null, null)
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

    fun getKanjiId(kanji: Char): Int? {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("id_kanji"), "kanji = ?", arrayOf(kanji.toString()), null, null, null).use { cursor ->
            if (cursor.count == 0)
                return null
            else {
                cursor.moveToFirst()
                return cursor.getInt(0)
            }
        }
    }

    fun getEnabledIdsAndWeights(): List<Pair<Int, Float>> {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("id_kanji", "weight"), "enabled = ?", arrayOf("1"), null, null, null).use { cursor ->
            val ret = mutableListOf<Pair<Int, Float>>()
            while (cursor.moveToNext()) {
                ret.add(Pair(cursor.getInt(0), cursor.getFloat(1)))
            }
            return ret
        }
    }

    data class Stats(val bad: Int, val meh: Int, val good: Int)

    fun getStats(): Stats =
            Stats(getCountForWeight(0.0f, BAD_WEIGHT), getCountForWeight(BAD_WEIGHT, GOOD_WEIGHT), getCountForWeight(GOOD_WEIGHT, 1.0f))

    private fun getCountForWeight(from: Float, to: Float): Int {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("COUNT(id_kanji)"), "enabled = ? AND weight BETWEEN ? AND ?", arrayOf("1", from.toString(), to.toString()), null, null, null).use { cursor ->
            cursor.moveToNext()
            return cursor.getInt(0)
        }
    }

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
                similarities.add(Kanji(cursor.getInt(0), "", listOf(), listOf(), listOf(), 0, 0.0f, false))
        }
        val ret = Kanji(id,"", readings, meanings, similarities,0, 0.0f, false)
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("kanji", "jlpt_level", "weight", "enabled"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id " + id)
            cursor.moveToFirst()
            ret.kanji = cursor.getString(0)
            ret.jlptLevel = cursor.getInt(1)
            ret.weight = cursor.getFloat(2)
            ret.enabled = cursor.getInt(3) != 0
        }
        return ret
    }

    fun getKanjisForJlptLevel(level: Int): List<Int> {
        val ret = mutableListOf<Int>()
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("id_kanji"), "jlpt_level = ?", arrayOf(level.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
        }
        return ret
    }

    fun updateWeight(kanji: String, certainty: Certainty) {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("weight"), "kanji = ?", arrayOf(kanji), null, null, null).use { cursor ->
            cursor.moveToFirst()
            val previousWeight = cursor.getDouble(0)
            val targetWeight = certaintyToWeight(certainty)
            if (targetWeight == previousWeight) {
                Log.v(TAG, "Weight of $kanji stays unchanged at $previousWeight")
                return
            }
            val newWeight =
                    if (targetWeight > previousWeight)
                        targetWeight - Math.exp(-1.0 + Math.log(targetWeight - previousWeight))
                    else
                        targetWeight + Math.exp(-1.0 + Math.log(-targetWeight + previousWeight))

            if (newWeight !in 0..1) {
                Log.wtf(TAG, "Weight calculation error, previousWeight = $previousWeight, targetWeight = $targetWeight, newWeight = $newWeight")
            }

            Log.v(TAG, "Weight of $kanji going from $previousWeight to $newWeight")

            val cv = ContentValues()
            cv.put("weight", newWeight.toFloat())
            writableDatabase.update(KANJIS_TABLE_NAME, cv, "kanji = ?", arrayOf(kanji))
        }
    }

    fun setKanjiEnabled(kanji: String, enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        writableDatabase.update(KANJIS_TABLE_NAME, cv, "kanji = ?", arrayOf(kanji))
    }

    fun setLevelEnabled(level: Int, enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        writableDatabase.update(KANJIS_TABLE_NAME, cv, "jlpt_level = ?", arrayOf(level.toString()))
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

    data class DumpRow(val weight: Float, val enabled: Boolean)

    fun dumpUserData(): Map<Char, DumpRow> {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("kanji", "weight", "enabled"), null, null, null, null, null).use { cursor ->
            val ret = mutableMapOf<Char, DumpRow>()
            while (cursor.moveToNext()) {
                ret[cursor.getString(0)[0]] = DumpRow(cursor.getFloat(1), cursor.getInt(2) != 0)
            }
            return ret
        }
    }

    fun restoreUserDataDump(data: Map<Char, DumpRow>) {
        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues()
            for ((kanji, row) in data) {
                cv.put("weight", row.weight)
                cv.put("enabled", if (row.enabled) 1 else 0)
                writableDatabase.update(KANJIS_TABLE_NAME, cv, "kanji = ?", arrayOf(kanji.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun certaintyToWeight(certainty: Certainty): Double =
            when (certainty) {
                Certainty.SURE -> 1.0
                Certainty.MAYBE -> 0.7
                Certainty.DONTKNOW -> 0.0
            }

    companion object {
        private const val TAG = "KanjiDb"

        private const val DATABASE_NAME = "kanjis"
        private const val DATABASE_VERSION = 3

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