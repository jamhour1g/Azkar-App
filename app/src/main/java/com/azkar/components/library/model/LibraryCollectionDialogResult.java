package com.azkar.components.library.model;

public record LibraryCollectionDialogResult(
        String collectionToApply, boolean addToReminderCollection, boolean addToReminderFavorites) {

    public boolean hasCollectionToApply() {
        return collectionToApply != null && !collectionToApply.isBlank();
    }
}
