package com.azkar.ui.library.data

import com.azkar.ui.notifications.ReminderQueueConfig
import com.azkar.ui.notifications.ReminderQueueProgress

/**
 * Which collections the reminder queue draws its passages from, and how the queue is set up.
 *
 * One seam, answered by the notifications side: the queue's source list lives in the notification
 * preferences, and this is what the library reads it through. The library owns *what* is reminded;
 * the settings own *when*.
 *
 * The set is read rather than observed, but the read is snapshot-backed: a caller that reads it
 * inside a composition is recomposed when it changes, so the bell on a card lights the moment the
 * collection is added and goes out the moment it is taken away.
 */
interface ReminderSources {
    /** The ids of the collections the queue currently draws from. */
    val enabledCollectionIds: Set<Long>

    /**
     * The queue's own settings, as the library needs to read them.
     *
     * The whole config rather than the two or three fields the summary draws, because the summary
     * asks the schedule a question — "when is the next slot?" — and the answer depends on the mode,
     * the cadence, the interval, the days and the window together. Reading the record and asking
     * the schedule keeps the library and the runtime asking the same question of the same data.
     */
    val queueConfig: ReminderQueueConfig

    /** The runtime's progress through the current cycle, so the summary can name the next passage. */
    val queueProgress: ReminderQueueProgress

    /**
     * Whether the queue is in weighted mode, which is what decides whether a collection shows a
     * chance of its own.
     */
    val isWeighted: Boolean

    /**
     * Adds a collection to the queue's sources, or takes it out.
     *
     * Adding a collection twice is not an error and adding one that is already there changes
     * nothing: the queue holds a set of ids, not a count of how many times the user asked.
     */
    fun setEnabled(
        collectionId: Long,
        enabled: Boolean,
    )

    /**
     * Sets how likely a collection is to be skipped in a weighted cycle, as a percentage.
     *
     * Only meaningful in weighted mode; in the other modes the number is stored and ignored, so
     * switching back to weighted does not lose what the user had set.
     */
    fun setDropChance(
        collectionId: Long,
        chance: Int,
    )
}
