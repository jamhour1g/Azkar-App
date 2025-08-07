package com.bayoumi.storage.statistics;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;

public enum SettingsStats implements PreferenceKeyProvider {
    SETTINGS_OPENED("settings_opened", "0"),
    SETTINGS_AZKAR_OPENED("settings_azkar_opened", "0"),
    SETTINGS_PRAYERS_OPENED("settings_prayers_opened", "0"),
    SETTINGS_OTHER_OPENED("settings_other_opened", "0"),
    SETTINGS_NOTIFICATION_COLORS_OPENED("settings_notification_colors_opened", "0"),
    SETTINGS_AZKAR_DB_OPENED("settings_azkar_db_opened", "0"),
    SETTINGS_TIMED_AZKAR_OPENED("settings_timed_azkar_opened", "0");

    private final PreferenceEntry entry;

    SettingsStats(String key, String def) {
        this.entry = new PreferenceEntry(key, def);
    }

    public PreferenceEntry entry() {
        return entry;
    }

    public String toString() {
        return entry.key();
    }
}
