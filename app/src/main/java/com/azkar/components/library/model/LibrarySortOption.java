package com.azkar.components.library.model;

import static com.azkar.components.library.util.LibraryKeys.SORT_CATEGORY_KEY;
import static com.azkar.components.library.util.LibraryKeys.SORT_RECENT_KEY;
import static com.azkar.components.library.util.LibraryKeys.SORT_SOURCE_KEY;
import static com.azkar.components.library.util.LibraryKeys.SORT_TEXT_KEY;

public record LibrarySortOption(String key, String label) {
    @Override
    public String toString() {
        return label;
    }

    public boolean isRecent() {
        return SORT_RECENT_KEY.equals(key);
    }

    public boolean isCategory() {
        return SORT_CATEGORY_KEY.equals(key);
    }

    public boolean isSource() {
        return SORT_SOURCE_KEY.equals(key);
    }

    public boolean isText() {
        return SORT_TEXT_KEY.equals(key);
    }

    public static LibrarySortOption recent(String label) {
        return new LibrarySortOption(SORT_RECENT_KEY, label);
    }

    public static LibrarySortOption category(String label) {
        return new LibrarySortOption(SORT_CATEGORY_KEY, label);
    }

    public static LibrarySortOption source(String label) {
        return new LibrarySortOption(SORT_SOURCE_KEY, label);
    }

    public static LibrarySortOption text(String label) {
        return new LibrarySortOption(SORT_TEXT_KEY, label);
    }
}
