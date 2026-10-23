package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationNotice
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The overlay's entire behaviour, expressed as state and rules, with no window in sight.
 *
 * Everything the overlay *is* — a card, a stack, a fade, an AWT frame that will not take focus — is
 * somebody else's problem. What is here is the part that decides what is true: what is currently
 * on screen, how many things may be on screen at once, when each of them has run out of time, and
 * what happens when the scheduler fires the same passage twice.
 *
 * ## Queueing and replacement policy
 *
 * The policy is a bounded stack, and it is worth writing out rather than leaving to the code:
 *
 * - **Cap.** At most `maxVisible` cards are held. The default is three.
 * - **Arrival.** A new card goes to the front of the list. Because the list is newest-first and the
 *   overlay lays it out anchored to the chosen corner, "front" means "closest to that corner", so
 *   the stack grows inwards and never pushes the card the user is reading.
 * - **Replacement.** If accepting a card would exceed the cap, the oldest card is dropped. It is
 *   dropped, not shown-and-then-hidden: a reminder that has already been on screen for a while and
 *   is then covered up reads as an error rather than as a reminder.
 * - **De-duplication.** A notice whose title, message and action all match one already on screen is
 *   refused and [present] answers `null`. This is what stops a reminder that re-fires on a short
 *   interval from stacking three identical cards on top of each other.
 * - **Expiry.** Each card runs its own countdown from the moment it was accepted, and expires on
 *   its own. Two reminders an hour apart therefore never interact.
 * - **Pause.** A hovered card's countdown freezes; leaving resumes it with the remainder intact.
 * - **Dismissal.** Dropping a card has no effect on the scheduler. Progress was committed to the
 *   settings file *before* [present] was ever called, so a card the user closes is a card that was
 *   already delivered.
 *
 * ## Why the expiry loop is a coroutine and not a clock read
 *
 * Each card's remaining time is derived from an absolute deadline, so a pause is just "move the
 * deadline". The loop that enforces it lives in the injected [CoroutineScope] rather than being
 * polled by the UI, which means the cards keep expiring correctly while the overlay window is
 * hidden, paused, or has not been composed yet — and means a test can drive the whole thing on
 * virtual time without a display.
 */
class OverlayNotificationPresenter(
    private val scope: CoroutineScope,
    private val settings: StateFlow<NotificationPresentationSettings>,
    /** Injected so a test can own the clock instead of sleeping. */
    private val now: () -> Long = System::currentTimeMillis,
    private val logger: KLogger = KotlinLogging.logger {},
) : NotificationPresenter {
    private val _notices = MutableStateFlow<List<PresentedNotice>>(emptyList())
    override val notices: StateFlow<List<PresentedNotice>> = _notices.asStateFlow()

    /**
     * Guards the bookkeeping, not the flow.
     *
     * [present] is called from the scheduler's own dispatcher and from the UI thread at the same
     * time, and the two disagreeing about the stack cap would drop the wrong card. [MutableStateFlow]
     * is already safe to write from any thread; this lock is what makes the *read-modify-write*
     * around it atomic, which a flow alone cannot promise.
     */
    private val lock = Any()
    private var nextId = 1L
    private var disposed = false
    private val deadlines = mutableMapOf<Long, Long>()
    private val durations = mutableMapOf<Long, Long>()
    private val frozenRemaining = mutableMapOf<Long, Long>()

    /**
     * Where each retired card's bar was left.
     *
     * A card that has been retired is off the stack but not off the screen: the overlay fades it
     * out, and a card that is fading is still composed and still asking how much time it has. The
     * answer it gets has to be the truth — a card that has run out must not claim a full bar on its
     * way off the screen, which is what made the bar jump back to the top as it was leaving.
     *
     * A map rather than a flag on the card because the card is gone: this is the only record left of
     * where its bar was, and it is kept only until the card has finished fading.
     */
    private val retiredRemaining = LinkedHashMap<Long, Float>()
    private val expiryJobs = mutableMapOf<Long, Job>()

    override fun present(notice: NotificationNotice): Long? =
        synchronized(lock) { acceptLocked(notice, settings.value.validated()) }
            .also { id ->
                if (id != null) {
                    val action =
                        when (val target = notice.action) {
                            is NotificationAction.Remembrance -> "remembrance:${target.id}"
                            NotificationAction.PrayerTimes -> "prayer-times"
                            NotificationAction.OpenApp -> "open-app"
                        }
                    logger.info { "Presented notification (id=$id, action=$action)" }
                } else {
                    logger.debug { "Suppressed notification presentation" }
                }
            }

    private fun acceptLocked(
        notice: NotificationNotice,
        current: NotificationPresentationSettings,
    ): Long? =
        when {
            disposed -> null
            _notices.value.any { it.isSameAnnouncementAs(notice) } -> null
            else -> appendLocked(notice, current)
        }

    private fun appendLocked(
        notice: NotificationNotice,
        current: NotificationPresentationSettings,
    ): Long {
        val id = nextId++
        val retained = _notices.value.take(current.maxVisible - 1)
        _notices.value.drop(current.maxVisible - 1).forEach { retire(it.id) }
        val durationMillis = current.durationFor(notice)
        deadlines[id] = now() + durationMillis
        durations[id] = durationMillis
        frozenRemaining.remove(id)
        _notices.value =
            listOf(
                PresentedNotice(
                    id = id,
                    title = notice.title,
                    message = notice.message,
                    action = notice.action,
                    content = notice.content,
                ),
            ) + retained
        startExpiryLocked(id)
        return id
    }

    override fun dismiss(id: Long) {
        synchronized(lock) {
            val remaining = _notices.value.filterNot { it.id == id }
            if (remaining.size == _notices.value.size) return
            retire(id)
            _notices.value = remaining
            logger.info { "Dismissed notification (id=$id, remaining=${remaining.size})" }
        }
    }

    override fun setPaused(
        id: Long,
        paused: Boolean,
    ) {
        synchronized(lock) {
            val deadline = deadlines[id] ?: return
            if (paused) {
                if (id !in frozenRemaining) {
                    frozenRemaining[id] = (deadline - now()).coerceAtLeast(0L)
                }
            } else {
                frozenRemaining.remove(id)?.let { remaining ->
                    deadlines[id] = now() + remaining
                }
            }
        }
    }

    /**
     * How much of [id]'s time is left, for the bar the card draws along its top edge.
     *
     * Read from the same deadline [setPaused] freezes and [nextWakeMillisLocked] sleeps against, so
     * the bar and the expiry can only ever disagree by the length of a poll.
     *
     * The duration is remembered per card rather than read back out of the difference between "now
     * and the deadline", because a reader who opens the settings and changes the on-screen duration
     * mid-countdown would otherwise watch the bar jump: the card keeps the length it was accepted
     * with, exactly as it keeps the deadline it was accepted with.
     *
     * A card that has been retired answers with the fraction it was left at rather than with the
     * interface's default. The default is right for a card this presenter has never heard of — it
     * has no deadline, so all of its time is left — but it is wrong for a card it *did* have and has
     * just let go of: that card is on its way off the screen and its bar is nearly empty, and
     * answering "all of it" is what made the bar fill up again on the way out.
     */
    override fun remainingFraction(id: Long): Float = synchronized(lock) { remainingFractionLocked(id) }

    private fun remainingFractionLocked(id: Long): Float {
        val duration = durations[id]?.toFloat() ?: return retiredRemaining[id] ?: DEFAULT_REMAINING
        val remaining = frozenRemaining[id] ?: (deadlines[id]?.minus(now()) ?: 0L)
        return (remaining / duration).coerceIn(0f, 1f)
    }

    override fun clear() {
        synchronized(lock) {
            _notices.value.forEach { retire(it.id) }
            _notices.value = emptyList()
        }
    }

    override fun dispose() {
        synchronized(lock) {
            disposed = true
            _notices.value.forEach { retire(it.id) }
            _notices.value = emptyList()
        }
    }

    /** Starts [id]'s countdown. Only ever called with [lock] held. */
    private fun startExpiryLocked(id: Long) {
        expiryJobs.remove(id)?.cancel()
        expiryJobs[id] =
            scope.launch {
                while (isActive) {
                    val wait = nextWakeMillisLocked(id) ?: return@launch
                    // Capped so that a pause is noticed promptly: without the cap a card that
                    // freezes for a minute would still be sleeping on its original deadline and
                    // would vanish the moment the pointer left it.
                    delay(minOf(wait, EXPIRY_POLL_MILLIS))
                    if (isExpiredLocked(id)) {
                        expire(id)
                        return@launch
                    }
                }
            }
    }

    /**
     * How long the expiry loop for [id] should sleep before looking again, or `null` if the card is
     * gone and the loop should end. Only ever called with [lock] held.
     *
     * A frozen card answers with a full poll interval rather than with its remaining time: the
     * remaining time is meaningless while the pointer is on it, and zero would expire it on the
     * very next tick.
     */
    private fun nextWakeMillisLocked(id: Long): Long? {
        val deadline = deadlines[id]
        return when {
            deadline == null -> null
            id in frozenRemaining -> EXPIRY_POLL_MILLIS
            else -> (deadline - now()).coerceAtLeast(0L)
        }
    }

    private fun isExpiredLocked(id: Long): Boolean =
        id in deadlines && id !in frozenRemaining && deadlines.getValue(id) <= now()

    private fun expire(id: Long) {
        synchronized(lock) {
            retire(id)
            _notices.value = _notices.value.filterNot { it.id == id }
            logger.debug { "Expired notification (id=$id, remaining=${_notices.value.size})" }
        }
    }

    /**
     * Cancels [id]'s job and forgets its deadline — but not where its bar was left.
     *
     * The card is on its way off the screen but is still composed for the length of the exit
     * transition, and it will keep asking how much time it has. The answer has to be the truth, so
     * the fraction is remembered before the deadline goes and handed back until the card is
     * forgotten.
     */
    private fun retire(id: Long) {
        expiryJobs.remove(id)?.cancel()
        rememberRetired(id)
        deadlines.remove(id)
        durations.remove(id)
        frozenRemaining.remove(id)
    }

    /**
     * Remembers where [id]'s bar was left, and forgets the oldest card once there are too many.
     *
     * Only ever called with [lock] held.
     */
    private fun rememberRetired(id: Long) {
        retiredRemaining[id] = remainingFractionLocked(id)
        while (retiredRemaining.size > RETIRED_HISTORY) {
            val oldest = retiredRemaining.keys.firstOrNull() ?: return
            retiredRemaining.remove(oldest)
        }
    }

    private companion object {
        /**
         * How often a live card re-checks its deadline.
         *
         * A tenth of a second is well under the eye's tolerance for a countdown that has visibly
         * stopped, and it costs one wake-up per visible card rather than per hidden one.
         */
        const val EXPIRY_POLL_MILLIS = 100L

        /**
         * What a card this presenter has never heard of reports as remaining.
         *
         * Mirrors the interface's own default rather than repeating its number, so that a presenter
         * which grows a real answer later cannot end up disagreeing with the contract it implements.
         */
        const val DEFAULT_REMAINING = 1f

        /**
         * How many retired cards the presenter remembers.
         *
         * A retired card is only ever asked about while it is fading off the screen, and the stack
         * holds three cards by default, so a handful is plenty. The bound is here so that an evening
         * of cards coming and going does not leave a growing map behind.
         */
        const val RETIRED_HISTORY = 16
    }
}
