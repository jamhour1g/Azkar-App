package com.azkar.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import lombok.*;
import org.jspecify.annotations.Nullable;

/// Represents a translated explanation for a [RemembranceEntity] in a specific language.
///
/// This entity stores localized explanatory text associated with a remembrance (zikr),
/// supporting multilingual applications. Each explanation is scoped to one
/// (`remembrance`, `locale`) pair, ensuring no duplicates per language.
///
/// ### Persistence & Database Mapping
/// - **Table:** `explanation_translation`
/// - **Primary Key:** `id` (auto-incremented by SQLite)
/// - **Foreign Key:** `remembrance_id` → [RemembranceEntity#id] with `ON DELETE CASCADE`
/// - **Uniqueness:** Enforced via unique constraint on (`remembrance_id`, `locale_code`)
/// - **Text Validity:** Non-empty enforced via `CHECK (length(text) > 0)`
///
/// ### Timestamps
/// - **`created_at`:** Set automatically using SQLite's `unixepoch()` on insert.
/// - **`updated_at`:** Automatically updated via trigger `trg_et__set_updated_at`
///     when any column changes. Application code must not modify this field.
///
/// ### Indexing
/// - `idx_et__by_remembrance`: Speeds up fetching explanations by remembrance.
/// - `idx_et__by_locale`: Optimizes queries filtering by language/locale.
///
/// ### Behavioral Notes
/// - Deleting [remembrance][#remembrance] automatically removes all associated explanation
/// translations due to `ON DELETE CASCADE`.
/// - This table is **not** currently indexed for full-text search. If needed, extend
///  the `remembrance_fts` virtual table and add triggers.
///
/// ### Usage Example
/// ```java
/// ExplanationTranslationEntity explanation = ExplanationTranslationEntity.builder()
/// .remembrance(remembrance)
/// .locale(Locale.forLanguageTag("ar"))
/// .text("هذا الذكر ورد في سنن أبي داود...")
/// .build();
///```
///
/// @see RemembranceEntity
/// @see Locale
/// @see RemembranceTranslationEntity
@Entity
@Table(
    name = "explanation_translation",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_et_rem_loc",
        columnNames = { "remembrance_id", "locale_code" }
    ),
    check = @CheckConstraint(
        name = "chk_et_text_not_empty",
        constraint = "length(text) > 0"
    ),
    indexes = {
        @Index(name = "idx_et__by_remembrance", columnList = "remembrance_id"),
        @Index(name = "idx_et__by_locale", columnList = "locale_code"),
    }
)
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Required by JPA
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class ExplanationTranslationEntity {

    /// Unique identifier assigned by the database.
    ///
    /// Auto-incremented primary key (`INTEGER PRIMARY KEY AUTOINCREMENT`).
    /// Will be `null` for new (transient) instances before persistence.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @ToString.Include
    @EqualsAndHashCode.Include
    private @Nullable Long id;

    /// The parent [RemembranceEntity] this explanation belongs to.
    ///
    /// Establishes ownership and cascading delete behavior:
    /// if the remembrance is deleted, this explanation is automatically removed
    /// (`ON DELETE CASCADE`).
    ///
    /// Fetched lazily ([FetchType#LAZY]) to avoid unnecessary joins.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "remembrance_id", nullable = false)
    @Getter
    @Setter
    private RemembranceEntity remembrance;

    /// The language and regional variant (locale) of this explanation.
    ///
    /// Stored as a BCP 47 language tag (e.g., `en`, `ar-SA`) in the
    /// `locale_code` column. Case-sensitive storage, but typically normalized
    /// at the application level.
    ///
    /// Part of the unique constraint with `remembrance_id`, ensuring one
    /// explanation per language per remembrance.
    @Column(name = "locale_code", nullable = false)
    @Getter
    @ToString.Include
    @EqualsAndHashCode.Include
    private Locale locale;

    /// The translated explanation text in the target language.
    ///
    /// Must not be `null` or blank. Enforced by:
    /// - Database: `NOT NULL` + `CHECK (length(text) > 0)`
    /// - Application: Constructor requires non-blank input
    ///
    ///
    @Column(
        nullable = false,
        check = { @CheckConstraint(constraint = "length(text) > 0") }
    )
    @Getter
    @ToString.Include
    @EqualsAndHashCode.Include
    private String text;

    /// Timestamp when this record was created.
    ///
    /// Set automatically by the database using `DEFAULT (unixepoch())`.
    /// Cannot be modified by application code (`insertable = false, updatable = false`).
    @Column(
        name = "created_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    @Getter
    private @Nullable Instant createdAt;

    /// Timestamp when this record was last updated.
    ///
    /// Maintained automatically by the `trg_et__set_updated_at` trigger.
    /// Updated to the current Unix epoch on any `UPDATE` operation.
    ///
    /// Not modifiable by application logic.
    @Column(
        name = "updated_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    @Getter
    private @Nullable Instant updatedAt;

    /// Private constructor used by Lombok's `@Builder`.
    ///
    /// Initializes the core fields. The following fields are excluded:
    /// - `id` — assigned by the database
    /// - `createdAt`, `updatedAt` — managed by the database
    ///
    /// **Note:** Prefer using [builder()][#builder()] over direct constructor invocation.
    ///
    /// @param id          the unique identifier; may be `null` for transient instances
    /// @param remembrance the parent remembrance; must not be `null`
    /// @param locale      the locale of the explanation; must not be `null`
    /// @param text        the explanation content; must not be `null` or blank
    @Builder
    private ExplanationTranslationEntity(
        @Nullable Long id,
        RemembranceEntity remembrance,
        Locale locale,
        String text
    ) {
        this.remembrance = remembrance;
        this.locale = locale;

        if (text.isBlank()) {
            throw new IllegalArgumentException(
                "Explanation text must not be blank"
            );
        }

        this.text = text;
        this.id = id;
    }

    /// Creates a new instance of this explanation with updated text, preserving all other fields.
    ///
    /// Useful for safely modifying content during update operations or testing.
    /// The returned instance isn't persisted and has no `id`.
    ///
    /// @param newText the new explanation text; must not be `null` or blank
    /// @return a new `ExplanationTranslationEntity` with updated text
    /// @throws IllegalArgumentException if `newText` is blank
    public ExplanationTranslationEntity withText(String newText) {
        if (newText.isBlank()) {
            throw new IllegalArgumentException("New text must not be blank");
        }

        return ExplanationTranslationEntity.builder()
            .remembrance(this.remembrance)
            .locale(this.locale)
            .text(newText)
            .build();
    }
}
