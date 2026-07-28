package com.azkar.components.library.service;

import com.azkar.components.library.model.NotificationPriority;
import com.azkar.components.library.model.ReminderPlan;
import com.azkar.components.library.model.ScheduledReminderItem;
import com.azkar.components.library.util.NotificationTextFactory;
import com.azkar.i18n.AppLocale;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javafx.application.Platform;

public final class ReminderScheduler implements AutoCloseable {

    private static final long INITIAL_DELAY_SECONDS = 2L;
    private static final Comparator<ScheduledReminderItem> PRIORITY_ORDER = Comparator.comparingInt(
                    (ScheduledReminderItem item) -> item.priority().orderWeight())
            .reversed()
            .thenComparingLong(ScheduledReminderItem::idOrFallback);

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();
    private final Lock scheduleLock = new ReentrantLock();
    private final Locale locale = Locale.getDefault();
    private final ResourceBundle bundle = ResourceBundle.getBundle("com.azkar.i18n.home", locale);

    private ScheduledFuture<?> scheduledTask;

    private List<ScheduledReminderItem> activeReminders = List.of();
    private boolean randomOrder;
    private int cursor;
    private int notificationsPerCycle = 1;

    public void schedule(ReminderPlan plan, NotificationNotifier notifier) {
        scheduleLock.lock();
        try {
            cancelCurrentScheduleLocked();

            if (plan.reminders().isEmpty()) {
                return;
            }

            activeReminders = new ArrayList<>(plan.reminders());
            randomOrder = plan.randomOrder();
            notificationsPerCycle = Math.max(1, plan.notificationsPerCycle());
            if (!randomOrder) {
                activeReminders.sort(PRIORITY_ORDER);
            }
            cursor = 0;

            Duration interval = plan.cadence().interval();
            long intervalSeconds = Math.max(60L, interval.toSeconds());

            scheduledTask = executorService.scheduleAtFixedRate(
                    () -> dispatchBatch(notifier), INITIAL_DELAY_SECONDS, intervalSeconds, TimeUnit.SECONDS);
        } finally {
            scheduleLock.unlock();
        }
    }

    public void cancelCurrentSchedule() {
        scheduleLock.lock();
        try {
            cancelCurrentScheduleLocked();
        } finally {
            scheduleLock.unlock();
        }
    }

    private void cancelCurrentScheduleLocked() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    private void dispatchBatch(NotificationNotifier notifier) {
        List<ScheduledReminderItem> batch;
        scheduleLock.lock();
        try {
            if (activeReminders.isEmpty()) {
                return;
            }
            batch = nextBatch();
        } finally {
            scheduleLock.unlock();
        }

        for (ScheduledReminderItem item : batch) {
            NotificationPriority priority = NotificationPriority.fallback(item.priority());
            String priorityLabel = bundle.getString(priority.labelKey());
            String title = bundle.getString("libraryNotificationTitle") + " · " + priorityLabel;
            var remembrance = item.remembrance();
            String sourceFallback = remembrance.getSource().orElse(bundle.getString("libraryNotificationFallback"));
            String message = NotificationTextFactory.preview(remembrance, locale, sourceFallback);
            Platform.runLater(() -> notifier.notify(title, message, priority));
        }
    }

    private List<ScheduledReminderItem> nextBatch() {
        int count = Math.min(Math.max(1, notificationsPerCycle), activeReminders.size());
        List<ScheduledReminderItem> batch = new ArrayList<>();
        if (randomOrder) {
            return weightedRandomBatch(count);
        }

        for (int i = 0; i < count; i++) {
            batch.add(activeReminders.get(cursor));
            cursor = (cursor + 1) % activeReminders.size();
        }
        return batch;
    }

    private List<ScheduledReminderItem> weightedRandomBatch(int count) {
        List<ScheduledReminderItem> pool = new ArrayList<>(activeReminders);
        List<ScheduledReminderItem> batch = new ArrayList<>(count);
        int selectionCount = Math.min(count, pool.size());

        for (int i = 0; i < selectionCount; i++) {
            int selectedIndex = pickWeightedIndex(pool);
            batch.add(pool.remove(selectedIndex));
        }

        batch.sort(PRIORITY_ORDER);
        return batch;
    }

    private int pickWeightedIndex(List<ScheduledReminderItem> pool) {
        int totalWeight = pool.stream()
                .map(ScheduledReminderItem::priority)
                .map(NotificationPriority::fallback)
                .mapToInt(priority -> Math.max(1, priority.randomWeight()))
                .sum();

        int pick = random.nextInt(totalWeight);
        int runningWeight = 0;
        for (int i = 0; i < pool.size(); i++) {
            int weight = Math.max(
                    1, NotificationPriority.fallback(pool.get(i).priority()).randomWeight());
            runningWeight += weight;
            if (pick < runningWeight) {
                return i;
            }
        }

        return pool.size() - 1;
    }

    @Override
    public void close() {
        scheduleLock.lock();
        try {
            cancelCurrentScheduleLocked();
            executorService.shutdownNow();
        } finally {
            scheduleLock.unlock();
        }
    }

    @FunctionalInterface
    public interface NotificationNotifier {
        void notify(String title, String message, NotificationPriority priority);
    }
}
