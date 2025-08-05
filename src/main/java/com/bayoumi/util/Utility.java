package com.bayoumi.util;

import com.bayoumi.Launcher;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper methods
 */
public class Utility {

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(Utility.class);


    public static void printAllRunningThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        threadSet.forEach(thread -> LOGGER.info(() -> "Running Thread: " + thread.getName()));
    }

    public static String toUTF(String val) {
        try {
            return new String(val.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to convert to UTF-8", ex);
        }
        return val;
    }

    /**
     * this method is used in exiting the program
     */
    public static void exitProgramAction() {
//        printAllRunningThreads();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Long exitTime = System.currentTimeMillis();
            LOGGER.info(() -> "App closed - Used for "
                               + (exitTime - Launcher.startTime) + " ms\n");
        }));
        System.exit(0);
    }

    /**
     * copy text to Clipboard
     *
     * @param text to be copied
     */
    public static void copyToClipboard(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }

    /**
     * @param i number
     * @return number as String formatted as two digits
     */
    public static String formatIntToTwoDigit(int i) {
        return String.format("%02d", i);
    }

    public static double formatNum(double num) {
        double returnedVal = Double.parseDouble(String.format("%.3f", num));
        return Math.abs(returnedVal) == 0 ? 0 : returnedVal;
    }

    public static int parseIntOrZero(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
