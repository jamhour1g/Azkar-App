package com.bayoumi.storage.preferences.keys;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;

public enum NotificationPreferences implements PreferenceKeyProvider {
    BACKGROUND_COLOR("notification_background_color", "#FFFFFF"),
    BORDER_COLOR("notification_border_color", "#E9C46A"),
    TEXT_COLOR("notification_text_color", "#000000"),
    POSITION("notification_pos", "BOTTOM_LEFT");

    private final PreferenceEntry entry;

    NotificationPreferences(String key, String defaultValue) {
        this.entry = new PreferenceEntry(key, defaultValue);
    }

    @Override
    public PreferenceEntry entry() {
        return entry;
    }
}