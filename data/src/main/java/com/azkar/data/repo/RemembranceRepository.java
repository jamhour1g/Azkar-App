package com.azkar.data.repo;

import com.azkar.data.entity.Remembrance;
import java.util.List;
import java.util.Optional;

public interface RemembranceRepository {

    Remembrance save(Remembrance r);

    void remove(long id);

    Optional<Remembrance> findById(long id);

    List<Remembrance> findAll();

    List<Remembrance> findByTagName(String tagName);

    List<Remembrance> findFavorites();

    List<Remembrance> search(String expression);

    void markFavorite(long remembranceId);

    void unmarkFavorite(long remembranceId);
}
