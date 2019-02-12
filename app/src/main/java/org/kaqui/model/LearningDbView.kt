package org.kaqui.model

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.kaqui.SrsCalculator

class LearningDbView(
        private val readableDatabase: SQLiteDatabase,
        private val writableDatabase: SQLiteDatabase,
        private val tableName: String,
        private val idColumnName: String,
        private val filter: String = "1",
        private val level: Int?,
        private val itemGetter: (id: Int) -> Item,
        private val itemSearcher: ((text: String) -> List<Int>)? = null) {

    fun getItem(id: Int): Item = itemGetter(id)

    fun search(text: String): List<Int> = itemSearcher!!(text)

    fun getAllItems(): List<Int> =
            if (level != null)
                getItemsForLevel(level)
            else
                getAllItemsForAnyLevel()

    private fun getAllItemsForAnyLevel(): List<Int> {
        val ret = mutableListOf<Int>()
        readableDatabase.query(tableName, arrayOf(idColumnName), filter, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
        }
        return ret
    }

    private fun getItemsForLevel(level: Int): List<Int> {
        val ret = mutableListOf<Int>()
        readableDatabase.query(tableName, arrayOf(idColumnName), "$filter AND jlpt_level = ?", arrayOf(level.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
        }
        return ret
    }

    fun setAllEnabled(enabled: Boolean) =
            if (level != null)
                setLevelEnabled(level, enabled)
            else
                setAllAnyLevelEnabled(enabled)

    private fun setAllAnyLevelEnabled(enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        writableDatabase.update(tableName, cv, filter, null)
    }

    private fun setLevelEnabled(level: Int, enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        writableDatabase.update(tableName, cv, "$filter AND jlpt_level = ?", arrayOf(level.toString()))
    }

    fun setItemEnabled(itemId: Int, enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        writableDatabase.update(tableName, cv, "$idColumnName = ?", arrayOf(itemId.toString()))
    }

    fun isItemEnabled(id: Int): Boolean {
        readableDatabase.query(tableName, arrayOf("enabled"), "$idColumnName = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) != 0
        }
    }

    fun getEnabledItemsAndScores(): List<SrsCalculator.ProbabilityData> {
        readableDatabase.query(tableName, arrayOf(idColumnName, "short_score", "long_score", "last_correct"), "$filter AND enabled = 1", null, null, null, null).use { cursor ->
            val ret = mutableListOf<SrsCalculator.ProbabilityData>()
            while (cursor.moveToNext()) {
                ret.add(SrsCalculator.ProbabilityData(cursor.getInt(0), cursor.getDouble(1), 0.0, cursor.getDouble(2), 0.0, cursor.getLong(3), 0.0, 0.0))
            }
            return ret
        }
    }

    fun getLastCorrectFirstDecile(): Int {
        val count = readableDatabase.query(tableName, arrayOf("COUNT(*)"), "$filter AND enabled = 1", null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        val decile1 = count / 10
        readableDatabase.query(tableName, arrayOf("last_correct"), "$filter AND enabled = 1", null, null, null, "last_correct ASC", "$decile1, 1").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun getEnabledCount(): Int {
        readableDatabase.query(tableName, arrayOf("COUNT(*)"), "$filter AND enabled = 1", null, null, null, null).use { cursor ->
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
        writableDatabase.update(tableName, cv, "$idColumnName = ?", arrayOf(scoreUpdate.itemId.toString()))
    }


    data class Stats(val bad: Int, val meh: Int, val good: Int, val disabled: Int)

    fun getStats(): Stats = getStats(level)
    fun getStats(level: Int?): Stats =
            Stats(getCountForWeight(0.0f, BAD_WEIGHT, level), getCountForWeight(BAD_WEIGHT, GOOD_WEIGHT, level), getCountForWeight(GOOD_WEIGHT, 1.0f, level), getDisabledCount(level))

    private fun getCountForWeight(from: Float, to: Float, level: Int?): Int {
        val selection = "$filter AND enabled = 1 AND short_score BETWEEN ? AND ?" +
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
        val selection = "$filter AND enabled = 0 AND $filter" +
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
