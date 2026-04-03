package com.azkar.data.repo.jpa;

import com.azkar.data.entity.RemembranceEntity;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Save;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Jakarta Data repository for [RemembranceEntity].
///
/// The `hibernate-processor` annotation processor generates an implementation
/// at compile time (`RemembranceDataRepository_`).
///
/// This interface works with **entity types only** — domain conversion is
/// handled by [RemembranceRepositoryAdapter].
///
/// **Note:** All read queries use explicit `JOIN FETCH` clauses to eagerly
/// load associations (`translations`, `explanations`, `tags`, `favorite`).
/// This is required because the generated implementation uses a
/// [org.hibernate.StatelessSession] which does **not** support lazy loading.
/// The `JOIN FETCH` blocks appear duplicated across queries because Jakarta Data's
/// `@Query` annotation requires string literals — JPQL fragments cannot be
/// extracted into Java constants or shared query building methods.
@Repository
public interface RemembranceDataRepository {
    @Save
    RemembranceEntity save(RemembranceEntity entity);

    @Query("""
        SELECT DISTINCT r FROM RemembranceEntity r
        LEFT JOIN FETCH r.translations
        LEFT JOIN FETCH r.explanations
        LEFT JOIN FETCH r.tags
        LEFT JOIN FETCH r.favorite
        WHERE r.id = :id
        """)
    Optional<RemembranceEntity> findById(long id);

    @Query("""
        SELECT DISTINCT r FROM RemembranceEntity r
        LEFT JOIN FETCH r.translations
        LEFT JOIN FETCH r.explanations
        LEFT JOIN FETCH r.tags
        LEFT JOIN FETCH r.favorite
        ORDER BY r.id
        """)
    List<RemembranceEntity> findAll();

    @Delete
    void delete(RemembranceEntity entity);

    @Query("""
        SELECT DISTINCT r FROM RemembranceEntity r
        LEFT JOIN FETCH r.translations
        LEFT JOIN FETCH r.explanations
        LEFT JOIN FETCH r.favorite
        JOIN FETCH r.tags t
        WHERE lower(t.name) = lower(:name)
        ORDER BY r.id
        """)
    List<RemembranceEntity> findByTagNameIgnoreCase(String name);

    @Query("""
        SELECT DISTINCT r FROM RemembranceEntity r
        LEFT JOIN FETCH r.translations
        LEFT JOIN FETCH r.explanations
        LEFT JOIN FETCH r.tags
        JOIN FETCH r.favorite
        ORDER BY r.id
        """)
    List<RemembranceEntity> findFavorites();

    @Query("""
        SELECT DISTINCT r FROM RemembranceEntity r
        LEFT JOIN FETCH r.translations
        LEFT JOIN FETCH r.explanations
        LEFT JOIN FETCH r.tags
        LEFT JOIN FETCH r.favorite
        WHERE r.id IN (
            SELECT rt.remembrance.id FROM RemembranceTranslationEntity rt
            WHERE rt.locale = :locale AND lower(rt.text) LIKE lower(:pattern) ESCAPE '\\'
        )
        ORDER BY r.id
        """)
    List<RemembranceEntity> searchByTranslationText(Locale locale, String pattern);
}
