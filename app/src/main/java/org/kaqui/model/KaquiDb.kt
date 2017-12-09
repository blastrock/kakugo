package org.kaqui.model

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.kaqui.data.*

class KaquiDb private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(database: SQLiteDatabase) {
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
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1,"
                        + "UNIQUE(item, reading)"
                        + ")")

        initKanas(database, HIRAGANAS_TABLE_NAME, SIMILAR_HIRAGANAS_TABLE_NAME, Hiraganas, SimilarHiraganas)
        initKanas(database, KATAKANAS_TABLE_NAME, SIMILAR_KATAKANAS_TABLE_NAME, Katakanas, SimilarKatakanas)
    }

    private fun initKanas(database: SQLiteDatabase, tableName: String, similarKanaTableName: String, kanas: Array<RawKana>, similarKanas: Array<SimilarKana>) {
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

        val kanaCount = database.query(tableName, arrayOf("COUNT(*)"), null, null, null, null, null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        if (kanaCount == 0) {
            for (kana in kanas) {
                val cv = ContentValues()
                cv.put("kana", kana.kana)
                cv.put("romaji", kana.romaji)
                database.insertOrThrow(tableName, null, cv)
            }
        }
        val similarKanaCount = database.query(similarKanaTableName, arrayOf("COUNT(*)"), null, null, null, null, null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        if (similarKanaCount == 0) {
            for (similarKana in similarKanas) {
                val id1 = database.query(tableName, arrayOf("id_kana"), "kana = ?", arrayOf(similarKana.kana), null, null, null).use {
                    it.moveToFirst()
                    it.getInt(0)
                }
                val id2 = database.query(tableName, arrayOf("id_kana"), "kana = ?", arrayOf(similarKana.similar), null, null, null).use {
                    it.moveToFirst()
                    it.getInt(0)
                }

                val cv = ContentValues()
                cv.put("id_kana", id1)
                cv.put("similar_kana", id2)
                database.insertOrThrow(similarKanaTableName, null, cv)
                cv.put("id_kana", id2)
                cv.put("similar_kana", id1)
                database.insertOrThrow(similarKanaTableName, null, cv)
            }
        }

    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 10) {
            val dump = dumpUserDataV9(database)
            database.execSQL("DROP TABLE IF EXISTS meanings")
            database.execSQL("DROP TABLE IF EXISTS readings")
            database.execSQL("DROP TABLE IF EXISTS $SIMILARITIES_TABLE_NAME")
            database.execSQL("DROP TABLE IF EXISTS $KANJIS_TABLE_NAME")
            database.execSQL("DROP TABLE IF EXISTS hiraganas")
            database.execSQL("DROP TABLE IF EXISTS similar_hiraganas")
            database.execSQL("DROP TABLE IF EXISTS katakanas")
            database.execSQL("DROP TABLE IF EXISTS similar_katakanas")
            onCreate(database)
            restoreUserData(database, dump)
        }
    }

    val needsInit: Boolean
        get() {
            readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("COUNT(*)"), "on_readings <> ''", null, null, null, null).use { cursor ->
                cursor.moveToFirst()
                if (cursor.getInt(0) == 0)
                    return true
            }
            readableDatabase.query(WORDS_TABLE_NAME, arrayOf("COUNT(*)"), "reading <> ''", null, null, null, null).use { cursor ->
                cursor.moveToFirst()
                if (cursor.getInt(0) == 0)
                    return true
            }
            return false
        }

    fun replaceKanjis(dictDb: String) {
        writableDatabase.execSQL("ATTACH DATABASE ? AS dict", arrayOf(dictDb))
        writableDatabase.beginTransaction()
        try {
            val dump = dumpUserData()
            writableDatabase.delete(SIMILARITIES_TABLE_NAME, null, null)
            writableDatabase.delete(KANJIS_TABLE_NAME, null, null)
            writableDatabase.execSQL(
                    "INSERT INTO $KANJIS_TABLE_NAME "
                            + "(id, item, on_readings, kun_readings, meanings, jlpt_level) "
                            + "SELECT id, item, on_readings, kun_readings, meanings, jlpt_level "
                            + "FROM dict.kanjis"
            )
            writableDatabase.execSQL(
                    "INSERT INTO $SIMILARITIES_TABLE_NAME "
                            + "(id_kanji1, id_kanji2) "
                            + "SELECT id_kanji1, id_kanji2 "
                            + "FROM dict.kanjis_similars "
            )
            restoreUserData(dump)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
            writableDatabase.execSQL("DETACH DATABASE dict")
        }
    }

    fun replaceWords(dictDb: String) {
        writableDatabase.execSQL("ATTACH DATABASE ? AS dict", arrayOf(dictDb))
        writableDatabase.beginTransaction()
        try {
            val dump = dumpUserData()
            writableDatabase.delete(WORDS_TABLE_NAME, null, null)
            writableDatabase.execSQL(
                    "INSERT INTO $WORDS_TABLE_NAME "
                            + "(id, item, reading, meanings) "
                            + "SELECT id, item, reading, meanings "
                            + "FROM dict.words"
            )
            restoreUserData(dump)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
            writableDatabase.execSQL("DETACH DATABASE dict")
        }
    }

    val hiraganaView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, HIRAGANAS_TABLE_NAME, "id_kana", this::getHiragana)
    val katakanaView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, KATAKANAS_TABLE_NAME, "id_kana", this::getKatakana)
    val kanjiView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, KANJIS_TABLE_NAME, "id", this::getKanji)
    val wordView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, WORDS_TABLE_NAME, "id", this::getWord)

    fun search(text: String): List<Int> {
        readableDatabase.rawQuery(
                """SELECT id
                FROM $KANJIS_TABLE_NAME
                WHERE item = ? OR on_readings LIKE ? OR kun_readings LIKE ? OR meanings LIKE ?""",
                arrayOf(text, "%$text%", "%$text%", "%$text%")).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    fun getHiragana(id: Int): Item = getKana(HIRAGANAS_TABLE_NAME, SIMILAR_HIRAGANAS_TABLE_NAME, id)
    fun getKatakana(id: Int): Item = getKana(KATAKANAS_TABLE_NAME, SIMILAR_KATAKANAS_TABLE_NAME, id)

    private fun getKana(tableName: String, similarKanaTableName: String, id: Int): Item {
        val similarities = mutableListOf<Item>()
        readableDatabase.query(similarKanaTableName, arrayOf("similar_kana"), "id_kana = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kana("", "", listOf()), 0.0, 0.0, 0, false))
        }
        val contents = Kana("", "", similarities)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        readableDatabase.query(tableName, arrayOf("kana", "romaji", "short_score", "long_score", "last_correct", "enabled"), "id_kana = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kana with id $id in $tableName")
            cursor.moveToFirst()
            contents.kana = cursor.getString(0)
            contents.romaji = cursor.getString(1)
            item.shortScore = cursor.getDouble(2)
            item.longScore = cursor.getDouble(3)
            item.lastCorrect = cursor.getLong(4)
            item.enabled = cursor.getInt(5) != 0
        }
        return item
    }

    fun getKanji(id: Int): Item {
        val similarities = mutableListOf<Item>()
        readableDatabase.query(SIMILARITIES_TABLE_NAME, arrayOf("id_kanji2"), "id_kanji1 = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kanji("", listOf(), listOf(), listOf(), listOf(), 0), 0.0, 0.0, 0, false))
        }
        val contents = Kanji("", listOf(), listOf(), listOf(), similarities, 0)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        readableDatabase.query(KANJIS_TABLE_NAME,
                arrayOf("item", "jlpt_level", "short_score", "long_score", "last_correct", "enabled", "on_readings", "kun_readings", "meanings"),
                "id = ?", arrayOf(id.toString()),
                null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id " + id)
            cursor.moveToFirst()
            contents.kanji = cursor.getString(0)
            contents.on_readings = cursor.getString(6).split('_')
            contents.kun_readings = cursor.getString(7).split('_')
            contents.meanings = cursor.getString(8).split('_')
            contents.jlptLevel = cursor.getInt(1)
            item.shortScore = cursor.getDouble(2)
            item.longScore = cursor.getDouble(3)
            item.lastCorrect = cursor.getLong(4)
            item.enabled = cursor.getInt(5) != 0
        }
        return item
    }

    fun getWord(id: Int): Item {
        val contents = Word("", "", listOf())
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        readableDatabase.query(WORDS_TABLE_NAME,
                arrayOf("item", "reading", "meanings", "short_score", "long_score", "last_correct", "enabled"),
                "id = ?", arrayOf(id.toString()),
                null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id " + id)
            cursor.moveToFirst()
            contents.word = cursor.getString(0)
            contents.reading = cursor.getString(1)
            contents.meanings = cursor.getString(2).split('_')
            item.shortScore = cursor.getDouble(3)
            item.longScore = cursor.getDouble(4)
            item.lastCorrect = cursor.getLong(5)
            item.enabled = cursor.getInt(6) != 0
        }
        return item
    }

    fun setSelection(kanjis: String) {
        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues()
            cv.put("enabled", false)
            writableDatabase.update(KANJIS_TABLE_NAME, cv, null, null)
            cv.put("enabled", true)
            for (c in kanjis) {
                writableDatabase.update(KANJIS_TABLE_NAME, cv, "item = ?", arrayOf(c.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    data class Dump(val hiraganas: List<DumpRow>, val katakanas: List<DumpRow>, val kanjis: List<DumpRow>)
    data class DumpRow(val char: Char, val shortScore: Float, val longScore: Float, val lastCorrect: Long, val enabled: Boolean)

    fun dumpUserData(): Dump = dumpUserData(readableDatabase)
    fun restoreUserData(data: Dump) = restoreUserData(writableDatabase, data)

    companion object {
        private const val TAG = "KaquiDb"

        private const val DATABASE_NAME = "kanjis"
        private const val DATABASE_VERSION = 10

        private const val HIRAGANAS_TABLE_NAME = "hiraganas"
        private const val SIMILAR_HIRAGANAS_TABLE_NAME = "similar_hiraganas"

        private const val KATAKANAS_TABLE_NAME = "katakanas"
        private const val SIMILAR_KATAKANAS_TABLE_NAME = "similar_katakanas"

        private const val KANJIS_TABLE_NAME = "kanjis"
        private const val SIMILARITIES_TABLE_NAME = "similarities"

        private const val WORDS_TABLE_NAME = "words"

        private var singleton: KaquiDb? = null

        fun getInstance(context: Context): KaquiDb {
            if (singleton == null)
                singleton = KaquiDb(context)
            return singleton!!
        }

        fun dumpUserDataV9(database: SQLiteDatabase): Dump {
            val hiraganas = mutableListOf<DumpRow>()
            database.query(HIRAGANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    hiraganas.add(DumpRow(cursor.getString(0)[0], cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val katakanas = mutableListOf<DumpRow>()
            database.query(KATAKANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    katakanas.add(DumpRow(cursor.getString(0)[0], cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val kanjis = mutableListOf<DumpRow>()
            database.query(KANJIS_TABLE_NAME, arrayOf("kanji", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    kanjis.add(DumpRow(cursor.getString(0)[0], cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            return Dump(hiraganas, katakanas, kanjis)
        }

        fun dumpUserData(database: SQLiteDatabase): Dump {
            val hiraganas = mutableListOf<DumpRow>()
            database.query(HIRAGANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    hiraganas.add(DumpRow(cursor.getString(0)[0], cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val katakanas = mutableListOf<DumpRow>()
            database.query(KATAKANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    katakanas.add(DumpRow(cursor.getString(0)[0], cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            val kanjis = mutableListOf<DumpRow>()
            database.query(KANJIS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext())
                    kanjis.add(DumpRow(cursor.getString(0)[0], cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
            }
            return Dump(hiraganas, katakanas, kanjis)
        }

        fun restoreUserData(database: SQLiteDatabase, data: Dump) {
            database.beginTransaction()
            try {
                run {
                    val cv = ContentValues()
                    for (row in data.hiraganas) {
                        cv.put("short_score", row.shortScore)
                        cv.put("long_score", row.longScore)
                        cv.put("last_correct", row.lastCorrect)
                        cv.put("enabled", if (row.enabled) 1 else 0)
                        database.update(HIRAGANAS_TABLE_NAME, cv, "kana = ?", arrayOf(row.char.toString()))
                    }
                }
                run {
                    val cv = ContentValues()
                    for (row in data.katakanas) {
                        cv.put("short_score", row.shortScore)
                        cv.put("long_score", row.longScore)
                        cv.put("last_correct", row.lastCorrect)
                        cv.put("enabled", if (row.enabled) 1 else 0)
                        database.update(KATAKANAS_TABLE_NAME, cv, "kana = ?", arrayOf(row.char.toString()))
                    }
                }
                run {
                    for (row in data.kanjis) {
                        val cv = ContentValues()
                        cv.put("short_score", row.shortScore)
                        cv.put("long_score", row.longScore)
                        cv.put("last_correct", row.lastCorrect)
                        cv.put("enabled", if (row.enabled) 1 else 0)
                        if (database.update(KANJIS_TABLE_NAME, cv, "item = ?", arrayOf(row.char.toString())) == 0) {
                            cv.put("item", row.char.toString())
                            database.insert(KANJIS_TABLE_NAME, null, cv)
                        }
                    }
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }
}
