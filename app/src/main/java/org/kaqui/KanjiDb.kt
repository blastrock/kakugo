package org.kaqui

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.*

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

    fun getEnabledIdsAndProbalities(): List<ProbabilityData> {
        val minLastCorrect = getMinLastCorrect()
        val probaParams = getProbaParams(minLastCorrect)
        val ret = readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("id_kanji", "short_score", "long_score", "last_correct"), "enabled = 1", null, null, null, null).use { cursor ->
            val ret = mutableListOf<ProbabilityData>()
            while (cursor.moveToNext()) {
                ret.add(getProbabilityDataStage1(probaParams, cursor.getInt(0), cursor.getDouble(1), cursor.getDouble(2), cursor.getLong(3)))
            }
            ret
        }
        val (totalShortProba, totalLongProba) = ret.fold(Pair(0.0, 0.0), { acc, v ->
            Pair(acc.first + v.shortProbability, acc.second + v.longProbability)
        })
        val countUnknown = ret.count { it.shortScore < 1.0 }
        val probaParams2 = getProbaParams2(countUnknown, totalShortProba, totalLongProba)
        return ret.map { getProbabilityDataStage2(probaParams2, it) }
    }

    private fun sigmoid(x: Double) = Math.exp(x) / (1 + Math.exp(x))

    data class ProbabilityData(var kanjiId: Int, var shortScore: Double, var shortProbability: Double, var longScore: Double, var longProbability: Double, var daysSinceCorrect: Double, var finalProbability: Double)

    private fun getProbabilityDataStage1(probaParams: ProbaParams, kanjiId: Int, shortScore: Double, longScore: Double, lastCorrect: Long): ProbabilityData {
        val shortProbability = 1 - shortScore
        if (shortProbability !in 0..1)
            Log.wtf(TAG, "Invalid shortProbability: $shortProbability, shortScore: $shortScore")

        val now = Calendar.getInstance().timeInMillis / 1000
        val daysSinceCorrect = (now - lastCorrect) / 3600.0 / 24.0

        val sigmoidArg = (daysSinceCorrect - probaParams.daysBegin - (probaParams.daysEnd - probaParams.daysBegin) * longScore) /
                (probaParams.spreadBegin + (probaParams.spreadEnd - probaParams.spreadBegin) * longScore)
        // cap it to avoid overflow on Math.exp in the sigmoid
        val longProbability = sigmoid(Math.min(sigmoidArg, 10.0))
        if (longProbability !in 0..1)
            Log.wtf(TAG, "Invalid longProbability: $longProbability, lastCorrect: $lastCorrect, now: $now, longScore: $longScore, probaParams: $probaParams")

        return ProbabilityData(kanjiId, shortScore, shortProbability, longScore, longProbability, daysSinceCorrect, 0.0)
    }

    private fun getProbabilityDataStage2(probaParams2: ProbaParams2, stage1: ProbabilityData): ProbabilityData {
        stage1.finalProbability = probaParams2.shortCoefficient * stage1.shortProbability + probaParams2.longCoefficient * stage1.longProbability
        return stage1
    }

    fun getProbabilityData(kanji: Kanji): ProbabilityData {
        val minLastCorrect = getMinLastCorrect()
        val probaParams = getProbaParams(minLastCorrect)
        return getProbabilityDataStage1(probaParams, kanji.id, kanji.shortScore, kanji.longScore, kanji.lastCorrect)
    }

    fun getMinLastCorrect(): Int {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("MIN(last_correct)"), "enabled = 1", null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun getEnabledCount(): Int {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("COUNT(id_kanji)"), "enabled = 1", null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
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
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("COUNT(id_kanji)"), selection, selectionArgs, null, null, null).use { cursor ->
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
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("COUNT(id_kanji)"), selection, selectionArgs, null, null, null).use { cursor ->
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

    fun getKanjisForJlptLevel(level: Int): List<Int> {
        val ret = mutableListOf<Int>()
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("id_kanji"), "jlpt_level = ?", arrayOf(level.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
        }
        return ret
    }

    fun updateScores(kanji: String, certainty: Certainty) {
        val probaParams = getProbaParams(getMinLastCorrect())
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("short_score", "long_score", "last_correct"), "kanji = ?", arrayOf(kanji), null, null, null).use { cursor ->
            cursor.moveToFirst()
            val previousShortScore = cursor.getDouble(0)
            val targetScore = certaintyToWeight(certainty)

            // short score reduces the distance by half to target score
            var newShortScore = previousShortScore + (targetScore - previousShortScore) / 2
            if (newShortScore !in 0..1) {
                Log.wtf(TAG, "Score calculation error, previousShortScore = $previousShortScore, targetScore = $targetScore, newShortScore = $newShortScore")
            }
            if (newShortScore >= 0.92) {
                newShortScore = 1.0
            }

            val previousLongScore = cursor.getDouble(1)
            val now = Calendar.getInstance().timeInMillis / 1000
            // if lastCorrect wasn't initialized, set it to now
            val lastCorrect =
                    if (cursor.getLong(2) > 0)
                        cursor.getLong(2)
                    else
                        now
            val daysSinceCorrect = (now - lastCorrect) / 3600.0 / 24.0
            // long score goes down by one half of the distance to the target score
            // and it goes up by one half of that distance prorated by the time since the last
            // correct answer
            val newLongScore =
                    when {
                        previousLongScore < targetScore ->
                            previousLongScore + ((targetScore - previousLongScore) / 2) *
                                    Math.min(daysSinceCorrect / 2.0 /
                                            (probaParams.daysBegin + (probaParams.daysEnd - probaParams.daysBegin) * previousLongScore), 1.0)
                        previousLongScore > targetScore ->
                            previousLongScore - (-targetScore + previousLongScore) / 2
                        else ->
                            previousLongScore
                    }
            if (newLongScore !in 0..1) {
                Log.wtf(TAG, "Score calculation error, previousLongScore = $previousLongScore, daysSinceCorrect = $daysSinceCorrect, targetScore = $targetScore, probaParams: $probaParams, newLongScore = $newLongScore")
            }

            Log.v(TAG, "Score calculation: targetScore: $targetScore, daysSinceCorrect: $daysSinceCorrect, probaParams: $probaParams")
            Log.v(TAG, "Short score of $kanji going from $previousShortScore to $newShortScore")
            Log.v(TAG, "Long score of $kanji going from $previousLongScore to $newLongScore")

            val cv = ContentValues()
            cv.put("short_score", newShortScore.toFloat())
            cv.put("long_score", newLongScore.toFloat())
            if (certainty != Certainty.DONTKNOW)
                cv.put("last_correct", now)
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

    fun isKanjiEnabled(id: Int): Boolean {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("enabled"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) != 0
        }
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

    private fun certaintyToWeight(certainty: Certainty): Double =
            when (certainty) {
                Certainty.SURE -> 1.0
                Certainty.MAYBE -> 0.7
                Certainty.DONTKNOW -> 0.0
            }

    data class ProbaParams(val daysBegin: Double, val daysEnd: Double, val spreadBegin: Double, val spreadEnd: Double)
    data class ProbaParams2(val shortCoefficient: Double, val longCoefficient: Double)

    companion object {
        private const val TAG = "KanjiDb"

        private const val DATABASE_NAME = "kanjis"
        private const val DATABASE_VERSION = 4

        private const val KANJIS_TABLE_NAME = "kanjis"
        private const val READINGS_TABLE_NAME = "readings"
        private const val MEANINGS_TABLE_NAME = "meanings"
        private const val SIMILARITIES_TABLE_NAME = "similarities"

        private const val MIN_PROBA_SHORT_UNKNOWN = 0.1
        private const val MAX_PROBA_SHORT_UNKNOWN = 0.5
        private const val MAX_COUNT_SHORT_UNKNOWN = 30

        private fun getProbaParams(minLastCorrect: Int): ProbaParams {
            val now = Calendar.getInstance().timeInMillis / 1000

            val daysEnd = (now - minLastCorrect) / 3600.0 / 24.0
            val spreadEnd = (daysEnd * 0.8) / 7.0

            return ProbaParams(1.0, daysEnd, 1 / 7.0, spreadEnd)
        }

        private fun lerp(start: Double, end: Double, value: Double): Double = start + value * (end - start)

        private fun getProbaParams2(countUnknown: Int, totalShortProba: Double, totalLongProba: Double): ProbaParams2 {
            val neededShortProba = lerp(MIN_PROBA_SHORT_UNKNOWN, MAX_PROBA_SHORT_UNKNOWN, countUnknown.toDouble() / MAX_COUNT_SHORT_UNKNOWN)
            val neededLongProba = 1 - neededShortProba
            val total = totalShortProba + totalLongProba
            val shortCoefficient = neededShortProba * total / totalShortProba
            val longCoefficient = neededLongProba * total / totalLongProba
            return ProbaParams2(shortCoefficient, longCoefficient)
        }

        private var singleton: KanjiDb? = null

        fun getInstance(context: Context): KanjiDb {
            if (singleton == null)
                singleton = KanjiDb(context)
            return singleton!!
        }
    }
}