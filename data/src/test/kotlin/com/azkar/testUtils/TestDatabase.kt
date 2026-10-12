package com.azkar.testUtils

import com.azkar.data.config.ExposedDatabaseConfig
import com.azkar.data.config.migrateToLatestSchema
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Throwaway in-memory H2 databases for the data-layer tests.
 *
 * The schema comes from the very same Exposed table objects the application runs against, so a
 * test that passes here is testing against the real DDL rather than against a hand-written
 * stand-in. Each call to [bootstrapWithH2] returns a database nothing else has touched, which
 * keeps tests that need a pristine schema (the seed-data suite) separate from the ones that
 * seed and wipe their own rows.
 */
object TestDatabase {
    /**
     * Creates a fresh in-memory database and migrates it to the latest version.
     *
     * "Latest version" includes the bundled seed data, so the database comes up holding a few
     * hundred seeded remembrances. Callers that want empty tables follow up with [reset].
     */
    fun bootstrapWithH2(): Database {
        // Matches the production URL's DATABASE_TO_LOWER so tests see the same identifier
        // casing the application database has — the INFORMATION_SCHEMA queries in
        // SchemaMigrationSuite assert against stored names, not just SQL that parses.
        val jdbcUrl = "jdbc:h2:mem:test_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        val database =
            Database.connect(
                jdbcUrl,
                "org.h2.Driver",
                "",
                "",
                { },
                ExposedDatabaseConfig.INSTANCE,
            )
        database.migrateToLatestSchema()
        return database
    }

    /**
     * Empties every table and rewinds the identity columns back to 1, in a single transaction.
     *
     * Rows go in foreign-key order and the seed data is undone too, so a test that asserts on
     * explicit ids starts from the same footing the H2 identity columns do.
     */
    fun reset(database: Database) {
        transaction(database) {
            TABLES_IN_FOREIGN_KEY_ORDER.forEach { table -> exec("DELETE FROM $table") }
            IDENTITY_COLUMNS.forEach { table -> exec("ALTER TABLE $table ALTER COLUMN id RESTART WITH 1") }
        }
    }

    private val TABLES_IN_FOREIGN_KEY_ORDER =
        listOf(
            // Progress points at both a collection and a remembrance, so it has to go before either,
            // and so does a reminder, which points only at the collection. A membership row likewise
            // points at a remembrance, so the parent cannot be deleted out from under it.
            "recitation_progress",
            "collection_reminder",
            "collection_remembrance",
            "collection_tag",
            "collection",
            "remembrance_tag",
            "tag",
            "explanation_translation",
            "remembrance_translation",
            "remembrance",
        )

    private val IDENTITY_COLUMNS =
        listOf("tag", "explanation_translation", "remembrance_translation", "remembrance", "collection")
}
