package com.bayoumi.storage.statistics;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;

public enum PrayerStats implements PreferenceKeyProvider {
    PRAYER_TIMES_OTHER_OPENED("prayer_times_other_opened", "0");

    private final PreferenceEntry entry;

    PrayerStats(String key, String def) {
        this.entry = new PreferenceEntry(key, def);
    }

    public PreferenceEntry entry() {
        return entry;
    }

    public String toString() {
        return entry.key();
    }
}
