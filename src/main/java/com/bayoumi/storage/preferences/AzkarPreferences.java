package com.bayoumi.storage.preferences;

import com.bayoumi.storage.preferences.keys.*;
import com.bayoumi.util.Constants;
import com.bayoumi.util.LoggerWrapper;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public enum AzkarPreferences {

    INSTANCE;

    private static final String IS_INITIALIZED = "is_initialized";
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(AzkarPreferences.class);
    private final Preferences preferences = Preferences.userRoot().node(Constants.APP_NAME);


    AzkarPreferences() {
        initializePreferences();
    }

    public void set(PreferenceKeyProvider key, String value) {
        preferences.put(key.entry().key(), value);
    }

    private void initializePreferences() {
        if (preferences.getBoolean(IS_INITIALIZED, false)) return;

        var all = List.of(
                com.bayoumi.storage.preferences.keys.PreferencesType.class,
                AzkarPeriodType.class,
                PrayerAdjustment.class,
                NotificationPreferences.class,
                PrayerTimesPreferences.class,
                AppBehaviorPreference.class,
                LocalizationPreference.class
        );

        Collection<PreferenceKeyProvider.PreferenceEntry> preferenceEntries = PreferenceKeyProvider.toPreferenceEntries(all);
        LOGGER.info(() -> "Initializing preferences with " + preferenceEntries.size() + " entries.");
        LOGGER.info(() -> "The preferences in the list are:" + preferenceEntries);

        preferenceEntries.forEach(p -> preferences.put(p.key(), p.defaultValue()));

        preferences.putBoolean(IS_INITIALIZED, true);
    }

}