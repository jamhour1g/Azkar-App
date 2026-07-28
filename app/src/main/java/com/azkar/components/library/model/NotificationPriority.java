package com.azkar.components.library.model;

public enum NotificationPriority {
    HIGH("libraryReminderPriorityHigh", 3, 7, "library-toast-high"),
    MEDIUM("libraryReminderPriorityMedium", 2, 4, "library-toast-medium"),
    LOW("libraryReminderPriorityLow", 1, 2, "library-toast-low");

    private final String labelKey;
    private final int orderWeight;
    private final int randomWeight;
    private final String toastStyleClass;

    NotificationPriority(String labelKey, int orderWeight, int randomWeight, String toastStyleClass) {
        this.labelKey = labelKey;
        this.orderWeight = orderWeight;
        this.randomWeight = randomWeight;
        this.toastStyleClass = toastStyleClass;
    }

    public String labelKey() {
        return labelKey;
    }

    public int orderWeight() {
        return orderWeight;
    }

    public int randomWeight() {
        return randomWeight;
    }

    public String toastStyleClass() {
        return toastStyleClass;
    }

    public static NotificationPriority fallback(NotificationPriority value) {
        return value == null ? MEDIUM : value;
    }
}
