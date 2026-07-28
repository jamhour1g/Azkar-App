package com.azkar.data.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

/// Runs Flyway database migrations against the configured data source.
///
/// Extracted from [JpaManager] so that migration logic has a single,
/// well-defined home. Migrations are loaded from `classpath:db/migration`.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FlywayMigrator {

    private static final String CLASSPATH_LOCATION = "classpath:db/migration";
    private static final List<Path> FILESYSTEM_CANDIDATES = List.of(
            Path.of("data", "src", "main", "resources", "db", "migration"),
            Path.of("..", "data", "src", "main", "resources", "db", "migration"),
            Path.of("..", "..", "data", "src", "main", "resources", "db", "migration"));

    /// Runs all pending Flyway migrations against the given JDBC URL.
    ///
    /// @param jdbcUrl the JDBC connection URL to migrate
    public static void migrate(String jdbcUrl) {
        FlywayException firstError;
        try {
            migrateWithLocation(jdbcUrl, CLASSPATH_LOCATION);
            return;
        } catch (FlywayException classpathError) {
            firstError = classpathError;
        }

        for (Path candidate : FILESYSTEM_CANDIDATES) {
            Path absolutePath = candidate.toAbsolutePath().normalize();
            if (!Files.isDirectory(absolutePath)) {
                continue;
            }

            try {
                migrateWithLocation(
                        jdbcUrl, "filesystem:" + absolutePath.toString().replace('\\', '/'));
                return;
            } catch (FlywayException ignored) {
                // Keep trying next candidate.
            }
        }

        throw firstError;
    }

    private static void migrateWithLocation(String jdbcUrl, String location) {
        Flyway.configure()
                .dataSource(jdbcUrl, null, null)
                .locations(location)
                .cleanDisabled(true)
                .load()
                .migrate();
    }
}
