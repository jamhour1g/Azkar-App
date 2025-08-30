package com.azkar.domain.model.impl;

import com.azkar.domain.model.Tag;
import java.time.Instant;
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder(toBuilder = true)
public record TagImpl(
    @Nullable Long id, // is null when tag is not saved in the database
    String name,
    @Nullable Instant createdAt
) implements Tag {
    public TagImpl {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Tag name must not blank");
        }

        name = name.trim();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Optional<Long> getId() {
        return Optional.ofNullable(id);
    }

    @Override
    public Optional<Instant> getCreatedAt() {
        return Optional.ofNullable(createdAt);
    }
}
