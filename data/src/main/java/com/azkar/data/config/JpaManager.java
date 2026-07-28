package com.azkar.data.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Singleton that manages the JPA [EntityManagerFactory] lifecycle.
///
/// On first access the manager runs [FlywayMigrator] migrations, then
/// creates the EMF with the JDBC URL from [DataSourceConfig] (overriding
/// whatever may be in `persistence.xml`).
public final class JpaManager implements AutoCloseable {

    public static final String DATA_PERSISTENCE_UNIT = "com.azkar.data.persistence";
    private static final Logger LOGGER = LoggerFactory.getLogger(JpaManager.class);

    @Nullable private static volatile JpaManager instance;

    private static final Lock INSTANCE_LOCK = new ReentrantLock();

    @Nullable private EntityManagerFactory emf;

    private final Lock emfLock = new ReentrantLock();

    private JpaManager() {
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    public static JpaManager getInstance() {
        if (instance == null) {
            INSTANCE_LOCK.lock();
            try {
                if (instance == null) {
                    LOGGER.atDebug().log("Creating JpaManager instance");
                    instance = new JpaManager();
                }
            } finally {
                INSTANCE_LOCK.unlock();
            }
        }
        // Just suppress the warning for nullability since at this point we know it's not null
        // noinspection ConstantConditions
        return instance;
    }

    public EntityManagerFactory getDataEntityManagerFactory() {
        emfLock.lock();
        try {
            if (emf == null || !emf.isOpen()) {
                String jdbcUrl = DataSourceConfig.JDBC_URL;

                // Run Flyway migrations before creating the EMF
                FlywayMigrator.migrate(jdbcUrl);

                // Override the JDBC URL so persistence.xml doesn't need it
                Map<String, String> props = Map.of("jakarta.persistence.jdbc.url", jdbcUrl);
                emf = Persistence.createEntityManagerFactory(DATA_PERSISTENCE_UNIT, props);
            }
            return emf;
        } finally {
            emfLock.unlock();
        }
    }

    /// Convenience helper. Callers can just use try-with-resources on the EM.
    public EntityManager getEntityManager() {
        return getDataEntityManagerFactory().createEntityManager();
    }

    public void warmUp() {
        getDataEntityManagerFactory();
    }

    @Override
    public void close() {
        emfLock.lock();
        try {
            if (emf != null && emf.isOpen()) {
                LOGGER.atDebug().log("Closing EntityManagerFactory");
                emf.close();
            }
        } finally {
            emfLock.unlock();
        }
    }

    @Override
    public String toString() {
        return "JpaManager{emfOpen=" + (emf != null && emf.isOpen()) + '}';
    }
}
