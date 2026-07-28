package com.azkar.components.home.controller;

import com.azkar.components.home.PrayerRowComponent;
import com.azkar.components.home.RemainingToPrayerComponent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import com.azkar.i18n.AppLocale;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

public class PrayerTimesController {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final ResourceBundle bundle = AppLocale.bundle();
    private final Locale uiLocale = AppLocale.current();
    private final LinkedHashMap<String, PrayerEntry> prayersByKey = new LinkedHashMap<>();

    private final DateTimeFormatter localizedBundleTimeFormatter = buildTwelveHourFormatter(uiLocale);
    private final DateTimeFormatter englishBundleTimeFormatter = buildTwelveHourFormatter(Locale.ENGLISH);
    private final DateTimeFormatter timeFormatter12 = DateTimeFormatter.ofPattern("hh:mm a", uiLocale);
    private final DateTimeFormatter timeFormatter24 = DateTimeFormatter.ofPattern("HH:mm");

    private Timeline countdownTicker;
    private Runnable onUpdateCallback;

    public record PrayerEntry(
            String key, String displayTime, String displayValue, LocalTime time, PrayerRowComponent row) {}

    public void bindPrayerRows(PrayerRowComponent fajrRow, PrayerRowComponent dhuhrRow,
                               PrayerRowComponent asrRow, PrayerRowComponent maghribRow, PrayerRowComponent ishaRow) {
        prayersByKey.clear();
        prayersByKey.put("fajr", buildPrayerEntry("fajr", fajrRow));
        prayersByKey.put("dhuhr", buildPrayerEntry("dhuhr", dhuhrRow));
        prayersByKey.put("asr", buildPrayerEntry("asr", asrRow));
        prayersByKey.put("maghrib", buildPrayerEntry("maghrib", maghribRow));
        prayersByKey.put("isha", buildPrayerEntry("isha", ishaRow));
    }

    private PrayerEntry buildPrayerEntry(String key, PrayerRowComponent row) {
        String timeKey = key + "Time";
        String valueKey = key + "Value";

        String displayTime = bundle.getString(timeKey).trim();
        String rawValue = bundle.getString(valueKey).trim();

        LocalTime time = parsePrayerTime(displayTime, rawValue);
        return new PrayerEntry(key, displayTime, rawValue, time, row);
    }

    public void startCountdownTicker(RemainingToPrayerComponent remainingToPrayerComponent) {
        updateCountdownAndHighlight(remainingToPrayerComponent);
        countdownTicker = new Timeline(
                new KeyFrame(javafx.util.Duration.ZERO, event -> updateCountdownAndHighlight(remainingToPrayerComponent)),
                new KeyFrame(javafx.util.Duration.seconds(1)));
        countdownTicker.setCycleCount(Animation.INDEFINITE);
        countdownTicker.play();
    }

    public void stopCountdownTicker() {
        if (countdownTicker != null) {
            countdownTicker.stop();
        }
    }

    public void setOnUpdateCallback(Runnable callback) {
        this.onUpdateCallback = callback;
    }

    private void updateCountdownAndHighlight(RemainingToPrayerComponent remainingToPrayerComponent) {
        if (prayersByKey.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now(SYSTEM_ZONE);
        NextPrayerInfo nextPrayerInfo = computeNextPrayer(now);

        for (PrayerEntry entry : prayersByKey.values()) {
            boolean isNext = entry.key().equals(nextPrayerInfo.entry().key());
            entry.row().setNextPrayer(isNext);
            entry.row().setPrayerTime(isNext ? bundle.getString("nextPrayerLabel") : formatTime(entry.time()));
            entry.row().setPrayerValue(entry.displayValue());
        }

        remainingToPrayerComponent.setCountdownText(formatDuration(nextPrayerInfo.remaining()));
        if (onUpdateCallback != null) {
            onUpdateCallback.run();
        }
    }

    private NextPrayerInfo computeNextPrayer(LocalDateTime now) {
        List<PrayerMoment> moments = new ArrayList<>();
        for (PrayerEntry entry : prayersByKey.values()) {
            LocalDateTime todayMoment = LocalDateTime.of(now.toLocalDate(), entry.time());
            moments.add(new PrayerMoment(entry, todayMoment));
            moments.add(new PrayerMoment(entry, todayMoment.plusDays(1)));
        }

        PrayerMoment nearest = moments.stream()
                .filter(moment -> !moment.at().isBefore(now))
                .min(Comparator.comparing(PrayerMoment::at))
                .orElse(moments.getFirst());

        Duration remaining = Duration.between(now, nearest.at());
        return new NextPrayerInfo(nearest.entry(), remaining);
    }

    private LocalTime parsePrayerTime(String... candidates) {
        for (String candidate : candidates) {
            if (candidate.isBlank()) continue;
            if (candidate.equalsIgnoreCase(bundle.getString("nextPrayerLabel"))) continue;

            try {
                return LocalTime.parse(candidate, localizedBundleTimeFormatter);
            } catch (RuntimeException ignored) {}

            try {
                return LocalTime.parse(candidate, englishBundleTimeFormatter);
            } catch (RuntimeException ignored) {}

            try {
                return LocalTime.parse(candidate, timeFormatter24);
            } catch (RuntimeException ignored) {}
        }
        return LocalTime.MIDNIGHT;
    }

    private String formatTime(LocalTime time) {
        return timeFormatter12.format(time);
    }

    private String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(uiLocale, "%02d:%02d:%02d", hours, minutes, secs);
    }

    public LinkedHashMap<String, PrayerEntry> getPrayersByKey() {
        return prayersByKey;
    }

    private static DateTimeFormatter buildTwelveHourFormatter(Locale locale) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendValue(ChronoField.CLOCK_HOUR_OF_AMPM)
                .appendLiteral(':')
                .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                .appendLiteral(' ')
                .appendText(ChronoField.AMPM_OF_DAY, TextStyle.SHORT)
                .toFormatter(locale);
    }

    private record PrayerMoment(PrayerEntry entry, LocalDateTime at) {}
    private record NextPrayerInfo(PrayerEntry entry, Duration remaining) {}
}
