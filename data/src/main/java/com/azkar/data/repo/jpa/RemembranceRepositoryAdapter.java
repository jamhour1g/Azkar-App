package com.azkar.data.repo.jpa;

import com.azkar.data.entity.RemembranceEntity;
import com.azkar.data.mapping.RemembranceMapper;
import com.azkar.domain.exception.PersistenceFailureException;
import com.azkar.domain.exception.RemembranceNotFoundException;
import com.azkar.domain.model.Remembrance;
import com.azkar.domain.repo.RemembranceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Adapter that bridges the Jakarta Data [RemembranceDataRepository] (entity layer)
/// to the domain [RemembranceRepository] interface, using [RemembranceMapper] for conversion.
///
/// Operations that cannot be expressed purely through Jakarta Data query methods
/// (e.g., `markFavorite`/`unmarkFavorite`) fall back to direct [EntityManager] usage.
///
/// All JPA/JDBC exceptions are caught and re-thrown as domain
/// [com.azkar.domain.exception.DataAccessException] subtypes so that callers
/// never depend on persistence-layer classes.
public final class RemembranceRepositoryAdapter
    implements RemembranceRepository {

    private final RemembranceDataRepository dataRepo;
    private final EntityManager em;

    public RemembranceRepositoryAdapter(
        RemembranceDataRepository dataRepo,
        EntityManager em
    ) {
        this.dataRepo = dataRepo;
        this.em = em;
    }

    @Override
    public Remembrance save(Remembrance remembrance) {
        try {
            RemembranceEntity entity = RemembranceMapper.fromRemembrance(
                remembrance
            );
            // em.merge() is required because the entity is always detached:
            // RemembranceMapper.fromRemembrance() creates a new instance outside
            // any persistence context, even for updates with a pre-existing id.
            RemembranceEntity merged = em.merge(entity);
            return RemembranceMapper.toRemembrance(merged);
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to save remembrance",
                e
            );
        }
    }

    @Override
    public void delete(Remembrance r) {
        if (r.getId().isEmpty()) {
            return;
        }
        try {
            RemembranceEntity ref = em.getReference(
                RemembranceEntity.class,
                r.getId().get()
            );
            dataRepo.delete(ref);
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to delete remembrance id=" + r.getId().get(),
                e
            );
        }
    }

    @Override
    public void deleteById(long id) {
        try {
            em
                .createQuery("DELETE FROM RemembranceEntity r WHERE r.id = :id")
                .setParameter("id", id)
                .executeUpdate();
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to delete remembrance id=" + id,
                e
            );
        }
    }

    @Override
    public Optional<Remembrance> findById(long id) {
        try {
            return dataRepo.findById(id).map(RemembranceMapper::toRemembrance);
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to find remembrance id=" + id,
                e
            );
        }
    }

    @Override
    public List<Remembrance> findAll() {
        try {
            return dataRepo
                .findAll()
                .stream()
                .map(RemembranceMapper::toRemembrance)
                .toList();
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to find all remembrances",
                e
            );
        }
    }

    @Override
    public List<Remembrance> findByTagNameIgnoreCase(String tagName) {
        try {
            return dataRepo
                .findByTagNameIgnoreCase(tagName)
                .stream()
                .map(RemembranceMapper::toRemembrance)
                .toList();
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to find remembrances by tag '" + tagName + "'",
                e
            );
        }
    }

    @Override
    public List<Remembrance> findFavorites() {
        try {
            return dataRepo
                .findFavorites()
                .stream()
                .map(RemembranceMapper::toRemembrance)
                .toList();
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to find favorite remembrances",
                e
            );
        }
    }

    @Override
    public List<Remembrance> search(
        Locale locale,
        String expressionToSearchFor
    ) {
        if (expressionToSearchFor.isBlank()) {
            return List.of();
        }

        try {
            String pattern =
                "%" + JpqlUtils.escapeLikePattern(expressionToSearchFor) + "%";
            return dataRepo
                .searchByTranslationText(locale, pattern)
                .stream()
                .map(RemembranceMapper::toRemembrance)
                .toList();
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to search remembrances",
                e
            );
        }
    }

    @Override
    public void markFavorite(long remembranceId) {
        try {
            RemembranceEntity entity = em.find(
                RemembranceEntity.class,
                remembranceId
            );
            if (entity == null) {
                throw new RemembranceNotFoundException(remembranceId);
            }
            entity.markFavorite();
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to mark remembrance id=" +
                remembranceId +
                " as favorite",
                e
            );
        }
    }

    @Override
    public void unmarkFavorite(long remembranceId) {
        try {
            RemembranceEntity entity = em.find(
                RemembranceEntity.class,
                remembranceId
            );
            if (entity == null) {
                throw new RemembranceNotFoundException(remembranceId);
            }
            entity.unmarkFavorite();
            em.merge(entity);
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                "Failed to unmark remembrance id=" +
                remembranceId +
                " as favorite",
                e
            );
        }
    }
}
