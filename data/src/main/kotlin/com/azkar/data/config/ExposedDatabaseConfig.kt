package com.azkar.data.config

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalKeywordApi

/**
 * Exposed settings shared by the application pool and the in-memory test databases.
 *
 * The only non-default value is [DatabaseConfig.preserveKeywordCasing]. A column that happens to
 * be spelled like a SQL keyword — `tag.name` is the one that bites — must be left unquoted for the
 * engine to fold it along with everything else. Left at its default (`true`), Exposed instead
 * quotes the identifier in the case it was declared, producing `INSERT INTO tag ("name", …)`, and
 * with `DATABASE_TO_LOWER` that names a lower-case column that does not exist.
 *
 * Note this says nothing about which case the identifiers end up stored in. That is the engine's
 * to decide — see `DATABASE_TO_LOWER` in [DataSourceConfig] — and Exposed reads the choice back
 * from JDBC metadata, so it agrees with whatever is there rather than imposing one.
 */
object ExposedDatabaseConfig {
    @OptIn(ExperimentalKeywordApi::class)
    val INSTANCE: DatabaseConfig = DatabaseConfig { preserveKeywordCasing = false }
}
