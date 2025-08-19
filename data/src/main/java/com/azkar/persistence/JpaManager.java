package com.azkar.persistence;

import com.azkar.utils.LoggerWrapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;

import java.util.logging.Logger;

public final class JpaManager implements AutoCloseable {
    public static final String DATA_PERSISTENCE_UNIT = "com.azkar.data.persistence";
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(JpaManager.class);

    @Nullable
    private static volatile JpaManager instance;

    @Nullable
    private EntityManagerFactory emf;

    private JpaManager() {
        System.setProperty("org.jboss.logging.provider", "jdk");
    }

    public static JpaManager getInstance() {
        if (instance == null) {
            synchronized (JpaManager.class) {
                if (instance == null) {
                    LOGGER.fine(() -> "Creating JpaManager instance");
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
            // run migrations first
            Flyway.configure()
                    .dataSource("jdbc:sqlite:./db/remembrance.db", null, null)
                    .locations("classpath:db/migration")
                    .initSql("PRAGMA foreign_keys=ON")
                    .cleanDisabled(true) // safety
                    // in
                    // prod
                    .load()
                    .migrate();

            emf = Persistence.createEntityManagerFactory(DATA_PERSISTENCE_UNIT);
        }
        return emf;
    }

    /** Convenience helper. Callers can just use try-with-resources on the EM. */
    public EntityManager getEntityManager() {
        return getDataEntityManagerFactory().createEntityManager();
    }

    @Override
    public synchronized void close() {
        if (emf != null && emf.isOpen()) {
            LOGGER.fine(() -> "Closing EntityManagerFactory");
            emf.close();
        }
    }

    @Override
    public String toString() {
        return "JpaManager{emfOpen=" + (emf != null && emf.isOpen()) + '}';
    }
}
