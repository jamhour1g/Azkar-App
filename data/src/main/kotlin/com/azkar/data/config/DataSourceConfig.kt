package com.azkar.data.config

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Centralizes the JDBC connection URL for the application database.
 *
 * [DatabaseManager] and the [migrateToLatestSchema] it calls read from here so the URL is defined
 * in exactly one place.
 */
object DataSourceConfig {
    private val databaseDirectory: Path = Path.of(System.getProperty("user.home"), ".azkar", "db")

    /** Default JDBC URL pointing to a deterministic user-level file-based H2 database. */
    val JDBC_URL: String = buildJdbcUrl(KotlinLogging.logger {})

    private fun buildJdbcUrl(logger: KLogger): String {
        try {
            Files.createDirectories(databaseDirectory)
        } catch (exception: IOException) {
            // `JDBC_URL` is a field initializer, so this throws while `DataSourceConfig` is still
            // being class-initialized. The stack trace therefore names only the class loader, and
            // the one fact worth keeping — which path could not be created, and why — has to be
            // logged here, or it is lost entirely.
            logger.error(exception) { "Unable to create the database directory" }

            // Rethrow as an unchecked exception so the application fails fast rather than trying to run
            // with a non-existent database directory.
            throw IllegalStateException(
                "Unable to create database directory: ${databaseDirectory.toAbsolutePath()}",
                exception,
            )
        }

        val databasePath = databaseDirectory.resolve("remembrance").toAbsolutePath()
        // `DATABASE_TO_LOWER` stores identifiers in the case they were declared in rather than
        // folding them to upper case, so `tag`, `created_at` and `name` read back the way
        // Tables.kt writes them. H2 bakes the setting into the file: there is no `ALTER SET
        // DATABASE TO LOWER`, so an existing database has to be deleted for this to take effect.
        return "jdbc:h2:file:${databasePath.toString().replace('\\', '/')};AUTO_SERVER=TRUE;DATABASE_TO_LOWER=TRUE"
    }
}
