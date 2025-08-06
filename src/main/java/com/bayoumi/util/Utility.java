package com.bayoumi.util;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Set;
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

}
