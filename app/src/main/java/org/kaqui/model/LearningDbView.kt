package org.kaqui.model

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.kaqui.SrsCalculator

class LearningDbView(
        private val database: SQLiteDatabase,
        private val tableName: String,
        private val knowledgeType: KnowledgeType?,
        private val filter: String = "1",
        private val classifier: Classifier? = null,
        private val itemGetter: (id: Int, knowledgeType: KnowledgeType?) -> Item,
        private val itemSearcher: ((text: String) -> List<Int>)? = null) {
    fun withClassifier(classifier: Classifier) =
            LearningDbView(database, tableName, knowledgeType, filter, classifier, itemGetter, itemSearcher)

    fun getItem(id: Int): Item = itemGetter(id, knowledgeType)

    fun search(text: String): List<Int> = itemSearcher!!(text)

    fun getAllItems(): List<Int> =
            if (classifier != null)
                getItemsForLevel(classifier)
            else
                getAllItemsForAnyLevel()

    private fun getAllItemsForAnyLevel(): List<Int> {
        val ret = mutableListOf<Int>()
        database.query(tableName, arrayOf("id"), filter, null, null, null, classifier?.orderColumn()).use { cursor ->
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
        }
        return ret
    }

    private fun getItemsForLevel(classifier: Classifier): List<Int> {
        val ret = mutableListOf<Int>()
        database.query(tableName, arrayOf("id"), "$filter AND " + classifier.whereClause(), classifier.whereArguments(), null, null, classifier.orderColumn()).use { cursor ->
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
        database.rawQuery("""
            SELECT $tableName.id, ifnull(s.short_score, 0.0), ifnull(s.long_score, 0.0), ifnull(s.last_correct, 0)
            FROM $tableName
            LEFT JOIN ${Database.ITEM_SCORES_TABLE_NAME} s ON $tableName.id = s.id AND s.type = ${knowledgeType!!.value}
            WHERE $filter AND $tableName.enabled = 1
        """, null).use { cursor ->
            val ret = mutableListOf<SrsCalculator.ProbabilityData>()
            while (cursor.moveToNext()) {
                ret.add(SrsCalculator.ProbabilityData(cursor.getInt(0), cursor.getDouble(1), 0.0, cursor.getDouble(2), 0.0, cursor.getLong(3), 0.0, 0.0))
            }
            return ret
        }
    }

    fun getMinLastCorrect(): Int = getLastCorrectFrom(0)

    fun getLastCorrectFirstDecile(): Int {
        val count = database.query(tableName, arrayOf("COUNT(*)"), "$filter AND enabled = 1", null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        val decile1 = count / 10
        return getLastCorrectFrom(decile1)
    }

    private fun getLastCorrectFrom(from: Int): Int {
        // I couldn't find how sqlite handles null values in order by, so I use ifnull there too
        database.rawQuery("""
            SELECT ifnull(s.last_correct, 0)
            FROM $tableName
            LEFT JOIN ${Database.ITEM_SCORES_TABLE_NAME} s ON $tableName.id = s.id AND s.type = ${knowledgeType!!.value}
            WHERE $filter AND $tableName.enabled = 1
            ORDER BY ifnull(s.last_correct, 0) ASC
            LIMIT $from, 1
        """, null).use { cursor ->
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
        cv.put("id", scoreUpdate.itemId)
        cv.put("type", knowledgeType!!.value)
        cv.put("short_score", scoreUpdate.shortScore)
        cv.put("long_score", scoreUpdate.longScore)
        if (scoreUpdate.lastCorrect != null)
            cv.put("last_correct", scoreUpdate.lastCorrect)
        database.insertWithOnConflict(Database.ITEM_SCORES_TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }


    data class Stats(val bad: Int, val meh: Int, val good: Int, val disabled: Int)

    fun getStats(): Stats {
        val stats = getCountsForEnabled(classifier, knowledgeType)
        val disabledCount = getDisabledCount(classifier)
        return Stats(stats.first, stats.second, stats.third, disabledCount)
    }

    private fun getCountsForEnabled(classifier: Classifier?, knowledgeType: KnowledgeType?): Triple<Int, Int, Int> {
        val andWhereClause =
                if (classifier != null)
                    " AND " + classifier.whereClause()
                else
                    ""

        val joinFiterAndClause =
                if (knowledgeType != null)
                    " AND s.type = ${knowledgeType.value}"
                else
                    ""

        val selectionArgsBase = arrayOf<String>()
        val selectionArgs =
                if (classifier != null)
                    selectionArgsBase + classifier.whereArguments()
                else
                    selectionArgsBase

        database.rawQuery("""
            WITH stats AS (
                SELECT MAX(ifnull(s.short_score, 0.0)) as stats_score
                FROM $tableName
                LEFT JOIN ${Database.ITEM_SCORES_TABLE_NAME} s
                    ON $tableName.id = s.id
                    $joinFiterAndClause
                WHERE $filter
                    AND $tableName.enabled = 1
                    $andWhereClause
                GROUP BY $tableName.id
            )
            SELECT
                SUM(case when stats_score BETWEEN 0.0 AND $BAD_WEIGHT then 1 else 0 end),
                SUM(case when stats_score BETWEEN $BAD_WEIGHT AND $GOOD_WEIGHT then 1 else 0 end),
                SUM(case when stats_score BETWEEN $GOOD_WEIGHT AND 1.0 then 1 else 0 end)
            FROM stats
        """, selectionArgs).use { cursor ->
            cursor.moveToNext()
            return Triple(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2))
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
