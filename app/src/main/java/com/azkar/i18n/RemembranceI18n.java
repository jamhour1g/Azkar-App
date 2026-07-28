package com.azkar.i18n;

import com.azkar.domain.model.Remembrance;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RemembranceI18n {

    private RemembranceI18n() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Optional<Remembrance.Translations> resolveTranslations(
            Remembrance remembrance, Locale preferredLocale) {
        Map<Locale, Remembrance.Translations> translations = remembrance.getTranslations();
        if (translations.isEmpty()) {
            return Optional.empty();
        }

        Locale normalizedPreferred = preferredLocale == null ? Locale.getDefault() : preferredLocale;
        Remembrance.Translations exactMatch = translations.get(normalizedPreferred);
        if (exactMatch != null) {
            return Optional.of(exactMatch);
        }

        Optional<Remembrance.Translations> byLanguage = byLanguage(translations, normalizedPreferred);
        if (byLanguage.isPresent()) {
            return byLanguage;
        }

        Locale oppositeFallback =
                AppLocale.sameLanguage(normalizedPreferred, AppLocale.ARABIC) ? AppLocale.ENGLISH : AppLocale.ARABIC;

        Optional<Remembrance.Translations> oppositeLanguage = byLanguage(translations, oppositeFallback);
        if (oppositeLanguage.isPresent()) {
            return oppositeLanguage;
        }

        return Optional.of(translations.values().iterator().next());
    }

    public static LocalizedTexts resolveTexts(
            Remembrance remembrance, Locale preferredLocale, String translationFallback, String explanationFallback) {
        Optional<Remembrance.Translations> resolved = resolveTranslations(remembrance, preferredLocale);

        String translation = resolved.map(Remembrance.Translations::translationPair)
                .map(Remembrance.Translations.Pair::text)
                .filter(RemembranceI18n::hasText)
                .orElse(translationFallback);

        String explanation = resolved.map(Remembrance.Translations::explanationPair)
                .map(Remembrance.Translations.Pair::text)
                .filter(RemembranceI18n::hasText)
                .orElse(explanationFallback);

        return new LocalizedTexts(translation, explanation);
    }

    private static Optional<Remembrance.Translations> byLanguage(
            Map<Locale, Remembrance.Translations> translations, Locale targetLocale) {
        if (targetLocale == null || targetLocale.getLanguage().isBlank()) {
            return Optional.empty();
        }

        return translations.entrySet().stream()
                .filter(entry -> targetLocale
                        .getLanguage()
                        .equalsIgnoreCase(entry.getKey().getLanguage()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record LocalizedTexts(String translation, String explanation) {}
}
