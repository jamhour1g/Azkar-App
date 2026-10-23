package com.azkar.ui.notifications

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore
import com.azkar.ui.home.PrayerKey
import com.azkar.ui.notifications.presentation.NotificationPresentationSettings
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Owns the persisted settings shared by the manager UI and the process notification service. */
class NotificationPreferencesRepository(
    private val settings: SettingsStore,
    private val logger: KLogger = KotlinLogging.logger {},
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _queue = MutableStateFlow(ReminderQueueConfig())
    private val _queueProgress = MutableStateFlow(ReminderQueueProgress())
    private val _prayerLocation = MutableStateFlow(PrayerLocationSettings())
    private val _prayerProgress = MutableStateFlow(PrayerNotificationProgress())
    private val _prayerFlags = MutableStateFlow(PrayerKey.entries.associateWith { true })
    private val _startOnLogin = MutableStateFlow(false)
    private val _presentation = MutableStateFlow(NotificationPresentationSettings())

    val queue: StateFlow<ReminderQueueConfig> = _queue.asStateFlow()
    val queueProgress: StateFlow<ReminderQueueProgress> = _queueProgress.asStateFlow()
    val prayerLocation: StateFlow<PrayerLocationSettings> = _prayerLocation.asStateFlow()
    val prayerProgress: StateFlow<PrayerNotificationProgress> = _prayerProgress.asStateFlow()
    val prayerFlags: StateFlow<Map<PrayerKey, Boolean>> = _prayerFlags.asStateFlow()
    val startOnLogin: StateFlow<Boolean> = _startOnLogin.asStateFlow()

    /**
     * How fired reminders are shown, as opposed to when they are due.
     *
     * Its own key rather than a field on [queue], so that changing a notification's duration cannot
     * rewrite the queue document and so that the manager screen's presentation controls and the
     * manager screen's schedule controls can be edited independently without either one clobbering
     * the other's pending write.
     */
    val presentation: StateFlow<NotificationPresentationSettings> = _presentation.asStateFlow()

    /** Begins following the DataStore once; safe to call from both the process service and a screen. */
    fun observe(scope: CoroutineScope) {
        scope.launch {
            settings.changes.collect { stored ->
                _queue.value = decode(stored[QUEUE_KEY], QUEUE_KEY.name, ReminderQueueConfig())
                _queueProgress.value =
                    decode(stored[QUEUE_PROGRESS_KEY], QUEUE_PROGRESS_KEY.name, ReminderQueueProgress())
                _prayerLocation.value =
                    decode(stored[PRAYER_LOCATION_KEY], PRAYER_LOCATION_KEY.name, PrayerLocationSettings())
                _prayerProgress.value =
                    decode(stored[PRAYER_PROGRESS_KEY], PRAYER_PROGRESS_KEY.name, PrayerNotificationProgress())
                _prayerFlags.value = PrayerKey.entries.associateWith { key -> stored[prayerKey(key)] ?: true }
                _startOnLogin.value = stored[START_ON_LOGIN_KEY] ?: false
                _presentation.value =
                    decode(stored[PRESENTATION_KEY], PRESENTATION_KEY.name, NotificationPresentationSettings())
            }
        }
    }

    suspend fun refresh() {
        val stored = settings.changes.first()
        _queue.value = decode(stored[QUEUE_KEY], QUEUE_KEY.name, ReminderQueueConfig())
        _queueProgress.value =
            decode(stored[QUEUE_PROGRESS_KEY], QUEUE_PROGRESS_KEY.name, ReminderQueueProgress())
        _prayerLocation.value =
            decode(stored[PRAYER_LOCATION_KEY], PRAYER_LOCATION_KEY.name, PrayerLocationSettings())
        _prayerProgress.value =
            decode(stored[PRAYER_PROGRESS_KEY], PRAYER_PROGRESS_KEY.name, PrayerNotificationProgress())
        _prayerFlags.value = PrayerKey.entries.associateWith { key -> stored[prayerKey(key)] ?: true }
        _startOnLogin.value = stored[START_ON_LOGIN_KEY] ?: false
        _presentation.value =
            decode(stored[PRESENTATION_KEY], PRESENTATION_KEY.name, NotificationPresentationSettings())
        logger.info { "Restored notification preferences (collections=${_queue.value.collectionIds.size})" }
    }

    suspend fun updateQueue(transform: (ReminderQueueConfig) -> ReminderQueueConfig) =
        mutex.withLock {
            val previous = _queue.value
            val next = transform(previous).validated()
            _queue.value = next
            if (previous != next) {
                val tuningOnly =
                    previous.copy(
                        intervalMinutes = next.intervalMinutes,
                        defaultDropChance = next.defaultDropChance,
                        collectionDropChances = next.collectionDropChances,
                    ) == next
                if (tuningOnly) {
                    logger.atDebug {
                        message = "Saving reminder queue tuning"
                        payload =
                            mapOf(
                                "intervalMinutes" to next.intervalMinutes,
                                "defaultDropChance" to next.defaultDropChance,
                                "collectionDropChanceCount" to next.collectionDropChances.size,
                            )
                    }
                } else {
                    logger.atInfo {
                        message = "Saving reminder queue"
                        payload =
                            mapOf(
                                "enabled" to next.enabled,
                                "mode" to next.mode,
                                "cadence" to next.cadence,
                                "intervalMinutes" to next.intervalMinutes,
                                "activeDayCount" to next.activeDays.size,
                                "collectionCount" to next.collectionIds.size,
                            )
                    }
                }
            }
            settings.edit(QUEUE_KEY.name) { this[QUEUE_KEY] = json.encodeToString(next) }
        }

    /**
     * Adds a collection to the queue's sources, or takes it out.
     *
     * Reads the *stored* document rather than the in-memory copy, and does it under the same lock
     * as [updateQueue], because this is the one write that can arrive before the first read has
     * landed: a user can open the library and click "add to reminders" before the settings file has
     * been read even once. Transforming the in-memory copy then would write a queue that had lost
     * every source the user already had.
     */
    suspend fun setQueueCollectionEnabled(
        collectionId: Long,
        enabled: Boolean,
    ) = mutex.withLock {
        val stored = decode(settings.read(QUEUE_KEY), QUEUE_KEY.name, ReminderQueueConfig())
        val next =
            stored
                .copy(
                    collectionIds =
                        if (enabled) {
                            (stored.collectionIds + collectionId).distinct()
                        } else {
                            stored.collectionIds - collectionId
                        },
                ).validated()
        _queue.value = next
        logger.info { "Saving reminder collection source (collectionId=$collectionId, enabled=$enabled)" }
        settings.edit(QUEUE_KEY.name) { this[QUEUE_KEY] = json.encodeToString(next) }
    }

    suspend fun saveQueueProgress(progress: ReminderQueueProgress) =
        mutex.withLock {
            _queueProgress.value = progress
            settings.edit(QUEUE_PROGRESS_KEY.name) { this[QUEUE_PROGRESS_KEY] = json.encodeToString(progress) }
        }

    suspend fun savePrayerLocation(location: PrayerLocationSettings) =
        mutex.withLock {
            _prayerLocation.value = location
            logger.info {
                "Saving prayer location settings (method=${location.method}, autoDetect=${location.autoDetect})"
            }
            settings.edit(PRAYER_LOCATION_KEY.name) { this[PRAYER_LOCATION_KEY] = json.encodeToString(location) }
        }

    suspend fun savePrayerProgress(progress: PrayerNotificationProgress) =
        mutex.withLock {
            _prayerProgress.value = progress
            settings.edit(PRAYER_PROGRESS_KEY.name) { this[PRAYER_PROGRESS_KEY] = json.encodeToString(progress) }
        }

    suspend fun setPrayerEnabled(
        key: PrayerKey,
        enabled: Boolean,
    ) {
        logger.info { "Saving prayer notification setting (prayer=${key.id}, enabled=$enabled)" }
        settings.edit("prayer.${key.id}.enabled") { this[prayerKey(key)] = enabled }
    }

    suspend fun updatePresentation(transform: (NotificationPresentationSettings) -> NotificationPresentationSettings) =
        mutex.withLock {
            val previous = _presentation.value
            val next = transform(previous).validated()
            _presentation.value = next
            if (previous != next) {
                val sizeAdjustmentOnly =
                    previous.copy(cardWidthDp = next.cardWidthDp, cardHeightDp = next.cardHeightDp) == next &&
                        (previous.cardWidthDp != next.cardWidthDp || previous.cardHeightDp != next.cardHeightDp)
                if (sizeAdjustmentOnly) {
                    logger.debug {
                        "Saving notification card size (${next.cardWidthDp}x${next.cardHeightDp} dp)"
                    }
                } else {
                    logger.atInfo {
                        message = "Saving notification presentation"
                        payload =
                            mapOf(
                                "durationMode" to next.durationMode,
                                "durationSeconds" to next.durationSeconds,
                                "maxVisible" to next.maxVisible,
                                "placement" to next.placement,
                                "cardSizing" to next.cardSizing,
                                "cardWidthDp" to next.cardWidthDp,
                                "cardHeightDp" to next.cardHeightDp,
                            )
                    }
                }
            }
            settings.edit(PRESENTATION_KEY.name) { this[PRESENTATION_KEY] = json.encodeToString(next) }
        }

    suspend fun setStartOnLogin(enabled: Boolean) =
        mutex.withLock {
            _startOnLogin.value = enabled
            logger.info { "Saving start-on-login preference (enabled=$enabled)" }
            settings.edit(START_ON_LOGIN_KEY.name) { this[START_ON_LOGIN_KEY] = enabled }
        }

    private inline fun <reified T> decode(
        value: String?,
        key: String,
        fallback: T,
    ): T =
        value?.let {
            runCatching { json.decodeFromString<T>(it) }
                .onFailure { failure ->
                    logger.warn(failure) { "Unable to decode stored notification setting [$key]; using defaults" }
                }.getOrNull()
        } ?: fallback

    private fun ReminderQueueConfig.validated(): ReminderQueueConfig =
        copy(
            collectionIds = collectionIds.filter { it > 0 }.distinct(),
            windowStartMinute = windowStartMinute.coerceIn(0, 1439),
            windowEndMinute =
                windowEndMinute.coerceIn(0, 1439).let { end ->
                    if (end == windowStartMinute) (end + 1) % 1440 else end
                },
            intervalMinutes = intervalMinutes.coerceIn(5, 1440),
            activeDays = activeDays.ifEmpty { ReminderQueueConfig.ALL_DAYS },
            defaultDropChance = defaultDropChance.coerceIn(0, 100),
            collectionDropChances = collectionDropChances.mapValues { it.value.coerceIn(0, 100) },
        )

    private fun prayerKey(key: PrayerKey) = booleanPreferencesKey("prayer.${key.id}.enabled")

    private companion object {
        val QUEUE_KEY = stringPreferencesKey("notifications.queue.v1")
        val QUEUE_PROGRESS_KEY = stringPreferencesKey("notifications.queue.progress.v1")
        val PRAYER_LOCATION_KEY = stringPreferencesKey("prayer.location.v1")
        val PRAYER_PROGRESS_KEY = stringPreferencesKey("prayer.notification.progress.v1")
        val START_ON_LOGIN_KEY = booleanPreferencesKey("notifications.startOnLogin.enabled")
        val PRESENTATION_KEY = stringPreferencesKey("notifications.presentation.v1")
    }
}
