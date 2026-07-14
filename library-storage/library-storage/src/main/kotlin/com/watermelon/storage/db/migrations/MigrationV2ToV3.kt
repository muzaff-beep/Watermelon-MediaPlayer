package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/** v2 → v3: introduce the PlaylistItems membership table. Idempotent. */
object MigrationV2ToV3 {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS PlaylistItems (
                playlistId TEXT NOT NULL,
                mediaId    TEXT NOT NULL,
                sortOrder  INTEGER,
                PRIMARY KEY (playlistId, mediaId)
            );
            """.trimIndent()
        )
    }
}
