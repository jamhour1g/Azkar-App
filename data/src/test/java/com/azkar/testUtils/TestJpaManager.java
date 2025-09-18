package com.azkar.testUtils;

import com.azkar.utils.LoggerWrapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.flywaydb.core.Flyway;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestJpaManager {

    public static EntityManagerFactory bootstrapWithTempSqlite() {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir")).resolve(
            "azkar-sqlite-tests"
        );

        return bootstrapWithTempSqlite(tempDir);
    }

    public static EntityManagerFactory bootstrapWithTempSqlite(Path tempDir) {
        try {
            bootstrapLogger();

            if (!Files.exists(tempDir)) {
                Files.createDirectory(tempDir);
            }

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
            Map<String, String> props = overrideDatabaseProperties(url);

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

    private static Map<String, String> overrideDatabaseProperties(String url) {
        return Map.ofEntries(
            Map.entry(
                "jakarta.persistence.jdbc.url",
                url + "?journal_mode=WAL&busy_timeout=2000"
            ),
            Map.entry("hibernate.show_sql", "true"),
            Map.entry("hibernate.format_sql", "false")
        );
    }
}
