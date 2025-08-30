package com.azkar.data.repo.jpa;

import com.azkar.data.entity.TagEntity;
import com.azkar.data.mapping.TagMapper;
import com.azkar.domain.model.Tag;
import com.azkar.domain.repo.TagRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public record TagRepositoryJpa(EntityManager em) implements TagRepository {

    @Override
    @Transactional
    public void delete(Tag tag) {
        em.remove(em.getReference(TagEntity.class, tag.getId()));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        em.remove(em.getReference(TagEntity.class, id));
    }

    @Override
    @Transactional
    public Tag save(Tag tag) {
        TagEntity tagEntity = TagMapper.fromTag(tag);
        return TagMapper.toTag(em.merge(tagEntity));
    }

    @Override
    public Optional<Tag> findById(Long id) {
        return Optional.ofNullable(em.<@Nullable TagEntity>find(TagEntity.class, id))
                .map(TagMapper::toTag);
    }

    @Override
    public Optional<Tag> findByNameIgnoreCase(String name) {
        TypedQuery<@Nullable TagEntity> query = em.createQuery(
                        """
                        SELECT t FROM TagEntity t
                        WHERE lower(t.name) = lower(:name)
                        """,
                        TagEntity.class)
                .setParameter("name", name)
                .setHint("org.hibernate.readOnly", true);

        TagEntity tagEntity = query.getSingleResultOrNull();

        return Optional.ofNullable(tagEntity).map(TagMapper::toTag);
    }

    @Override
    public List<Tag> findAll() {

        return em.createQuery(
                        """
                        SELECT t FROM TagEntity t
                        ORDER BY t.id
                        """,
                        TagEntity.class)
                .setHint("org.hibernate.readOnly", true)
                .getResultStream()
                .map(TagMapper::toTag)
                .toList();
    }

    @Override
    public List<Tag> findByNameContainingIgnoreCase(String name) {
        return em.createQuery(
                        """
                        SELECT t FROM TagEntity t
                        WHERE lower(t.name) LIKE lower(:name)
                        """,
                        TagEntity.class)
                .setParameter("name", "%" + name + "%")
                .setHint("org.hibernate.readOnly", true)
                .getResultStream()
                .map(TagMapper::toTag)
                .toList();
    }
}
