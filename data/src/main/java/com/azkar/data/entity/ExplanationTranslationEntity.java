package com.azkar.data.entity;

import jakarta.persistence.*;
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
/// @see AbstractTranslationEntity
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplanationTranslationEntity extends AbstractTranslationEntity {

    private static final String ENTITY_KIND = "Explanation";

    /// Private constructor used by Lombok's `@Builder`.
    ///
    /// Initializes the core fields via [AbstractTranslationEntity]. The following fields
    /// are excluded:
    /// - `id` — assigned by the database
    /// - `createdAt`, `updatedAt` — managed by the database
    ///
    /// **Note:** Prefer using [builder()][#builder()] over direct constructor invocation.
    ///
    /// @param id          the unique identifier; may be `null` for transient instances
    /// @param remembrance the parent remembrance; must not be `null`
    /// @param locale      the locale of the explanation; must not be `null`
    /// @param text        the explanation content; must not be `null` or blank
    /// @throws IllegalArgumentException if `text` is blank
    @Builder
    private ExplanationTranslationEntity(
        @Nullable Long id,
        RemembranceEntity remembrance,
        Locale locale,
        String text
    ) {
        super(id, remembrance, locale, text, ENTITY_KIND);
    }

    /// Creates a new instance of this explanation with updated text, preserving all other fields.
    ///
    /// Useful for safely modifying content during update operations or testing.
    /// The returned instance isn't persisted and has no `id`.
    ///
    /// @param newText the new explanation text; must not be `null` or blank
    /// @return a new `ExplanationTranslationEntity` with updated text
    /// @throws IllegalArgumentException if `newText` is blank
    @Override
    public ExplanationTranslationEntity withText(String newText) {
        return ExplanationTranslationEntity.builder()
            .remembrance(this.getRemembrance())
            .locale(this.getLocale())
            .text(newText)
            .build();
    }
}
