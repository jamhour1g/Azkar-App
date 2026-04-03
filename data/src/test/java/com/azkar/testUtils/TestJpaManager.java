package com.azkar.testUtils;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.flywaydb.core.Flyway;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestJpaManager {

    /// Bootstraps an in-memory H2 database with a unique name for each invocation.
    ///
    /// Each call creates a fresh, isolated database — safe for parallel test execution.
    public static EntityManagerFactory bootstrapWithH2() {
        try {
            System.setProperty("org.jboss.logging.provider", "slf4j");

            String dbName = "test_" + System.nanoTime();
            String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

            // 1) Migrate schema using the SAME migrations as prod
            Flyway.configure()
                    .dataSource(url, null, null)
                    .locations("classpath:db/migration")
                    .cleanDisabled(false) // ok in test
                    .load()
                    .migrate();

            // 2) Build EMF with schema-generation disabled
            Map<String, String> props = overrideDatabaseProperties(url);

            return Persistence.createEntityManagerFactory("com.azkar.data.persistence", props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> overrideDatabaseProperties(String url) {
        return Map.ofEntries(
                Map.entry("jakarta.persistence.jdbc.url", url),
                Map.entry("hibernate.show_sql", "true"),
                Map.entry("hibernate.format_sql", "false"));
    }
}
