package org.kaqui.model

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.kaqui.SrsCalculator

class LearningDbView(
        private val database: SQLiteDatabase,
        private val tableName: String,
        private val filter: String = "1",
        private val classifier: Classifier? = null,
        private val itemGetter: (id: Int) -> Item,
        private val itemSearcher: ((text: String) -> List<Int>)? = null) {

    fun getItem(id: Int): Item = itemGetter(id)

    fun search(text: String): List<Int> = itemSearcher!!(text)

    fun getAllItems(): List<Int> =
            if (classifier != null)
                getItemsForLevel(classifier)
            else
                getAllItemsForAnyLevel()

    private fun getAllItemsForAnyLevel(): List<Int> {
        val ret = mutableListOf<Int>()
        database.query(tableName, arrayOf("id"), filter, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
        }
        return ret
    }

    private fun getItemsForLevel(classifier: Classifier): List<Int> {
        val ret = mutableListOf<Int>()
        database.query(tableName, arrayOf("id"), "$filter AND " + classifier.whereClause(), classifier.whereArguments(), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
        }
        return ret
    }

    fun setAllEnabled(enabled: Boolean) =
            if (classifier != null)
                setLevelEnabled(classifier, enabled)
            else
                setAllAnyLevelEnabled(enabled)

    private fun setAllAnyLevelEnabled(enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        database.update(tableName, cv, filter, null)
    }

    private fun setLevelEnabled(classifier: Classifier, enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        database.update(tableName, cv, "$filter AND " + classifier.whereClause(), classifier.whereArguments())
    }

    fun setItemEnabled(itemId: Int, enabled: Boolean) {
        val cv = ContentValues()
        cv.put("enabled", if (enabled) 1 else 0)
        database.update(tableName, cv, "id = ?", arrayOf(itemId.toString()))
    }

    fun isItemEnabled(id: Int): Boolean {
        database.query(tableName, arrayOf("enabled"), "id = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) != 0
        }
    }

    fun getEnabledItemsAndScores(): List<SrsCalculator.ProbabilityData> {
        database.query(tableName, arrayOf("id", "short_score", "long_score", "last_correct"), "$filter AND enabled = 1", null, null, null, null).use { cursor ->
            val ret = mutableListOf<SrsCalculator.ProbabilityData>()
            while (cursor.moveToNext()) {
                ret.add(SrsCalculator.ProbabilityData(cursor.getInt(0), cursor.getDouble(1), 0.0, cursor.getDouble(2), 0.0, cursor.getLong(3), 0.0, 0.0))
            }
            return ret
        }
    }

    fun getLastCorrectFirstDecile(): Int {
        val count = database.query(tableName, arrayOf("COUNT(*)"), "$filter AND enabled = 1", null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        val decile1 = count / 10
        database.query(tableName, arrayOf("last_correct"), "$filter AND enabled = 1", null, null, null, "last_correct ASC", "$decile1, 1").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun getEnabledCount(): Int {
        database.query(tableName, arrayOf("COUNT(*)"), "$filter AND enabled = 1", null, null, null, null).use { cursor ->
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
        database.update(tableName, cv, "id = ?", arrayOf(scoreUpdate.itemId.toString()))
    }


    data class Stats(val bad: Int, val meh: Int, val good: Int, val disabled: Int)

    fun getStats(): Stats = getStats(classifier)
    fun getStats(classifier: Classifier?): Stats =
            Stats(getCountForScore(0.0f, BAD_WEIGHT, classifier), getCountForScore(BAD_WEIGHT, GOOD_WEIGHT, classifier), getCountForScore(GOOD_WEIGHT, 1.0f, classifier), getDisabledCount(classifier))

    private fun getCountForScore(from: Float, to: Float, classifier: Classifier?): Int {
        val selection = "$filter AND enabled = 1 AND short_score BETWEEN ? AND ?" +
                if (classifier != null)
                    " AND " + classifier.whereClause()
                else
                    ""
        val selectionArgsBase = arrayOf(from.toString(), to.toString())
        val selectionArgs =
                if (classifier != null)
                    selectionArgsBase + classifier.whereArguments()
                else
                    selectionArgsBase
        database.query(tableName, arrayOf("COUNT(*)"), selection, selectionArgs, null, null, null).use { cursor ->
            cursor.moveToNext()
            return cursor.getInt(0)
        }
    }

    private fun getDisabledCount(classifier: Classifier?): Int {
        val selection = "$filter AND enabled = 0" +
                if (classifier != null)
                    " AND " + classifier.whereClause()
                else
                    ""
        val selectionArgsBase = arrayOf<String>()
        val selectionArgs =
                if (classifier != null)
                    selectionArgsBase + classifier.whereArguments()
                else
                    selectionArgsBase
        database.query(tableName, arrayOf("COUNT(*)"), selection, selectionArgs, null, null, null).use { cursor ->
            cursor.moveToNext()
            return cursor.getInt(0)
        }
    }
}
