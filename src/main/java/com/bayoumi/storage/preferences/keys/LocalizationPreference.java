package com.bayoumi.storage.preferences.keys;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;

import java.time.Instant;

public enum LocalizationPreference implements PreferenceKeyProvider {
    LANGUAGE("language", "ar"),
    HIJRI_OFFSET("hijri_offset", "5"),
    WEEK_START("week_start", Instant.EPOCH.toString());

    private final PreferenceEntry entry;

    LocalizationPreference(String key, String defaultValue) {
        this.entry = new PreferenceEntry(key, defaultValue);
    }

    @Override
    public PreferenceEntry entry() {
        return entry;
    }

}
