package com.watermelon.storage.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.watermelon.storage.db.migrations.MigrationV1ToV2
import com.watermelon.storage.db.migrations.MigrationV2ToV3
import com.watermelon.storage.db.migrations.MigrationV3ToV4
import com.watermelon.storage.db.migrations.MigrationV4ToV5
import com.watermelon.storage.db.migrations.MigrationV5ToV6
import com.watermelon.storage.db.migrations.MigrationV6ToV7
import com.watermelon.storage.db.migrations.MigrationV7ToV8
import com.watermelon.storage.db.migrations.MigrationV8ToV9
import com.watermelon.storage.db.migrations.MigrationV9ToV10

/**
 * Hand-written [SQLiteOpenHelper] (no Room). Schema is frozen — Handover §2.2 / Manifest §10.1.
 *
 * Migration policy (Manifest §10.1 "Migration Rules"):
 *  - [onUpgrade] iterates oldVersion → newVersion applying ordered, idempotent steps.
 *  - Every step guards with `IF NOT EXISTS`; drop-and-recreate is forbidden.
 *  - `PlaybackPositions` and `SubtitleOffsets` are never dropped or truncated.
 */
class WatermelonDatabase(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        createBaselineV1(db)
        runMigrations(db, fromVersion = 1, toVersion = DATABASE_VERSION)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        runMigrations(db, fromVersion = oldVersion, toVersion = newVersion)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    private fun runMigrations(db: SQLiteDatabase, fromVersion: Int, toVersion: Int) {
        com.watermelon.common.util.FileLogger.i("DB", "runMigrations $fromVersion -> $toVersion")
        for (version in fromVersion until toVersion) {
            com.watermelon.common.util.FileLogger.i("DB", "applying migration step for version $version")
            when (version) {
                1 -> MigrationV1ToV2.migrate(db)
                2 -> MigrationV2ToV3.migrate(db)
                3 -> MigrationV3ToV4.migrate(db)
                4 -> MigrationV4ToV5.migrate(db)
                5 -> MigrationV5ToV6.migrate(db)
                6 -> MigrationV6ToV7.migrate(db)
                7 -> MigrationV7ToV8.migrate(db)
                8 -> MigrationV8ToV9.migrate(db)
                9 -> MigrationV9ToV10.migrate(db)
            }
        }
        com.watermelon.common.util.FileLogger.i("DB", "migrations complete, now at v$toVersion")
    }

    private fun createBaselineV1(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS MediaItems (
                mediaId      TEXT PRIMARY KEY,
                fileSize     INTEGER NOT NULL,
                displayName  TEXT NOT NULL,
                parentFolder TEXT NOT NULL,
                durationMs   INTEGER,
                width        INTEGER,
                height       INTEGER,
                mimeType     TEXT
            );
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Folders (
                folderPath    TEXT PRIMARY KEY,
                displayName   TEXT NOT NULL,
                itemCount     INTEGER DEFAULT 0,
                lastScannedAt INTEGER
            );
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS PlaybackPositions (
                mediaId    TEXT NOT NULL,
                fileSize   INTEGER NOT NULL,
                positionMs INTEGER NOT NULL,
                updatedAt  INTEGER,
                PRIMARY KEY (mediaId, fileSize)
            );
            """.trimIndent()
        )
    }

    companion object {
        const val DATABASE_NAME = "watermelon.db"
        const val DATABASE_VERSION = 10
    }
}