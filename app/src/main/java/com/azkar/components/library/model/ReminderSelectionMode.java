package com.azkar.components.library.model;

public enum ReminderSelectionMode {
    SINGLE_ITEM("libraryReminderModeSingle"),
    COLLECTIONS("libraryReminderModeCollections"),
    CUSTOM_COLLECTIONS("libraryReminderModeCustom");

    private final String labelKey;

    ReminderSelectionMode(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }
}
