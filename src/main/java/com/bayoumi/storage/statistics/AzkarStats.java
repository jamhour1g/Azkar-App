package com.bayoumi.storage.statistics;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;

public enum AzkarStats implements PreferenceKeyProvider {
    MORNING_AZKAR_OPENED("morning_azkar_opened", "0"),
    NIGHT_AZKAR_OPENED("night_azkar_opened", "0"),
    MORNING_AZKAR_NOTIFICATION_SHOWN("morning_azkar_notification_shown", "0"),
    MORNING_AZKAR_NOTIFICATION_CLICKED("morning_azkar_notification_clicked", "0"),
    NIGHT_AZKAR_NOTIFICATION_SHOWN("night_azkar_notification_shown", "0"),
    NIGHT_AZKAR_NOTIFICATION_CLICKED("night_azkar_notification_clicked", "0"),
    AZKAR_NOTIFICATION_SHOWN("azkar_notification_shown", "0"),
    AZKAR_NOTIFICATION_CLICKED("azkar_notification_clicked", "0");

    private final PreferenceEntry entry;

    AzkarStats(String key, String def) {
        this.entry = new PreferenceEntry(key, def);
    }

    public PreferenceEntry entry() {
        return entry;
    }

    public String toString() {
        return entry.key();
    }
}
