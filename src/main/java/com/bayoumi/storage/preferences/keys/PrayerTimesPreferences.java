package com.bayoumi.storage.preferences.keys;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;

public enum PrayerTimesPreferences implements PreferenceKeyProvider {
    COUNTRY("country", "Egypt"),
    CITY("city", "Cairo"),
    SUMMER_TIMING("summer_timing", "false"),
    METHOD("method", "5"),
    ASR_JURISTIC("asr_juristic", "0"),
    LATITUDE("latitude", "27.556363"),
    LONGITUDE("longitude", "30.807579"),
    IS_MANUAL_LOCATION_SELECTED("IS_MANUAL_LOCATION_SELECTED", "true"),
    ADHAN_AUDIO("adhan_audio", "adhan-abdulbasit-abdusamad.mp3");

    private final PreferenceEntry entry;

    PrayerTimesPreferences(String key, String defaultValue) {
        this.entry = new PreferenceEntry(key, defaultValue);
    }

    @Override
    public PreferenceEntry entry() {
        return entry;
    }
}