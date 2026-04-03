package com.azkar.data.repo.jpa;

import com.azkar.data.entity.TagEntity;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Save;
import java.util.List;
import java.util.Optional;

/// Jakarta Data repository for [TagEntity].
///
/// The `hibernate-processor` annotation processor generates an implementation
/// at compile time (`TagDataRepository_`).
///
/// This interface works with **entity types only** — domain conversion is
/// handled by [TagRepositoryAdapter].
@Repository
public interface TagDataRepository {
    @Save
    TagEntity save(TagEntity entity);

    @Find
    Optional<TagEntity> findById(long id);

    @Find
    @OrderBy("id")
    List<TagEntity> findAll();

    @Delete
    void delete(TagEntity entity);

    @Query("FROM TagEntity t WHERE lower(t.name) = lower(:name)")
    Optional<TagEntity> findByNameIgnoreCase(String name);

    @Query("FROM TagEntity t WHERE lower(t.name) LIKE lower(:pattern) ESCAPE '\\'")
    List<TagEntity> findByNameContainingIgnoreCase(String pattern);
}
