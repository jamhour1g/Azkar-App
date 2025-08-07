package com.bayoumi.storage.preferences.keys;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;

public enum PrayerAdjustment implements PreferenceKeyProvider {
    FAJR("fajr_adjustment", "0"),
    SUNRISE("sunrise_adjustment", "0"),
    DHUHR("dhuhr_adjustment", "0"),
    ASR("asr_adjustment", "0"),
    MAGHRIB("maghrib_adjustment", "0"),
    ISHAA("isha_adjustment", "0");

    private final PreferenceEntry entry;

    PrayerAdjustment(String key, String defaultValue) {
        this.entry = new PreferenceEntry(key, defaultValue);
    }

    @Override
    public PreferenceEntry entry() {
        return entry;
    }
}