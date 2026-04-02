package com.azkar.domain.service;

import com.azkar.domain.model.Remembrance;
import com.azkar.domain.repo.RemembranceRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Application service that exposes [Remembrance] operations with
/// proper transactional boundaries.
///
/// Mutating operations (save, delete, mark/unmark favorite) are wrapped
/// in a transaction via [TransactionRunner]. Read-only operations
/// delegate directly to the underlying [RemembranceRepository].
public final class RemembranceService {

    private final RemembranceRepository repo;
    private final TransactionRunner tx;

    public RemembranceService(
        RemembranceRepository repo,
        TransactionRunner tx
    ) {
        this.repo = repo;
        this.tx = tx;
    }

    // ── Mutating (transactional) ────────────────────────────────

    public Remembrance save(Remembrance remembrance) {
        return tx.run(() -> repo.save(remembrance));
    }

    public void delete(Remembrance remembrance) {
        tx.run(() -> repo.delete(remembrance));
    }

    public void deleteById(long id) {
        tx.run(() -> repo.deleteById(id));
    }

    public void markFavorite(long remembranceId) {
        tx.run(() -> repo.markFavorite(remembranceId));
    }

    public void unmarkFavorite(long remembranceId) {
        tx.run(() -> repo.unmarkFavorite(remembranceId));
    }

    // ── Read-only (no explicit transaction needed) ──────────────

    public Optional<Remembrance> findById(long id) {
        return repo.findById(id);
    }

    public List<Remembrance> findAll() {
        return repo.findAll();
    }

    public List<Remembrance> findByTagNameIgnoreCase(String tagName) {
        return repo.findByTagNameIgnoreCase(tagName);
    }

    public List<Remembrance> findFavorites() {
        return repo.findFavorites();
    }

    public List<Remembrance> search(
        Locale locale,
        String expressionToSearchFor
    ) {
        return repo.search(locale, expressionToSearchFor);
    }
}
