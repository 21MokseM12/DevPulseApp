package com.devpulse.app.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class AppDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName: String = "app-database-migration-test.db"

    @get:Rule
    val migrationHelper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrateFromVersion2To4_keepsUpdatesAndDropsLegacySessionTable() {
        createDatabase(
            version = 2,
            setupSql =
                listOf(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_session` (
                        `id` INTEGER NOT NULL,
                        `clientLogin` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                    """
                    CREATE TABLE IF NOT EXISTS `updates_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `remoteEventId` TEXT,
                        `linkUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `receivedAtEpochMs` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_updates_history_remoteEventId`
                    ON `updates_history` (`remoteEventId`)
                    """.trimIndent(),
                    """
                    INSERT INTO `cached_session` (`id`, `clientLogin`, `updatedAtEpochMs`)
                    VALUES (0, 'moksem', 1715700000000)
                    """.trimIndent(),
                    """
                    INSERT INTO `updates_history`
                    (`id`, `remoteEventId`, `linkUrl`, `title`, `content`, `receivedAtEpochMs`, `isRead`)
                    VALUES (1, 'evt-1', 'https://example.com/1', 'title', 'body', 1715700000100, 0)
                    """.trimIndent(),
                ),
        )

        val migratedDb =
            migrationHelper.runMigrationsAndValidate(
                databaseName,
                4,
                true,
                *DatabaseMigrations.ALL,
            )

        assertEquals(
            1L,
            scalarLong(migratedDb, "SELECT COUNT(*) FROM `updates_history`"),
        )
        assertEquals(
            1L,
            scalarLong(
                migratedDb,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' " +
                    "AND name = 'index_updates_history_remoteEventId'",
            ),
        )
        assertEquals(
            0L,
            scalarLong(
                migratedDb,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'cached_session'",
            ),
        )
        migratedDb.close()
    }

    @Test
    fun migrateFromVersion3To4_keepsSubscriptionsCacheAndSyncState() {
        createDatabase(
            version = 3,
            setupSql =
                listOf(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_session` (
                        `id` INTEGER NOT NULL,
                        `clientLogin` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                    """
                    CREATE TABLE IF NOT EXISTS `updates_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `remoteEventId` TEXT,
                        `linkUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `receivedAtEpochMs` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_updates_history_remoteEventId`
                    ON `updates_history` (`remoteEventId`)
                    """.trimIndent(),
                    """
                    CREATE TABLE IF NOT EXISTS `cached_subscriptions` (
                        `id` INTEGER NOT NULL,
                        `url` TEXT NOT NULL,
                        `tagsSerialized` TEXT NOT NULL,
                        `filtersSerialized` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                    """
                    CREATE TABLE IF NOT EXISTS `subscriptions_sync_state` (
                        `id` INTEGER NOT NULL,
                        `lastSyncAtEpochMs` INTEGER,
                        `isStale` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                    """
                    INSERT INTO `cached_subscriptions` (`id`, `url`, `tagsSerialized`, `filtersSerialized`)
                    VALUES (7, 'https://example.com/7', 'kotlin,android', 'mentions')
                    """.trimIndent(),
                    """
                    INSERT INTO `subscriptions_sync_state` (`id`, `lastSyncAtEpochMs`, `isStale`)
                    VALUES (0, 1715700000300, 1)
                    """.trimIndent(),
                ),
        )

        val migratedDb =
            migrationHelper.runMigrationsAndValidate(
                databaseName,
                4,
                true,
                DatabaseMigrations.MIGRATION_3_4,
            )

        assertEquals(
            1L,
            scalarLong(
                migratedDb,
                "SELECT COUNT(*) FROM `cached_subscriptions` WHERE id = 7",
            ),
        )
        assertEquals(
            1L,
            scalarLong(
                migratedDb,
                "SELECT COUNT(*) FROM `subscriptions_sync_state` WHERE id = 0 AND isStale = 1",
            ),
        )
        assertEquals(
            0L,
            scalarLong(
                migratedDb,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'cached_session'",
            ),
        )
        migratedDb.close()
    }

    private fun createDatabase(
        version: Int,
        setupSql: List<String>,
    ) {
        context.deleteDatabase(databaseName)
        val dbFile = context.getDatabasePath(databaseName)
        dbFile.parentFile?.mkdirs()

        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.beginTransaction()
        try {
            setupSql.forEach(db::execSQL)
            db.version = version
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    private fun scalarLong(
        db: SupportSQLiteDatabase,
        query: String,
    ): Long {
        val cursor = db.query(query)
        return try {
            check(cursor.moveToFirst()) { "Expected a row for query: $query" }
            cursor.getLong(0)
        } finally {
            cursor.close()
        }
    }
}
