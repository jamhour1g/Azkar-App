package com.azkar.ui.notifications

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.ui.notifications.presentation.OverlayPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Transient state for the notification and prayer settings that live on the settings screen.
 *
 * These used to sit on [ReminderManagerState] alongside the queue, because a single screen owned all
 * three sets of settings. They do not any more: prayer *times* are toggled from the home screen's
 * prayer card, and the location that calculator reads and the way a reminder is drawn over other
 * windows are both preferences rather than anything about a queue. So this class holds the four
 * location fields and the two presentations, and the queue's state holder no longer has to know what
 * a time zone is.
 *
 * Persisted values stay on [NotificationPreferencesRepository]'s `StateFlow`s and are read for drawing
 * with `collectAsState`; only the editable copies — the half-typed coordinates, the last failure —
 * live here. This class is the only writer, so every mutation goes through one `scope.launch` and one
 * repository call instead of being scattered across the body of a composable where a `LaunchedEffect`
 * could re-run it.
 *
 * Each of the two editors keeps its own notice type, so the prayer tab cannot render a startup
 * failure and the notifications tab cannot render a rejected coordinate. That is not cosmetic: the
 * two tabs are on different screens' worth of attention, and the old screen's single shared error
 * string put a login-manager complaint at the bottom of the prayer location card.
 */
@Stable
class NotificationSettingsState(
    private val scope: CoroutineScope,
    private val preferences: NotificationPreferencesRepository,
    /**
     * The IP lookup behind "Auto-detect", when the host can offer one.
     *
     * Nullable rather than required because it is a capability the surrounding platform supplies — a
     * network client — and a host that has none (a `@Preview`, a test of the presentation settings)
     * still has to be able to draw every other row on the screen. A missing one produces the same
     * notice a refused lookup does, so the button reports the truth instead of being hidden or
     * silently doing nothing.
     */
    private val locationDetector: LocationDetector? = null,
    /** The desktop login manager, when there is one. See [locationDetector] on nullability. */
    private val startupRegistration: StartupRegistration? = null,
) {
    /** The location editor's working copy of the four fields. */
    var locationDraft: PrayerLocationDraft by mutableStateOf(PrayerLocationDraft.of(preferences.prayerLocation.value))
        private set

    /** Writes for the prayer tab's location card. */
    val prayer = PrayerEditor()

    /** Writes for the notifications tab's overlay, channel and startup cards. */
    val notifications = NotificationEditor()

    /**
     * Writes for the reminder schedule, the card at the top of the notifications tab.
     *
     * The same instance type the reminders screen uses, over the same repository singleton, so there
     * is one queue and two places to set it up. Both screens construct their own rather than sharing
     * one because each has its own composition scope to launch from; the editor holds nothing, so two
     * of them cannot disagree about what is stored.
     */
    val schedule = ReminderScheduleEditor(scope = scope, preferences = preferences)

    /**
     * Writes for the prayer tab.
     *
     * An inner class rather than four methods on [NotificationSettingsState] so that "these belong to
     * the prayer card" is a fact about the type rather than about the order somebody happened to
     * write the methods in. It holds no state of its own — every method writes through the same
     * [preferences] singleton — so switching tabs and dropping it loses nothing.
     */
    @Stable
    inner class PrayerEditor {
        /** The last location failure or confirmation, or null. See [LocationNotice]. */
        var notice: LocationNotice? by mutableStateOf(null)
            private set

        /**
         * Edits the four fields of the location editor.
         *
         * A transform rather than four setters so the draft is replaced as one value: half-typed
         * coordinates are read as a whole by [saveLocation], and letting them be three independent
         * writes would mean the validation below ran against a mix of the old and the new text.
         */
        fun editDraft(transform: (PrayerLocationDraft) -> PrayerLocationDraft) {
            locationDraft = transform(locationDraft)
        }

        /**
         * Validates the draft and writes it.
         *
         * The zone id is checked against [ZoneId] before anything is written, because
         * [PrayerTimeCalculator] reads it on every tick to build its times table — a bad id saved here
         * takes the whole runtime's prayer pass down, and it does so twenty seconds later, somewhere
         * with no link back to the field that was typed into. The coordinates get the same treatment
         * only because nothing downstream can reject them: they are stored as doubles, and a latitude
         * of 400 would be read happily by every calculator that sees it.
         */
        fun saveLocation() {
            val draft = locationDraft
            val latitude = draft.latitude.trim().toDoubleOrNull()
            val longitude = draft.longitude.trim().toDoubleOrNull()
            if (latitude == null || longitude == null) {
                notice = LocationNotice.InvalidCoordinates
                return
            }
            if (runCatching { ZoneId.of(draft.zoneId.trim()) }.isFailure) {
                notice = LocationNotice.InvalidTimeZone
                return
            }
            val candidate =
                preferences.prayerLocation.value.copy(
                    latitude = latitude,
                    longitude = longitude,
                    city = draft.city.trim(),
                    zoneId = draft.zoneId.trim(),
                    autoDetect = false,
                )
            scope.launch {
                preferences.savePrayerLocation(candidate)
                locationDraft = PrayerLocationDraft.of(preferences.prayerLocation.value)
                notice = LocationNotice.Saved
            }
        }

        /**
         * Switches the calculation method, and nothing else.
         *
         * A dropdown rather than a field because the five values are a closed set whose angles are the
         * whole difference between them — [PrayerCalculationMethod] exists precisely so the numbers do
         * not have to be typed in either.
         *
         * The method is written straight to the stored location rather than being held on the draft,
         * because it is not part of what "Save location" means: it is valid for every city, so holding
         * it behind a Save button that also writes coordinates would let a user pick a method, switch
         * tabs without saving, and lose the choice.
         */
        fun setMethod(method: PrayerCalculationMethod) {
            scope.launch {
                preferences.savePrayerLocation(preferences.prayerLocation.value.copy(method = method))
            }
        }

        /**
         * Fills the draft from IP geolocation and saves it.
         *
         * A network lookup inside a settings screen is the one thing here that can take seconds or fail
         * outright, so its three outcomes — found, refused, and no host capability at all — are all
         * first-class. The old inline `runCatching` collapsed the last two into one message that named
         * the failure in English inside a Kotlin file, which is how a Georgian error message ended up
         * next to an Arabic interface.
         *
         * The calculation method is carried across from what is stored rather than taken from the
         * detector's own default: a user who picked a method deliberately should not have it silently
         * reset by pressing the button that only meant to find their city.
         */
        fun detectLocation() {
            scope.launch {
                val detector =
                    locationDetector ?: run {
                        notice = LocationNotice.Unavailable(reason = null)
                        return@launch
                    }
                runCatching { detector.detect() }
                    .onSuccess { detected ->
                        val updated = detected.copy(method = preferences.prayerLocation.value.method)
                        preferences.savePrayerLocation(updated)
                        locationDraft = PrayerLocationDraft.of(preferences.prayerLocation.value)
                        notice = LocationNotice.Detected(updated.city)
                    }.onFailure { failure ->
                        notice = LocationNotice.Unavailable(failure.message)
                    }
            }
        }

        /** Clears the outstanding message. Called on every fresh action so errors do not linger. */
        fun dismissNotice() {
            notice = null
        }
    }

    /**
     * Writes for the notifications tab.
     *
     * Split from [PrayerEditor] for the same reason, and additionally because the startup registration
     * is the one write here that talks to the platform: keeping it in its own namespace keeps
     * `StartupNotice` reachable from exactly one place.
     */
    @Stable
    inner class NotificationEditor {
        /** The last login-manager failure, or null. See [StartupNotice]. */
        var notice: StartupNotice? by mutableStateOf(null)
            private set

        fun setSystemNotifications(enabled: Boolean) {
            scope.launch { preferences.updatePresentation { it.copy(systemNotificationsEnabled = enabled) } }
        }

        fun setOverlayDuration(seconds: Int) {
            scope.launch {
                preferences.updatePresentation {
                    it.copy(
                        durationSeconds = seconds.coerceIn(MIN_DURATION, MAX_DURATION),
                    )
                }
            }
        }

        fun setOverlayStackSize(count: Int) {
            scope.launch { preferences.updatePresentation { it.copy(maxVisible = count.coerceIn(1, 5)) } }
        }

        fun setPlacement(placement: OverlayPlacement) {
            scope.launch { preferences.updatePresentation { it.copy(placement = placement) } }
        }

        /**
         * Registers or unregisters the desktop launcher, then records the preference.
         *
         * The order matters and is the reason this is not a bare toggle: the stored preference is
         * only written once the platform call has actually succeeded. Writing it first — or ignoring
         * the result — is how a switch ends up reading "on" for a machine where the registration
         * failed and nothing happens at the next login.
         */
        fun setStartOnLogin(enabled: Boolean) {
            scope.launch {
                val registration =
                    startupRegistration ?: run {
                        notice = StartupNotice.Unavailable(reason = null)
                        return@launch
                    }
                registration
                    .setEnabled(enabled)
                    .onSuccess {
                        preferences.setStartOnLogin(enabled)
                        notice = null
                    }.onFailure { failure ->
                        notice = StartupNotice.Unavailable(failure.message)
                    }
            }
        }

        /** Clears the outstanding message. Called on every fresh action so errors do not linger. */
        fun dismissNotice() {
            notice = null
        }
    }

    private companion object {
        /**
         * The bounds the overlay duration is clamped to.
         *
         * Duplicated from [com.azkar.ui.notifications.presentation.NotificationPresentationSettings]'s own
         * `require`, because `require` throws and a stepper must not be able to hand the settings
         * something that throws. Anything outside the range is a bug in the stepper, not a user error.
         */
        const val MIN_DURATION = 3
        const val MAX_DURATION = 120
    }
}
