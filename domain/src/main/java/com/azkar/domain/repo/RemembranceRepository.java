package com.azkar.domain.repo;

import com.azkar.domain.model.Remembrance;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface RemembranceRepository {

    Remembrance save(Remembrance r);

    void delete(Remembrance r);

    void deleteById(long id);

    Optional<Remembrance> findById(long id);

    List<Remembrance> findAll();

    List<Remembrance> findByTagNameIgnoreCase(String tagName);

    List<Remembrance> findFavorites();

    List<Remembrance> search(Locale locale, String expressionToSearchFor);

    void markFavorite(long remembranceId);

    void unmarkFavorite(long remembranceId);
}
