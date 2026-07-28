package com.azkar.components.home.controller;

import com.azkar.components.home.PrayerNotificationsToggleComponent;
import com.azkar.components.home.PrayerRowComponent;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class NotificationSettingsController {

    private static final String PREFS_NODE = "com.azkar.app.home.notifications";
    private static final String KEY_GLOBAL_NOTIFICATIONS = "globalEnabled";

    private final Preferences preferences;
    private boolean syncingNotifications = false;

    private LinkedHashMap<String, PrayerRowComponent> prayerRows;
    private PrayerNotificationsToggleComponent globalToggle;
    private Consumer<Boolean> onGlobalChanged;

    public NotificationSettingsController() {
        this.preferences = Preferences.userRoot().node(PREFS_NODE);
    }

    public void initialize(LinkedHashMap<String, PrayerTimesController.PrayerEntry> prayerEntries,
                          PrayerNotificationsToggleComponent globalToggle,
                          Consumer<Boolean> onGlobalChanged) {
        this.prayerRows = new LinkedHashMap<>();
        for (var entry : prayerEntries.values()) {
            this.prayerRows.put(entry.key(), entry.row());
        }
        this.globalToggle = globalToggle;
        this.onGlobalChanged = onGlobalChanged;

        globalToggle.setOnToggleAction(this::onGlobalToggleChanged);

        for (var entry : prayerRows.entrySet()) {
            String key = entry.getKey();
            PrayerRowComponent row = entry.getValue();
            row.notificationEnabledProperty().addListener((observable, oldValue, enabled) -> {
                if (syncingNotifications) return;
                persistBoolean(preferenceKeyForPrayer(key), enabled);
                if (!enabled) maybeDisableGlobalToggle();
            });
        }
    }

    public void loadPersistedSettings() {
        boolean globalEnabled = preferences.getBoolean(KEY_GLOBAL_NOTIFICATIONS, true);
        globalToggle.setNotificationsEnabled(globalEnabled);

        syncingNotifications = true;
        boolean atLeastOneEnabled = false;
        try {
            for (var entry : prayerRows.entrySet()) {
                boolean enabled = preferences.getBoolean(preferenceKeyForPrayer(entry.getKey()), globalEnabled);
                entry.getValue().setNotificationEnabled(enabled);
                atLeastOneEnabled = atLeastOneEnabled || enabled;
            }
        } finally {
            syncingNotifications = false;
        }

        if (!atLeastOneEnabled && globalEnabled) {
            globalToggle.setNotificationsEnabled(false);
            persistBoolean(KEY_GLOBAL_NOTIFICATIONS, false);
        }
    }

    private void onGlobalToggleChanged(boolean enabled) {
        if (onGlobalChanged != null) {
            onGlobalChanged.accept(enabled);
        }
        persistBoolean(KEY_GLOBAL_NOTIFICATIONS, enabled);
        syncingNotifications = true;
        try {
            for (var row : prayerRows.values()) {
                row.setNotificationEnabled(enabled);
            }
        } finally {
            syncingNotifications = false;
        }
        for (var entry : prayerRows.entrySet()) {
            persistBoolean(preferenceKeyForPrayer(entry.getKey()), enabled);
        }
    }

    private void maybeDisableGlobalToggle() {
        boolean anyEnabled = prayerRows.values().stream().anyMatch(PrayerRowComponent::isNotificationEnabled);
        if (!anyEnabled && globalToggle.isNotificationsEnabled()) {
            globalToggle.setNotificationsEnabled(false);
            persistBoolean(KEY_GLOBAL_NOTIFICATIONS, false);
        }
    }

    private String preferenceKeyForPrayer(String prayerKey) {
        return "prayer." + prayerKey + ".enabled";
    }

    private void persistBoolean(String key, boolean value) {
        preferences.putBoolean(key, value);
        flushPreferencesSafely();
    }

    private void flushPreferencesSafely() {
        try {
            preferences.flush();
        } catch (BackingStoreException ignored) {}
    }
}