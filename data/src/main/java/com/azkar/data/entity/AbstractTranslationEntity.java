package com.azkar.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

/// Abstract base class for translation entities associated with a [RemembranceEntity].
///
/// Provides the common schema shared by both [RemembranceTranslationEntity]
/// (zikr text translations) and [ExplanationTranslationEntity] (explanatory commentary).
///
/// ### Shared Fields
/// - [id][#id] — auto-incremented primary key
/// - [remembrance][#remembrance] — owning `RemembranceEntity` (lazy, cascade delete)
/// - [locale][#locale] — BCP 47 language tag stored in `locale_code`
/// - [text][#text] — the translated content (non-blank, enforced by DB CHECK and constructor)
/// - [createdAt][#createdAt] / [updatedAt][#updatedAt] — managed by Hibernate timestamps
///
/// ### Subclass Responsibilities
/// Concrete subclasses must:
/// - Declare their own `@Entity` and `@Table` with table-specific constraint names and indexes
/// - Provide a builder / factory that calls [#AbstractTranslationEntity(Long, RemembranceEntity, Locale, String,
// String)]
///   with an appropriate `entityKind` label for error messages
/// - Implement `withText(String)` to create a copy with new text
///
/// @see RemembranceTranslationEntity
/// @see ExplanationTranslationEntity
/// @see RemembranceEntity
@MappedSuperclass
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public abstract class AbstractTranslationEntity {

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
    /// if the remembrance is deleted, this translation is automatically removed
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

    /// The translated text in the target language.
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
    /// Managed by Hibernate's `@CreationTimestamp` — set once on initial persist.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Getter
    private @Nullable Instant createdAt;

    /// Timestamp when this record was last updated.
    ///
    /// Managed by Hibernate's `@UpdateTimestamp` — refreshed on every flush/commit.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @Getter
    private @Nullable Instant updatedAt;

    /// Initializes the shared fields for a translation entity.
    ///
    /// @param id          the unique identifier; may be `null` for transient instances
    /// @param remembrance the parent remembrance; must not be `null`
    /// @param locale      the locale of the translation; must not be `null`
    /// @param text        the translation content; must not be `null` or blank
    /// @param entityKind  a human-readable label (e.g., "Translation", "Explanation")
    ///                    used in error messages when validation fails
    /// @throws IllegalArgumentException if `text` is blank
    protected AbstractTranslationEntity(
            @Nullable Long id, RemembranceEntity remembrance, Locale locale, String text, String entityKind) {
        this.remembrance = remembrance;
        this.locale = locale;

        if (text.isBlank()) {
            throw new IllegalArgumentException(entityKind + " text must not be blank");
        }

        this.text = text;
        this.id = id;
    }

    /// Creates a new instance of this translation with updated text, preserving all other fields.
    ///
    /// Subclasses must implement this to return their own concrete type.
    ///
    /// @param newText the new text; must not be `null` or blank
    /// @return a new instance of the concrete translation entity with updated text
    /// @throws IllegalArgumentException if `newText` is blank
    public abstract AbstractTranslationEntity withText(String newText);
}
