package com.azkar.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.trayNextPrayerCountdown
import com.azkar.ui.home.PrayerKey
import com.azkar.ui.home.PrayerTimesModel
import com.azkar.ui.notifications.NotificationPreferencesRepository
import com.azkar.ui.notifications.PrayerLocationSettings
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * The countdown the tray shows, in the shape `Asr in 5m` or `Asr in <1m`.
 *
 * Why this is not [com.azkar.ui.home.PrayerTimesState]: that holder is remembered inside the main
 * window's content, and the tooltip has to keep counting while that window is hidden — the state a
 * hidden window's composition might stop recomposing is exactly the state the tray has to keep
 * telling time through. This one holds its own [PrayerTimesModel] and is composed at the root,
 * beside the tray icon itself, so both live and die with the application composition.
 *
 * Every [tick] recomputes the next prayer from the wall clock rather than decrementing a stored
 * duration. A decrementer drifts: each one-second step rounds, and the error compounds over hours.
 * Recomputing against the real `now` cannot drift, costs one solar calculation per second (the
 * same price [com.azkar.ui.home.HomeScreen]'s ticker already pays), and falls out of a debt the
 * display has — when the prayer time arrives, the next tick simply names a different prayer.
 * Formatting clamps at zero, so a tick that lands a fraction after the moment can never read
 * negative.
 *
 * The ticker itself lives in [com.azkar.ui.shell.ReminderTray]'s `LaunchedEffect` — one loop, in
 * the composition that owns this holder. A locale or location change replaces the holder, the
 * effect restarts, and there is still exactly one ticker; the old one dies with the composition
 * that remembered the old holder, with nothing to cancel by hand at shutdown.
 */
@Stable
class PrayerTrayCountdownState(
    private val uiLocale: Locale,
    private val location: PrayerLocationSettings,
) {
    private val model = PrayerTimesModel(uiLocale, location)

    /**
     * Empty until [start] has resolved the prayer names and the line's format, at which point it
     * is a full `Asr in 5m`. The tooltip shows the app's own name in the meantime, so the first
     * second of launch keeps the tray legible while the resources load.
     */
    var tooltipText: String by mutableStateOf("")
        private set

    /**
     * The coming prayer's name alone, used until the countdown resources have loaded.
     *
     * This remains stable between prayer changes; the menu and tooltip use [tooltipText] for the
     * remaining time, which changes only when the displayed minute changes.
     */
    var nextPrayerName: String by mutableStateOf("")
        private set

    private var loaded: Boolean = false
    private var lastKey: PrayerKey? = null

    /** The sentence the line is written into, `%1$s` the prayer name and `%2$s` the clock. */
    private var format: String = "%1\$s in %2\$s"

    /**
     * Resolves the localized prayer text, then publishes the first line.
     *
     * Suspends because both the names and the format come from the resource store; [tooltipText]
     * stays empty until they arrive, and [tick] refuses to publish a line it cannot yet write.
     */
    suspend fun start() {
        if (loaded) {
            logger.debug { "Prayer tray countdown is already started" }
            return
        }
        logger.info { "Starting prayer tray countdown (zone=${location.zoneId}, method=${location.method})" }
        model.load()
        format = getString(Res.string.trayNextPrayerCountdown)
        loaded = true
        tick()
        logger.info { "Prayer tray countdown started" }
    }

    /**
     * Recomputes the line from the wall clock, defaulting to the app's configured prayer zone.
     *
     * Passing [now] is what makes the ticking testable and drift-free: the clock is an argument,
     * not a read buried in a loop that might lag behind it.
     */
    fun tick(now: LocalDateTime = LocalDateTime.now(ZoneId.of(location.zoneId))) {
        if (!loaded) return
        val next = model.nextPrayer(now) ?: return
        // The name is published only when the answer changes: rewriting it every second would
        // churn state and repaint a menu whose rebuild is already the library's doing.
        if (next.key != lastKey) {
            nextPrayerName = next.name
            lastKey = next.key
            logger.info { "Next prayer is ${next.name} at ${next.at}" }
        }
        val updatedTooltip = String.format(uiLocale, format, next.name, formatRemaining(next.remaining))
        if (updatedTooltip != tooltipText) {
            tooltipText = updatedTooltip
            logger.debug { "Prayer tray countdown changed: $updatedTooltip" }
        }
    }
}

/**
 * The tray's countdown holder, derived from the app's locale and the configured prayer location.
 *
 * Koin hands over the preferences repository here — the same hand [com.azkar.ui.home.HomeScreen]
 * takes for its home-screen holder — because the countdown is composed at the application root, a
 * level with no services in scope, and a second source for the location would be a second opinion
 * about where the prayer times come from. A locale or location change rebuilds the holder, so the
 * tray picks the new language or city up on the next tick; the old ticker dies with the effect
 * that owned the old holder, with nothing to cancel by hand at shutdown.
 */
@Composable
fun rememberPrayerTrayCountdown(locale: Locale): PrayerTrayCountdownState {
    val preferences = koinInject<NotificationPreferencesRepository>()
    val prayerLocation by preferences.prayerLocation.collectAsState()
    return remember(locale, prayerLocation) { PrayerTrayCountdownState(locale, prayerLocation) }
}

/**
 * Whole remaining minutes, truncated so the display changes only when another full minute passes.
 * Durations below one minute, including a tick just past the target, read `<1m`. Digits use
 * [Locale.ROOT] because only the sentence around this value is translated.
 */
internal fun formatRemaining(duration: Duration): String {
    val totalSeconds = maxOf(0L, duration.seconds)
    if (totalSeconds < 60) return "<1m"

    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> String.format(Locale.ROOT, "%dh %dm", hours, minutes)
        hours > 0 -> String.format(Locale.ROOT, "%dh", hours)
        else -> String.format(Locale.ROOT, "%dm", totalMinutes)
    }
}
