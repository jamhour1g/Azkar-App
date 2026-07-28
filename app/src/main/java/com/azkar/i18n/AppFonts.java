package com.azkar.i18n;

import java.util.Locale;
import java.util.Set;
import javafx.scene.Node;

public final class AppFonts {

    private static final String FONT_UNIVERSAL = "Noto Sans, Segoe UI, system-ui, sans-serif";
    private static final String FONT_ARAB = "Amiri, Noto Naskh Arabic, serif";

    private static final Set<String> SCRIPT_ARAB = Set.of("Arab", "Thaa");

    private AppFonts() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String fontFamily(Locale locale) {
        String script = scriptCode(locale);
        if (SCRIPT_ARAB.contains(script)) {
            return FONT_ARAB;
        }
        return FONT_UNIVERSAL;
    }

    public static void applyFont(Node node, Locale locale) {
        node.setStyle("-fx-font-family: \"" + fontFamily(locale) + "\";");
    }

    public static void applyFontRecursive(Node node, Locale locale) {
        applyFont(node, locale);
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyFontRecursive(child, locale);
            }
        }
    }

    public static void applyFontRecursive(Node node) {
        applyFontRecursive(node, Locale.getDefault());
    }

    private static String scriptCode(Locale locale) {
        String explicitScript = locale.getScript();
        if (!explicitScript.isBlank()) {
            return explicitScript;
        }
        return LANGUAGE_SCRIPT_FALLBACKS.getOrDefault(locale.getLanguage().toLowerCase(Locale.ROOT), "");
    }

    private static final java.util.Map<String, String> LANGUAGE_SCRIPT_FALLBACKS = java.util.Map.ofEntries(
            java.util.Map.entry("ar", "Arab"),
            java.util.Map.entry("dv", "Thaa"),
            java.util.Map.entry("en", "Latn"),
            java.util.Map.entry("fa", "Arab"),
            java.util.Map.entry("he", "Hebr"),
            java.util.Map.entry("ps", "Arab"),
            java.util.Map.entry("sd", "Arab"),
            java.util.Map.entry("ur", "Arab"),
            java.util.Map.entry("yi", "Hebr"));
}
