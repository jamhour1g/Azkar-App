package com.bayoumi.storage.preferences.keys;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;


public enum AzkarPeriodType implements PreferenceKeyProvider {
    HIGH("high_period", "5"),
    MID("mid_period", "10"),
    LOW("low_period", "20"),
    REAR("rear_period", "30");

    private final PreferenceEntry entry;

    AzkarPeriodType(String key, String defaultValue) {
        this.entry = new PreferenceEntry(key, defaultValue);
    }

    @Override
    public PreferenceEntry entry() {
        return entry;
    }
}
