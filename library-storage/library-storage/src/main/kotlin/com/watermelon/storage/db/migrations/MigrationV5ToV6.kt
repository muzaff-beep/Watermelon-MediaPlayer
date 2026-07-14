package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * v5 → v6: add an index on PlaybackPositions(updatedAt) to support "recently played"
 * ordering. Index-only change; PlaybackPositions data is preserved. Idempotent.
 */
object MigrationV5ToV6 {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_positions_updatedAt ON PlaybackPositions(updatedAt);"
        )
    }
}
