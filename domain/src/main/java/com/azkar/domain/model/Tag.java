package com.azkar.domain.model;

import java.time.Instant;
import java.util.Optional;

/// Represents a tag that can be associated with a [Remembrance].
///
/// A `Tag` is a simple label used for categorization, grouping,
/// or filtering. It carries a name and may optionally include metadata
/// such as a unique identifier and the time it was introduced.
///
/// The origin of a tag is left abstract: it may come from a local source,
/// an external service, or be dynamically created within the system.
public interface Tag {
    /// Returns a unique identifier for this tag, if available.
    ///
    /// The identifier can be useful for distinguishing between tags with
    /// similar names, but its presence is not guaranteed.
    ///
    /// @return an [Optional] containing the identifier, or empty if none exists.
    Optional<Long> getId();

    /// Returns the name of this tag.
    ///
    /// The name is the main label that describes the tag, such as
    /// "morning", "forgiveness", or "gratitude".
    ///
    /// @return the non-null name of the tag.
    String getName();

    /// Returns the timestamp indicating when this tag was first
    /// introduced or recorded in the system.
    ///
    /// @return an [Optional] containing the creation time, or empty if unknown.
    Optional<Instant> getCreatedAt();
}
