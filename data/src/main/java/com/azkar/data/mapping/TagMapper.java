package com.azkar.data.mapping;

import com.azkar.data.entity.TagEntity;
import com.azkar.domain.model.Tag;
import com.azkar.domain.model.impl.TagImpl;

/// Utility class for mapping between persistence [TagEntity] and
/// domain-level [Tag].
///
/// Responsibilities:
/// - Convert a JPA [TagEntity] into an immutable domain [Tag].
/// - Convert a domain [Tag] back into a [TagEntity] for persistence.
///
/// @see TagEntity
/// @see Tag
/// @see TagImpl
public final class TagMapper {

    private TagMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /// Converts a persistence [TagEntity] into a domain [Tag].
    ///
    /// Maps simple scalar fields:
    /// - [TagEntity.id][TagEntity#id] → [Tag#getId()]
    /// - [TagEntity.name][TagEntity#name] → [Tag#getName()]
    /// - [TagEntity.createdAt][TagEntity#createdAt] → [Tag#getCreatedAt()]
    ///
    /// @param tagEntity the JPA entity to convert
    /// @return an immutable domain [Tag]
    public static Tag toTag(TagEntity tagEntity) {
        return TagImpl.builder()
                .id(tagEntity.getId())
                .name(tagEntity.getName())
                .createdAt(tagEntity.getCreatedAt())
                .build();
    }

    /// Converts a domain [Tag] into persistence [TagEntity].
    ///
    /// Notes:
    /// - If the domain `id` is empty, `null` is passed to
    /// the builder to allow database auto-generation.
    /// - `createdAt` is omitted: the database column defaults to
    /// `unixepoch()` at insertion time.
    ///
    /// @param tag the domain model to convert
    /// @return a new [TagEntity] suitable for persistence
    public static TagEntity fromTag(Tag tag) {
        return TagEntity.builder()
                .id(tag.getId().orElse(null))
                .name(tag.getName())
                .build();
    }
}
