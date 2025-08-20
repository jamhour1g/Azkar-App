package com.azkar.data.repo.jpa;

import com.azkar.data.entity.Favorite;
import com.azkar.data.entity.Remembrance;
import com.azkar.data.repo.RemembranceRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record RemembranceRepositoryJpa(EntityManager em) implements RemembranceRepository {

    @Override
    @Transactional
    public Remembrance save(Remembrance remembrance) {
        return em.merge(remembrance);
    }

    @Override
    public void remove(long id) {
        em.remove(em.getReference(Remembrance.class, id));
    }

    @Override
    public Optional<Remembrance> findById(long id) {
        return Optional.ofNullable(em.<@Nullable Remembrance>find(Remembrance.class, id));
    }

    @Override
    public List<Remembrance> findAll() {
        return em.createQuery("SELECT r FROM Remembrance r ORDER BY r.id", Remembrance.class)
                .getResultList();
    }

    @Override
    public List<Remembrance> findByTagName(String tagName) {
        var q = em.createQuery(
                "SELECT r FROM Remembrance r JOIN r.tags t WHERE lower(t.name) = lower(:name) ORDER BY r.id",
                Remembrance.class);
        q.setParameter("name", tagName);

        return q.getResultList();
    }

    @Override
    public List<Remembrance> findFavorites() {
        return em.createQuery("SELECT f.remembrance FROM Favorite f ORDER BY f.remembranceId", Remembrance.class)
                .getResultList();
    }

    @Override
    public List<Remembrance> search(String expression) {
        em.createNativeQuery("SELECT remembrance_id FROM remembrance_fts WHERE text MATCH :expr")
                .setParameter("expr", expression);

        return List.of();
    }

    @Transactional
    @Override
    public void markFavorite(long remembranceId) {
        Remembrance r = em.getReference(Remembrance.class, remembranceId);
        Favorite f = r.markFavorite();

        if (!em.contains(f)) {
            em.persist(f);
        }
    }

    @Transactional
    @Override
    public void unmarkFavorite(long remembranceId) {
        Favorite f = em.<@Nullable Favorite>find(Favorite.class, remembranceId);
        if (f != null) em.remove(f); // authoritative
    }
}
