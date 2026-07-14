package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * v6 → v7: add [firstSeenAt] and [lastPlayedAt] columns to MediaItems to power the
 * ⭐ new-file badge.
 *
 * [firstSeenAt] — epoch-ms when the URI was first indexed (written once on INSERT, never
 *                 overwritten). DEFAULT 0 so pre-migration rows are treated as "old".
 * [lastPlayedAt] — epoch-ms when playback last started. NULL means never played → ⭐ shown.
 *
 * Note: ALTER TABLE ADD COLUMN is not idempotent — the version system guarantees this
 * migration runs exactly once per database. MediaItems data is fully preserved.
 */
object MigrationV6ToV7 {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE MediaItems ADD COLUMN firstSeenAt INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE MediaItems ADD COLUMN lastPlayedAt INTEGER")
    }
}
