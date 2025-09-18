package com.azkar.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Represents a remembrance (dua, or hadith) in the domain model.
///
/// A `Remembrance` encapsulates information about a specific remembrance,
/// including its grade, source reference, translations, and [associated tags][Tag].
/// It also exposes metadata such as when it was first introduced into the system
/// and when it was last updated.
///
/// The actual storage or retrieval mechanism is intentionally abstracted away—
/// a remembrance may come from a local repository, an online source, or another
/// provider. Some fields may be absent ([Optional#empty()]) when no value
/// is known or provided.
public interface Remembrance {
    /// Returns a unique identifier for this remembrance, if available.
    ///
    /// The identifier can be used to distinguish this remembrance from others,
    /// but its presence is not guaranteed.
    ///
    /// @return an [Optional] containing the identifier, or empty if none exists.
    Optional<Long> getId();

    /// Returns the hadith grade (level of authenticity or reliability)
    /// associated with this remembrance or [UNSPECIFIED][HadithGrade#UNSPECIFIED] if unknown.
    ///
    /// @return the [HadithGrade] of this remembrance.
    HadithGrade getGrade();

    /// Indicates whether this remembrance has been marked as a favorite
    /// by a user.
    ///
    /// @return `true` if it is a favorite, otherwise `false`.
    boolean isFavorite();

    /// Returns the source reference of the remembrance, if available.
    /// This could be a book title, a scholar's name, a collection,
    /// or any other textual reference.
    ///
    /// @return an [Optional] containing the source reference, or empty if not specified.
    Optional<String> getSource();

    /// Returns the timestamp indicating when this remembrance was first
    /// introduced or recorded in the system.
    ///
    /// @return an [Optional] containing the creation time, or empty if unknown.
    Optional<Instant> getCreatedAt();

    /// Returns the timestamp indicating the last time this remembrance
    /// was updated or modified.
    ///
    /// @return an [Optional] containing the update time, or empty if unknown.
    Optional<Instant> getUpdatedAt();

    /// Returns a mapping of translations of this remembrance,
    /// keyed by [Locale].
    ///
    /// Each translation provides both an explanation and a textual
    /// rendering of the remembrance in the target language.
    ///
    /// @return a [Map] of translations, indexed by locale.
    Map<Locale, Translations> getTranslations();

    /// Returns the set of tags associated with this remembrance.
    /// Tags may be used for categorization, filtering, or grouping.
    ///
    /// @return a [Set] of [Tag] instances.
    Set<Tag> getTags();

    /// Retrieves the translations for a specific locale, if available.
    ///
    /// @param locale the locale for which translations are requested
    /// @return an [Optional] containing the translations for that locale,
    ///         or empty if not present.
    default Optional<Translations> getTranslations(Locale locale) {
        return Optional.ofNullable(getTranslations().get(locale));
    }

    /// Represents translations of a remembrance, containing both an explanation
    /// and a translation of the remembrance text.
    ///
    /// @param explanationPair explanation of the remembrance
    /// @param translationPair translation of the remembrance text
    record Translations(Pair explanationPair, Pair translationPair) {
        /// Represents a single text element (either an explanation or a translation).
        ///
        /// @param id   an optional unique identifier for referencing this element
        ///                                                 may be `null` if not provided
        /// @param text the actual text content
        public record Pair(@Nullable Long id, String text) {}
    }
}
