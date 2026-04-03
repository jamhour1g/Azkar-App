package com.azkar.data.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/// Centralizes the JDBC connection URL for the application database.
///
/// Both [FlywayMigrator] and [JpaManager] read from here so that the
/// URL is defined in exactly one place. The same value is passed as
/// an override property to [jakarta.persistence.Persistence] so that
/// the URL no longer needs to live in `persistence.xml`.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataSourceConfig {

    /// Default JDBC URL pointing to a file-based H2 database.
    public static final String JDBC_URL = "jdbc:h2:file:./db/remembrance;AUTO_SERVER=TRUE";
}
