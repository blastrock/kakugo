package org.kaqui.model

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log

class DatabaseUpdater(private val database: SQLiteDatabase, private val dictDb: String) {
    data class Dump(val hiraganas: List<DumpRow>, val katakanas: List<DumpRow>, val kanjis: List<DumpRow>, val words: List<DumpRow>, val kanjiSelections: Map<String, List<String>>)
    data class DumpRow(val item: String, val shortScore: Float, val longScore: Float, val lastCorrect: Long, val enabled: Boolean)

    private fun createDatabase() {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANJIS_TABLE_NAME} ("
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
                "CREATE TABLE IF NOT EXISTS ${Database.STROKES_TABLE_NAME} ("
                        + "id INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "ordinal INT NOT NULL,"
                        + "path TEXT NOT NULL,"
                        + "UNIQUE(id_kanji, ordinal)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANJIS_COMPOSITION_TABLE_NAME} ("
                        + "id_composition INTEGER PRIMARY KEY,"
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.SIMILARITIES_TABLE_NAME} ("
                        + "id_kanji1 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "id_kanji2 INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY (id_kanji1, id_kanji2)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANJIS_ITEM_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER NOT NULL,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id),"
                        + "PRIMARY KEY(id_selection, id_kanji)"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.KANJIS_SELECTION_TABLE_NAME} ("
                        + "id_selection INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${Database.WORDS_TABLE_NAME} ("
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

        createKanaTable(Database.HIRAGANAS_TABLE_NAME, Database.HIRAGANA_STROKES_TABLE_NAME, Database.SIMILAR_HIRAGANAS_TABLE_NAME)
        createKanaTable(Database.KATAKANAS_TABLE_NAME, Database.KATAKANA_STROKES_TABLE_NAME, Database.SIMILAR_KATAKANAS_TABLE_NAME)
    }

    private fun createKanaTable(tableName: String, strokesTableName: String, similarKanaTableName: String) {
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

    private fun doUpgrade() {
        database.execSQL("ATTACH DATABASE ? AS dict", arrayOf(dictDb))
        database.beginTransaction()
        try {
            val oldVersion = database.version

            val dump =
                    when {
                        oldVersion == 0 -> null
                        oldVersion < 10 -> dumpUserDataV9()
                        oldVersion < 13 -> dumpUserDataV12()
                        oldVersion < 17 -> dumpUserDataV16()
                        else -> dumpUserData()
                    }
            database.execSQL("DROP TABLE IF EXISTS main.meanings")
            database.execSQL("DROP TABLE IF EXISTS main.readings")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.SIMILARITIES_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.STROKES_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KANJIS_ITEM_SELECTION_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KANJIS_SELECTION_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KANJIS_COMPOSITION_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KANJIS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.HIRAGANAS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.SIMILAR_HIRAGANAS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.KATAKANAS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.SIMILAR_KATAKANAS_TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS main.${Database.WORDS_TABLE_NAME}")
            createDatabase()

            replaceDict()
            if (dump != null)
                restoreUserData(dump)
            database.version = DATABASE_VERSION
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
            database.execSQL("DETACH DATABASE dict")
        }
    }

    private fun replaceDict() {
        replaceKanas(Database.HIRAGANAS_TABLE_NAME, Database.HIRAGANA_STROKES_TABLE_NAME, Database.SIMILAR_HIRAGANAS_TABLE_NAME)
        replaceKanas(Database.KATAKANAS_TABLE_NAME, Database.KATAKANA_STROKES_TABLE_NAME, Database.SIMILAR_KATAKANAS_TABLE_NAME)

        database.delete(Database.SIMILARITIES_TABLE_NAME, null, null)
        database.delete(Database.STROKES_TABLE_NAME, null, null)
        database.delete(Database.KANJIS_COMPOSITION_TABLE_NAME, null, null)
        database.delete(Database.KANJIS_TABLE_NAME, null, null)
        database.execSQL(
                "INSERT INTO ${Database.KANJIS_TABLE_NAME} "
                        + "(id, on_readings, kun_readings, meanings_en, meanings_fr, jlpt_level, kaqui_level, part_count, radical, enabled) "
                        + "SELECT id, on_readings, kun_readings, meanings_en, meanings_fr, jlpt_level, kaqui_level, part_count, radical, jlpt_level = 5 "
                        + "FROM dict.kanjis"
        )
        database.execSQL(
                "INSERT INTO ${Database.STROKES_TABLE_NAME} "
                        + "(id, id_kanji, ordinal, path) "
                        + "SELECT id, id_kanji, ordinal, path "
                        + "FROM dict.strokes"
        )
        database.execSQL(
                "INSERT INTO ${Database.SIMILARITIES_TABLE_NAME} "
                        + "(id_kanji1, id_kanji2) "
                        + "SELECT id_kanji1, id_kanji2 "
                        + "FROM dict.kanjis_similars "
        )
        database.execSQL(
                "INSERT INTO ${Database.KANJIS_COMPOSITION_TABLE_NAME} "
                        + "(id_kanji1, id_kanji2) "
                        + "SELECT id_kanji1, id_kanji2 "
                        + "FROM dict.kanjis_composition "
        )

        database.delete(Database.WORDS_TABLE_NAME, null, null)
        database.execSQL(
                "INSERT INTO ${Database.WORDS_TABLE_NAME} "
                        + "(id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class) "
                        + "SELECT id, item, reading, meanings_en, meanings_fr, jlpt_level, similarity_class "
                        + "FROM dict.words"
        )
    }

    private fun replaceKanas(tableName: String, strokesTableName: String, similarKanaTableName: String) {
        database.delete(tableName, null, null)
        database.delete(similarKanaTableName, null, null)

        database.execSQL(
                "INSERT INTO $tableName "
                        + "(id, romaji, unique_romaji) "
                        + "SELECT id, romaji, unique_romaji "
                        + "FROM dict.$tableName"
        )
        database.execSQL(
                "INSERT INTO $strokesTableName "
                        + "(id, id_kana, ordinal, path) "
                        + "SELECT id, id_kana, ordinal, path "
                        + "FROM dict.$strokesTableName"
        )
        database.execSQL(
                "INSERT INTO $similarKanaTableName "
                        + "(id_kana1, id_kana2) "
                        + "SELECT id_kana1, id_kana2 "
                        + "FROM dict.$similarKanaTableName"
        )
    }

    private fun dumpUserDataV9(): Dump {
        val hiraganas = mutableListOf<DumpRow>()
        database.query(Database.HIRAGANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                hiraganas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val katakanas = mutableListOf<DumpRow>()
        database.query(Database.KATAKANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                katakanas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val kanjis = mutableListOf<DumpRow>()
        database.query(Database.KANJIS_TABLE_NAME, arrayOf("kanji", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                kanjis.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        return Dump(hiraganas, katakanas, kanjis, listOf(), mapOf())
    }

    private fun dumpUserDataV12(): Dump {
        val hiraganas = mutableListOf<DumpRow>()
        database.query(Database.HIRAGANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                hiraganas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val katakanas = mutableListOf<DumpRow>()
        database.query(Database.KATAKANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                katakanas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val kanjis = mutableListOf<DumpRow>()
        database.query(Database.KANJIS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                kanjis.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val words = mutableListOf<DumpRow>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                words.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        return Dump(hiraganas, katakanas, kanjis, words, mapOf())
    }

    private fun dumpUserDataV16(): Dump {
        val hiraganas = mutableListOf<DumpRow>()
        database.query(Database.HIRAGANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                hiraganas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val katakanas = mutableListOf<DumpRow>()
        database.query(Database.KATAKANAS_TABLE_NAME, arrayOf("kana", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                katakanas.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val kanjis = mutableListOf<DumpRow>()
        database.query(Database.KANJIS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                kanjis.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val kanjiSelections = mutableMapOf<String, MutableList<String>>()
        database.rawQuery("""
                SELECT ks.name, k.item
                FROM ${Database.KANJIS_SELECTION_TABLE_NAME} ks
                LEFT JOIN ${Database.KANJIS_ITEM_SELECTION_TABLE_NAME} kis USING(id_selection)
                LEFT JOIN ${Database.KANJIS_TABLE_NAME} k ON kis.id_kanji = k.id
            """, null).use { cursor ->
            while (cursor.moveToNext())
                kanjiSelections.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getString(1))
        }
        val words = mutableListOf<DumpRow>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                words.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        return Dump(hiraganas, katakanas, kanjis, words, kanjiSelections)
    }

    private fun dumpUserData(): Dump {
        val hiraganas = mutableListOf<DumpRow>()
        database.query(Database.HIRAGANAS_TABLE_NAME, arrayOf("id", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                hiraganas.add(DumpRow(Character.toChars(cursor.getInt(0)).joinToString(), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val katakanas = mutableListOf<DumpRow>()
        database.query(Database.KATAKANAS_TABLE_NAME, arrayOf("id", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                katakanas.add(DumpRow(Character.toChars(cursor.getInt(0)).joinToString(), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val kanjis = mutableListOf<DumpRow>()
        database.query(Database.KANJIS_TABLE_NAME, arrayOf("id", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                kanjis.add(DumpRow(Character.toChars(cursor.getInt(0)).joinToString(), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        val kanjiSelections = mutableMapOf<String, MutableList<String>>()
        database.rawQuery("""
                SELECT ks.name, kis.id_kanji
                FROM ${Database.KANJIS_SELECTION_TABLE_NAME} ks
                LEFT JOIN ${Database.KANJIS_ITEM_SELECTION_TABLE_NAME} kis USING(id_selection)
            """, null).use { cursor ->
            while (cursor.moveToNext())
                kanjiSelections.getOrPut(cursor.getString(0)) { mutableListOf() }.add(Character.toChars(cursor.getInt(1)).joinToString())
        }
        val words = mutableListOf<DumpRow>()
        database.query(Database.WORDS_TABLE_NAME, arrayOf("item", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext())
                words.add(DumpRow(cursor.getString(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0))
        }
        return Dump(hiraganas, katakanas, kanjis, words, kanjiSelections)
    }

    private fun restoreUserData(data: Dump) {
        database.beginTransaction()
        try {
            run {
                val cv = ContentValues()
                for (row in data.hiraganas) {
                    cv.put("short_score", row.shortScore)
                    cv.put("long_score", row.longScore)
                    cv.put("last_correct", row.lastCorrect)
                    cv.put("enabled", if (row.enabled) 1 else 0)
                    database.update(Database.HIRAGANAS_TABLE_NAME, cv, "id = ?", arrayOf(row.item.codePointAt(0).toString()))
                }
            }
            run {
                val cv = ContentValues()
                for (row in data.katakanas) {
                    cv.put("short_score", row.shortScore)
                    cv.put("long_score", row.longScore)
                    cv.put("last_correct", row.lastCorrect)
                    cv.put("enabled", if (row.enabled) 1 else 0)
                    database.update(Database.KATAKANAS_TABLE_NAME, cv, "id = ?", arrayOf(row.item.codePointAt(0).toString()))
                }
            }
            run {
                for (row in data.kanjis) {
                    val cv = ContentValues()
                    cv.put("short_score", row.shortScore)
                    cv.put("long_score", row.longScore)
                    cv.put("last_correct", row.lastCorrect)
                    cv.put("enabled", if (row.enabled) 1 else 0)
                    database.update(Database.KANJIS_TABLE_NAME, cv, "id = ?", arrayOf(row.item.codePointAt(0).toString()))
                }
            }
            run {
                database.delete(Database.KANJIS_ITEM_SELECTION_TABLE_NAME, null, null)
                database.delete(Database.KANJIS_SELECTION_TABLE_NAME, null, null)
                for ((selectionName, selectionItems) in data.kanjiSelections) {
                    val cv = ContentValues()
                    cv.put("name", selectionName)
                    val selectionId = database.insertOrThrow(Database.KANJIS_SELECTION_TABLE_NAME, null, cv)
                    for (item in selectionItems) {
                        val cv = ContentValues()
                        cv.put("id_selection", selectionId)
                        cv.put("id_kanji", item.codePointAt(0))
                        database.insertOrThrow(Database.KANJIS_ITEM_SELECTION_TABLE_NAME, null, cv)
                    }
                }
            }
            run {
                for (row in data.words) {
                    val cv = ContentValues()
                    cv.put("short_score", row.shortScore)
                    cv.put("long_score", row.longScore)
                    cv.put("last_correct", row.lastCorrect)
                    cv.put("enabled", if (row.enabled) 1 else 0)
                    database.update(Database.WORDS_TABLE_NAME, cv, "item = ?", arrayOf(row.item))
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    companion object {
        const val TAG = "DatabaseUpdater"
        const val DATABASE_VERSION = 17

        fun databaseNeedsUpdate(context: Context): Boolean {
            try {
                SQLiteDatabase.openDatabase(context.getDatabasePath(Database.DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    if (db.version != DATABASE_VERSION)
                        return true

                    return false
                }
            } catch (e: SQLiteException) {
                Log.i(TAG, "Failed to open database", e)
                return true
            }
        }

        fun upgradeDatabase(context: Context, dictDb: String) {
            // the databases folder may not exist on older androids
            context.getDatabasePath(Database.DATABASE_NAME).parentFile.mkdirs()
            SQLiteDatabase.openDatabase(context.getDatabasePath(Database.DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use { db ->
                DatabaseUpdater(db, dictDb).doUpgrade()
            }
        }
    }
}
