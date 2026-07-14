package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * v9 → v10: fixes a schema collision introduced by early migrations.
 *
 * MigrationV1ToV2 / MigrationV2ToV3 created `Playlists(playlistId, name, createdAt, updatedAt)`
 * and `PlaylistItems(playlistId, mediaId, sortOrder)` — an old, unused schema.
 *
 * MigrationV7ToV8 later tried to introduce the schema that [PlaylistRepositoryImpl] actually
 * uses — `Playlists(id, name, type, createdAt)` and `PlaylistItems(playlistId, uri, addedAt)` —
 * but used `CREATE TABLE IF NOT EXISTS`. Because the V1-V3 tables already existed by that point,
 * that statement silently no-opped, leaving every database (fresh installs included, since
 * onCreate runs all migrations in order) stuck with the old, incompatible schema. Playlist
 * reads/writes have been failing silently (caught by runCatching in the repository) ever since.
 *
 * This migration renames the old tables out of the way, creates the correct schema, migrates
 * any existing rows across (best-effort — the old schema has no `type`/`uri` data, so we map
 * what we can), and drops the renamed-aside old tables. Idempotent: guarded so it's safe to
 * run against a database that's already correct (e.g. if a future fresh install skips straight
 * to v10's baseline).
 */
object MigrationV9ToV10 {
    fun migrate(db: SQLiteDatabase) {
        if (!hasOldPlaylistsSchema(db)) {
            // Already correct — either a fresh v10+ baseline, or this migration already ran.
            ensureCorrectTablesExist(db)
            return
        }

        db.beginTransaction()
        try {
            // 1. Rename the old-schema tables out of the way.
            db.execSQL("ALTER TABLE Playlists RENAME TO Playlists_old_v9")
            db.execSQL("ALTER TABLE PlaylistItems RENAME TO PlaylistItems_old_v9")

            // 2. Create the correct schema.
            ensureCorrectTablesExist(db)

            // 3. Migrate any existing user playlists across. Old schema has no `type` column,
            //    so every migrated row is treated as a USER playlist (the only kind that was
            //    ever persisted here, since Favourites/Recently Added are handled separately).
            db.execSQL(
                """
                INSERT OR IGNORE INTO Playlists (id, name, type, createdAt)
                SELECT playlistId, name, 'USER', COALESCE(createdAt, 0)
                FROM Playlists_old_v9
                """.trimIndent()
            )

            // 4. Migrate playlist membership. Old schema stored `mediaId`, which for this app
            //    is the same content:// URI string used as `uri` elsewhere, so it maps directly.
            db.execSQL(
                """
                INSERT OR IGNORE INTO PlaylistItems (playlistId, uri, addedAt)
                SELECT playlistId, mediaId, 0
                FROM PlaylistItems_old_v9
                """.trimIndent()
            )

            // 5. Drop the old tables now that data has been carried over.
            db.execSQL("DROP TABLE IF EXISTS PlaylistItems_old_v9")
            db.execSQL("DROP TABLE IF EXISTS Playlists_old_v9")

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** True if `Playlists` currently has the old (v1-v3) column layout. */
    private fun hasOldPlaylistsSchema(db: SQLiteDatabase): Boolean {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(Playlists)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) columns += cursor.getString(nameIdx)
        }
        // Old schema has `playlistId`; new schema has `id` instead.
        return columns.contains("playlistId") && !columns.contains("id")
    }

    private fun ensureCorrectTablesExist(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Playlists (
                id        TEXT PRIMARY KEY,
                name      TEXT NOT NULL,
                type      TEXT NOT NULL DEFAULT 'USER',
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS PlaylistItems (
                playlistId TEXT NOT NULL,
                uri        TEXT NOT NULL,
                addedAt    INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (playlistId, uri)
            )
            """.trimIndent()
        )
    }
}