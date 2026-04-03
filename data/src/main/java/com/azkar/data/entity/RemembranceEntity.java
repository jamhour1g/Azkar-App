package com.azkar.data.entity;

import com.azkar.domain.model.HadithGrade;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

/// Represents a remembrance (ذِكْر/ "zikr") — a spiritual phrase or supplication,
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
/// ### Querying with Favorites
///
/// The `remembrance_with_favorite` database view provides efficient access to remembrances
/// with an `is_favorite` flag (0 or 1), avoiding expensive joins in list displays.
///
/// SELECT * FROM remembrance_with_favorite WHERE is_favorite = 1;
///
/// ### Full-Text Search
///
/// Optional FTS5 support via `remembrance_fts`, kept in sync by triggers
/// on `remembrance_translation` inserts/updates/deletes.
///
/// ### Usage Example
///
/// ```java
/// RemembranceEntity remembrance = RemembranceEntity.builder()
/// .source("Sahih al-Bukhari")
/// .grade(HadithGrade.SAHIH)
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
@Table(name = "remembrance")
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class RemembranceEntity {

    /// Unique identifier assigned by the database.
    ///
    /// Auto-incremented primary key (`INTEGER PRIMARY KEY AUTOINCREMENT`). Will be `null` for
    /// transient
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
    /// Enforced by the database `ENUM` type.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ToString.Include
    @Getter
    @Setter
    private HadithGrade grade = HadithGrade.UNSPECIFIED;

    /// Timestamp when this remembrance was created.
    ///
    /// Managed by Hibernate's `@CreationTimestamp` — set once on initial persist.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Getter
    private @Nullable Instant createdAt;

    /// Timestamp when this remembrance was last updated.
    ///
    /// Managed by Hibernate's `@UpdateTimestamp` — refreshed on every flush/commit.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @Getter
    private @Nullable Instant updatedAt;

    /// Optional favorite marker for this remembrance.
    ///
    /// Managed via a one-to-one relationship with
    /// [favorite.remembrance][FavoriteEntity#remembrance].
    /// Uses `cascade = CascadeType.ALL, orphanRemoval = true`
    /// setting this to `null` will delete the associated `favorite` row.
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(
            name = "favorite_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_remembrance_favorite"),
            unique = true)
    @Getter
    private @Nullable FavoriteEntity favorite;

    /// Map of translations for this remembrance, keyed by locale.
    ///
    /// Each remembrance can have one translation per locale
    /// (enforced by unique constraint on `(remembrance_id,locale_code)` in
    /// `remembrance_translation`).
    ///
    /// Uses `orphanRemoval = true` — removing a translation from this map will delete it from the
    /// database on flushes.
    @OneToMany(mappedBy = "remembrance", cascade = CascadeType.ALL, orphanRemoval = true)
    @MapKey(name = "locale")
    @Getter
    private Map<Locale, RemembranceTranslationEntity> translations = new HashMap<>();

    /// Map of explanations (commentary) for this remembrance, keyed by locale.
    ///
    /// Like translations, one explanation per locale is allowed.
    ///
    /// Also uses `orphanRemoval = true` for automatic cleanup.
    @OneToMany(mappedBy = "remembrance", cascade = CascadeType.ALL, orphanRemoval = true)
    @MapKey(name = "locale")
    @Getter
    private Map<Locale, ExplanationTranslationEntity> explanations = new HashMap<>();

    /// Set of tags associated with this remembrance.
    ///
    /// Many-to-many relationship via join table `remembrance_tag`.
    /// To maintain bidirectional consistency, use [#addTag(TagEntity)] and [#removeTag(TagEntity)]
    ///
    /// Uses [LinkedHashSet] to preserve insertion order.
    @ManyToMany(
            cascade = {
                CascadeType.DETACH,
                CascadeType.MERGE,
                CascadeType.PERSIST,
                CascadeType.REFRESH,
            })
    @JoinTable(
            name = "remembrance_tag",
            joinColumns = @JoinColumn(name = "remembrance_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @Getter
    private Set<TagEntity> tags = new LinkedHashSet<>();

    /// Creates a new builder for constructing [RemembranceEntity] instances.
    ///
    /// @return a new [RemembranceEntityBuilder] instance
    public static RemembranceEntityBuilder builder() {
        return new RemembranceEntityBuilder();
    }

    /// Package-private factory used by [RemembranceEntityBuilder] to create a fresh instance.
    ///
    /// This avoids exposing the no-arg constructor beyond the entity and its builder.
    static RemembranceEntity create() {
        return new RemembranceEntity();
    }

    /// Adds a translation for the given locale.
    ///
    /// Delegates to [#addTranslation(Long, Locale, String)] with a `null` id.
    ///
    /// @param locale the locale for the translation; must not be `null`
    /// @param text   the translated text; must not be `null` or blank
    /// @throws IllegalArgumentException if `text` is blank
    public void addTranslation(Locale locale, String text) {
        addTranslation(null, locale, text);
    }

    void addTranslation(@Nullable Long id, Locale locale, String text) {
        addTranslation(RemembranceTranslationEntity.builder()
                .remembrance(this)
                .locale(locale)
                .text(text)
                .id(id)
                .build());
    }

    /// Adds an explanation for the given locale.
    ///
    /// Delegates to [#addExplanation(Long, Locale, String)] with a `null` id.
    ///
    /// @param locale the locale for the explanation; must not be `null`
    /// @param text   the explanation text; must not be `null` or blank
    /// @throws IllegalArgumentException if `text` is blank
    public void addExplanation(Locale locale, String text) {
        addExplanation(null, locale, text);
    }

    void addExplanation(@Nullable Long id, Locale locale, String text) {
        addExplanation(ExplanationTranslationEntity.builder()
                .remembrance(this)
                .locale(locale)
                .text(text)
                .id(id)
                .build());
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
        return tags.stream().map(TagEntity::getName).collect(Collectors.toUnmodifiableSet());
    }

    /// Adds a tag to this remembrance.
    ///
    /// Maintains bidirectional consistency by also adding this remembrance to the tag's remembrance
    /// set.
    ///
    /// @param tag the tag to add; must not be `null`
    public void addTag(TagEntity tag) {
        tag.addRemembrance(this);
    }

    /// Removes a tag from this remembrance.
    ///
    /// Maintains bidirectional consistency by also removing this remembrance from the tag's
    /// remembrance set.
    ///
    /// @param tag the tag to remove; must not be `null`
    public void removeTag(TagEntity tag) {
        tag.removeRemembrance(this);
    }

    /// Marks this remembrance as a favorite.
    ///
    /// If not already favorited, creates a new [FavoriteEntity] instance and associates it with
    /// this remembrance.
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
    /// Due to `orphanRemoval = true`, this will delete the associated `favorite` row from the
    /// database during the next flush.
    public void unmarkFavorite() {
        if (this.favorite != null) {
            FavoriteEntity f = this.favorite;
            this.favorite = null; // clears FK on the owner (remembrance.favorite_id)
            if (f.getRemembrance() != null) {
                f.setRemembrance(null); // keep the in-memory graph consistent
            }
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
    /// **Note:** This method does not validate text content — that is handled at the public API
    /// layer.
    ///
    /// @param translation the explanation to add; must not be `null`
    private void addExplanation(ExplanationTranslationEntity translation) {
        translation.setRemembrance(this);
        explanations.put(translation.getLocale(), translation);
    }
}
