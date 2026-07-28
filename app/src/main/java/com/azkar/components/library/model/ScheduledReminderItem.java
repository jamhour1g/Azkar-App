package com.azkar.components.library.model;

import com.azkar.domain.model.Remembrance;

public record ScheduledReminderItem(Remembrance remembrance, NotificationPriority priority) {

    public ScheduledReminderItem {
        priority = NotificationPriority.fallback(priority);
    }

    public long idOrFallback() {
        return remembrance.getId().orElse(Long.MAX_VALUE);
    }
}
