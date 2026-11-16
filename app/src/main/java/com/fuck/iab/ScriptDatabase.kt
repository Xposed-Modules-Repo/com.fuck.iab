package com.fuck.iab

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Where a script file lives: among the APK assets, or in the Xposed file store. */
enum class ScriptOrigin { BUNDLED, STORE }

/**
 * The `// key: value` tags of one script file, as they read the last time the file was opened.
 * `file` is the name inside [origin]: `com.foo.js` among the assets, the store file name otherwise.
 */
internal data class ScriptMeta(
    val file: String,
    val origin: ScriptOrigin,
    val author: String? = null,
    val name: String? = null,
    val url: String? = null,
    val summary: String? = null,
    val description: String? = null,
    /** The `// version:` tag, `null` when the script carries none. */
    val version: Int? = null,
    val optIn: Boolean = false,
    val size: Int = 0,
    val modified: Long = 0L
)

/**
 * Cache of what the header block of a script says, so listing the scripts never has to open
 * them. A file is read once, while it is unknown to the cache, and again only after the app has
 * written it. The files stay the source of truth: a database that cannot be opened only costs a
 * re-read, and a row that the cache lost is rebuilt from the file.
 */
internal object ScriptDatabase {

    private const val DB_NAME = "script_meta.db"
    private const val DB_VERSION = 1

    private const val TABLE = "script_meta"
    private const val SYNC = "sync"

    private val COLUMNS = arrayOf(
        "file", "origin", "author", "name", "url", "summary", "description",
        "version", "opt_in", "size", "modified"
    )

    private var helper: Helper? = null

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (origin TEXT NOT NULL, file TEXT NOT NULL, author TEXT, " +
                        "name TEXT, url TEXT, summary TEXT, description TEXT, version INTEGER, " +
                        "opt_in INTEGER NOT NULL DEFAULT 0, size INTEGER NOT NULL DEFAULT 0, " +
                        "modified INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (origin, file))"
            )
            db.execSQL("CREATE TABLE $SYNC (key TEXT PRIMARY KEY, value TEXT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // the rows only repeat what the files say, so dropping them is safe
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            db.execSQL("DROP TABLE IF EXISTS $SYNC")
            onCreate(db)
        }
    }

    private fun database(context: Context): SQLiteDatabase? {
        return try {
            val open = synchronized(this) {
                helper ?: Helper(context.applicationContext).also { helper = it }
            }
            open.writableDatabase
        } catch (e: Exception) {
            null
        }
    }

    /** Every cached row of `origin`, keyed by file name. Empty when the cache cannot be read. */
    fun readAll(context: Context, origin: ScriptOrigin): Map<String, ScriptMeta> {
        val db = database(context) ?: return emptyMap()

        val ret = hashMapOf<String, ScriptMeta>()
        try {
            db.query(TABLE, COLUMNS, "origin = ?", arrayOf(origin.name), null, null, null)
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        val meta = read(cursor, origin)
                        ret[meta.file] = meta
                    }
                }
        } catch (e: Exception) {
            return emptyMap()
        }
        return ret
    }

    fun save(context: Context, meta: ScriptMeta) {
        save(context, listOf(meta))
    }

    fun save(context: Context, metas: List<ScriptMeta>) {
        if (metas.isEmpty()) return

        val db = database(context) ?: return
        try {
            db.beginTransaction()
            try {
                metas.forEach { meta ->
                    db.insertWithOnConflict(TABLE, null, values(meta), SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            // the files are still there, the next listing simply reads them again
        }
    }

    fun delete(context: Context, origin: ScriptOrigin, file: String) {
        val db = database(context) ?: return
        try {
            db.delete(TABLE, "origin = ? AND file = ?", arrayOf(origin.name, file))
        } catch (e: Exception) {
            // a leftover row only repeats a file that no longer exists
        }
    }

    fun deleteAll(context: Context, origin: ScriptOrigin) {
        val db = database(context) ?: return
        try {
            db.delete(TABLE, "origin = ?", arrayOf(origin.name))
        } catch (e: Exception) {
            // rebuilt from the files on the next listing
        }
    }

    /**
     * Drops every row of `origin` whose file is not one of `files`, so a script that was deleted,
     * unpublished or removed from the APK stops being listed.
     */
    fun retain(context: Context, origin: ScriptOrigin, files: Set<String>) {
        val stale = readAll(context, origin).keys - files
        if (stale.isEmpty()) return

        val db = database(context) ?: return
        try {
            db.beginTransaction()
            try {
                stale.forEach { file ->
                    db.delete(TABLE, "origin = ? AND file = ?", arrayOf(origin.name, file))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            // a leftover row only repeats a file that no longer exists
        }
    }

    /** The value last written under `key`, `null` when there is none. */
    fun sync(context: Context, key: String): String? {
        val db = database(context) ?: return null

        return try {
            db.query(SYNC, arrayOf("value"), "key = ?", arrayOf(key), null, null, null)
                .use { cursor -> if (cursor.moveToFirst()) cursor.string("value") else null }
        } catch (e: Exception) {
            null
        }
    }

    fun setSync(context: Context, key: String, value: String) {
        val db = database(context) ?: return
        try {
            db.insertWithOnConflict(
                SYNC, null,
                ContentValues().apply {
                    put("key", key)
                    put("value", value)
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            // the worst case is dropping the rows once more than needed
        }
    }

    private fun read(cursor: Cursor, origin: ScriptOrigin) = ScriptMeta(
        file = cursor.string("file").orEmpty(),
        origin = origin,
        author = cursor.string("author"),
        name = cursor.string("name"),
        url = cursor.string("url"),
        summary = cursor.string("summary"),
        description = cursor.string("description"),
        version = cursor.int("version"),
        optIn = cursor.int("opt_in") == 1,
        size = cursor.int("size") ?: 0,
        modified = cursor.long("modified") ?: 0L
    )

    private fun values(meta: ScriptMeta) = ContentValues().apply {
        put("origin", meta.origin.name)
        put("file", meta.file)
        put("author", meta.author)
        put("name", meta.name)
        put("url", meta.url)
        put("summary", meta.summary)
        put("description", meta.description)
        if (meta.version == null) putNull("version") else put("version", meta.version)
        put("opt_in", if (meta.optIn) 1 else 0)
        put("size", meta.size)
        put("modified", meta.modified)
    }

    private fun Cursor.index(column: String): Int = getColumnIndexOrThrow(column)

    private fun Cursor.string(column: String): String? {
        val index = index(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.int(column: String): Int? {
        val index = index(column)
        return if (isNull(index)) null else getInt(index)
    }

    private fun Cursor.long(column: String): Long? {
        val index = index(column)
        return if (isNull(index)) null else getLong(index)
    }
}
