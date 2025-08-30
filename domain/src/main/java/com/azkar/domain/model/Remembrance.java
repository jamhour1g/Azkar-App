package com.azkar.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface Remembrance {
    Optional<Long> getId(); // null when remembrance is not saved in the database or when one wants to create a new remembrance

    HadithGrade getGrade();

    boolean isFavorite();

    Optional<String> getSource();

    Optional<Instant> getCreatedAt(); // null when remembrance is not saved in the database or when one wants to create a new
    // remembrance

    Optional<Instant> getUpdatedAt(); // null when remembrance is not saved in the database or when one wants to create a new
    // remembrance

    Map<Locale, Translations> getTranslations();

    Set<Tag> getTags();

    default Optional<Translations> getTranslations(Locale locale) {
        return Optional.ofNullable(getTranslations().get(locale));
    }

    record Translations(String explanationText, String translationText) {}
}
