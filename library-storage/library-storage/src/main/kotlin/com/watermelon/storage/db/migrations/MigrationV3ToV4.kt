package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * v3 → v4: introduce the SubtitleOffsets table for non-destructive subtitle corrections.
 * Idempotent. This table must never be dropped or truncated by later migrations.
 */
object MigrationV3ToV4 {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS SubtitleOffsets (
                mediaId   TEXT NOT NULL,
                fileSize  INTEGER NOT NULL,
                lang      TEXT NOT NULL,
                offsetMs  INTEGER NOT NULL,
                syncMode  TEXT NOT NULL,          -- 'LINEAR' | 'LEXICAL'
                updatedAt INTEGER,
                PRIMARY KEY (mediaId, fileSize, lang)
            );
            """.trimIndent()
        )
    }
}
