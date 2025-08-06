package com.bayoumi.storage;


import com.bayoumi.util.LoggerWrapper;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private static final String DATA_SOURCE_URL = "jdbc:sqlite:" + DatabaseManager.class.getResource("/db/data.db");
    private static DatabaseManager databaseManager = null;
    public PreparedStatement stat = null;
    public Connection con = null;
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(DatabaseManager.class);


    private DatabaseManager() {
    }

    public static DatabaseManager getInstance() {
        if (databaseManager == null) {
            databaseManager = new DatabaseManager();
        }
        return databaseManager;
    }

    public boolean init() {
        try {
            Flyway.configure()
                    .dataSource(DATA_SOURCE_URL, "", "")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
            if (!connectToDatabase()) {
                throw new Exception("Cannot init DatabaseManager");
            }
            return true;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "DatabaseManager init error", ex);
        }
        return false;
    }

    private boolean connectToDatabase() {
        try {
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl(DATA_SOURCE_URL);

            con = dataSource.getConnection();
            con.prepareStatement("PRAGMA foreign_keys=ON").execute();
            return true;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Cannot connect to database", ex);
            return false;
        }

    }


    /**
     * Retrieves the ID from the database. If the ID is null or empty, a new one is generated and saved to the database.
     *
     * @return the ID as a string
     */
    public String getID() {
        try {
            ResultSet res = DatabaseManager.getInstance().con.prepareStatement("SELECT ID FROM program_characteristics").executeQuery();
            if (res.next()) {
                final String id = res.getString("ID");
                if (id != null && !id.isEmpty()) return id;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "DatabaseManager getID error", ex);
        }
        return "";
    }

    public void setID(String ID) {
        try {
            DatabaseManager databaseManager = DatabaseManager.getInstance();
            databaseManager.stat = databaseManager.con.prepareStatement("UPDATE program_characteristics set ID = ?");
            databaseManager.stat.setString(1, ID);
            databaseManager.stat.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "DatabaseManager setID error", ex);
        }
    }
}
