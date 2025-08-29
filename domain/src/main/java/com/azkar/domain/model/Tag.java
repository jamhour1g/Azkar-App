package com.azkar.domain.model;

import java.time.Instant;
import java.util.Optional;

public interface Tag {
    Optional<Long> getId(); // null when tag is not saved in the database or when one wants to create a new tag

    String getName();

    Optional<Instant> getCreatedAt();
}
