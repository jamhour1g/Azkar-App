package com.azkar.ui.notifications

import com.azkar.domain.model.Remembrance
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.notificationReminderBodyFallback
import com.azkar.generated.resources.notificationReminderTitle
import com.azkar.i18n.RemembranceI18n
import com.azkar.ui.components.AzkarClock
import com.azkar.ui.home.PrayerKey
import com.azkar.ui.home.name
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.model.CollectionsSnapshot
import com.azkar.ui.notifications.presentation.NotificationPublisher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

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

    @Volatile
    private var favoriteChanged: (Long, Boolean) -> Unit = { _, _ -> }

    /** Installed once by the application root, before any window exists. */
    fun setBeforeNavigate(handler: (NotificationAction) -> Unit) {
        beforeNavigate = handler
    }

    /** Installed by the navigation tree, and re-installed whenever it recomposes. */
    fun setNavigator(handler: (NotificationAction) -> Unit) {
        navigate = handler
    }

    /**
     * Installed by the library tree, and re-installed whenever it recomposes.
     *
     * A star can be flipped on a surface the library cannot see — the overlay's card — and the
     * library keeps its own copy of the flag, because the tiles it draws come from that copy rather
     * than from the database. Without this the tile behind the overlay would still show the old star,
     * and the next press would write back the value the library believed rather than the one the
     * reader meant, which reads as a button that does nothing.
     */
    fun setFavoriteListener(handler: (Long, Boolean) -> Unit) {
        favoriteChanged = handler
    }

    fun dispatch(target: NotificationAction) {
        beforeNavigate(target)
        navigate(target)
    }

    /**
     * Tells the tree that a star was flipped somewhere it cannot see.
     *
     * Deliberately not part of [dispatch]: dispatching an action *brings the app forward*, and
     * starring a passage is not a request to be taken somewhere. A reader who stars a passage from a
     * reminder and is thrown into the reader screen has been told the wrong thing about what the
     * button does.
     */
    fun dispatchFavoriteChanged(
        id: Long,
        favorite: Boolean,
    ) {
        favoriteChanged(id, favorite)
    }
}

/** Process-owned scheduler for the shared remembrance queue and enabled prayer notifications. */
class NotificationRuntime(
    private val preferences: NotificationPreferencesRepository,
    private val collections: CollectionsGateway,
    private val locationDetector: LocationDetector,
    private val publisher: NotificationPublisher,
    private val logger: KLogger = KotlinLogging.logger {},
) {
    private val startLock = Any()
    private var schedulerJob: Job? = null

    /**
     * Starts the process-owned scheduler once and keeps its polling cadence independent of tick cost.
     *
     * The runtime is a singleton, but its caller lives in a recomposing application root. A second
     * start must not create a second consumer of the same saved queue progress: two loops could both
     * read one slot as unconsumed, race to save the same next index, and make one visible notice stand
     * in for two attempted deliveries. Polls are also anchored to a monotonic clock rather than
     * sleeping for thirty seconds *after* every database read; otherwise a slow read shifts every
     * later check farther from the slot boundary. Work that takes longer than the polling period
     * coalesces to the latest due slot, matching the schedule's existing missed-slot policy.
     */
    fun start(scope: CoroutineScope) {
        synchronized(startLock) {
            if (schedulerJob?.isActive == true) return
            preferences.observe(scope)
            schedulerJob =
                scope.launch {
                    runCatching { preferences.refresh() }
                        .onFailure { logger.error(it) { "Unable to load notification preferences" } }
                    refreshDetectedLocation()
                        .onFailure {
                            logger.warn(
                                it,
                            ) { "Unable to detect prayer-time location; keeping the saved location" }
                        }
                    var nextTickNanos = System.nanoTime()
                    while (isActive) {
                        runCatching { tick() }
                            .onFailure { logger.error(it) { "Notification scheduler tick failed" } }
                        nextTickNanos += TICK_NANOS
                        val remainingNanos = nextTickNanos - System.nanoTime()
                        if (remainingNanos > 0L) {
                            delay(remainingNanos / NANOS_PER_MILLI)
                        } else {
                            nextTickNanos = System.nanoTime()
                        }
                    }
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
                val content = buildPrayerContent(key, time, location)
                publisher.publish(
                    NotificationNotice(
                        title = content.prayer,
                        message =
                            buildString {
                                append(content.time)
                                append(TIME_CITY_SEPARATOR)
                                append(content.city)
                            },
                        action = NotificationAction.PrayerTimes,
                        content = content,
                    ),
                )
            }
        }
    }

    /**
     * Answers everything the prayer card draws, in the language the user is reading.
     *
     * The time is formatted against the same locale rather than against the host's: a reader who set
     * the app to Arabic is reading twelve-hour times with Arabic numerals and an English one is not,
     * and a clock in the wrong locale is the kind of detail that makes a whole screen look borrowed.
     */
    private suspend fun buildPrayerContent(
        key: PrayerKey,
        time: LocalTime,
        location: PrayerLocationSettings,
    ): NotificationContent.Prayer =
        NotificationContent.Prayer(
            prayer = key.name(),
            time = AzkarClock.format(time, Locale.getDefault()),
            city = location.city,
        )

    /**
     * Delivers the passage this moment's slot is for, if there is one.
     *
     * [QueueCadence] is read once here and decides whether the daily window applies, rather than being
     * pushed down into [QueueSchedule] as another argument. A continuous queue has no window to be
     * outside of, so handing its arithmetic a start and an end would have meant inventing a window to
     * immediately ignore — and inventing one is how a schedule that never stops ends up stopping at
     * half past ten, with the preview counting down to tomorrow because the preview asked the same
     * question.
     */
    private suspend fun deliverQueueIfDue(
        now: LocalDateTime,
        config: ReminderQueueConfig,
    ) {
        val interval = config.effectiveIntervalMinutes
        val slot =
            when {
                !config.enabled -> null
                !QueueSchedule.isDayActive(now, config.activeDays) -> null
                config.cadence == QueueCadence.CONTINUOUS ->
                    ContinuousSchedule.dueSlot(now = now, intervalMinutes = interval)

                else ->
                    QueueSchedule.dueSlot(
                        now = now,
                        startMinute = config.windowStartMinute,
                        endMinute = config.windowEndMinute,
                        intervalMinutes = interval,
                    )
            } ?: return
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

        publishNextPassage(snapshot, cycle, slot)
    }

    /**
     * Commits the index the passage occupied and then publishes it.
     *
     * The progress is written *before* the publish, and that order is the whole of the scheduler's
     * promise: a crash between the two loses a reminder rather than repeating one, and a repeated
     * reminder is the failure a reader would actually notice.
     *
     * @return whether a passage was published. `false` means the cycle named an index it does not
     *   have a passage for — the queue was edited between the cycle being built and being read.
     */
    private suspend fun publishNextPassage(
        snapshot: CollectionsSnapshot,
        cycle: ReminderQueueProgress,
        slot: LocalDateTime,
    ): Boolean {
        val next =
            cycle.cycleOrder
                .getOrNull(cycle.nextIndex)
                ?.let { id -> snapshot.remembrances.firstOrNull { it.id == id }?.let { it to id } }
        return next?.let { (remembrance, id) ->
            val locale = reminderLocale()
            val content = buildRemembranceContent(remembrance, cycle, locale)
            preferences.saveQueueProgress(
                cycle.copy(
                    nextIndex = cycle.nextIndex + 1,
                    lastDeliveredSlot = slot.toString(),
                ),
            )
            publisher.publish(
                NotificationNotice(
                    title = getString(Res.string.notificationReminderTitle),
                    message = remembrance.toNoticeText(locale),
                    action = NotificationAction.Remembrance(id),
                    content = content,
                ),
            )
            true
        } ?: false
    }

    /**
     * The language a reminder's *text* is written in.
     *
     * The reader's own choice when they have made one, and the interface language otherwise. It is
     * deliberately not the interface language unconditionally: this whole setting exists because the two
     * can legitimately differ, and a reader who reads the app in English may still want the passage they
     * are being reminded of in Arabic. Only the passage follows it — the title and the prayer names are
     * the app's own voice and stay in the interface language.
     */
    private fun reminderLocale(): Locale = preferences.presentation.value.notificationLocale ?: Locale.getDefault()

    /**
     * Resolves the passage into the two things the card draws: the passage, and how far through it
     * the reader is.
     *
     * Which collection a passage belongs to is deliberately not resolved. It used to be, and the card
     * printed it in a pill at the top of the header, which made a reminder into a card *about* a
     * passage: the reader had to read a queue's provenance before the reminder could become the thing
     * the queue was reminding them of. Everything that pill said — which collection, which grade,
     * which source, which tags, whether it is kept — is answered by the screen the click opens, and
     * answering it here meant the passage had to share a card with it.
     */
    private suspend fun buildRemembranceContent(
        remembrance: Remembrance,
        cycle: ReminderQueueProgress,
        locale: Locale,
    ): NotificationContent.Remembrance =
        NotificationContent.Remembrance(
            text = remembrance.passageText(locale),
            position = cycle.nextIndex + 1,
            total = cycle.cycleOrder.size,
            isFavorite = remembrance.isFavorite,
        )

    /**
     * Keeps or unkeeps a passage, on behalf of the card that is showing it.
     *
     * The overlay's card is the one place in the app where a star can be pressed without the library
     * being on screen at all, so the write has to work from the runtime rather than from a screen
     * state that may never have been composed. The runtime already holds the same
     * [CollectionsGateway] the library writes through, which is what makes this the same write rather
     * than a second, parallel one: the flag lands in the column *and* the passage joins or leaves
     * the starred mirror, exactly as it would from the library.
     *
     * Reported as a plain [Boolean] because the caller has something to do with the failure: the card
     * flips its star before the write, and a star that stays flipped after a failed write is a lie
     * the reader cannot see through.
     */
    suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    ): Boolean =
        runCatching { collections.setFavorite(id, favorite) }
            .onFailure { failure ->
                logger.error(failure) { "Unable to persist the favorite flag for remembrance $id" }
            }.isSuccess

    /**
     * The queue's sources, in the order the library holds them.
     *
     * The selection itself is [QueueSources]' work, shared with the library's preview so the two
     * cannot disagree; this only drops the passages the catalogue has not loaded, which is the one
     * thing the runtime knows that the selection does not.
     */
    private fun resolveQueueSources(
        snapshot: CollectionsSnapshot,
        config: ReminderQueueConfig,
    ): ResolvedQueueSources? {
        val selection = QueueSources.select(snapshot.collections, config, snapshot.reminderPicksCollectionId)
        val availableIds = selection.orderedIds.filter { id -> snapshot.remembrances.any { it.id == id } }
        return if (availableIds.isEmpty()) {
            null
        } else {
            ResolvedQueueSources(selection.owningCollections, availableIds)
        }
    }

    private suspend fun ensureQueueCycle(
        progress: ReminderQueueProgress,
        config: ReminderQueueConfig,
        sources: ResolvedQueueSources,
        fingerprint: String,
    ): ReminderQueueProgress {
        if (progress.sourceFingerprint == fingerprint && progress.nextIndex in progress.cycleOrder.indices) {
            return progress
        }
        val order =
            QueueSchedule.cycleOrder(
                itemIds = sources.availableIds,
                mode = config.mode,
                dropChance = { id ->
                    sources.memberCollections[id]
                        ?.mapNotNull { collectionId -> config.collectionDropChances[collectionId.toString()] }
                        ?.minOrNull()
                        ?.coerceIn(0, 100)
                        ?: config.defaultDropChance
                },
            )
        return ReminderQueueProgress(sourceFingerprint = fingerprint, cycleOrder = order)
            .also { preferences.saveQueueProgress(it) }
    }

    private data class ResolvedQueueSources(
        val memberCollections: Map<Long, List<Long>>,
        val availableIds: List<Long>,
    )

    /**
     * What the current cycle was built from.
     *
     * The ids are joined in order rather than sorted, because the order is part of the source now:
     * rearranging the library changes the sequence the queue recites, and a sorted fingerprint would
     * not notice.
     */
    private fun fingerprint(
        config: ReminderQueueConfig,
        ids: List<Long>,
    ): String =
        listOf(
            ids.joinToString(","),
            config.mode.name,
            config.defaultDropChance,
            config.collectionDropChances.toSortedMap(),
        ).joinToString("|")

    /**
     * The passage in [locale], which is the reminder language the reader chose.
     *
     * Resolved the same way every other string on screen is, through [RemembranceI18n]: the requested
     * language if the passage has it, the same language under a different regional tag if not, and only
     * then the other one it does have. Pinning this to Arabic — which an earlier revision did, because a
     * dhikr *is* Arabic — would answer an English reader with a passage they had just told the app they
     * did not want to read. What language somebody wants their adhkar in is a setting they have already
     * made, and a reminder that ignores it is nagging in a language they chose not to speak.
     *
     * The fallback is a sentence rather than an empty card because the card is the one place with no
     * room to explain itself: a blank box in the corner is read as a fault, and the sentence is what
     * the app says everywhere else it has nothing to show. It is drawn from resources, and so follows
     * the interface language rather than [locale]: it is the app speaking, not the passage, and it only
     * appears when a passage has no text in any language it was stored with.
     */
    private suspend fun Remembrance.passageText(locale: Locale): String =
        RemembranceI18n
            .resolveTexts(
                remembrance = this,
                preferredLocale = locale,
                translationFallback = "",
                explanationFallback = "",
            ).translation
            .ifBlank { getString(Res.string.notificationReminderBodyFallback) }

    /**
     * A passage clipped to the one line a notice's `message` is allowed to be.
     *
     * Newlines go before the clip rather than after it, because collapsing them is what makes the
     * remaining characters contiguous: a passage stored as a wrapped block would otherwise lose its
     * tail to [MAX_NOTICE_LENGTH] mid-sentence.
     */
    private suspend fun Remembrance.toNoticeText(locale: Locale): String =
        passageText(locale).replace('\n', ' ').take(MAX_NOTICE_LENGTH)

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val TICK_NANOS = TICK_MILLIS * NANOS_PER_MILLI
        const val PRAYER_GRACE_MINUTES = 2L
        const val MAX_NOTICE_LENGTH = 180

        const val TIME_CITY_SEPARATOR = " • "
    }
}
