package com.azkar.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import lombok.*;
import org.jspecify.annotations.Nullable;

/// Represents a translated text of a [RemembranceEntity] (zikr) in a specific language.
///
/// This entity stores the localized version of the core remembrance phrase,
/// enabling multilingual display of zikr content. Each translation is scoped to one
/// (`remembrance`, `locale`) pair, ensuring no duplicates per language.
///
/// ### Persistence & Database Mapping
/// - **Table:** `remembrance_translation`
/// - **Primary Key:** [id][#id] (auto-incremented by SQLite)
/// - **Foreign Key:** `remembrance_id` → [remembrance.id][RemembranceEntity#id] with `ON DELETE CASCADE`
/// - **Uniqueness:** Enforced via unique constraint `uq_rt_rem_loc` on (`remembrance_id`, `locale_code`)
/// - **Text Validity:** Non-empty enforced via `CHECK (length(text) > 0)`
///
/// ### Timestamps
/// - **`created_at`:** Set automatically using SQLite's `unixepoch()` on insert.
/// - **`updated_at`:** Automatically updated via trigger `trg_rt__set_updated_at`
/// when any column changes. Application code must not modify this field.
///
/// ### Indexing
/// - `idx_rt__by_remembrance`: Speeds up fetching translations by remembrance.
/// - `idx_rt__by_locale`: Optimizes queries filtering by language/locale (e.g., preload all English).
///
/// ### Behavioral Notes
/// - Deleting [remembrance][#remembrance] automatically removes all associated translations
/// due to `ON DELETE CASCADE`.
/// - This table is integrated with FTS5 search via triggers on the `remembrance_fts` virtual table.
///
/// ### Usage Example
/// ```java
/// RemembranceTranslationEntity translation = RemembranceTranslationEntity.builder()
/// .remembrance(remembrance)
/// .locale(Locale.forLanguageTag("ar"))
/// .text("سبحان الله")
/// .build();
/// ```
///
/// @see RemembranceEntity
/// @see Locale
/// @see ExplanationTranslationEntity
@Entity
@Table(
        name = "remembrance_translation",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_rt_rem_loc",
                        columnNames = {"remembrance_id", "locale_code"}),
        check = @CheckConstraint(name = "chk_et_text_not_empty", constraint = "length(text) > 0"),
        indexes = {
            @Index(name = "idx_rt__by_remembrance", columnList = "remembrance_id"),
            @Index(name = "idx_rt__by_locale", columnList = "locale_code")
        })
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class RemembranceTranslationEntity {

    /// Unique identifier assigned by the database.
    ///
    /// Auto-incremented primary key (`INTEGER PRIMARY KEY AUTOINCREMENT`).
    /// Will be `null` for new (transient) instances before persistence.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @EqualsAndHashCode.Include
    @ToString.Include
    private @Nullable Long id;

    /// The parent [RemembranceEntity] this translation belongs to.
    ///
    /// Establishes ownership and cascading delete behavior:
    /// if the [remembrance][#remembrance] is deleted, this translation is automatically removed
    /// (`ON DELETE CASCADE`).
    ///
    /// Fetched lazily ([FetchType#LAZY]) to avoid unnecessary joins.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "remembrance_id", nullable = false)
    @Getter
    @Setter
    private RemembranceEntity remembrance;

    /// The language and regional variant (locale) of this translation.
    ///
    /// Stored as a BCP 47 language tag (e.g., `en`, `ar-SA`) in the
    /// `locale_code` column. Case-sensitive storage, but typically normalized
    /// at the application level.
    ///
    /// Part of the unique constraint with `remembrance_id`, ensuring one
    /// translation per language per remembrance.
    @Column(name = "locale_code", nullable = false)
    @Getter
    @EqualsAndHashCode.Include
    @ToString.Include
    private Locale locale;

    /// The translated zikr text in the target language.
    ///
    /// Must not be `null` or blank. Enforced by:
    /// - Database: `NOT NULL` + `CHECK (length(text) > 0)`
    /// - Application: Constructor requires non-blank input
    @Column(
            nullable = false,
            check = {@CheckConstraint(constraint = "length(text) > 0")})
    @Getter
    @EqualsAndHashCode.Include
    @ToString.Include
    private String text;

    /// Timestamp when this record was created.
    ///
    /// Set automatically by the database using `DEFAULT (unixepoch())`.
    /// Cannot be modified by application code (`insertable = false, updatable = false`).
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Getter
    private @Nullable Instant createdAt;

    /// Timestamp when this record was last updated.
    ///
    /// Maintained automatically by the `trg_rt__set_updated_at` trigger.
    /// Updated to the current Unix epoch on any `UPDATE` operation.
    ///
    /// Not modifiable by application logic.
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @Getter
    private @Nullable Instant updatedAt;

    /// Private constructor used by Lombok's `@Builder`.
    ///
    /// Initializes the core fields. The following fields are excluded:
    /// - `id` — assigned by the database
    /// - `createdAt`, `updatedAt` — managed by the database
    ///
    /// **Note:** Prefer using [#builder()] over direct constructor invocation.
    ///
    /// @param remembrance the parent remembrance; must not be `null`
    /// @param locale      the locale of the translation; must not be `null`
    /// @param text        the translation content; must not be `null` or blank
    /// @throws IllegalArgumentException if `text` is blank
    @Builder
    private RemembranceTranslationEntity(RemembranceEntity remembrance, Locale locale, String text) {
        this.remembrance = remembrance;
        this.locale = locale;

        if (text.isBlank()) {
            throw new IllegalArgumentException("Explanation text must not be blank");
        }

        this.text = text;
    }

    /// Creates a new instance of this translation with updated text, preserving all other fields.
    ///
    /// Useful for safely modifying content during update operations or testing.
    /// The returned instance isn't persisted and has no `id`.
    ///
    /// @param newText the new translation text; must not be `null` or blank
    /// @return a new `RemembranceTranslationEntity` with updated text
    /// @throws IllegalArgumentException if `newText` is blank
    public RemembranceTranslationEntity withText(String newText) {

        if (newText.isBlank()) {
            throw new IllegalArgumentException("Explanation text must not be blank");
        }

        return RemembranceTranslationEntity.builder()
                .remembrance(this.remembrance)
                .locale(this.locale)
                .text(newText)
                .build();
    }
}
