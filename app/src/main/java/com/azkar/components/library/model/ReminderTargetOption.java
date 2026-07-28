package com.azkar.components.library.model;

import com.azkar.domain.model.Remembrance;

public record ReminderTargetOption(long id, String label, Remembrance remembrance) {
    @Override
    public String toString() {
        return label;
    }
}
