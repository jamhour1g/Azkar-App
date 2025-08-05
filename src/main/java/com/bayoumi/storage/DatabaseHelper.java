package com.bayoumi.storage;


import com.bayoumi.util.LoggerWrapper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.logging.Logger;

public class DatabaseHelper {

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(DatabaseHelper.class);

    public static boolean checkIfTablesExist(Connection con, String tableName) throws Exception {
        final ResultSet resultSet = con.prepareStatement("SELECT EXISTS ( SELECT name FROM sqlite_schema WHERE type='table' AND name='" + tableName + "' );").executeQuery();
        resultSet.next();
        LOGGER.info("table: " + tableName + " = " + (resultSet.getInt(1) == 1));
        return resultSet.getInt(1) == 1;
    }
}
