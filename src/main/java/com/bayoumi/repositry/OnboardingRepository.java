package com.bayoumi.repositry;

import com.bayoumi.storage.DatabaseManager;
import com.bayoumi.util.LoggerWrapper;

import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnboardingRepository {

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(OnboardingRepository.class);

    public static boolean isFirstTimeOpened() {
        try {
            ResultSet res = DatabaseManager.getInstance().con.prepareStatement("SELECT * FROM onboarding").executeQuery();
            if (res.next()) {
                return res.getInt("isFirstTimeOpened") == 1;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "isFirstTimeOpened", ex);
        }
        return false;
    }

    public static void setFirstTimeOpened(int isFirstTimeOpened) {
        try {
            DatabaseManager.getInstance()
                    .con
                    .prepareStatement("UPDATE onboarding set isFirstTimeOpened = " + isFirstTimeOpened)
                    .executeUpdate();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "setFirstTimeOpened", ex);
        }
    }
}
