package com.azkar.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.logging.*;

public final class LoggerWrapper {

    private static final DateTimeFormatter FILE_NAME_TIME_FORMAT =
        DateTimeFormatter.ofPattern("dd_MM_yyyy_HH_mm");
    private static final DateTimeFormatter LINE_TS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd@HH:mm:ss");

    private static final String KEY_ENABLE_CONSOLE = "ENABLE_CONSOLE_LOGGING";
    private static final String KEY_ENABLE_FILE = "ENABLE_FILE_LOGGING";
    private static final String KEY_LOG_LEVEL = "LOG_LEVEL"; // e.g. FINE, INFO
    private static final String KEY_LOG_DIR = "LOG_DIR"; // default: logs/
    private static final String DEFAULT_LOG_DIR = "logs";

    private static final Properties ENV_PROPS = loadDotEnvThenEnvThenProps();

    private static final Logger ROOT_LOGGER = Logger.getLogger("");

    static {
        resetRootHandlers();
        installConfiguredHandlers();
        applyRootLevel();
    }

    private LoggerWrapper() {
        throw new IllegalStateException(
            "This class should not be instantiated"
        );
    }

    /** Factory for per-class loggers; also ensures a file handler is added per-logger if enabled. */
    public static Logger loggerFactory(Class<?> loggerForClass) {
        Logger logger = Logger.getLogger(loggerForClass.getName());
        attachPerLoggerFileHandlerIfEnabled(loggerForClass, logger);

        return logger;
    }

    private static void resetRootHandlers() {
        for (Handler h : ROOT_LOGGER.getHandlers()) {
            ROOT_LOGGER.removeHandler(h);
        }
    }

    private static void installConfiguredHandlers() {
        boolean consoleOn = getBoolean(KEY_ENABLE_CONSOLE, false);
        boolean fileOn = getBoolean(KEY_ENABLE_FILE, false);

        if (consoleOn) {
            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(Level.ALL);
            ch.setFormatter(new LogFormatter());

            ROOT_LOGGER.addHandler(ch);
        }

        if (fileOn) {
            try {
                Path dir = Path.of(
                    ENV_PROPS.getProperty(KEY_LOG_DIR, DEFAULT_LOG_DIR)
                ).toAbsolutePath();
                Files.createDirectories(dir);

                String file =
                    "root-" +
                    LocalDateTime.now().format(FILE_NAME_TIME_FORMAT) +
                    ".log";

                FileHandler fh = new FileHandler(
                    dir.resolve(file).toString(),
                    true
                );
                fh.setLevel(Level.ALL);
                fh.setFormatter(new LogFormatter());

                ROOT_LOGGER.addHandler(fh);
            } catch (IOException e) {
                System.err.println(
                    "Failed to create root log file: " + e.getMessage()
                );
            }
        }
    }

    private static void applyRootLevel() {
        String lvl = ENV_PROPS.getProperty(KEY_LOG_LEVEL, Level.INFO.getName())
            .trim()
            .toUpperCase(Locale.ROOT);

        ROOT_LOGGER.setLevel(Level.parse(lvl));
    }

    private static void attachPerLoggerFileHandlerIfEnabled(
        Class<?> ownerClass,
        Logger logger
    ) {
        if (!getBoolean(KEY_ENABLE_FILE, false)) {
            return;
        }

        // logs/<package>/<ClassName>/<timestamp>.log (customizable via LOG_DIR)
        String base = ENV_PROPS.getProperty(KEY_LOG_DIR, DEFAULT_LOG_DIR);
        String time = LocalDateTime.now().format(FILE_NAME_TIME_FORMAT);
        Path dir = Path.of(
            base,
            ownerClass.getPackageName(),
            ownerClass.getSimpleName()
        );

        try {
            Files.createDirectories(dir);

            FileHandler fh = new FileHandler(
                dir.resolve(time + ".log").toString(),
                true
            );
            fh.setLevel(Level.ALL);
            fh.setFormatter(new LogFormatter());

            logger.addHandler(fh);
        } catch (IOException e) {
            ROOT_LOGGER.log(
                Level.SEVERE,
                "Failed to create per-logger file: " +
                dir +
                " (" +
                e.getMessage() +
                ")",
                e
            );
        }
    }

    // ----- configuration resolution: .env -> env vars -> system props -----
    private static Properties loadDotEnvThenEnvThenProps() {
        Properties p = new Properties();

        // 1) .env in project root (if present)
        Path dotEnv = Path.of(".env");
        if (Files.isRegularFile(dotEnv)) {
            try (InputStream in = Files.newInputStream(dotEnv)) {
                // .env is KEY=VALUE per line; Properties can read that format
                p.load(in);
            } catch (IOException e) {
                System.err.println(
                    "LoggerWrapper: Could not load .env: " + e.getMessage()
                );
            }
        }

        // 2) env vars override .env
        Set<String> logSettingsKeys = Set.of(
            KEY_ENABLE_CONSOLE,
            KEY_ENABLE_FILE,
            KEY_LOG_LEVEL,
            KEY_LOG_DIR
        );

        System.getenv()
            .entrySet()
            .stream()
            .filter(
                e ->
                    e.getValue() != null && logSettingsKeys.contains(e.getKey())
            )
            .forEach(e -> p.setProperty(e.getKey(), e.getValue()));

        // 3) system properties override both
        System.getProperties()
            .entrySet()
            .stream()
            .filter(e -> e.getKey() != null && e.getValue() != null)
            .filter(e -> logSettingsKeys.contains(e.getKey().toString()))
            .forEach(e ->
                p.setProperty(e.getKey().toString(), e.getValue().toString())
            );

        return p;
    }

    private static boolean getBoolean(String key, boolean def) {
        String v = ENV_PROPS.getProperty(key);
        return (v == null) ? def : Boolean.parseBoolean(v.trim());
    }

    static final class LogFormatter extends Formatter {

        @Override
        public String format(LogRecord r) {
            StringBuilder sb = new StringBuilder(256);

            sb
                .append('[')
                .append(r.getLoggerName())
                .append(']')
                .append(' ')
                .append('[')
                .append(r.getSourceMethodName())
                .append(']')
                .append(' ')
                .append('[')
                .append(Thread.currentThread().getName())
                .append(']')
                .append(' ')
                .append('[')
                .append(LocalDateTime.now().format(LINE_TS))
                .append(']')
                .append(' ')
                .append('[')
                .append(r.getLevel())
                .append(']')
                .append(" - ")
                .append(formatMessage(r))
                .append(System.lineSeparator());

            if (r.getThrown() != null) {
                StringWriter sw = new StringWriter(512);
                r.getThrown().printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            }
            return sb.toString();
        }
    }
}
