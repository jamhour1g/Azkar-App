package com.bayoumi.storage.preferences.keys;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;

public enum PreferencesType implements PreferenceKeyProvider {

    MORNING_AZKAR_REMINDER("morning_azkar_reminder", "30"),
    NIGHT_AZKAR_REMINDER("night_azkar_reminder", "30"),
    IS_AZKAR_STOPPED("is_stopped", "false"),
    AZKAR_DURATION("azkar_duration", "30"),
    AUDIO_NAME("audio_name", ""),
    SELECTED_PERIOD("selected_period", "high"),
    TIMED_AZKAR_FONT_SIZE("timed_azkar_font_size", "23"),
    TIMED_AZKAR_DATA_VERSION("timed_azkar_data_version", "0.0.0"),
    IS_PRAYERS_REMINDER_STOPPED("is_prayers_reminder_stopped", "false"),
    ENABLE_DARK_MODE("enable_dark_mode", "false"),
    ENABLE_24_FORMAT("enable_24_format", "false");

    private final PreferenceEntry entry;

    PreferencesType(String key, String defaultValue) {
        this.entry = new PreferenceEntry(key, defaultValue);
    }

    @Override
    public PreferenceEntry entry() {
        return entry;
    }
}
