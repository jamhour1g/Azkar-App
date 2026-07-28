package com.azkar.components.library.model;

import java.time.Duration;

public enum NotificationCadence {
    EVERY_1_MINUTES("libraryCadenceEvery1Minutes", Duration.ofMinutes(1)),
    EVERY_30_MINUTES("libraryCadenceEvery30Minutes", Duration.ofMinutes(30)),
    EVERY_2_HOURS("libraryCadenceEvery2Hours", Duration.ofHours(2)),
    DAILY("libraryCadenceDaily", Duration.ofDays(1));

    private final String labelKey;
    private final Duration interval;

    NotificationCadence(String labelKey, Duration interval) {
        this.labelKey = labelKey;
        this.interval = interval;
    }

    public String labelKey() {
        return labelKey;
    }

    public Duration interval() {
        return interval;
    }
}
