package com.kuangcx.ufm.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class FavItem(val path: String, val mediaId: Long, val addedAt: Long)

/** Splits favourites into still-existing and vanished entries; the caller decides when to delete. */
fun partitionMissing(
    items: List<FavItem>,
    exists: (String) -> Boolean
): Pair<List<FavItem>, List<FavItem>> = items.partition { exists(it.path) }

/** A path can only be favourited once; when duplicates exist the earliest timestamp wins. */
fun dedupeByPath(items: List<FavItem>): List<FavItem> =
    items.groupBy { it.path }
        .map { (_, group) -> group.minByOrNull { it.addedAt }!! }
        .sortedByDescending { it.addedAt }

/**
 * Favourites are references only: the photo stays wherever it lives, so moving it between groups
 * does not disturb the list. Entries whose file disappeared are pruned on read.
 */
class FavStore(context: Context) : SQLiteOpenHelper(context, "favorites.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE favorites (
              path TEXT PRIMARY KEY,
              media_id INTEGER,
              added_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(path: String, mediaId: Long) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO favorites(path, media_id, added_at) VALUES(?,?,?)",
            arrayOf<Any>(path, mediaId, System.currentTimeMillis())
        )
    }

    fun remove(path: String) {
        writableDatabase.delete("favorites", "path=?", arrayOf(path))
    }

    fun list(): List<FavItem> {
        val out = mutableListOf<FavItem>()
        readableDatabase
            .rawQuery("SELECT path, media_id, added_at FROM favorites ORDER BY added_at DESC", null)
            .use { c ->
                while (c.moveToNext()) out += FavItem(c.getString(0), c.getLong(1), c.getLong(2))
            }
        return out
    }

    /** Drops rows whose file no longer exists; returns how many were removed. */
    fun pruneMissing(): Int {
        val (_, gone) = partitionMissing(list()) { java.io.File(it).exists() }
        gone.forEach { remove(it.path) }
        return gone.size
    }
}
