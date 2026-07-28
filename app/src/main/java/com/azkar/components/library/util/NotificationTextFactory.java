package com.azkar.components.library.util;

import com.azkar.domain.model.Remembrance;
import com.azkar.i18n.RemembranceI18n;

import java.util.Locale;

public final class NotificationTextFactory {

    private static final int PREVIEW_LIMIT = 190;

    private NotificationTextFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String preview(Remembrance remembrance, Locale preferredLocale, String sourceFallback) {
        String text = RemembranceI18n.resolveTexts(remembrance, preferredLocale, sourceFallback, sourceFallback)
                .translation();
        if (text.length() <= PREVIEW_LIMIT) {
            return text;
        }
        return text.substring(0, PREVIEW_LIMIT - 1) + "...";
    }
}
