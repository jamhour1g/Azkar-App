package com.azkar.testUtils;

import com.azkar.utils.LoggerWrapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.flywaydb.core.Flyway;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestJpaManager {

    public static EntityManagerFactory bootstrapWithTempSqlite() {
        try {
            return bootstrapWithTempSqlite(
                Files.createTempDirectory("azkar-sqlite-tests")
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static EntityManagerFactory bootstrapWithTempSqlite(Path tempDir) {
        try {
            bootstrapLogger();

            Files.createDirectories(tempDir);
            Path db = tempDir.resolve("test-" + System.nanoTime() + ".db");
            String url = "jdbc:sqlite:" + db.toAbsolutePath();

            // 1) Migrate schema using the SAME migrations as prod
            Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .initSql("PRAGMA foreign_keys=ON")
                .cleanDisabled(false) // ok in test
                .load()
                .migrate();

            // 2) Build EMF with schema-generation disabled
            Map<String, String> props = createDatabaseProperties(url);

            return Persistence.createEntityManagerFactory(
                "com.azkar.data.persistence",
                props
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void bootstrapLogger() {
        System.setProperty("org.jboss.logging.provider", "jdk");
        LoggerWrapper.loggerFactory(TestJpaManager.class);
    }

    private static Map<String, String> createDatabaseProperties(String url) {
        return Map.ofEntries(
            Map.entry("jakarta.persistence.jdbc.driver", "org.sqlite.JDBC"),
            Map.entry("jakarta.persistence.jdbc.url", url),
            Map.entry(
                "hibernate.dialect",
                "org.hibernate.community.dialect.SQLiteDialect"
            ),
            Map.entry(
                "jakarta.persistence.schema-generation.database.action",
                "none"
            ),
            Map.entry(
                "hibernate.connection.init_sql",
                "PRAGMA foreign_keys=ON"
            ),
            Map.entry("hibernate.show_sql", "false"),
            Map.entry("hibernate.format_sql", "true")
        );
    }
}
