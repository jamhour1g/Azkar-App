package com.azkar.data.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.jspecify.annotations.Nullable;

/// Represents a user-level "favorite" marker applied to a [RemembranceEntity].
///
/// This entity models a boolean flag: a remembrance is either favorited or not.
/// It uses the remembrance's ID as both the primary key and foreign key, ensuring:
///
/// - Each remembrance can be favorited at most once (uniqueness enforced by PK)
/// - Tight coupling via shared identity
/// - Efficient lookups and storage
///
/// ### Database Mapping
/// - **Table:** `favorite`
/// - **Primary Key:** `remembrance_id` — also a foreign key to [remembrance(id)][RemembranceEntity#id]
/// - **Constraint:** `ON DELETE CASCADE` — if a [remembrance][#remembrance] is deleted, its favorite is automatically
/// removed
/// - **Implied Semantics:** Presence of a row = favorited; absence = not favorited
///
/// ### Timestamps
/// - **`created_at`:** Set automatically using SQLite's `unixepoch()` when inserted.
/// Immutable and reflects when the user first marked the item as favorite.
///
/// ### Usage Example
/// ```java
/// FavoriteEntity favorite = FavoriteEntity.builder()
/// .remembrance(remembrance)
/// .build();
/// ```
///
/// @see RemembranceEntity
@Entity
@Table(name = "favorite")
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Required by JPA
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class FavoriteEntity {

    /// The ID of the [remembrance][#remembrance] that is marked as favorite.
    ///
    /// This field serves as both:
    /// - Primary key of the `favorite` table
    /// - Foreign key to [remembrance(id)][RemembranceEntity#id]
    ///
    /// Automatically populated via [remembrance][#remembrance] using `@MapsId`.
    @Id
    @Column(name = "remembrance_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    @Getter
    private @Nullable Long remembranceId;

    /// The [remembrance][RemembranceEntity] instance that this favorite refers to.
    ///
    /// This is the owning side of the one-to-one relationship.
    /// The [MapsId][MapsId] annotation ensures that:
    ///
    /// - The favorite's ID is taken from [remembrance.id][RemembranceEntity#id]
    /// - No separate `favorite.id` column is needed
    ///
    /// Setting this to `null` breaks the association and, when combined with
    /// orphan removal in [favorite][RemembranceEntity#favorite], it will delete the row.
    @OneToOne
    @MapsId
    @JoinColumn(name = "remembrance_id", nullable = false)
    @Getter
    @Setter
    private RemembranceEntity remembrance;

    /// Timestamp when the [remembrance][#remembrance] was favorited.
    ///
    /// Set automatically by the database using `DEFAULT (unixepoch())`.
    /// Cannot be modified by application code (`insertable = false, updatable = false`).
    ///
    /// Useful for:
    /// - Displaying "favorited on" dates
    /// - Sorting favorites by recency
    /// - Audit and sync logic
    @Column(
        name = "created_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    @Getter
    private @Nullable Instant createdAt;

    /// Constructs a new favorite for the given remembrance.
    ///
    /// This constructor sets the [remembrance][#remembrance] reference only.
    /// The `remembranceId` and `createdAt` are managed by the database.
    ///
    /// **Note:** Prefer using [#builder()] over direct constructor invocation.
    ///
    /// @param remembrance the remembrance to mark as favorite; must not be `null`
    @Builder
    private FavoriteEntity(RemembranceEntity remembrance) {
        this.remembrance = remembrance;
    }

    /// Checks whether this favorite refers to the given remembrance.
    ///
    /// @param remembrance the remembrance to compare against
    /// @return `true` if this favorite is for the provided remembrance
    public boolean isFor(RemembranceEntity remembrance) {
        return this.remembrance.equals(remembrance);
    }
}
