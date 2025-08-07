package com.bayoumi.storage;

import com.bayoumi.util.LoggerWrapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.logging.Logger;


public final class JpaManager implements AutoCloseable {

    private static final String LOCATIONS_PERSISTENCE_UNIT = "com.bayoumi.locations.persistence";
    private static final String DATA_PERSISTENCE_UNIT = "com.bayoumi.data.persistence";
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(JpaManager.class);
    private static JpaManager instance;
    private EntityManagerFactory locationsEntityManagerFactory;
    private EntityManagerFactory dataEntityManagerFactory;


    private JpaManager() {
        // This is a singleton class
    }

    public static synchronized JpaManager getInstance() {
        if (instance == null) {
            LOGGER.fine(() -> "JpaManager was null, creating new instance");
            instance = new JpaManager();
        }
        LOGGER.fine(() -> "JpaManager was not null, returning existing instance");
        return instance;
    }

    public void createSchema() {
        getLocationsEntityManagerFactory().getSchemaManager().create(true);
        getDataEntityManagerFactory().getSchemaManager().create(true);
    }

    public synchronized EntityManagerFactory getLocationsEntityManagerFactory() {
        if (locationsEntityManagerFactory == null) {
            locationsEntityManagerFactory = Persistence.createEntityManagerFactory(LOCATIONS_PERSISTENCE_UNIT);
        }
        return locationsEntityManagerFactory;
    }

    public synchronized EntityManagerFactory getDataEntityManagerFactory() {
        if (dataEntityManagerFactory == null) {
            dataEntityManagerFactory = Persistence.createEntityManagerFactory(DATA_PERSISTENCE_UNIT);
        }
        return dataEntityManagerFactory;
    }

    @Override
    public void close() {
        if (locationsEntityManagerFactory != null) {
            locationsEntityManagerFactory.close();
        }
        if (dataEntityManagerFactory != null) {
            dataEntityManagerFactory.close();
        }
    }

    @Override
    public String toString() {
        return "JpaManager{" + "locationsEntityManagerFactory=" + locationsEntityManagerFactory +
               ", dataEntityManagerFactory=" + dataEntityManagerFactory +
               '}';
    }
}