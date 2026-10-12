package com.azkar.data.config

import com.azkar.data.table.ALL_TABLES
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

private val logger = KotlinLogging.logger {}

/**
 * Brings this database in line with the Exposed table objects.
 *
 * The same code runs against production (the file-backed H2 database from [DataSourceConfig])
 * and against the throwaway in-memory databases the tests build, so a test that passes here is
 * running against the very DDL the application deploys. There are no versioned SQL scripts: the
 * schema lives entirely in [ALL_TABLES], and Exposed works out what that means for a database
 * that is either empty or one release behind.
 *
 * Creates any missing table, column, index or constraint. Schema and nothing else: the dataset is
 * written once by [LibraryBootstrapper], and a loader that ran here would run on every start and so
 * would have to recognise its own output — which means carrying an identity the user is free to
 * change. See [LibraryBootstrapper] for what it costs.
 *
 * Existing rows are never dropped or rewritten, so pointing an older database at a newer
 * [ALL_TABLES] only ever adds to it. A column that has been removed from a table object is left
 * where it is rather than dropped, which is why the removal of `collection.visibility`,
 * `collection.cover_remembrance_id` and `collection.system_key` needed the database deleted rather
 * than upgraded.
 *
 * The `SpreadOperator` lint is suppressed for the one call that reads [ALL_TABLES]:
 * `statementsRequiredForDatabaseMigration` takes its tables as a `vararg`, so the spread is the only
 * way to call it with an array, and `UnsafeSpread` is not on the classpath. The cost the rule warns
 * about — one extra array copy — is paid once per process over the handful of tables in
 * [ALL_TABLES], inside the one call that migrates the schema.
 */
@Suppress("SpreadOperator")
fun Database.migrateToLatestSchema() {
    transaction(this) {
        val tables = ALL_TABLES.toTypedArray()
        val statements = MigrationUtils.statementsRequiredForDatabaseMigration(*tables, withLogs = false)

        // The empty case is the overwhelmingly common one — it is every start after the first,
        // and every test that hands over a freshly migrated database. Reporting it as a
        // migration would bury the one that actually changed the schema.
        if (statements.isEmpty()) {
            logger.debug { "Schema is already up to date" }
        } else {
            val applicable = statements.withoutUndeclaredIndexDrops()
            logger.info { "Applying ${applicable.size} schema statement(s)" }
            applicable.forEach { exec(it) }
        }
    }
}

/**
 * Drops the requests to remove indexes that none of [ALL_TABLES] declares.
 *
 * Exposed diffs the schema it was given against the schema the database reports, so an index it
 * did not create is a candidate for removal. H2 creates indexes of its own to back foreign keys
 * — one under a name like `FK_COLLECTION_REMINDER_COLLECTION_ID__ID_INDEX_7` for the
 * `collection_reminder.collection_id` reference — and those names are generated, so they can never
 * appear in the table objects. Exposed therefore asked H2 to drop an index that a live constraint
 * owns, and H2 refuses: it checks constraint ownership before it honours `IF EXISTS`, so
 * `DROP INDEX IF EXISTS` on one fails with error 90085 even though the `IF EXISTS` was written
 * precisely to make it harmless.
 *
 * Skipping these is safe and the omission is deliberate. The index the statement would have
 * removed still exists and still serves its constraint, so leaving it changes nothing about the
 * schema; and the alternative is that one statement H2 will not accept aborts the whole
 * transaction, which leaves the application with no database at all. Anything else that fails
 * still propagates, because a real migration error has to be visible.
 *
 * The trade-off is that an index deleted from the table objects on purpose now lingers instead
 * of being cleaned up on the next start. That is the safer direction to err in, and the skip is
 * logged so it is not silent.
 */
private fun List<String>.withoutUndeclaredIndexDrops(): List<String> {
    val declared = ALL_TABLES.flatMapTo(mutableSetOf()) { table -> table.indices.map { it.indexName } }
    return filterNot { statement ->
        val name = INDEX_DROP.find(statement)?.groupValues?.get(1) ?: return@filterNot false
        if (name in declared) return@filterNot false
        logger.info { "Leaving the engine-owned index [$name] in place" }
        true
    }
}

/** Matches the `DROP INDEX` statements [MigrationUtils] generates, capturing the index name. */
private val INDEX_DROP = Regex("""DROP\s+INDEX\s+(?:IF\s+EXISTS\s+)?(\S+)""", RegexOption.IGNORE_CASE)
