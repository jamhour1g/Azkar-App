package com.azkar.i18n;

import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import org.slf4j.Logger;

import java.awt.ComponentOrientation;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class AppLocale {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale ARABIC = Locale.forLanguageTag("ar");

    private static final String RESOURCE_BUNDLE_KEY = "com.azkar.i18n.home";
    private static final Locale DEFAULT_LOCALE = ENGLISH;
    private static final String PREFS_NODE = "com.azkar.app.settings";
    private static final String KEY_LOCALE = "locale";

    private static final Preferences PREFERENCES_NODE = Preferences.userRoot().node(PREFS_NODE);
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AppLocale.class);

    private static Locale currentLocale = getLocaleFromPreferences();

    private AppLocale() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static Locale getLocaleFromPreferences() {
        String storedLanguageTag =
                PREFERENCES_NODE.get(KEY_LOCALE, DEFAULT_LOCALE.toLanguageTag());
        return Locale.forLanguageTag(storedLanguageTag);
    }

    public static Locale current() {
        return currentLocale;
    }

    public static void applyPersisted() {
        currentLocale = getLocaleFromPreferences();
        Locale.setDefault(currentLocale);
    }

    public static void applyAndPersist(Locale locale) {
        currentLocale = locale;
        Locale.setDefault(locale);

        Preferences prefs = PREFERENCES_NODE;
        prefs.put(KEY_LOCALE, locale.toLanguageTag());
        try {
            prefs.flush();
        } catch (BackingStoreException _) {
            LOGGER.atError().log("Failed to persist locale change to '{}'", locale.toLanguageTag());
        }
    }

    public static ResourceBundle bundle() {
        return ResourceBundle.getBundle(RESOURCE_BUNDLE_KEY, currentLocale);
    }

    public static boolean sameLanguage(Locale left, Locale right) {
        return left.getLanguage().equalsIgnoreCase(right.getLanguage());
    }

    public static List<Locale> suggestedUiLocales() {
        return List.of(
                ENGLISH,
                ARABIC
        );
    }

    public static String displayLanguage(Locale targetLocale, Locale displayLocale) {
        return targetLocale.getDisplayLanguage(displayLocale).trim();
    }

    public static boolean isCurrentRtl() {
        return isRtl(current());
    }

    public static boolean isRtl(Locale locale) {
        return !ComponentOrientation.getOrientation(locale).isLeftToRight();
    }

    public static void applyNodeOrientation(Node node) {
        applyNodeOrientation(node, current());
    }

    public static void applyNodeOrientation(Node node, Locale locale) {
        node.setNodeOrientation(isRtl(locale) ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
    }

}
