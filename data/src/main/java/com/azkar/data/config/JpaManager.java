package com.azkar.data.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Singleton that manages the JPA [EntityManagerFactory] lifecycle.
///
/// On first access the manager runs [FlywayMigrator] migrations, then
/// creates the EMF with the JDBC URL from [DataSourceConfig] (overriding
/// whatever may be in `persistence.xml`).
public final class JpaManager implements AutoCloseable {

    public static final String DATA_PERSISTENCE_UNIT =
        "com.azkar.data.persistence";
    private static final Logger LOGGER = LoggerFactory.getLogger(
        JpaManager.class
    );

    @Nullable private static volatile JpaManager instance;

    @Nullable private EntityManagerFactory emf;

    private JpaManager() {
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    public static JpaManager getInstance() {
        if (instance == null) {
            synchronized (JpaManager.class) {
                if (instance == null) {
                    LOGGER.atDebug().log("Creating JpaManager instance");
                    instance = new JpaManager();
                }
            }
        }
        // Just suppress the warning for nullability since at this point we know it's not null
        // noinspection ConstantConditions
        return instance;
    }

    public synchronized EntityManagerFactory getDataEntityManagerFactory() {
        if (emf == null || !emf.isOpen()) {
            String jdbcUrl = DataSourceConfig.JDBC_URL;

            // Run Flyway migrations before creating the EMF
            FlywayMigrator.migrate(jdbcUrl);

            // Override the JDBC URL so persistence.xml doesn't need it
            Map<String, String> props = Map.of(
                "jakarta.persistence.jdbc.url",
                jdbcUrl
            );
            emf = Persistence.createEntityManagerFactory(
                DATA_PERSISTENCE_UNIT,
                props
            );
        }
        return emf;
    }

    /// Convenience helper. Callers can just use try-with-resources on the EM.
    public EntityManager getEntityManager() {
        return getDataEntityManagerFactory().createEntityManager();
    }

    @Override
    public synchronized void close() {
        if (emf != null && emf.isOpen()) {
            LOGGER.atDebug().log("Closing EntityManagerFactory");
            emf.close();
        }
    }

    @Override
    public String toString() {
        return "JpaManager{emfOpen=" + (emf != null && emf.isOpen()) + '}';
    }
}
