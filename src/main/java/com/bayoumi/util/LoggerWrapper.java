package com.bayoumi.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.logging.*;

/**
 * Used to log Exceptions and Information
 *
 * @author Abdelrahman Bayoumi
 */
public final class LoggerWrapper {

    private static final Logger ROOT_LOGGER = Logger.getLogger("");
    public static final DateTimeFormatter FILE_NAME_TIME_FORMAT = DateTimeFormatter.ofPattern("dd_MM_yyyy_HH_mm");

    private static final boolean IS_CONSOLE_LOGGING_ENABLED = System.getenv("ENABLE_CONSOLE_LOGGING") != null && Boolean.parseBoolean(System.getenv("ENABLE_CONSOLE_LOGGING"));
    private static final boolean IS_FILE_LOGGING_ENABLED = System.getenv("ENABLE_FILE_LOGGING") != null && Boolean.parseBoolean(System.getenv("ENABLE_FILE_LOGGING"));

    static {
        for (Handler handler : ROOT_LOGGER.getHandlers()) {
            ROOT_LOGGER.removeHandler(handler);
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new LogFormatter());

        if (IS_CONSOLE_LOGGING_ENABLED) {
            ROOT_LOGGER.addHandler(consoleHandler);
        }
    }

    private LoggerWrapper() {
        throw new IllegalStateException("This class should not be instantiated");
    }

    public static Logger loggerFactory(Class<?> loggerForClass) {
        Logger logger = Logger.getLogger(loggerForClass.getName());
        createLogFiles(loggerForClass, logger);

        return logger;
    }

    private static void createLogFiles(Class<?> loggerForClass, Logger logger) {
        if (!IS_FILE_LOGGING_ENABLED) {
            return;
        }

        String timeFormat = LocalDateTime.now().format(FILE_NAME_TIME_FORMAT);
        String directoryFormat = "logs/%s/%s".formatted(loggerForClass.getPackageName(), loggerForClass.getSimpleName());
        Path filePath = Path.of(directoryFormat);

        if (Files.notExists(filePath)) {
            try {
                Files.createDirectories(filePath);
            } catch (IOException e) {
                ROOT_LOGGER.log(Level.SEVERE, "Failed to create log directory", e);
            }
        }

        try {
            FileHandler fileHandler = new FileHandler(filePath.resolve("%s.log".formatted(timeFormat)).toString(), true);
            fileHandler.setFormatter(new LogFormatter());

            logger.addHandler(fileHandler);
        } catch (IOException e) {
            ROOT_LOGGER.log(Level.SEVERE, "Failed to create log file", e);
        }
    }

    private static final class LogFormatter extends Formatter {

        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd@HH:mm:ss");

        @Override
        public String format(LogRecord record) {

            StringBuilder sb = new StringBuilder();

            sb.append("[").append(record.getLoggerName()).append("]").append(" ")
                    .append("[").append(record.getSourceMethodName()).append("]").append(" ")
                    .append("[").append(Thread.currentThread().getName()).append("]").append(" ")
                    .append("[").append(LocalDateTime.now().format(DATE_TIME_FORMATTER)).append("]").append(" ")
                    .append("[").append(record.getLevel()).append("]").append(" - ")
                    .append(record.getMessage()).append(System.lineSeparator());

            if (record.getThrown() != null) {
                sb.append("Throwable: ").append(record.getThrown().getMessage()).append(System.lineSeparator());
                sb.append("Cause: ").append(record.getThrown().getCause()).append(System.lineSeparator());
                sb.append("StackTrace: ").append(Arrays.toString(record.getThrown().getStackTrace())).append(System.lineSeparator());
            }

            return sb.toString();
        }

    }


}