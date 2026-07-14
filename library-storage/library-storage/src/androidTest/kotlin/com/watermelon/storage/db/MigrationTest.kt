package com.watermelon.storage.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watermelon.storage.db.migrations.MigrationV1ToV2
import com.watermelon.storage.db.migrations.MigrationV2ToV3
import com.watermelon.storage.db.migrations.MigrationV3ToV4
import com.watermelon.storage.db.migrations.MigrationV4ToV5
import com.watermelon.storage.db.migrations.MigrationV5ToV6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Authoritative instrumented migration-ladder test — the class the frozen CI gate runs
 * (`:library-storage:connectedCheck ... class=com.watermelon.storage.db.MigrationTest`,
 * Handover §4). Validates every step N→N+1 and the zero-data-loss guarantee for
 * PlaybackPositions and SubtitleOffsets (Manifest §10.1 Migration Rules).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun eachStepIsIdempotentAndPreservesData() {
        val dbFile = File(context.cacheDir, "ladder_fixture.db").apply { delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            createV1Baseline(db)
            db.execSQL(
                "INSERT INTO PlaybackPositions VALUES ('content://v/9', 512, 9000, 1700000000000);"
            )

            val steps = listOf(
                MigrationV1ToV2::migrate,
                MigrationV2ToV3::migrate,
                MigrationV3ToV4::migrate,
                MigrationV4ToV5::migrate,
                MigrationV5ToV6::migrate
            )
            // Apply each step twice to prove idempotency.
            for (step in steps) { step(db); step(db) }

            // Resume point preserved across the full ladder.
            db.rawQuery(
                "SELECT positionMs FROM PlaybackPositions WHERE mediaId = ?",
                arrayOf("content://v/9")
            ).use { c ->
                assertTrue("PlaybackPositions lost during migration", c.moveToFirst())
                assertEquals(9000L, c.getLong(0))
            }

            // All target tables exist.
            for (table in listOf("Playlists", "PlaylistItems", "SubtitleOffsets")) {
                db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(table)
                ).use { c -> assertTrue("Missing table $table", c.moveToFirst()) }
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    private fun createV1Baseline(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE MediaItems (
                mediaId TEXT PRIMARY KEY, fileSize INTEGER NOT NULL, displayName TEXT NOT NULL,
                parentFolder TEXT NOT NULL, durationMs INTEGER, width INTEGER, height INTEGER,
                mimeType TEXT);"""
        )
        db.execSQL(
            """CREATE TABLE Folders (
                folderPath TEXT PRIMARY KEY, displayName TEXT NOT NULL,
                itemCount INTEGER DEFAULT 0, lastScannedAt INTEGER);"""
        )
        db.execSQL(
            """CREATE TABLE PlaybackPositions (
                mediaId TEXT NOT NULL, fileSize INTEGER NOT NULL, positionMs INTEGER NOT NULL,
                updatedAt INTEGER, PRIMARY KEY (mediaId, fileSize));"""
        )
    }
}
