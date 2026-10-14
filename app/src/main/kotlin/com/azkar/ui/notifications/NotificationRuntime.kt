package com.azkar.ui.notifications

import com.azkar.domain.model.Remembrance
import com.azkar.i18n.RemembranceI18n
import com.azkar.ui.home.name
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.model.CollectionsSnapshot
import com.azkar.ui.notifications.presentation.NotificationPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * Routes a notification activation to the live navigation tree, in two halves.
 *
 * Both halves are needed and neither derives from the other. *Showing the window* is the process's
 * job and is owned by the application root, because the notification may have arrived from the OS's
 * own thread, from the tray, or from the overlay — none of which are inside the app's tree. *Moving
 * the user to the right screen* is the tree's job, and it does not exist at all until
 * [com.azkar.ui.shell.AzkarApp] has composed. Splitting them means neither half has to overwrite the
 * other: the root installs the first once at startup, the tree installs the second on every
 * composition, and [dispatch] always runs the first even when the second is not there yet.
 *
 * Both handlers are expected to be safe to call from a foreign thread and to marshal to whatever
 * thread owns the state they touch; both of the two implementations already do.
 */
class NotificationActionRouter {
    @Volatile
    private var beforeNavigate: (NotificationAction) -> Unit = {}

    @Volatile
    private var navigate: (NotificationAction) -> Unit = {}

    /** Installed once by the application root, before any window exists. */
    fun setBeforeNavigate(handler: (NotificationAction) -> Unit) {
        beforeNavigate = handler
    }

    /** Installed by the navigation tree, and re-installed whenever it recomposes. */
    fun setNavigator(handler: (NotificationAction) -> Unit) {
        navigate = handler
    }

    fun dispatch(target: NotificationAction) {
        beforeNavigate(target)
        navigate(target)
    }
}

/** Process-owned scheduler for the shared remembrance queue and enabled prayer notifications. */
class NotificationRuntime(
    private val preferences: NotificationPreferencesRepository,
    private val collections: CollectionsGateway,
    private val locationDetector: LocationDetector,
    private val publisher: NotificationPublisher,
) {
    fun start(scope: CoroutineScope) {
        preferences.observe(scope)
        scope.launch {
            runCatching { preferences.refresh() }
                .onFailure { logger.error(it) { "Unable to load notification preferences" } }
            refreshDetectedLocation()
                .onFailure { logger.warn(it) { "Unable to detect prayer-time location; keeping the saved location" } }
            while (isActive) {
                runCatching { tick() }
                    .onFailure { logger.error(it) { "Notification scheduler tick failed" } }
                delay(TICK_MILLIS)
            }
        }
    }

    suspend fun refreshDetectedLocation(): Result<PrayerLocationSettings> =
        runCatching {
            val current = preferences.prayerLocation.value
            if (!current.autoDetect) return@runCatching current
            locationDetector
                .detect()
                .copy(method = current.method, autoDetect = true)
                .also { preferences.savePrayerLocation(it) }
        }

    private suspend fun tick() {
        val location = preferences.prayerLocation.value
        val now = LocalDateTime.now(ZoneId.of(location.zoneId))
        deliverPrayerIfDue(now, location)
        deliverQueueIfDue(LocalDateTime.now(), preferences.queue.value)
    }

    private suspend fun deliverPrayerIfDue(
        now: LocalDateTime,
        location: PrayerLocationSettings,
    ) {
        val times = PrayerTimeCalculator.calculate(now.toLocalDate(), location)
        val progress = preferences.prayerProgress.value
        for ((key, time) in times) {
            val dateKey = now.toLocalDate().toString()
            val prayerAt = now.toLocalDate().atTime(time)
            val isWithinGraceWindow =
                !now.isBefore(prayerAt) && !now.isAfter(prayerAt.plusMinutes(PRAYER_GRACE_MINUTES))
            if (
                preferences.prayerFlags.value[key] == true &&
                progress.lastDeliveredLocalDates[key.id] != dateKey &&
                isWithinGraceWindow
            ) {
                val updated =
                    progress.copy(
                        lastDeliveredLocalDates = progress.lastDeliveredLocalDates + (key.id to dateKey),
                    )
                preferences.savePrayerProgress(updated)
                publisher.publish(
                    NotificationNotice(
                        title = key.name(),
                        message = "${key.name()} prayer time • ${location.city}",
                        action = NotificationAction.PrayerTimes,
                    ),
                )
            }
        }
    }

    private suspend fun deliverQueueIfDue(
        now: LocalDateTime,
        config: ReminderQueueConfig,
    ) {
        if (!config.enabled) return
        val slot =
            QueueSchedule.dueSlot(
                now = now,
                startMinute = config.windowStartMinute,
                endMinute = config.windowEndMinute,
                intervalMinutes = config.intervalMinutes,
            ) ?: return
        deliverQueueAtSlot(slot, config)
    }

    @Suppress("ReturnCount")
    private suspend fun deliverQueueAtSlot(
        slot: LocalDateTime,
        config: ReminderQueueConfig,
    ) {
        val progress = preferences.queueProgress.value
        if (progress.lastDeliveredSlot == slot.toString()) return

        val snapshot = collections.fetchSnapshot()
        val sources = resolveQueueSources(snapshot, config) ?: return
        val fingerprint = fingerprint(config, sources.availableIds)
        val cycle = ensureQueueCycle(progress, config, sources, fingerprint)

        if (cycle.cycleOrder.isEmpty()) {
            // Consume an all-skipped weighted cycle once for this interval.
            preferences.saveQueueProgress(cycle.copy(lastDeliveredSlot = slot.toString()))
            return
        }

        val id = cycle.cycleOrder[cycle.nextIndex]
        val remembrance = snapshot.remembrances.firstOrNull { it.id == id } ?: return
        val text = remembrance.toNotificationText()
        preferences.saveQueueProgress(
            cycle.copy(
                nextIndex = cycle.nextIndex + 1,
                lastDeliveredSlot = slot.toString(),
            ),
        )
        publisher.publish(
            NotificationNotice(
                title = "Azkar reminder",
                message = text,
                action = NotificationAction.Remembrance(id),
            ),
        )
    }

    private fun resolveQueueSources(
        snapshot: CollectionsSnapshot,
        config: ReminderQueueConfig,
    ): QueueSources? {
        val selectedCollections = snapshot.collections.filter { it.id in config.collectionIds }
        val memberCollections = mutableMapOf<Long, MutableList<Long>>()
        selectedCollections.forEach { collection ->
            val collectionId = collection.id ?: return@forEach
            collection.members.mapNotNull { it.id }.forEach { remembranceId ->
                memberCollections.getOrPut(remembranceId) { mutableListOf() }.add(collectionId)
            }
        }
        val availableIds =
            (memberCollections.keys + config.individualRemembranceIds)
                .filter { id -> snapshot.remembrances.any { it.id == id } }
                .distinct()
        return if (availableIds.isEmpty()) {
            null
        } else {
            QueueSources(memberCollections, availableIds)
        }
    }

    private suspend fun ensureQueueCycle(
        progress: ReminderQueueProgress,
        config: ReminderQueueConfig,
        sources: QueueSources,
        fingerprint: String,
    ): ReminderQueueProgress {
        if (progress.sourceFingerprint == fingerprint && progress.nextIndex in progress.cycleOrder.indices) {
            return progress
        }
        val order =
            QueueSchedule.cycleOrder(
                itemIds = sources.availableIds,
                mode = config.mode,
                manualOrder = config.manualOrder,
                dropChance = { id ->
                    config.remembranceDropOverrides[id.toString()]?.coerceIn(0, 100)
                        ?: sources.memberCollections[id]
                            ?.mapNotNull { collectionId -> config.collectionDropChances[collectionId.toString()] }
                            ?.minOrNull()
                            ?.coerceIn(0, 100)
                        ?: config.defaultDropChance
                },
            )
        return ReminderQueueProgress(sourceFingerprint = fingerprint, cycleOrder = order)
            .also { preferences.saveQueueProgress(it) }
    }

    private data class QueueSources(
        val memberCollections: Map<Long, List<Long>>,
        val availableIds: List<Long>,
    )

    private fun fingerprint(
        config: ReminderQueueConfig,
        ids: List<Long>,
    ): String =
        listOf(
            ids.sorted().joinToString(","),
            config.mode.name,
            config.manualOrder.joinToString(","),
            config.defaultDropChance,
            config.collectionDropChances.toSortedMap(),
            config.remembranceDropOverrides.toSortedMap(),
        ).joinToString("|")

    private fun Remembrance.toNotificationText(): String {
        val texts = RemembranceI18n.resolveTexts(this, Locale.getDefault(), "", "")
        return (texts.translation.ifBlank { texts.explanation.ifBlank { "A remembrance from your queue" } })
            .replace('\n', ' ')
            .take(MAX_NOTICE_LENGTH)
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val PRAYER_GRACE_MINUTES = 2L
        const val MAX_NOTICE_LENGTH = 180
    }
}
