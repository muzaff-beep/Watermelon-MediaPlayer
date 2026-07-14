package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * V8 adds three tables for playlist support:
 *   Playlists      — user-created playlist metadata
 *   PlaylistItems  — videos inside user playlists
 *   Favourites     — videos marked as favourite
 *
 * Recently Added is computed dynamically from MediaItems.firstSeenAt — no table needed.
 */
object MigrationV7ToV8 {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Playlists (
                id        TEXT PRIMARY KEY,
                name      TEXT NOT NULL,
                type      TEXT NOT NULL DEFAULT 'USER',
                createdAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS PlaylistItems (
                playlistId TEXT NOT NULL,
                uri        TEXT NOT NULL,
                addedAt    INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (playlistId, uri)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Favourites (
                uri     TEXT PRIMARY KEY,
                addedAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}
