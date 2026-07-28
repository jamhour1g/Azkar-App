package com.azkar.components.library.model;

public record LibraryRemembranceRow(
        long id, String category, String primaryPreview, String secondaryPreview, String source, boolean favorite) {}
