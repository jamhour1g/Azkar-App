package com.bayoumi.storage.preferences.keys;

import com.bayoumi.storage.preferences.PreferenceKeyProvider;

public enum AppBehaviorPreference implements PreferenceKeyProvider {
    AUTO_UPDATE("automatic_check_for_updates", "true"),
    SEND_USAGE_DATA("send_usage_data", "true"),
    APP_VERSION("app_version", "0"),
    MINIMIZED("minimized", "false");

    private final PreferenceEntry entry;

    AppBehaviorPreference(String key, String defaultValue) {
        this.entry = new PreferenceEntry(key, defaultValue);
    }

    @Override
    public PreferenceEntry entry() {
        return entry;
    }
}