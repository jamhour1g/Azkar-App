package com.azkar.components.library.service;

import com.azkar.components.library.model.LibraryCollectionOption;
import com.azkar.components.library.model.LibrarySortOption;
import com.azkar.domain.model.Remembrance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class LibraryFilteringService {

    private final Function<Remembrance, String> primaryCategoryProvider;
    private final Function<Remembrance, String> primaryTextProvider;

    public LibraryFilteringService(
            Function<Remembrance, String> primaryCategoryProvider, Function<Remembrance, String> primaryTextProvider) {
        this.primaryCategoryProvider = primaryCategoryProvider;
        this.primaryTextProvider = primaryTextProvider;
    }

    public List<Remembrance> apply(
            List<Remembrance> allRemembrances,
            String query,
            boolean favoritesOnly,
            LibraryCollectionOption selectedCollection,
            Predicate<Remembrance> tabMatcher,
            LibrarySortOption selectedSort) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim().toLowerCase(Locale.ROOT);
        List<Remembrance> visible = new ArrayList<>(allRemembrances.stream()
                .filter(tabMatcher)
                .filter(item -> matchesCollection(item, selectedCollection))
                .filter(item -> !favoritesOnly || item.isFavorite())
                .filter(item -> matchesQuery(item, normalizedQuery))
                .toList());

        visible.sort(comparatorFor(selectedSort));
        return List.copyOf(visible);
    }

    private Comparator<Remembrance> comparatorFor(LibrarySortOption selectedSort) {
        if (selectedSort.isCategory()) {
            return Comparator.comparing(primaryCategoryProvider, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(item -> item.getId().orElse(Long.MAX_VALUE));
        }
        if (selectedSort.isSource()) {
            return Comparator.comparing(
                            (Remembrance item) -> item.getSource().orElse(""), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(item -> item.getId().orElse(Long.MAX_VALUE));
        }
        if (selectedSort.isText()) {
            return Comparator.comparing(primaryTextProvider, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(item -> item.getId().orElse(Long.MAX_VALUE));
        }

        return Comparator.comparingLong((Remembrance item) -> item.getId().orElse(Long.MAX_VALUE))
                .reversed();
    }

    private boolean matchesCollection(Remembrance remembrance, LibraryCollectionOption selectedCollection) {
        if (selectedCollection.isAllCollections()) {
            return true;
        }
        if (selectedCollection.isFavorites()) {
            return remembrance.isFavorite();
        }
        if (selectedCollection.isUncategorized()) {
            return remembrance.getTags().isEmpty();
        }
        if (!selectedCollection.isTagCollection()) {
            return false;
        }

        String tagName = selectedCollection.tagNameKey();
        return remembrance.getTags().stream().anyMatch(tag -> tag.getName().equalsIgnoreCase(tagName));
    }

    private boolean matchesQuery(Remembrance remembrance, String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return true;
        }

        if (remembrance.getSource().orElse("").toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }

        return remembrance.getTranslations().values().stream()
                .map(Remembrance.Translations::translationPair)
                .map(Remembrance.Translations.Pair::text)
                .map(text -> text.toLowerCase(Locale.ROOT))
                .anyMatch(text -> text.contains(normalizedQuery));
    }
}
