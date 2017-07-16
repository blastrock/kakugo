package org.kaqui.kaqui

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.support.v4.database.DatabaseUtilsCompat
import android.util.Log

class KanjiDb private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE $KANJIS_TABLE_NAME ("
                        + "id_kanji INTEGER PRIMARY KEY,"
                        + "kanji TEXT NOT NULL UNIQUE,"
                        + "jlpt_level INTEGER,"
                        + "weight FLOAT NOT NULL DEFAULT 0.0"
                        + ")")
        database.execSQL(
                "CREATE TABLE $READINGS_TABLE_NAME ("
                        + "id_reading INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji),"
                        + "reading_type TEXT NOT NULL,"
                        + "reading TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE $MEANINGS_TABLE_NAME ("
                        + "id_reading INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji),"
                        + "meaning TEXT NOT NULL"
                        + ")")
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    val empty: Boolean
        get() = DatabaseUtils.queryNumEntries(readableDatabase, KANJIS_TABLE_NAME) == 0L

    fun addKanjis(kanjis: List<Kanji>) {
        try {
            writableDatabase.beginTransaction()
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
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getAllIds(): List<Int> {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("id_kanji"), null, null, null, null, null).use { cursor ->
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
        readableDatabase.query(READINGS_TABLE_NAME, arrayOf("reading_type", "reading"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                readings.add(Reading(cursor.getString(0), cursor.getString(1)))
        }
        readableDatabase.query(MEANINGS_TABLE_NAME, arrayOf("meaning"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                meanings.add(cursor.getString(0))
        }
        val ret = Kanji("", readings, meanings, null)
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("kanji", "jlpt_level"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id " + id)
            cursor.moveToFirst()
            ret.kanji = cursor.getString(0)
            ret.jlptLevel =
                    if (!cursor.isNull(1))
                        cursor.getInt(1)
                    else
                        null
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

    private fun certaintyToWeight(certainty: Certainty): Double =
            when (certainty) {
                Certainty.SURE -> 1.0
                Certainty.MAYBE -> 0.7
                Certainty.DONTKNOW -> 0.0
            }

    companion object {
        private const val TAG = "KanjiDb"

        private const val DATABASE_NAME = "kanjis"
        private const val DATABASE_VERSION = 1

        private const val KANJIS_TABLE_NAME = "kanjis"
        private const val READINGS_TABLE_NAME = "readings"
        private const val MEANINGS_TABLE_NAME = "meanings"

        private var singleton: KanjiDb? = null

        fun getInstance(context: Context): KanjiDb {
            if (singleton == null)
                singleton = KanjiDb(context)
            return singleton!!
        }
    }
}