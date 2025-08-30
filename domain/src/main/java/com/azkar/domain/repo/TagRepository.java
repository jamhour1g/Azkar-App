package com.azkar.domain.repo;

import com.azkar.domain.model.Tag;
import java.util.List;
import java.util.Optional;

public interface TagRepository {
    void delete(Tag tag);

    void deleteById(Long id);

    Tag save(Tag tag);

    Optional<Tag> findById(Long id);

    Optional<Tag> findByNameIgnoreCase(String name);

    List<Tag> findAll();

    List<Tag> findByNameContainingIgnoreCase(String name);
}
