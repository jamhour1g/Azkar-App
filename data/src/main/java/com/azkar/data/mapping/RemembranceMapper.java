package com.azkar.data.mapping;

import com.azkar.data.entity.RemembranceEntity;
import com.azkar.data.entity.RemembranceEntityBuilder;
import com.azkar.data.entity.TagEntity;
import com.azkar.domain.model.Remembrance;
import com.azkar.domain.model.Tag;
import com.azkar.domain.model.impl.RemembranceImpl;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Utility class responsible for mapping between persistence entities
/// ([RemembranceEntity], [TagEntity]) and domain models
/// ([Remembrance], [Tag]).
///
/// Responsibilities:
/// - Convert a JPA [RemembranceEntity] into a domain-level
/// [Remembrance] with immutable collections and value objects.
/// - Convert a domain [Remembrance] back into a [RemembranceEntity]
/// for persistence, including nested tags, translations, and explanations.
/// - Enforce data consistency: only locales with both a translation and an explanation
/// are preserved during mapping. If no such locale exists, mapping fails fast.
///
/// This class is `final` and has a private constructor: it cannot be instantiated
/// or extended. All functionality is provided through static methods.
///
/// @see RemembranceEntity
/// @see TagEntity
/// @see Remembrance
/// @see Tag
/// @see TagMapper
public final class RemembranceMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        RemembranceMapper.class
    );

    private RemembranceMapper() {
        throw new UnsupportedOperationException(
            "This is a utility class and cannot be instantiated"
        );
    }

    /// Converts persistence [RemembranceEntity] into a domain [Remembrance].
    ///
    /// @param remembranceEntity the JPA entity to convert
    /// @return an immutable domain [Remembrance]
    /// @throws IllegalArgumentException if no locale has both a translation and an explanation
    public static Remembrance toRemembrance(
        RemembranceEntity remembranceEntity
    ) {
        Set<Tag> tagList = remembranceEntity
            .getTags()
            .stream()
            .map(TagMapper::toTag)
            .collect(Collectors.toUnmodifiableSet());

        return RemembranceImpl.builder()
            .id(remembranceEntity.getId())
            .source(remembranceEntity.getSource())
            .grade(remembranceEntity.getGrade())
            .favorite(remembranceEntity.isFavorited())
            .createdAt(remembranceEntity.getCreatedAt())
            .updatedAt(remembranceEntity.getUpdatedAt())
            .translations(groupByLocale(remembranceEntity))
            .tags(tagList)
            .build();
    }

    /// Converts a domain [Remembrance] into persistence [RemembranceEntity].
    ///
    /// @param remembrance the domain model to convert
    /// @return a new [RemembranceEntity] ready for persistence
    public static RemembranceEntity fromRemembrance(Remembrance remembrance) {
        Set<TagEntity> tags = remembrance
            .getTags()
            .stream()
            .map(TagMapper::fromTag)
            .collect(Collectors.toUnmodifiableSet());

        RemembranceEntityBuilder builder = RemembranceEntity.builder()
            .id(remembrance.getId().orElse(null))
            .source(remembrance.getSource().orElse(null))
            .grade(remembrance.getGrade())
            .favorite(remembrance.isFavorite())
            .addTags(tags);

        remembrance
            .getTranslations()
            .forEach((loc, trEntity) -> {
                Remembrance.Translations.Pair translationPair =
                    trEntity.translationPair();
                Remembrance.Translations.Pair explanationPair =
                    trEntity.explanationPair();

                builder
                    .addTranslation(
                        translationPair.id(),
                        loc,
                        translationPair.text()
                    )
                    .addExplanation(
                        explanationPair.id(),
                        loc,
                        explanationPair.text()
                    );
            });

        return builder.build();
    }

    /// Groups translations and explanations by locale, ensuring consistency.
    ///
    /// For each locale:
    /// - Collects a translation pair (id + text) from `remembrance_translation`.
    /// - Collects an explanation pair (id + text) from `explanation_translation`.
    /// - Keeps only locales that have _both_ present.
    ///
    /// If no locale has both a translation and an explanation,
    /// throws [IllegalArgumentException] to prevent constructing
    /// an incomplete [Remembrance].
    ///
    /// @param remembranceEntity the entity whose child rows are grouped
    /// @return an immutable map from [Locale] to [Remembrance.Translations]
    /// @throws IllegalArgumentException if no locale has both translation and explanation
    private static Map<Locale, Remembrance.Translations> groupByLocale(
        RemembranceEntity remembranceEntity
    ) {
        Map<Locale, Remembrance.Translations.Pair> tr = remembranceEntity
            .getTranslations()
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, entry ->
                    new Remembrance.Translations.Pair(
                        entry.getValue().getId(),
                        entry.getValue().getText()
                    )
                )
            );
        Map<Locale, Remembrance.Translations.Pair> ex = remembranceEntity
            .getExplanations()
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, entry ->
                    new Remembrance.Translations.Pair(
                        entry.getValue().getId(),
                        entry.getValue().getText()
                    )
                )
            );

        // keep only intersection of locales
        Set<Locale> locales = new HashSet<>(tr.keySet());
        locales.retainAll(ex.keySet());

        // warn about locales that were dropped because they lack a counterpart
        Set<Locale> droppedTranslations = new HashSet<>(tr.keySet());
        droppedTranslations.removeAll(ex.keySet());
        Set<Locale> droppedExplanations = new HashSet<>(ex.keySet());
        droppedExplanations.removeAll(tr.keySet());
        if (!droppedTranslations.isEmpty() || !droppedExplanations.isEmpty()) {
            LOGGER.atWarn()
                .setMessage("Dropped locales missing counterpart")
                .addKeyValue("remembranceId", remembranceEntity.getId())
                .addKeyValue("droppedTranslations", droppedTranslations)
                .addKeyValue("droppedExplanations", droppedExplanations)
                .log();
        }

        // require at least one complete locale
        if (locales.isEmpty()) {
            throw new IllegalArgumentException(
                "No locale has both translation and explanation for id=" +
                remembranceEntity.getId()
            );
        }

        return locales
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    loc -> loc,
                    loc ->
                        new Remembrance.Translations(
                            Objects.requireNonNull(ex.get(loc)),
                            Objects.requireNonNull(tr.get(loc))
                        )
                )
            );
    }
}
