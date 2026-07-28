package com.azkar.components.library.model;

import com.azkar.domain.model.Remembrance;
import java.util.List;
import java.util.Locale;

public record ReminderSelectionEntry(
        String id,
        String label,
        NotificationPriority priority,
        ReminderSelectionType type,
        List<Remembrance> remembrances,
        String sourceTag) {

    public ReminderSelectionEntry {
        priority = NotificationPriority.fallback(priority);
        remembrances = List.copyOf(remembrances);
    }

    public static ReminderSelectionEntry single(Remembrance remembrance, String label, NotificationPriority priority) {
        long itemId = remembrance.getId().orElse(-1L);
        return new ReminderSelectionEntry(
                "single:" + itemId, label, priority, ReminderSelectionType.SINGLE, List.of(remembrance), "");
    }

    public static ReminderSelectionEntry collection(
            String collectionKey,
            String label,
            NotificationPriority priority,
            List<Remembrance> remembrances,
            String sourceTag) {
        return new ReminderSelectionEntry(
                "collection:" + collectionKey,
                label,
                priority,
                ReminderSelectionType.COLLECTION,
                remembrances,
                sourceTag);
    }

    public static ReminderSelectionEntry custom(
            String customCollectionName, String label, NotificationPriority priority, List<Remembrance> remembrances) {
        return new ReminderSelectionEntry(
                "custom:" + customCollectionName.toLowerCase(Locale.ROOT),
                label,
                priority,
                ReminderSelectionType.CUSTOM,
                remembrances,
                customCollectionName);
    }
}
