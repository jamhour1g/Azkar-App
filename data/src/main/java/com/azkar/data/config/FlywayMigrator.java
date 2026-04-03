package com.azkar.data.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.flywaydb.core.Flyway;

/// Runs Flyway database migrations against the configured data source.
///
/// Extracted from [JpaManager] so that migration logic has a single,
/// well-defined home. Migrations are loaded from `classpath:db/migration`.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FlywayMigrator {

    /// Runs all pending Flyway migrations against the given JDBC URL.
    ///
    /// @param jdbcUrl the JDBC connection URL to migrate
    public static void migrate(String jdbcUrl) {
        Flyway.configure()
                .dataSource(jdbcUrl, null, null)
                .locations("classpath:db/migration")
                .cleanDisabled(true) // safety in prod
                .load()
                .migrate();
    }
}
