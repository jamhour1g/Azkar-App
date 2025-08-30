package com.azkar.domain.model.impl;

import com.azkar.domain.model.HadithGrade;
import com.azkar.domain.model.Remembrance;
import com.azkar.domain.model.Tag;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder(toBuilder = true)
public record RemembranceImpl(
    @Nullable Long id,
    HadithGrade grade,
    boolean favorite,
    @Nullable String source,
    @Nullable Instant createdAt,
    @Nullable Instant updatedAt,
    Map<Locale, Translations> translations,
    Set<Tag> tags
) implements Remembrance {
    public RemembranceImpl {
        if (
            (createdAt != null && updatedAt != null) &&
            updatedAt.isBefore(createdAt)
        ) {
            throw new IllegalArgumentException(
                "updatedAt cannot be before createdAt"
            );
        }

        source = (source == null || source.isBlank()) ? null : source.trim();
        tags = Set.copyOf(tags);
        translations = Map.copyOf(translations);
    }

    @Override
    public Optional<Long> getId() {
        return Optional.ofNullable(id);
    }

    @Override
    public Optional<String> getSource() {
        return Optional.ofNullable(source);
    }

    @Override
    public HadithGrade getGrade() {
        return grade;
    }

    @Override
    public boolean isFavorite() {
        return favorite;
    }

    @Override
    public Optional<Instant> getCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    @Override
    public Optional<Instant> getUpdatedAt() {
        return Optional.ofNullable(updatedAt);
    }

    @Override
    public Map<Locale, Translations> getTranslations() {
        return translations;
    }

    @Override
    public Set<Tag> getTags() {
        return tags;
    }
}
