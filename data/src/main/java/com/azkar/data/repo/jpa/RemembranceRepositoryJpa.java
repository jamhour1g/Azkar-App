package com.azkar.data.repo.jpa;

import com.azkar.data.entity.FavoriteEntity;
import com.azkar.data.entity.RemembranceEntity;
import com.azkar.data.mapping.RemembranceMapper;
import com.azkar.domain.model.Remembrance;
import com.azkar.domain.repo.RemembranceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record RemembranceRepositoryJpa(EntityManager em) implements RemembranceRepository {

    @Override
    @Transactional
    public Remembrance save(Remembrance remembrance) {
        RemembranceEntity remembranceEntity = RemembranceMapper.fromRemembrance(remembrance);
        return RemembranceMapper.toRemembrance(em.merge(remembranceEntity));
    }

    @Override
    @Transactional
    public void delete(Remembrance r) {
        em.remove(em.getReference(RemembranceEntity.class, r.getId()));
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        em.remove(em.getReference(RemembranceEntity.class, id));
    }

    @Override
    public Optional<Remembrance> findById(long id) {
        RemembranceEntity remembranceEntity = em.<@Nullable RemembranceEntity>find(RemembranceEntity.class, id);

        if (remembranceEntity == null) {
            return Optional.empty();
        }

        return Optional.of(RemembranceMapper.toRemembrance(remembranceEntity));
    }

    @Override
    public List<Remembrance> findAll() {
        return em.createQuery(
                        """
                        SELECT r FROM RemembranceEntity r
                        ORDER BY r.id
                        """,
                        RemembranceEntity.class)
                .setHint("org.hibernate.readOnly", true)
                .getResultStream()
                .map(RemembranceMapper::toRemembrance)
                .toList();
    }

    @Override
    public List<Remembrance> findByTagNameIgnoreCase(String tagName) {
        return em.createQuery(
                        """
                        SELECT r FROM RemembranceEntity r
                        JOIN r.tags t
                        WHERE lower(t.name) = lower(:name)
                        ORDER BY r.id
                        """,
                        RemembranceEntity.class)
                .setParameter("name", tagName)
                .setHint("org.hibernate.readOnly", true)
                .getResultStream()
                .map(RemembranceMapper::toRemembrance)
                .toList();
    }

    @Override
    public List<Remembrance> findFavorites() {
        return em.createQuery(
                        """
                        SELECT f.remembrance FROM FavoriteEntity f
                        ORDER BY f.remembranceId
                        """,
                        RemembranceEntity.class)
                .setHint("org.hibernate.readOnly", true)
                .getResultStream()
                .map(RemembranceMapper::toRemembrance)
                .toList();
    }

    @Override
    public List<Remembrance> search(Locale locale, String expressionToSearchFor) {
        if (expressionToSearchFor.isBlank()) {
            return List.of();
        }

        // 1) Get matching IDs from the FTS table (filter by locale).
        @SuppressWarnings("unchecked")
        List<Number> idNums = em.createNativeQuery(
                        """
                        SELECT remembrance_id
                        FROM remembrance_fts
                        WHERE text MATCH :expr
                        AND locale_code = :locale
                        """)
                .setParameter("expr", expressionToSearchFor)
                .setParameter("locale", locale.toLanguageTag())
                .getResultList();

        if (idNums.isEmpty()) {
            return List.of();
        }

        // Convert to Longs, keep order as returned by FTS (ranked).
        List<Long> ids = idNums.stream().map(Number::longValue).toList();

        // 2) Load the actual entities preserving the FTS order.
        // Build an ORDER BY CASE ... to keep the FTS ranking.
        // Example:
        // ORDER BY CASE r.id
        //    WHEN :id0 THEN 0
        //    WHEN :id1 THEN 1
        //    WHEN :id2 THEN 2
        // END
        StringBuilder orderBy = new StringBuilder("CASE r.id ");
        for (int i = 0; i < ids.size(); i++) {
            orderBy.append("WHEN :id").append(i).append(" THEN ").append(i).append(' ');
        }
        orderBy.append("END");

        String jpql =
                """
                SELECT r FROM RemembranceEntity r
                WHERE r.id IN :ids
                ORDER BY
                """
                        + orderBy;

        TypedQuery<RemembranceEntity> q = em.createQuery(jpql, RemembranceEntity.class)
                .setParameter("ids", ids)
                .setHint("org.hibernate.readOnly", true);

        for (int i = 0; i < ids.size(); i++) {
            q.setParameter("id" + i, ids.get(i));
        }

        // 3) Map to domain (this will enforce that both translation and explanation
        // exist for the requested locale, per your RemembranceMapper logic).
        return q.getResultStream().map(RemembranceMapper::toRemembrance).toList();
    }

    @Override
    @Transactional
    public void markFavorite(long remembranceId) {
        RemembranceEntity r = em.getReference(RemembranceEntity.class, remembranceId);
        FavoriteEntity f = r.markFavorite();

        if (!em.contains(f)) {
            em.persist(f);
        }
    }

    @Override
    @Transactional
    public void unmarkFavorite(long remembranceId) {
        FavoriteEntity f = em.<@Nullable FavoriteEntity>find(FavoriteEntity.class, remembranceId);
        if (f != null) em.remove(f);
    }
}
