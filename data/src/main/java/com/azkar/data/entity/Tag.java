package com.azkar.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.*;
import org.jspecify.annotations.Nullable;

/// Represents a tag entity used to categorize or label [Remembrance] instances.
///
/// Tags provide a flexible way to group and filter remembrances by theme, time of day,
/// source, or any custom label. Each tag has a unique name (case-insensitive) and
/// supports a bidirectional many-to-many relationship with remembrances.
///
/// ### Database Mapping
/// - **Table:** `tag`
/// - **Primary Key:** [id][#id] (auto-incremented)
/// - **Name Uniqueness:** Enforced via unique index `uq_tag__name_nocase`
/// using `COLLATE NOCASE`, preventing duplicates like "Morning" vs "morning"
/// - **`created_at`:** Set automatically using SQLite's `unixepoch()`
///
/// ### Relationships
/// - **Many-to-Many:** With [Remembrance] via join table `remembrance_tag`
/// - **Inverse Side:** Managed by [tags][Remembrance#tags]
///
/// ### Usage Example
/// ```java
/// Tag tag = Tag.builder()
/// .name("Spiritual")
/// .addRemembrance(remembrance1)
/// .addRemembrance(remembrance2)
/// .build();
/// ```
///
/// @see Remembrance
@Entity
@Table(name = "tag", uniqueConstraints = @UniqueConstraint(name = "uq_tag__name_nocase", columnNames = "name"))
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Tag {

    /// Unique identifier assigned by the database.
    ///
    /// Auto-incremented primary key (`INTEGER PRIMARY KEY AUTOINCREMENT`).
    /// Will be `null` for new (transient) instances before persistence.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    @Getter
    private @Nullable Long id;

    /// The name of the tag, which is mandatory and used for display and categorization.
    ///
    /// This field cannot be `null` or blank. It is:
    /// - Enforced as `NOT NULL` at the database level
    /// - Unique (case-insensitive) via index `uq_tag__name_nocase` with `COLLATE NOCASE`
    /// - Included in `equals` and `toString` calculations
    ///
    /// **Note:** Attempting to persist a duplicate name (case-insensitive) will
    /// cause a database constraint violation.
    @ToString.Include
    @EqualsAndHashCode.Include
    @Column(nullable = false)
    @Getter
    private String name;

    /// Timestamp when this tag was created.
    ///
    /// Set automatically by the database using `DEFAULT (unixepoch())`.
    /// Cannot be modified by application code (`insertable = false, updatable = false`).
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Getter
    private @Nullable Instant createdAt;

    /// The set of remembrances associated with this tag.
    ///
    /// This is the inverse side of a many-to-many relationship with [Remembrance].
    /// The relationship is managed through the `remembrance_tag` join table.
    ///
    /// Uses a [LinkedHashSet] to preserve insertion order and avoid duplicates.
    @ManyToMany(mappedBy = "tags")
    @Getter
    private Set<Remembrance> remembrances = new LinkedHashSet<>();

    /// Creates a new instance of the [Builder] to construct a `Tag` object.
    ///
    /// @return a new `Builder` instance
    public static Builder builder() {
        return new Builder();
    }

    /// Associates a remembrance with this tag and updates the remembrance to include this tag.
    ///
    /// This method maintains the integrity of the bidirectional many-to-many relationship
    /// by adding this tag to the given remembrance's tag set, and vice versa.
    ///
    ///
    /// Both sides of the relationship are updated:
    /// - This tag is added to `remembrance.getTags()`
    /// - The remembrance is added to this tag's `remembrances` set
    ///
    /// @param remembrance the remembrance to associate with this tag.
    public void addRemembrance(Remembrance remembrance) {
        remembrance.getTags().add(this);
        remembrances.add(remembrance);
    }

    /// Disassociates a remembrance from this tag and updates the remembrance to remove this tag.
    ///
    /// This method maintains the integrity of the bidirectional many-to-many relationship
    /// by removing this tag from the given remembrance's tag set, and vice versa.
    ///
    ///
    /// Both sides of the relationship are updated:
    /// - This tag is removed from `remembrance.getTags()`
    /// - The remembrance is removed from this tag's `remembrances` set
    ///
    /// @param remembrance the remembrance to disassociate from this tag.
    public void removeRemembrance(Remembrance remembrance) {
        remembrance.getTags().remove(this);
        remembrances.remove(remembrance);
    }

    /// Checks whether this tag is associated with the given remembrance.
    ///
    /// @param remembrance the remembrance to check
    /// @return `true` if associated; `false` otherwise
    public boolean hasRemembrance(Remembrance remembrance) {
        return remembrances.contains(remembrance);
    }

    /// Returns the number of remembrances tagged with this tag.
    ///
    /// @return the count of associated remembrances
    public int getRemembranceCount() {
        return remembrances.size();
    }

    /// Builder pattern implementation for creating [Tag] instances.
    ///
    /// Ensures required fields (like `name`) are set before building.
    /// Allows optional addition of associated remembrances during construction.
    ///
    public static class Builder {
        private final Set<Remembrance> remembrances = new LinkedHashSet<>();
        private String name = "";

        /// Sets the name of the tag.
        ///
        /// @param name the name to assign; must not be `null`
        /// @return this builder instance
        public Builder name(String name) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("Tag name must not blank");
            }

            this.name = name;
            return this;
        }

        /// Adds a remembrance to be linked with the tag upon creation.
        ///
        /// @param remembrance the remembrance to associate; must not be `null`
        /// @return this builder instance
        public Builder addRemembrance(Remembrance remembrance) {
            remembrances.add(remembrance);
            return this;
        }

        /// Constructs and returns a fully initialized [Tag] instance.
        ///
        /// The returned tag will have the specified name and all added remembrances.
        /// The actual persistence of relationships depends on the owning side ([Remembrance]).
        ///
        /// @return a new `Tag` instance
        public Tag build() {
            Tag tag = new Tag();
            tag.name = name;
            remembrances.forEach(tag::addRemembrance);
            return tag;
        }
    }
}
