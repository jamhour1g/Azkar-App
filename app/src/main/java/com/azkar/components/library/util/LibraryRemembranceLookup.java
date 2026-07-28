package com.azkar.components.library.util;

import com.azkar.domain.model.Remembrance;
import java.util.List;

public final class LibraryRemembranceLookup {

    private LibraryRemembranceLookup() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Remembrance findById(List<Remembrance> remembrances, long remembranceId) {
        return remembrances.stream()
                .filter(item -> item.getId().isPresent())
                .filter(item -> item.getId().orElse(-1L) == remembranceId)
                .findFirst()
                .orElse(null);
    }
}
