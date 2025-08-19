package com.azkar.data.repo.jpa;

import com.azkar.data.repo.FtsSearch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;

public record FtsSearchJpa(EntityManager em) implements FtsSearch {
    @Override
    public List<Long> search(String expression) {

        Query q = em.createNativeQuery("SELECT remembrance_id FROM remembrance_fts WHERE text MATCH :expr")
                .setParameter("expr", expression);

        @SuppressWarnings("unchecked")
        List<Long> list = q.getResultStream()
                .filter(obj -> obj instanceof Number)
                .map(obj -> ((Number) obj).longValue())
                .toList();

        return list;
    }
}
