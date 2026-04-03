package com.azkar.domain.model.impl;

import com.azkar.domain.model.Tag;
import java.time.Instant;
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/// Default immutable implementation of the [Tag] interface.
///
/// A `TagImpl` is a simple label used to categorize or group
/// [com.azkar.domain.model.Remembrance] entries. It provides a name
/// and may optionally include an identifier and a creation timestamp.
///
/// Instances are immutable and validated upon creation. The tag name is
/// automatically trimmed and must not be blank.
///
/// @param id        an optional unique identifier for the tag may be `null`
/// @param name      the name of the tag; must not be blank, leading and trailing
///                                                                     whitespace will be trimmed
/// @param createdAt timestamp indicating when the tag was first introduced
///                                                                     or recorded; may be `null`
@Builder(toBuilder = true)
public record TagImpl(
        @Nullable Long id, String name, @Nullable Instant createdAt) implements Tag {
    /// Compact constructor that validates and normalizes the tag name.
    ///
    /// @param id        the optional identifier of the tag may be `null`
    /// @param name      the name of the tag must not be blank
    /// @param createdAt the creation timestamp, may be `null`
    /// @throws IllegalArgumentException if `name` is blank
    public TagImpl {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Tag name must not blank");
        }
        name = name.trim();
    }

    /// {@inheritDoc}
    @Override
    public String getName() {
        return name;
    }

    /// {@inheritDoc}
    @Override
    public Optional<Long> getId() {
        return Optional.ofNullable(id);
    }

    /// {@inheritDoc}
    @Override
    public Optional<Instant> getCreatedAt() {
        return Optional.ofNullable(createdAt);
    }
}
