package com.azkar.data.repo.jpa;

import com.azkar.data.entity.TagEntity;
import com.azkar.data.mapping.TagMapper;
import com.azkar.domain.exception.PersistenceFailureException;
import com.azkar.domain.model.Tag;
import com.azkar.domain.repo.TagRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;

/// Adapter that bridges the Jakarta Data [TagDataRepository] (entity layer)
/// to the domain [TagRepository] interface, using [TagMapper] for conversion.
///
/// All JPA/JDBC exceptions are caught and re-thrown as domain
/// [com.azkar.domain.exception.DataAccessException] subtypes so that callers
/// never depend on persistence-layer classes.
public final class TagRepositoryAdapter implements TagRepository {

    private final TagDataRepository dataRepo;
    private final EntityManager em;

    public TagRepositoryAdapter(TagDataRepository dataRepo, EntityManager em) {
        this.dataRepo = dataRepo;
        this.em = em;
    }

    @Override
    public void delete(Tag tag) {
        if (tag.getId().isEmpty()) {
            return;
        }
        try {
            TagEntity ref = em.getReference(TagEntity.class, tag.getId().get());
            dataRepo.delete(ref);
        } catch (PersistenceException e) {
            throw new PersistenceFailureException(
                    "Failed to delete tag id=" + tag.getId().get(), e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try {
            em.createQuery("DELETE FROM TagEntity t WHERE t.id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
        } catch (PersistenceException e) {
            throw new PersistenceFailureException("Failed to delete tag id=" + id, e);
        }
    }

    @Override
    public Tag save(Tag tag) {
        try {
            TagEntity entity = TagMapper.fromTag(tag);
            // em.merge() is required because the entity is always detached:
            // TagMapper.fromTag() creates a new instance outside any persistence
            // context, even for updates with a pre-existing id.
            TagEntity merged = em.merge(entity);
            return TagMapper.toTag(merged);
        } catch (PersistenceException e) {
            throw new PersistenceFailureException("Failed to save tag", e);
        }
    }

    @Override
    public Optional<Tag> findById(Long id) {
        try {
            return dataRepo.findById(id).map(TagMapper::toTag);
        } catch (PersistenceException e) {
            throw new PersistenceFailureException("Failed to find tag id=" + id, e);
        }
    }

    @Override
    public Optional<Tag> findByNameIgnoreCase(String name) {
        try {
            return dataRepo.findByNameIgnoreCase(name).map(TagMapper::toTag);
        } catch (PersistenceException e) {
            throw new PersistenceFailureException("Failed to find tag by name '" + name + "'", e);
        }
    }

    @Override
    public List<Tag> findAll() {
        try {
            return dataRepo.findAll().stream().map(TagMapper::toTag).toList();
        } catch (PersistenceException e) {
            throw new PersistenceFailureException("Failed to find all tags", e);
        }
    }

    @Override
    public List<Tag> findByNameContainingIgnoreCase(String name) {
        try {
            String pattern = "%" + JpqlUtils.escapeLikePattern(name) + "%";
            return dataRepo.findByNameContainingIgnoreCase(pattern).stream()
                    .map(TagMapper::toTag)
                    .toList();
        } catch (PersistenceException e) {
            throw new PersistenceFailureException("Failed to search tags by name '" + name + "'", e);
        }
    }
}
