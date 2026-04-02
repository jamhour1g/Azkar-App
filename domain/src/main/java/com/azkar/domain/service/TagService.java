package com.azkar.domain.service;

import com.azkar.domain.model.Tag;
import com.azkar.domain.repo.TagRepository;
import java.util.List;
import java.util.Optional;

/// Application service that exposes [Tag] operations with
/// proper transactional boundaries.
///
/// Mutating operations (save, delete) are wrapped in a transaction
/// via [TransactionRunner]. Read-only operations delegate directly
/// to the underlying [TagRepository].
public final class TagService {

    private final TagRepository repo;
    private final TransactionRunner tx;

    public TagService(TagRepository repo, TransactionRunner tx) {
        this.repo = repo;
        this.tx = tx;
    }

    // ── Mutating (transactional) ────────────────────────────────

    public Tag save(Tag tag) {
        return tx.run(() -> repo.save(tag));
    }

    public void delete(Tag tag) {
        tx.run(() -> repo.delete(tag));
    }

    public void deleteById(Long id) {
        tx.run(() -> repo.deleteById(id));
    }

    // ── Read-only (no explicit transaction needed) ──────────────

    public Optional<Tag> findById(Long id) {
        return repo.findById(id);
    }

    public Optional<Tag> findByNameIgnoreCase(String name) {
        return repo.findByNameIgnoreCase(name);
    }

    public List<Tag> findAll() {
        return repo.findAll();
    }

    public List<Tag> findByNameContainingIgnoreCase(String name) {
        return repo.findByNameContainingIgnoreCase(name);
    }
}
