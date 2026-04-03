package com.azkar.data.entity;

import com.azkar.domain.model.HadithGrade;
import java.util.*;
import org.jspecify.annotations.Nullable;

/// Fluent builder for constructing [RemembranceEntity] instances with support for:
///
/// - Multi-locale translations and explanations
/// - Tagging
/// - FavoriteEntity marking
/// - Source and authenticity grade
///
/// ### Usage Example
/// ```java
/// RemembranceEntity remembrance = RemembranceEntity.builder()
///     .source("Sahih Muslim")
///     .grade(HadithGrade.SAHIH)
///     .addTranslation(Locale.ENGLISH, "SubhanAllah")
///     .addTranslation(Locale.forLanguageTag("ar"), "سبحان الله")
///     .addExplanation(Locale.ENGLISH, "Glory be to God")
///     .addTag("Morning")
///     .addTag("Tasbih")
///     .favorite(true)
///     .build();
/// ```
///
/// @see RemembranceEntity
public final class RemembranceEntityBuilder {

    private final Set<LocalizedText> explanations = new HashSet<>();
    private final Set<TagEntity> tags = new LinkedHashSet<>();
    private final Set<LocalizedText> translations = new HashSet<>();
    private boolean isFavorite;
    private @Nullable Long id;
    private @Nullable String source;
    private HadithGrade grade = HadithGrade.UNSPECIFIED;

    public RemembranceEntityBuilder id(@Nullable Long id) {
        this.id = id;
        return this;
    }

    /// Sets the source reference for the remembrance (e.g., "Sahih al-Bukhari").
    ///
    /// @param source the source description; may be `null` if unknown
    /// @return this builder instance
    public RemembranceEntityBuilder source(@Nullable String source) {
        this.source = source;
        return this;
    }

    /// Sets the authenticity grade of the hadith.
    /// If not provided, defaults to [HadithGrade#UNSPECIFIED].
    ///
    /// @param grade the hadith grade; must not be `null`
    /// @return this builder instance
    public RemembranceEntityBuilder grade(HadithGrade grade) {
        this.grade = grade;
        return this;
    }

    /// Marks the remembrance as favorited upon creation.
    ///
    /// If `true`, calling [#build()] will automatically call
    /// [RemembranceEntity#markFavorite()] on the constructed instance.
    ///
    /// @param isFavorite `true` to mark as favorite; `false` otherwise
    /// @return this builder instance
    public RemembranceEntityBuilder favorite(boolean isFavorite) {
        this.isFavorite = isFavorite;
        return this;
    }

    /// Adds a translated zikr text for the given locale.
    ///
    /// If `text` is blank, an [IllegalArgumentException] is thrown.
    ///
    /// Since translations are stored in a [Set], multiple entries for the same locale will
    /// result in only the first one added being stored. See [Set#add(Object)] for details.
    ///
    /// @param locale the target locale (e.g., `Locale.ENGLISH`); must not be `null`
    /// @param text   the translated zikr text; must not be blank
    /// @return this builder instance
    /// @throws IllegalArgumentException if `text` is blank
    public RemembranceEntityBuilder addTranslation(Locale locale, String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("Translation text must not be blank");
        }

        translations.add(new LocalizedText(null, locale, text));
        return this;
    }

    /// Adds an explanation (commentary) for the given locale.
    ///
    /// If `text` is blank, an [IllegalArgumentException] is thrown.
    ///
    /// Since explanations are stored in a [Set], multiple entries for the same locale will
    /// result in only the first one added being stored. See [Set#add(Object)] for details.
    ///
    /// @param locale the target locale; must not be `null`
    /// @param text   the explanation text; must not be blank
    /// @return this builder instance
    /// @throws IllegalArgumentException if `text` is blank
    public RemembranceEntityBuilder addExplanation(Locale locale, String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("Explanation text must not be blank");
        }

        explanations.add(new LocalizedText(null, locale, text));
        return this;
    }

    /// Adds a localized translation of the remembrance text itself.
    ///
    /// Each translation maps to a row in [RemembranceTranslationEntity],
    /// with a unique combination of `remembrance_id` and `locale_code`.
    ///
    /// Constraints:
    /// - `text` must not be blank (enforced here).
    /// - Database CHECK constraint ensures `length(text) > 0`.
    /// - Uniqueness enforced per locale at the DB layer.
    ///
    /// @param id     optional pre-existing translation ID (nullable for new rows)
    /// @param locale the locale of this translation (e.g. `"ar"`, `"en-US"`)
    /// @param text   the translated remembrance text (must be non-blank)
    /// @return this builder for chaining
    /// @throws IllegalArgumentException if `text` is blank
    /// @see RemembranceTranslationEntity
    public RemembranceEntityBuilder addTranslation(@Nullable Long id, Locale locale, String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("Translation text must not be blank");
        }

        translations.add(new LocalizedText(id, locale, text));
        return this;
    }

    /// Adds a localized explanation (commentary) for the remembrance.
    ///
    /// Each explanation maps to a row in [ExplanationTranslationEntity],
    /// with a unique combination of `remembrance_id` and `locale_code`.
    ///
    /// Constraints:
    /// - `text` must not be blank (enforced here).
    /// - Database CHECK constraint ensures `length(text) > 0`.
    /// - Uniqueness enforced per locale at the DB layer.
    ///
    /// @param id     optional pre-existing explanation ID (nullable for new rows)
    /// @param locale the locale of this explanation (e.g. `"en-US"`)
    /// @param text   the explanation text (must be non-blank)
    /// @return this builder for chaining
    /// @throws IllegalArgumentException if `text` is blank
    /// @see ExplanationTranslationEntity
    public RemembranceEntityBuilder addExplanation(@Nullable Long id, Locale locale, String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("Explanation text must not be blank");
        }

        explanations.add(new LocalizedText(id, locale, text));
        return this;
    }

    /// Adds a tag by name.
    ///
    /// If `tagName` is blank, an [IllegalArgumentException] is thrown.
    ///
    /// Tags are case-sensitive in storage, but uniqueness is enforced
    /// case-insensitively via database constraint (`uq_tag__name_nocase`).
    ///
    /// Preserves insertion order using [LinkedHashSet].
    ///
    /// @param tagName the name of the tag (e.g., "Morning"); must not be blank
    /// @return this builder instance
    /// @throws IllegalArgumentException if `tagName` is blank
    public RemembranceEntityBuilder addTag(String tagName) {
        if (tagName.isBlank()) {
            throw new IllegalArgumentException("TagEntity name must not be blank");
        }

        tags.add(TagEntity.builder().name(tagName).build());
        return this;
    }

    /// Adds a set of tags to the remembrance being built.
    ///
    /// Each [TagEntity] corresponds to a row in the `tag` table
    /// and is linked via the `remembrance_tag` join table.
    ///
    /// Semantics:
    /// - Ensures all tags in the set are copied defensively.
    /// - Used to categorize remembrances (e.g., Morning, Evening, Juma).
    ///
    /// @param tags the tags to associate with this remembrance (non-null)
    /// @return this builder for chaining
    /// @see TagEntity
    public RemembranceEntityBuilder addTags(Set<TagEntity> tags) {
        this.tags.addAll(Set.copyOf(tags));
        return this;
    }

    /// Constructs and returns a fully initialized [RemembranceEntity] instance.
    ///
    /// This method:
    /// - Sets source and grade
    /// - Applies all translations, explanations, and tags
    /// - Optionally marks as favorite
    ///
    /// The returned instance is transient (not yet persisted) and ready for use.
    ///
    /// @return a new `RemembranceEntity` instance with all configured data
    public RemembranceEntity build() {
        RemembranceEntity remembrance = RemembranceEntity.create();
        remembrance.setId(id);
        remembrance.setSource(source);
        remembrance.setGrade(grade);

        if (isFavorite) {
            remembrance.markFavorite();
        }

        translations.forEach(t -> remembrance.addTranslation(t.id(), t.locale(), t.text()));
        explanations.forEach(e -> remembrance.addExplanation(e.id(), e.locale(), e.text()));
        tags.forEach(tag -> tag.addRemembrance(remembrance));

        return remembrance;
    }

    /// Internal container for locale–text pairs collected during construction.
    ///
    /// Used by [RemembranceEntityBuilder#addTranslation] and [RemembranceEntityBuilder#addExplanation]
    /// to hold data before persisting to [RemembranceTranslationEntity] or
    /// [ExplanationTranslationEntity].
    ///
    /// Immutable and lightweight:
    /// - `id` links back to an existing row (nullable if new).
    /// - `locale` identifies the language/region.
    /// - `text` guaranteed non-blank by validation.
    ///
    /// @see RemembranceTranslationEntity
    /// @see ExplanationTranslationEntity
    record LocalizedText(@Nullable Long id, Locale locale, String text) {}
}
