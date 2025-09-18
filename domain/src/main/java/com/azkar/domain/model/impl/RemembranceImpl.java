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

/// Default immutable implementation of the [Remembrance] interface.
///
/// A `RemembranceImpl` represents a remembrance (dua, or hadith)
/// with metadata such as grade, source reference, translations, tags, and
/// timestamps. It also indicates whether the remembrance has been marked
/// as a favorite.
///
/// This record enforces invariants:
/// - `updatedAt` cannot be earlier than `createdAt`.
/// - `source` is normalized by trimming whitespace; if blank, it is stored as `null`.
/// - `tags` and `translations` are defensively copied into unmodifiable collections.
///
///
/// Instances can be created via the canonical constructor or through the
/// [Builder] provided by Lombok.
///
/// @param id           an optional unique identifier for the remembrance may be `null`
/// @param grade        the hadith grade (authenticity/reliability level)
/// @param favorite     whether this remembrance has been marked as a favorite
/// @param source       the source reference (e.g., book, scholar), may be `null` or blank
/// @param createdAt    the timestamp of creation may be `null`
/// @param updatedAt    the timestamp of the last update may be `null`
/// @param translations translations of this remembrance, keyed by locale; not `null`
/// @param tags         tags associated with this remembrance; not `null`
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
    /// Compact constructor that enforces invariants:
    ///
    /// - [updatedAt][#updatedAt] must not be before [createdAt][#createdAt].
    /// - [source][#source] is trimmed and normalized to `null` if blank.
    /// - [tags][#tags] and [translations][#translations] are copied defensively into immutable collections.
    ///
    /// @throws IllegalArgumentException if `updatedAt` is earlier than `createdAt`
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

    /// {@inheritDoc}
    @Override
    public Optional<Long> getId() {
        return Optional.ofNullable(id);
    }

    /// {@inheritDoc}
    @Override
    public Optional<String> getSource() {
        return Optional.ofNullable(source);
    }

    /// {@inheritDoc}
    @Override
    public HadithGrade getGrade() {
        return grade;
    }

    /// {@inheritDoc}
    @Override
    public boolean isFavorite() {
        return favorite;
    }

    /// {@inheritDoc}
    @Override
    public Optional<Instant> getCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    /// {@inheritDoc}
    @Override
    public Optional<Instant> getUpdatedAt() {
        return Optional.ofNullable(updatedAt);
    }

    /// {@inheritDoc}
    @Override
    public Map<Locale, Translations> getTranslations() {
        return translations;
    }

    /// {@inheritDoc}
    @Override
    public Set<Tag> getTags() {
        return tags;
    }
}
