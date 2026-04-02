package com.azkar.data.entity;

import jakarta.persistence.*;
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
/// - **Foreign Key:** `remembrance_id` → [remembrance.id][RemembranceEntity#id] with `ON DELETE
/// CASCADE`
/// - **Uniqueness:** Enforced via unique constraint `uq_rt_rem_loc` on (`remembrance_id`,
/// `locale_code`)
/// - **Text Validity:** Non-empty enforced via `CHECK (length(text) > 0)`
///
/// ### Timestamps
/// - **`created_at`:** Set automatically using SQLite's `unixepoch()` on insert.
/// - **`updated_at`:** Automatically updated via trigger `trg_rt__set_updated_at`
/// when any column changes. Application code must not modify this field.
///
/// ### Indexing
/// - `idx_rt__by_remembrance`: Speeds up fetching translations by remembrance.
/// - `idx_rt__by_locale`: Optimizes queries filtering by language/locale (e.g., preload all
/// English).
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
///```
///
/// @see RemembranceEntity
/// @see Locale
/// @see ExplanationTranslationEntity
/// @see AbstractTranslationEntity
@Entity
@Table(
    name = "remembrance_translation",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_rt_rem_loc",
        columnNames = { "remembrance_id", "locale_code" }
    ),
    check = @CheckConstraint(
        name = "chk_rt_text_not_empty",
        constraint = "length(text) > 0"
    ),
    indexes = {
        @Index(name = "idx_rt__by_remembrance", columnList = "remembrance_id"),
        @Index(name = "idx_rt__by_locale", columnList = "locale_code"),
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemembranceTranslationEntity extends AbstractTranslationEntity {

    private static final String ENTITY_KIND = "Translation";

    /// Private constructor used by Lombok's `@Builder`.
    ///
    /// Initializes the core fields via [AbstractTranslationEntity]. The following fields
    /// are excluded:
    /// - `id` — assigned by the database
    /// - `createdAt`, `updatedAt` — managed by the database
    ///
    /// **Note:** Prefer using [#builder()] over direct constructor invocation.
    ///
    /// @param id          the unique identifier; may be `null` for transient instances
    /// @param remembrance the parent remembrance; must not be `null`
    /// @param locale      the locale of the translation; must not be `null`
    /// @param text        the translation content; must not be `null` or blank
    /// @throws IllegalArgumentException if `text` is blank
    @Builder
    private RemembranceTranslationEntity(
        @Nullable Long id,
        RemembranceEntity remembrance,
        Locale locale,
        String text
    ) {
        super(id, remembrance, locale, text, ENTITY_KIND);
    }

    /// Creates a new instance of this translation with updated text, preserving all other fields.
    ///
    /// Useful for safely modifying content during update operations or testing.
    /// The returned instance isn't persisted and has no `id`.
    ///
    /// @param newText the new translation text; must not be `null` or blank
    /// @return a new `RemembranceTranslationEntity` with updated text
    /// @throws IllegalArgumentException if `newText` is blank
    @Override
    public RemembranceTranslationEntity withText(String newText) {
        return RemembranceTranslationEntity.builder()
            .remembrance(this.getRemembrance())
            .locale(this.getLocale())
            .text(newText)
            .build();
    }
}
