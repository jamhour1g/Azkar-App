package com.bayoumi.storage;


import com.bayoumi.util.LoggerWrapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocationsDBManager {

    private static final String DATA_SOURCE_URL = "jdbc:sqlite:" + DatabaseManager.class.getResource("/db/locations.db");
    private static LocationsDBManager databaseManager = null;  // static
    public Connection con = null;

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(LocationsDBManager.class);


    private LocationsDBManager() throws Exception {
        try {
            if (!connectToDatabase()) {
                throw new Exception("Cannot connect to LocationsDB");
            }

            if (!DatabaseHelper.checkIfTablesExist(con, "Countries")
                || !DatabaseHelper.checkIfTablesExist(con, "cityd")) {
                // close connection
                con.close();
                con = null;
            }

        } catch (Exception ex) {
            // close connection
            if (con != null) {
                con.close();
                con = null;
            }
            throw ex;
        }
    }

    public static LocationsDBManager getInstance() throws Exception {
        if (databaseManager == null) {
            databaseManager = new LocationsDBManager();
        }
        return databaseManager;
    }

    private boolean connectToDatabase() {
        try {
            if (con == null) {
                // .... Connect to SQlLite ....
                con = DriverManager.getConnection(DATA_SOURCE_URL);
                con.prepareStatement("PRAGMA foreign_keys=ON").execute();
                return true;
            }
        } catch (SQLException ex) {
            con = null;
            LOGGER.log(Level.SEVERE, "Cannot connect to database", ex);
        }
        return false;
    }

}
