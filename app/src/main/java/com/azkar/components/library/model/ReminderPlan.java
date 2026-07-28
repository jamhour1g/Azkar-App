package com.azkar.components.library.model;

import java.util.List;

public record ReminderPlan(
        String title,
        List<ScheduledReminderItem> reminders,
        NotificationCadence cadence,
        int notificationsPerCycle,
        boolean randomOrder) {

    public ReminderPlan {
        reminders = List.copyOf(reminders);
    }
}
