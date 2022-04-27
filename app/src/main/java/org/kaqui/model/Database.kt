package org.kaqui.model

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Path
import android.util.Log
import androidx.core.database.sqlite.transaction
import org.kaqui.LocaleManager
import org.kaqui.asUnicodeCodePoint
import org.kaqui.roundToPreviousDay
import java.util.*
import kotlin.collections.HashSet

class Database private constructor(context: Context, private val database: SQLiteDatabase) {
    private val locale: String get() = LocaleManager.getDictionaryLocale()

    init {
        // the app can be restored from android without going through the main activity,
        // we need a locale here though and we can't try to fetch it every time we need it
        if (!LocaleManager.isReady())
            LocaleManager.updateDictionaryLocale(context)
    }

    fun getHiraganaView(knowledgeType: KnowledgeType? = null) =
            LearningDbView(database, KANAS_TABLE_NAME, knowledgeType, filter = "$KANAS_TABLE_NAME.id BETWEEN ${HiraganaRange.first} AND ${HiraganaRange.last}", itemGetter = this::getKana)

    fun getKatakanaView(knowledgeType: KnowledgeType? = null) =
            LearningDbView(database, KANAS_TABLE_NAME, knowledgeType, filter = "$KANAS_TABLE_NAME.id BETWEEN ${KatakanaRange.first} AND ${KatakanaRange.last}", itemGetter = this::getKana)

    fun getKanjiView(knowledgeType: KnowledgeType? = null, classifier: Classifier? = null): LearningDbView =
            LearningDbView(database, KANJIS_TABLE_NAME, knowledgeType, filter = "radical = 0", classifier = classifier, itemGetter = this::getKanji, itemSearcher = this::searchKanji)

    fun getWordView(knowledgeType: KnowledgeType? = null, classifier: Classifier? = null, withKanaAlone: Boolean = true): LearningDbView =
            LearningDbView(database, WORDS_TABLE_NAME, knowledgeType, classifier = classifier, itemGetter = this::getWord, itemSearcher = this::searchWord, filter = if (!withKanaAlone) "kana_alone = 0" else "1")

    fun getOtherCompositionAnswerIds(kanjiId: Int): List<Int> {
        database.rawQuery("""
            SELECT c3.id_kanji2
            FROM $KANJIS_COMPOSITION_TABLE_NAME c1
            JOIN $KANJIS_COMPOSITION_TABLE_NAME c2 ON c1.id_kanji2 = c2.id_kanji2
            JOIN $KANJIS_TABLE_NAME k2 ON c2.id_kanji1 = k2.id AND k2.enabled = 1
            JOIN $KANJIS_COMPOSITION_TABLE_NAME c3 ON c2.id_kanji1 = c3.id_kanji1
            WHERE c1.id_kanji1 = ?
                UNION
            SELECT c.id_kanji1
            FROM $KANJIS_COMPOSITION_TABLE_NAME c
            JOIN $KANJIS_TABLE_NAME k ON c.id_kanji1 = k.id AND k.enabled = 1
            WHERE c.id_kanji2 = ?
            """, arrayOf(kanjiId.toString(), kanjiId.toString())).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    fun getSimilarCompositionAnswerIds(kanjiId: Int): List<Int> {
        database.rawQuery("""
            SELECT DISTINCT c.id_kanji2
            FROM $SIMILAR_ITEMS_TABLE_NAME s
            JOIN $KANJIS_TABLE_NAME sk ON s.id_item2 = sk.id AND sk.enabled = 1
            JOIN $KANJIS_COMPOSITION_TABLE_NAME c ON c.id_kanji1 = s.id_item2
            WHERE s.id_item1 = ?
            """, arrayOf(kanjiId.toString())).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    private fun searchKanji(text: String): List<Int> {
        val firstCodePoint =
                if (text.isNotEmpty())
                    text.codePointAt(0).toString()
                else
                    ""
        database.rawQuery(
                """SELECT id
                FROM $KANJIS_TABLE_NAME
                WHERE (id = ? OR on_readings LIKE ? OR kun_readings LIKE ? OR (meanings_$locale <> '' AND meanings_$locale LIKE ? OR meanings_$locale == '' AND meanings_en LIKE ?)) AND radical = 0""",
                arrayOf(firstCodePoint, "%$text%", "%$text%", "%$text%", "%$text%")).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    private fun searchWord(text: String): List<Int> {
        database.rawQuery(
                """SELECT id
                FROM $WORDS_TABLE_NAME
                WHERE item LIKE ? OR reading LIKE ? OR (meanings_$locale <> '' AND meanings_$locale LIKE ? OR meanings_$locale == '' AND meanings_en LIKE ?)""",
                arrayOf("%$text%", "%$text%", "%$text%", "%$text%")).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    fun getStrokes(id: Int): List<Path> {
        val strokes = mutableListOf<Path>()
        database.query(ITEM_STROKES_TABLE_NAME, arrayOf("path"), "id_item = ?", arrayOf(id.toString()), null, null, "ordinal").use { cursor ->
            val PathParser = Class.forName("androidx.core.graphics.PathParser")
            val createPathFromPathData = PathParser.getMethod("createPathFromPathData", String::class.java)
            while (cursor.moveToNext()) {
                strokes.add(createPathFromPathData.invoke(null, cursor.getString(0)) as Path)
            }
        }
        return strokes
    }

    private fun getOnClause(knowledgeType: KnowledgeType?) =
            if (knowledgeType != null)
                " AND s.type = ${knowledgeType.value} "
            else
                ""

    private fun getKana(id: Int, knowledgeType: KnowledgeType?): Item {
        val similarities = mutableListOf<Item>()
        database.query(SIMILAR_ITEMS_TABLE_NAME, arrayOf("id_item2"), "id_item1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kana("", "", "", listOf()), 0.0, 0.0, 0, false))
        }
        val contents = Kana("", "", "", similarities)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        database.rawQuery("""
            SELECT romaji, MAX(ifnull(short_score, 0.0)), MAX(ifnull(long_score, 0.0)), ifnull(last_correct, 0), enabled, unique_romaji
            FROM $KANAS_TABLE_NAME k
            LEFT JOIN $ITEM_SCORES_TABLE_NAME s ON k.id = s.id ${getOnClause(knowledgeType)}
            WHERE k.id = $id
            GROUP BY k.id
            """, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kana with id $id")
            cursor.moveToFirst()
            contents.kana = id.asUnicodeCodePoint()
            contents.romaji = cursor.getString(0)
            contents.uniqueRomaji = cursor.getString(5)
            item.shortScore = cursor.getDouble(1)
            item.longScore = cursor.getDouble(2)
            item.lastAsked = cursor.getLong(3)
            item.enabled = cursor.getInt(4) != 0
        }
        return item
    }

    fun getKanji(id: Int, knowledgeType: KnowledgeType?): Item {
        val similarities = mutableListOf<Item>()
        database.query(SIMILAR_ITEMS_TABLE_NAME, arrayOf("id_item2"), "id_item1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kanji("", listOf(), listOf(), listOf(), listOf(), listOf(), 0), 0.0, 0.0, 0, false))
        }
        val parts = mutableListOf<Item>()
        database.query(KANJIS_COMPOSITION_TABLE_NAME, arrayOf("id_kanji2"), "id_kanji1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                parts.add(Item(cursor.getInt(0), Kanji("", listOf(), listOf(), listOf(), listOf(), listOf(), 0), 0.0, 0.0, 0, false))
        }
        val contents = Kanji("", listOf(), listOf(), listOf(), similarities, parts, 0)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        database.rawQuery("""
            SELECT jlpt_level, MAX(ifnull(short_score, 0.0)), MAX(ifnull(long_score, 0.0)), ifnull(last_correct, 0), enabled, on_readings, kun_readings, meanings_$locale, meanings_en
            FROM $KANJIS_TABLE_NAME k
            LEFT JOIN $ITEM_SCORES_TABLE_NAME s ON k.id = s.id ${getOnClause(knowledgeType)}
            WHERE k.id = $id
            GROUP BY k.id
            """, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id $id")
            cursor.moveToFirst()
            contents.kanji = id.asUnicodeCodePoint()
            contents.on_readings = cursor.getString(5).split('_')
            contents.kun_readings = cursor.getString(6).split('_')
            val localMeaning = cursor.getString(7)
            if (localMeaning != "")
                contents.meanings = localMeaning.split('_')
            else
                contents.meanings = cursor.getString(8).split('_')
            contents.jlptLevel = cursor.getInt(0)
            item.shortScore = cursor.getDouble(1)
            item.longScore = cursor.getDouble(2)
            item.lastAsked = cursor.getLong(3)
            item.enabled = cursor.getInt(4) != 0
        }
        return item
    }

    fun getWord(id: Int, knowledgeType: KnowledgeType?): Item {
        val contents = Word("", "", listOf(), listOf(), false)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        var similarityClass = 0
        database.rawQuery("""
            SELECT item, reading, meanings_$locale, MAX(ifnull(short_score, 0.0)), MAX(ifnull(long_score, 0.0)), ifnull(last_correct, 0), enabled, similarity_class, meanings_en, kana_alone
            FROM $WORDS_TABLE_NAME k
            LEFT JOIN $ITEM_SCORES_TABLE_NAME s ON k.id = s.id ${getOnClause(knowledgeType)}
            WHERE k.id = $id
            GROUP BY k.id
            """, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find word with id $id")
            cursor.moveToFirst()
            contents.word = cursor.getString(0)
            contents.reading = cursor.getString(1)
            val localMeaning = cursor.getString(2)
            if (localMeaning != "")
                contents.meanings = localMeaning.split('_')
            else
                contents.meanings = cursor.getString(8).split('_')
            contents.kanaAlone = cursor.getInt(9) != 0
            item.shortScore = cursor.getDouble(3)
            item.longScore = cursor.getDouble(4)
            item.lastAsked = cursor.getLong(5)
            item.enabled = cursor.getInt(6) != 0
            similarityClass = cursor.getInt(7)
        }
        val similarWords = mutableListOf<Item>()
        database.query(WORDS_TABLE_NAME, arrayOf("id"),
                "similarity_class = ? AND id <> ?", arrayOf(similarityClass.toString(), id.toString()),
                null, null, "RANDOM()", "20").use { cursor ->
            while (cursor.moveToNext())
                similarWords.add(Item(cursor.getInt(0), Word("", "", listOf(), listOf(), false), 0.0, 0.0, 0, false))
        }
        contents.similarities = similarWords
        return item
    }

    fun setKanjiSelection(kanjis: String) {
        database.transaction {
            val cv = ContentValues()
            cv.put("enabled", true)
            for (i in 0 until kanjis.codePointCount(0, kanjis.length)) {
                database.update(KANJIS_TABLE_NAME, cv, "id = ?", arrayOf(kanjis.codePointAt(i).toString()))
            }
        }
    }

    fun setWordSelection(wordsString: String) {
        val words = wordsString.split("\n")
        database.transaction {
            val cv = ContentValues()
            cv.put("enabled", true)
            for (word in words) {
                val trimmedWord = word.trim()
                database.update(WORDS_TABLE_NAME, cv, "item = ? OR (kana_alone = 1 AND reading = ?)", arrayOf(trimmedWord, trimmedWord))
            }
        }
    }

    fun autoSelectWords(classifier: Classifier? = null) {
        val enabledKanjis = HashSet<Char>()
        database.query(KANJIS_TABLE_NAME, arrayOf("id"), "enabled = 1", null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                enabledKanjis.add(Character.toChars(cursor.getInt(0))[0])
            }
        }
        var allWords =
                database.query(WORDS_TABLE_NAME, arrayOf("id, item"), classifier?.whereClause(), classifier?.whereArguments(), null, null, null).use { cursor ->
                    val ret = mutableListOf<Pair<Long, String>>()
                    while (cursor.moveToNext()) {
                        ret.add(Pair(cursor.getLong(0), cursor.getString(1)))
                    }
                    ret.toList()
                }
        allWords = allWords.map { Pair(it.first, it.second.filter { isKanji(it) }) }
        allWords = allWords.filter { it.second.all { it in enabledKanjis } }

        database.transaction {
            val cv = ContentValues()
            cv.put("enabled", false)
            database.update(WORDS_TABLE_NAME, cv, classifier?.whereClause(), classifier?.whereArguments())
            cv.put("enabled", true)
            for (word in allWords) {
                database.update(WORDS_TABLE_NAME, cv, "id = ?", arrayOf(word.first.toString()))
            }
        }
    }

    data class SavedSelection(
            val id: Long,
            val name: String,
            val count: Int
    )

    fun listKanjiSelections(): List<SavedSelection> {
        val out = mutableListOf<SavedSelection>()
        database.rawQuery("""
            SELECT id_selection, name, (
                SELECT COUNT(*)
                FROM $KANJIS_ITEM_SELECTION_TABLE_NAME ss WHERE ss.id_selection = s.id_selection
            )
            FROM $KANJIS_SELECTION_TABLE_NAME s
            """, arrayOf()).use { cursor ->
            while (cursor.moveToNext())
                out.add(SavedSelection(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)))
        }
        return out
    }

    fun saveKanjiSelectionTo(name: String) {
        database.transaction {
            val idSelection = database.query(KANJIS_SELECTION_TABLE_NAME, arrayOf("id_selection"), "name = ?", arrayOf(name), null, null, null).use { cursor ->
                if (cursor.count == 0) {
                    val cv = ContentValues()
                    cv.put("name", name)
                    return@use database.insert(KANJIS_SELECTION_TABLE_NAME, null, cv)
                } else {
                    cursor.moveToFirst()
                    return@use cursor.getInt(0)
                }
            }
            database.delete(KANJIS_ITEM_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            database.execSQL("""
            INSERT INTO $KANJIS_ITEM_SELECTION_TABLE_NAME (id_selection, id_kanji)
            SELECT ?, id FROM $KANJIS_TABLE_NAME WHERE enabled = 1
            """, arrayOf(idSelection.toString()))
        }
    }

    fun restoreKanjiSelectionFrom(idSelection: Long) {
        database.transaction {
            database.execSQL("""
                UPDATE $KANJIS_TABLE_NAME
                SET enabled = 0
                """)
            database.execSQL("""
                UPDATE $KANJIS_TABLE_NAME
                SET enabled = 1
                WHERE id IN (
                    SELECT id_kanji
                    FROM $KANJIS_ITEM_SELECTION_TABLE_NAME
                    WHERE id_selection = ?
                )
                """, arrayOf(idSelection.toString()))
        }
    }

    fun deleteKanjiSelection(idSelection: Long) {
        database.transaction {
            database.delete(KANJIS_ITEM_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            database.delete(KANJIS_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
        }
    }

    fun listWordSelections(): List<SavedSelection> {
        val out = mutableListOf<SavedSelection>()
        database.rawQuery("""
            SELECT id_selection, name, (
                SELECT COUNT(*)
                FROM $WORDS_ITEM_SELECTION_TABLE_NAME ss WHERE ss.id_selection = s.id_selection
            )
            FROM $WORDS_SELECTION_TABLE_NAME s
            """, arrayOf()).use { cursor ->
            while (cursor.moveToNext())
                out.add(SavedSelection(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)))
        }
        return out
    }

    fun saveWordSelectionTo(name: String) {
        database.transaction {
            val idSelection = database.query(WORDS_SELECTION_TABLE_NAME, arrayOf("id_selection"), "name = ?", arrayOf(name), null, null, null).use { cursor ->
                if (cursor.count == 0) {
                    val cv = ContentValues()
                    cv.put("name", name)
                    return@use database.insert(WORDS_SELECTION_TABLE_NAME, null, cv)
                } else {
                    cursor.moveToFirst()
                    return@use cursor.getInt(0)
                }
            }
            database.delete(WORDS_ITEM_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            database.execSQL("""
            INSERT INTO $WORDS_ITEM_SELECTION_TABLE_NAME (id_selection, id_word)
            SELECT ?, id FROM $WORDS_TABLE_NAME WHERE enabled = 1
            """, arrayOf(idSelection.toString()))
        }
    }

    fun restoreWordSelectionFrom(idSelection: Long) {
        database.transaction {
            database.execSQL("""
                UPDATE $WORDS_TABLE_NAME
                SET enabled = 0
                """)
            database.execSQL("""
                UPDATE $WORDS_TABLE_NAME
                SET enabled = 1
                WHERE id IN (
                    SELECT id_word
                    FROM $WORDS_ITEM_SELECTION_TABLE_NAME
                    WHERE id_selection = ?
                )
                """, arrayOf(idSelection.toString()))
        }
    }

    fun deleteWordSelection(idSelection: Long) {
        database.transaction {
            database.delete(WORDS_ITEM_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
            database.delete(WORDS_SELECTION_TABLE_NAME, "id_selection = ?", arrayOf(idSelection.toString()))
        }
    }

    private fun takeLastSnapshot(testTypes: List<TestType>) {
        database.transaction {
            val byItemAndKnowledge = testTypes.map { Pair(getItemType(it), getKnowledgeType(it)) }
            byItemAndKnowledge.forEach { (itemType, knowledgeType) ->
                val view = when (itemType) {
                    ItemType.Hiragana -> getHiraganaView(knowledgeType)
                    ItemType.Katakana -> getKatakanaView(knowledgeType)
                    ItemType.Kanji -> getKanjiView(knowledgeType)
                    ItemType.Word -> getWordView(knowledgeType)
                }
                val stats = view.getLongStats(knowledgeType)
                val testTypesStr = itemAndKnowledgeTypeToTestType(itemType, knowledgeType).joinToString(",") { it.value.toString() }
                // Get the last time the corresponding stats are updated so that we can make a snapshot for that date
                var lastQuestion = database.query(SESSION_ITEMS_TABLE_NAME, arrayOf("MAX(time)"), "test_type IN ($testTypesStr)", null, null, null, null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(0)
                }
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                // If this is the first run, just take today
                if (lastQuestion != 0L)
                    calendar.timeInMillis = lastQuestion * 1000
                calendar.roundToPreviousDay()
                lastQuestion = calendar.timeInMillis / 1000

                Log.d(TAG, "Taking snapshot for $itemType $knowledgeType, last question was $lastQuestion")

                val cv = ContentValues()
                cv.put("item_type", itemType.value)
                cv.put("knowledge_type", knowledgeType.value)
                cv.put("time", lastQuestion / 3600 / 24 * 3600 * 24)
                cv.put("good_count", stats.good)
                cv.put("meh_count", stats.meh)
                cv.put("bad_count", stats.bad)
                cv.put("long_score_sum", stats.longScoreSum)
                cv.put("long_score_partition", stats.longPartition.joinToString(","))
                // If multiple snapshots were taken for the same day, the last one is the most up to date, so we replace
                database.insertWithOnConflict(STATS_SNAPSHOT_TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun initSession(itemType: ItemType, testTypes: List<TestType>): Long {
        takeLastSnapshot(testTypes)

        val cv = ContentValues()
        cv.put("item_type", itemType.value)
        cv.put("test_types", testTypes.joinToString(",") { it.value.toString() })
        cv.put("start_time", Calendar.getInstance().timeInMillis / 1000)
        return database.insertOrThrow(SESSIONS_TABLE_NAME, null, cv)
    }

    private fun commitAllSessions() {
        // Delete empty sessions
        val emptySessions = mutableListOf<Long>()
        database.rawQuery("""
            SELECT s.id
            FROM $SESSIONS_TABLE_NAME s
            LEFT JOIN $SESSION_ITEMS_TABLE_NAME si ON si.id_session = s.id
            WHERE si.id_session IS NULL
        """, arrayOf()).use { cursor ->
            while (cursor.moveToNext())
                emptySessions.add(cursor.getLong(0))
        }
        database.delete(SESSIONS_TABLE_NAME, "id in (${emptySessions.joinToString(",")})", null)

        // Update end_time and item_count for all not committed sessions
        database.transaction {
            val openSessions = mutableListOf<Long>()
            database.query(SESSIONS_TABLE_NAME, arrayOf("id"), "end_time IS NULL", null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    openSessions.add(cursor.getLong(0))
            }

            data class Session(val id: Long, val totalCount: Int, val correctCount: Int, val endTime: Long)

            val sessions = mutableListOf<Session>()
            database.query(
                    SESSION_ITEMS_TABLE_NAME,
                    arrayOf("id_session, COUNT(*), MAX(time), SUM(CASE WHEN certainty != ${Certainty.DONTKNOW.value} THEN 1 ELSE 0 END)"),
                    "id_session IN (${openSessions.joinToString(",")})",
                    null,
                    "id_session", null, null).use { cursor ->
                while (cursor.moveToNext())
                    sessions.add(Session(cursor.getLong(0), cursor.getInt(1), cursor.getInt(3), cursor.getLong(2)))
            }

            for (session in sessions) {
                val cv = ContentValues()
                cv.put("item_count", session.totalCount)
                cv.put("correct_count", session.correctCount)
                cv.put("end_time", session.endTime)
                database.update(SESSIONS_TABLE_NAME, cv, "id = ?", arrayOf(session.id.toString()))
            }
        }
    }

    data class DayStatistics(val timestamp: Long, val askedCount: Int, val correctCount: Int)

    fun getAskedItem(): List<DayStatistics> {
        commitAllSessions()

        val stats = mutableListOf<DayStatistics>()
        database.query(SESSIONS_TABLE_NAME, arrayOf("start_time", "item_count", "correct_count"), null, null, null, null, "start_time").use { cursor ->
            while (cursor.moveToNext())
                stats.add(DayStatistics(cursor.getLong(0), cursor.getInt(1), cursor.getInt(2)))
        }
        return stats
    }

    companion object {
        private const val TAG = "Database"

        const val DATABASE_NAME = "kanjis"

        const val KANAS_TABLE_NAME = "kanas"

        const val KANJIS_TABLE_NAME = "kanjis"
        const val KANJIS_COMPOSITION_TABLE_NAME = "kanjis_composition"

        const val SIMILAR_ITEMS_TABLE_NAME = "similar_items"
        const val ITEM_STROKES_TABLE_NAME = "item_strokes"

        const val ITEM_SCORES_TABLE_NAME = "item_scores"
        const val SESSIONS_TABLE_NAME = "sessions"
        const val SESSION_ITEMS_TABLE_NAME = "session_items"
        const val STATS_SNAPSHOT_TABLE_NAME = "stats_snapshots"

        const val KANJIS_SELECTION_TABLE_NAME = "kanjis_selection"
        const val KANJIS_ITEM_SELECTION_TABLE_NAME = "kanjis_item_selection"

        const val WORDS_SELECTION_TABLE_NAME = "word_selection"
        const val WORDS_ITEM_SELECTION_TABLE_NAME = "word_item_selection"

        const val WORDS_TABLE_NAME = "words"

        private var singleton: Database? = null

        private fun isKanji(c: Char): Boolean {
            // This is the hiragana/katakana range
            return c.code !in 0x3040..0x3100
        }

        fun getInstance(context: Context): Database {
            if (singleton == null) {
                val db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                singleton = Database(context, db)
            }
            return singleton!!
        }
    }
}
