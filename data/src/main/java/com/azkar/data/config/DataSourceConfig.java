package com.azkar.data.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final Path DATABASE_DIRECTORY =
            Path.of(System.getProperty("user.home"), ".azkar", "db");

    /// Default JDBC URL pointing to a deterministic user-level file-based H2 database.
    public static final String JDBC_URL = buildJdbcUrl();

    private static String buildJdbcUrl() {
        try {
            Files.createDirectories(DATABASE_DIRECTORY);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to create database directory: " + DATABASE_DIRECTORY.toAbsolutePath(), exception);
        }

        Path databasePath = DATABASE_DIRECTORY.resolve("remembrance").toAbsolutePath();
        return "jdbc:h2:file:" + databasePath.toString().replace('\\', '/') + ";AUTO_SERVER=TRUE";
    }
}
