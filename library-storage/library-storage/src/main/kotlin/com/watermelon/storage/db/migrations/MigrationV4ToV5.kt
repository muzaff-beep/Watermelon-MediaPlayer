package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * v4 → v5: add a covering index on MediaItems(parentFolder) to speed up folder-grouped
 * queries on large libraries. Pure index addition — no data is touched. Idempotent.
 */
object MigrationV4ToV5 {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_media_parentFolder ON MediaItems(parentFolder);"
        )
    }
}
