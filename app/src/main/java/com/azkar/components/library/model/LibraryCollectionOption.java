package com.azkar.components.library.model;

import static com.azkar.components.library.util.LibraryKeys.ALL_COLLECTION_KEY;
import static com.azkar.components.library.util.LibraryKeys.FAVORITES_COLLECTION_KEY;
import static com.azkar.components.library.util.LibraryKeys.TAG_COLLECTION_PREFIX;
import static com.azkar.components.library.util.LibraryKeys.UNCATEGORIZED_COLLECTION_KEY;

import java.util.Locale;

public record LibraryCollectionOption(String key, String label) {
    @Override
    public String toString() {
        return label;
    }

    public boolean isAllCollections() {
        return ALL_COLLECTION_KEY.equals(key);
    }

    public boolean isFavorites() {
        return FAVORITES_COLLECTION_KEY.equals(key);
    }

    public boolean isUncategorized() {
        return UNCATEGORIZED_COLLECTION_KEY.equals(key);
    }

    public boolean isTagCollection() {
        return key != null && key.startsWith(TAG_COLLECTION_PREFIX);
    }

    public String tagNameKey() {
        if (!isTagCollection()) {
            return "";
        }
        return key.substring(TAG_COLLECTION_PREFIX.length());
    }

    public static LibraryCollectionOption all(String label) {
        return new LibraryCollectionOption(ALL_COLLECTION_KEY, label);
    }

    public static LibraryCollectionOption favorites(String label) {
        return new LibraryCollectionOption(FAVORITES_COLLECTION_KEY, label);
    }

    public static LibraryCollectionOption uncategorized(String label) {
        return new LibraryCollectionOption(UNCATEGORIZED_COLLECTION_KEY, label);
    }

    public static LibraryCollectionOption tag(String name) {
        return new LibraryCollectionOption(TAG_COLLECTION_PREFIX + name.toLowerCase(Locale.ROOT), name);
    }
}
