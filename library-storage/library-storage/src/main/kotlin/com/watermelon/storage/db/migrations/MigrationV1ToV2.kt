package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/** v1 → v2: introduce the Playlists table. Idempotent. */
object MigrationV1ToV2 {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Playlists (
                playlistId TEXT PRIMARY KEY,
                name       TEXT NOT NULL,
                createdAt  INTEGER,
                updatedAt  INTEGER
            );
            """.trimIndent()
        )
    }
}
