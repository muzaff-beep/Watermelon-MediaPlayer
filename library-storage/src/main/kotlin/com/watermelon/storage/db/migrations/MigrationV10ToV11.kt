package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * V11 adds:
 *   - MediaItems.dateModified column (epoch-ms from MediaStore DATE_MODIFIED) for the
 *     Folder screen's "Modified" sort.
 *
 * Distinct from the existing `dateAdded` (DATE_ADDED, "when this file was indexed/
 * discovered by the app"): `dateModified` tracks the file's own content modification time
 * — e.g. a video re-encoded or edited after being added would show a newer dateModified
 * than dateAdded. Folder.lastModifiedAt (the max across a folder's videos) is computed
 * from this column, not dateAdded, so "Modified" genuinely reflects file content changes.
 */
object MigrationV10ToV11 {
    fun migrate(db: SQLiteDatabase) {
        // default 0 for existing rows; Phase2Extractor backfills real values on next index.
        runCatching {
            db.execSQL("ALTER TABLE MediaItems ADD COLUMN dateModified INTEGER NOT NULL DEFAULT 0")
        }
    }
}
