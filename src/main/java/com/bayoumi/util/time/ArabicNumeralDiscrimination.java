package com.bayoumi.util.time;

import java.util.ResourceBundle;

/**
 * Helper Methods
 */
public class ArabicNumeralDiscrimination {

    /**
     * @param bundle value
     * @return Arabic String for the numerical discrimination
     * for seconds
     */
    public static String secondsArabicPlurality(ResourceBundle bundle) {
        return bundle.getString("seconds");
    }


    /**
     * @param minute value
     * @return Arabic String for the numerical discrimination
     * for minutes
     */
    public static String minutesArabicPlurality(ResourceBundle bundle, int minute) {
        if (minute == 1) {
            return bundle.getString("oneMinute");
        } else if (minute == 2) {
            return bundle.getString("twoMinutes");
        } else if (minute < 11) {
            return bundle.getString("minutes");
        } else {
            return bundle.getString("oneMinute");
        }
    }

    /**
     * @param hour value
     * @return Arabic String for the numerical discrimination
     * for hours
     */
    public static String hoursArabicPlurality(ResourceBundle bundle, int hour) {
        if (hour == 1) {
            return bundle.getString("oneHour");
        } else if (hour == 2) {
            return bundle.getString("twoHours");
        } else if (hour < 11) {
            return bundle.getString("hours");
        } else {
            return bundle.getString("oneHour");
        }
    }

    public static String getTimeArabicPlurality(ResourceBundle bundle, int time) {
        String res = "";
        if (time % 60 != 0) {
            res += (isOneOrTwo(time % 60) ? "" : time % 60 + " ") + ArabicNumeralDiscrimination.minutesArabicPlurality(bundle, time % 60);
        }
        if (time / 60 != 0) {
            if (time % 60 != 0) {
                res += " " + bundle.getString("and");
            }
            res += (isOneOrTwo(time / 60) ? "" : time / 60 + " ") + ArabicNumeralDiscrimination.hoursArabicPlurality(bundle, time / 60);
        }

        return res;
    }

    public static String getTimeGeneralPlurality(ResourceBundle bundle, int time) {
        String res = "";
        if (time % 60 != 0) {
            res += (isOneOrTwo(time % 60) ? "" : time % 60 + " ") + ArabicNumeralDiscrimination.minutesArabicPlurality(bundle, time % 60);
        }
        if (time / 60 != 0) {
            if (time % 60 != 0) {
                res += " " + bundle.getString("and") + " ";
            }
            res += (isOneOrTwo(time / 60) ? "" : time / 60 + " ") + ArabicNumeralDiscrimination.hoursArabicPlurality(bundle, time / 60);
        }

        return res;
    }

    /**
     * checks if n number is equal to 1 or 2
     *
     * @param n input number
     * @return true if number is equal to 1 or 2
     * and false if not.
     */
    private static boolean isOneOrTwo(int n) {
        return n == 1 || n == 2;
    }
}
