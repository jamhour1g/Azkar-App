package com.azkar.data.entity;

import com.azkar.data.converter.HadithGradeConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;
import org.jspecify.annotations.Nullable;

/// Represents a remembrance (ذِكْر / "zikr") — a spiritual phrase or supplication,
/// used for reflection, worship, or mindfulness.
///
/// This is the core entity in the system. It supports:
/// - **Internationalization:** Translations and explanations per locale
/// - **Metadata:** Source reference and authenticity grade
/// - **Organization:** Tagging via many-to-many relationships
/// - **User interaction:** FavoriteEntity marking
///
/// ### Database Mapping
/// - **Table:** `remembrance`
/// - **Primary Key:** `id` (auto-incremented by SQLite)
/// - **`source`:** Optional textual reference (e.g., "Sahih Muslim")
/// - **`grade`:** Hadith reliability: one of `SAHIH`, `HASAN`, `DAIF`, `UNSPECIFIED`
/// - **`created_at`:** Set via `DEFAULT (unixepoch())` on insert
/// - **`updated_at`:** Auto-updated by trigger `trg_remembrance__set_updated_at`
///
/// ### Related Tables
/// - **`remembrance_translation`:** Stores translated zikr text per locale
/// - **`explanation_translation`:** Stores explanatory commentary per locale
/// - **`remembrance_tag`:** Join table for tags
/// - **`favorite`:** User-level favorite marker
///
/// ### Querying with Favorites // TODO: Update when you create the view
///
/// Use the `remembrance_with_favorite` view to efficiently fetch remembrances
/// with an `is_favorite` flag (0 or 1), avoiding expensive joins in list displays.
/// <pre>
/// SELECT * FROM remembrance_with_favorite WHERE is_favorite = 1;
/// </pre>
/// ### Full-Text Search // TODO: Update when you understand ftsSearch
///
/// Optional FTS5 support via `remembrance_fts`, kept in sync by triggers
/// on `remembrance_translation` inserts/updates/deletes.
/// ### Usage Example
/// <pre>
/// ```java
/// RemembranceEntity remembrance = RemembranceEntity.builder()
/// .source("Sahih al-Bukhari")
/// .grade(DatabaseHadithGrade.SAHIH)
/// .addTranslation(Locale.ENGLISH, "SubhanAllah")
/// .addExplanation(Locale.ENGLISH, "Glory be to God")
/// .addTag("Morning")
/// .favorite(true)
/// .build();
/// ````
///
/// @see RemembranceTranslationEntity
/// @see ExplanationTranslationEntity
/// @see TagEntity
/// @see FavoriteEntity
@Entity
@Table(
    name = "remembrance",
    check = @CheckConstraint(
        name = "remembrance_grade_check",
        constraint = "grade IN ('SAHIH', 'HASAN', 'DAIF', 'UNSPECIFIED')"
    )
)
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class RemembranceEntity {

    /// Unique identifier assigned by the database.
    ///
    /// Auto-incremented primary key (`INTEGER PRIMARY KEY AUTOINCREMENT`). Will be `null` for transient
    /// (unsaved) instances.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private @Nullable Long id;

    /// Optional source reference for the remembrance (e.g., "Sahih Muslim").
    ///
    /// Free-text field for attribution. May be `null` if unknown.
    @ToString.Include
    @Getter
    @Setter
    private @Nullable String source;

    /// The authenticity grade of the hadith.
    ///
    /// One of: `SAHIH` (authentic), `HASAN` (good), `DAIF` (weak), or `UNSPECIFIED`.
    /// Enforced by the database `CHECK` constraint.
    @Convert(converter = HadithGradeConverter.class)
    @Column(
        nullable = false,
        check = @CheckConstraint(
            name = "remembrance_grade_check",
            constraint = "grade IN ('SAHIH', 'HASAN', 'DAIF', 'UNSPECIFIED')"
        )
    )
    @ToString.Include
    @Getter
    @Setter
    private DatabaseHadithGrade grade = DatabaseHadithGrade.UNSPECIFIED;

    /// Timestamp when this remembrance was created.
    ///
    /// Set automatically by the database using `DEFAULT (unixepoch())`.
    /// Not modifiable by application code.
    @Column(
        name = "created_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    @Getter
    private @Nullable Instant createdAt;

    /// Timestamp when this remembrance was last updated.
    ///
    /// Maintained automatically by the `trg_remembrance__set_updated_at` trigger.
    /// Updated to the current Unix epoch on any `UPDATE` operation.
    @Column(
        name = "updated_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    @Getter
    private @Nullable Instant updatedAt;

    /// Optional favorite marker for this remembrance.
    ///
    /// Managed via a one-to-one relationship with [favorite.remembrance][FavoriteEntity#remembrance].
    /// Uses `cascade = CascadeType.ALL, orphanRemoval = true`
    /// setting this to `null` will delete the associated `favorite` row.
    @OneToOne(
        mappedBy = "remembrance",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @Getter
    private @Nullable FavoriteEntity favorite;

    /// Map of translations for this remembrance, keyed by locale.
    ///
    /// Each remembrance can have one translation per locale
    /// (enforced by unique constraint on `(remembrance_id,locale_code)` in `remembrance_translation`).
    ///
    /// Uses `orphanRemoval = true` — removing a translation from this map will delete it from the database on
    /// flushes.
    @OneToMany(
        mappedBy = "remembrance",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @MapKey(name = "locale")
    @Getter
    private Map<Locale, RemembranceTranslationEntity> translations =
        new HashMap<>();

    /// Map of explanations (commentary) for this remembrance, keyed by locale.
    ///
    /// Like translations, one explanation per locale is allowed.
    ///
    /// Also uses `orphanRemoval = true` for automatic cleanup.
    @OneToMany(
        mappedBy = "remembrance",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @MapKey(name = "locale")
    @Getter
    private Map<Locale, ExplanationTranslationEntity> explanations =
        new HashMap<>();

    /// Set of tags associated with this remembrance.
    ///
    /// Many-to-many relationship via join table `remembrance_tag`.
    /// Bidirectional — use [#addTag(TagEntity)] and [#removeTag(TagEntity)] to maintain consistency.
    ///
    /// Uses [LinkedHashSet] to preserve insertion order.
    @ManyToMany
    @JoinTable(
        name = "remembrance_tag",
        joinColumns = @JoinColumn(name = "remembrance_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Getter
    private Set<TagEntity> tags = new LinkedHashSet<>();

    /// Creates a new builder for constructing [RemembranceEntity] instances.
    ///
    /// @return a new [Builder] instance
    public static Builder builder() {
        return new Builder();
    }

    /// Adds a translation for the given locale.
    ///
    /// @param locale the locale for the translation; must not be `null`
    /// @param text   the translated text; must not be `null` or blank
    /// @throws IllegalArgumentException if `text` is blank
    public void addTranslation(Locale locale, String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                "Translation text must not be blank"
            );
        }

        addTranslation(
            RemembranceTranslationEntity.builder()
                .remembrance(this)
                .locale(locale)
                .text(text)
                .build()
        );
    }

    /// Adds an explanation for the given locale.
    ///
    /// @param locale the locale for the explanation; must not be `null`
    /// @param text   the explanation text; must not be `null` or blank
    /// @throws IllegalArgumentException if `text` is blank
    public void addExplanation(Locale locale, String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                "Explanation text must not be blank"
            );
        }

        addExplanation(
            ExplanationTranslationEntity.builder()
                .remembrance(this)
                .locale(locale)
                .text(text)
                .build()
        );
    }

    /// Replaces or creates a translation for the given locale.
    ///
    /// This method:
    /// - Removes any existing translation for the locale (if present)
    /// - Adds a new translation with the provided text
    ///
    /// Due to `orphanRemoval = true`, the old translation (if any) will be deleted
    /// from the database during the next flush.
    ///
    /// The new translation must have a non-blank text; otherwise, [#addTranslation(Locale,String)]
    /// will throw an exception.
    ///
    /// @param locale the locale for the translation; must not be `null`
    /// @param text   the new translation text; must not be `null` or blank
    /// @throws IllegalArgumentException if `text` is `null` or blank
    /// @see #addTranslation(Locale, String)
    /// @see #removeTranslation(Locale)
    public void replaceTranslation(Locale locale, String text) {
        removeTranslation(locale);
        addTranslation(locale, text);
    }

    /// Replaces or creates an explanation for the given locale.
    ///
    /// This method:
    /// - Removes any existing explanation for the locale (if present)
    /// - Adds a new explanation with the provided text
    ///
    /// Due to `orphanRemoval = true`, the old explanation (if any) will be deleted
    /// from the database during the next flush.
    ///
    /// The new explanation must have a non-blank text; otherwise, [#addExplanation(Locale,String)]
    /// will throw an exception.
    ///
    /// @param locale the locale for the explanation; must not be `null`
    /// @param text   the new explanation text; must not be `null` or blank
    /// @throws IllegalArgumentException if `text` is `null` or blank
    /// @see #addExplanation(Locale, String)
    /// @see #removeExplanation(Locale)
    public void replaceExplanation(Locale locale, String text) {
        removeExplanation(locale);
        addExplanation(locale, text);
    }

    /// Removes the translation for the specified locale, if present.
    ///
    /// Due to `orphanRemoval = true`, this will automatically schedule the translation
    /// for deletion from the database during the next flush.
    ///
    /// @param locale the locale to remove; must not be `null`
    public void removeTranslation(Locale locale) {
        translations.remove(locale);
    }

    /// Removes the explanation for the specified locale, if present.
    ///
    /// Due to `orphanRemoval = true`, this will automatically schedule the explanation
    /// for deletion from the database during the next flush.
    ///
    /// @param locale the locale to remove; must not be `null`
    public void removeExplanation(Locale locale) {
        explanations.remove(locale);
    }

    /// Checks whether this remembrance has a translation for the specified locale.
    ///
    /// @param locale the locale to check; must not be `null`
    /// @return `true` if a translation exists
    public boolean hasTranslation(Locale locale) {
        return translations.containsKey(locale);
    }

    /// Checks whether this remembrance has an explanation for the specified locale.
    ///
    /// @param locale the locale to check; must not be `null`
    /// @return `true` if an explanation exists
    public boolean hasExplanation(Locale locale) {
        return explanations.containsKey(locale);
    }

    /// Retrieves the translated text for the given locale, if available.
    ///
    /// @param locale the target locale; must not be `null`
    /// @return the translated text, or `null` if no translation exists
    public @Nullable String getTranslationText(Locale locale) {
        RemembranceTranslationEntity t = translations.get(locale);
        return t != null ? t.getText() : null;
    }

    /// Retrieves the explanation text for the given locale, if available.
    ///
    /// @param locale the target locale; must not be `null`
    /// @return the explanation text, or `null` if no explanation exists
    public @Nullable String getExplanationText(Locale locale) {
        ExplanationTranslationEntity e = explanations.get(locale);
        return e != null ? e.getText() : null;
    }

    /// Checks whether this remembrance is currently marked as a favorite.
    ///
    /// @return `true` if favorited; `false` otherwise
    public boolean isFavorited() {
        return favorite != null;
    }

    /// Returns the set of tag names associated with this remembrance.
    ///
    /// @return an unmodifiable set of tag names
    public Set<String> getTagNames() {
        return tags
            .stream()
            .map(TagEntity::getName)
            .collect(Collectors.toUnmodifiableSet());
    }

    /// Adds a tag to this remembrance.
    ///
    /// Maintains bidirectional consistency by also adding this remembrance to the tag's remembrance set.
    ///
    /// @param tag the tag to add; must not be `null`
    public void addTag(TagEntity tag) {
        tag.addRemembrance(this);
    }

    /// Removes a tag from this remembrance.
    ///
    /// Maintains bidirectional consistency by also removing this remembrance from the tag's remembrance set.
    ///
    /// @param tag the tag to remove; must not be `null`
    public void removeTag(TagEntity tag) {
        tag.removeRemembrance(this);
    }

    /// Marks this remembrance as a favorite.
    ///
    /// If not already favorited, creates a new [FavoriteEntity] instance and associates it with this remembrance.
    ///
    /// @return the associated `FavoriteEntity` instance
    public FavoriteEntity markFavorite() {
        if (favorite == null) {
            this.favorite = FavoriteEntity.builder().remembrance(this).build();
        }
        return favorite;
    }

    /// Removes the favorite marker from this remembrance.
    ///
    /// Due to `orphanRemoval = true`, this will delete the associated `favorite` row from the database
    /// during the next flush.
    public void unmarkFavorite() {
        if (favorite != null) {
            this.favorite = null;
        }
    }

    // Internal helpers (private) — maintain bidirectional relationships

    /// Adds a pre-built translation to this remembrance and maintains bidirectional consistency.
    ///
    /// This internal method ensures that:
    /// - The translation's `remembrance` field is set to this instance
    /// - The translation is inserted into the `translations` map under its locale key
    ///
    /// It is used exclusively by public `addTranslation` overloads and the builder.
    /// Direct external use is not intended.
    ///
    /// @param translation the translation to add; must not be `null`
    private void addTranslation(RemembranceTranslationEntity translation) {
        translation.setRemembrance(this);
        translations.put(translation.getLocale(), translation);
    }

    /// Adds a pre-built explanation to this remembrance and maintains bidirectional consistency.
    ///
    /// This internal method ensures that:
    /// - The explanation's `remembrance` field is set to this instance
    /// - The explanation is inserted into the `explanations` map under its locale key
    ///
    /// Used by public `addExplanation` methods and the builder. Not for external use.
    ///
    /// **Note:** This method does not validate text content — that is handled at the public API layer.
    ///
    /// @param translation the explanation to add; must not be `null`
    private void addExplanation(ExplanationTranslationEntity translation) {
        translation.setRemembrance(this);
        explanations.put(translation.getLocale(), translation);
    }

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
    /// .source("Sahih Muslim")
    /// .grade(DatabaseHadithGrade.SAHIH)
    /// .addTranslation(Locale.ENGLISH, "SubhanAllah")
    /// .addTranslation(Locale.forLanguageTag("ar"), "سبحان الله")
    /// .addExplanation(Locale.ENGLISH, "Glory be to God")
    /// .addTag("Morning")
    /// .addTag("Tasbih")
    /// .favorite(true)
    /// .build();
    ///```
    ///
    /// @see RemembranceEntity
    public static final class Builder {

        private final Set<Translation> explanations = new HashSet<>();
        private final Set<TagEntity> tags = new LinkedHashSet<>();
        private final Set<Translation> translations = new HashSet<>();
        private boolean isFavorite;
        private @Nullable Long id;
        private @Nullable String source;
        private DatabaseHadithGrade grade = DatabaseHadithGrade.UNSPECIFIED;

        public Builder id(@Nullable Long id) {
            this.id = id;
            return this;
        }

        /// Sets the source reference for the remembrance (e.g., "Sahih al-Bukhari").
        ///
        /// @param source the source description; may be `null` if unknown
        /// @return this builder instance
        public Builder source(@Nullable String source) {
            this.source = source;
            return this;
        }

        /// Sets the authenticity grade of the hadith.
        /// If not provided, defaults to [DatabaseHadithGrade#UNSPECIFIED].
        ///
        /// @param grade the hadith grade; must not be `null`
        /// @return this builder instance
        public Builder grade(DatabaseHadithGrade grade) {
            this.grade = grade;
            return this;
        }

        /// Marks the remembrance as favorited upon creation.
        ///
        /// If `true`, calling [#build()] will automatically call
        /// [#markFavorite()] on the constructed instance.
        ///
        /// @param isFavorite `true` to mark as favorite; `false` otherwise
        /// @return this builder instance
        public Builder favorite(boolean isFavorite) {
            this.isFavorite = isFavorite;
            return this;
        }

        /// Adds a translated zikr text for the given locale.
        ///
        /// If `text` is blank, an [IllegalArgumentException] is thrown.
        ///
        /// Since translations are stored in a [Set], multiple entries for the same locale will result in only
        /// the first one added being stored See [Set#add(Object)] for details.
        ///
        /// @param locale the target locale (e.g., `Locale.ENGLISH`); must not be `null`
        /// @param text   the translated zikr text; [IllegalArgumentException] is thrown if `text` is blank
        /// @return this builder instance
        /// @throws IllegalArgumentException if `text` is blank
        public Builder addTranslation(Locale locale, String text) {
            if (text.isBlank()) {
                throw new IllegalArgumentException(
                    "Translation text must not be blank"
                );
            }

            translations.add(new Translation(locale, text));
            return this;
        }

        /// Adds an explanation (commentary) for the given locale.
        ///
        /// If `text` is blank, an [IllegalArgumentException] is thrown.
        ///
        /// Since translations are stored in a [Set], multiple entries for the same locale will result in only
        /// the first one added being stored See [Set#add(Object)] for details.
        ///
        /// @param locale the target locale; must not be `null`
        /// @param text   the explanation text; [IllegalArgumentException] is thrown if `text` is blank
        /// @return this builder instance
        /// @throws IllegalArgumentException if `text` is blank
        public Builder addExplanation(Locale locale, String text) {
            if (text.isBlank()) {
                throw new IllegalArgumentException(
                    "Explanation text must not be blank"
                );
            }

            explanations.add(new Translation(locale, text));
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
        /// @param tagName the name of the tag (e.g., "Morning"); ignored if blank
        /// @return this builder instance
        /// @throws IllegalArgumentException if `tagName` is blank
        public Builder addTag(String tagName) {
            if (tagName.isBlank()) {
                throw new IllegalArgumentException(
                    "TagEntity name must not be blank"
                );
            }

            tags.add(TagEntity.builder().name(tagName).build());
            return this;
        }

        public Builder addTags(Collection<TagEntity> tags) {
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
            RemembranceEntity remembrance = new RemembranceEntity();
            remembrance.setId(id);
            remembrance.setSource(source);
            remembrance.setGrade(grade);

            if (isFavorite) {
                remembrance.markFavorite();
            }

            translations.forEach(t ->
                remembrance.addTranslation(t.locale, t.text)
            );
            explanations.forEach(e ->
                remembrance.addExplanation(e.locale, e.text)
            );
            tags.forEach(remembrance::addTag);

            return remembrance;
        }

        /// Internal container for holding locale-text pairs during construction.
        ///
        /// Used to collect translations and explanations before building the final entity.
        /// Ensures immutability and avoids premature entity creation.
        ///
        /// @param locale the target locale
        /// @param text   the associated text (non-blank)
        private record Translation(Locale locale, String text) {}
    }
}
