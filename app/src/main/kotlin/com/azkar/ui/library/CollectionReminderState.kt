package com.azkar.ui.library

import androidx.compose.runtime.Stable
import com.azkar.ui.library.data.ReminderSources
import com.azkar.ui.notifications.ReminderQueueConfig
import com.azkar.ui.notifications.ReminderQueueProgress

/**
 * Which collections are in the reminder queue, as the library sees it.
 *
 * A thin holder over [ReminderSources] rather than a state of its own, because there is nothing to
 * keep in sync: the queue's source list is the truth, and a second copy of it would be a second
 * answer to "is this collection reminded" — and the two would eventually disagree.
 *
 * What the holder adds is a vocabulary. The library talks about collections being *in* the reminders
 * and the notifications side talks about sources being *enabled*, and the two words are the same
 * thing said from opposite ends.
 */
@Stable
class CollectionReminderState(
    private val sources: ReminderSources,
) {
    /** The ids of the collections the queue draws from. */
    val enabledCollectionIds: Set<Long> get() = sources.enabledCollectionIds

    /** The queue's own settings, for the summary strip and the chance stepper. */
    val queueConfig: ReminderQueueConfig get() = sources.queueConfig

    /** The runtime's progress through the current cycle. */
    val queueProgress: ReminderQueueProgress get() = sources.queueProgress

    /** Whether the queue is in weighted mode, which is what decides whether a chance stepper shows. */
    val isWeighted: Boolean get() = sources.isWeighted

    /** Whether [collectionId] is one of the queue's sources. */
    fun contains(collectionId: Long): Boolean = collectionId in sources.enabledCollectionIds

    /**
     * Adds a collection to the queue's sources, or takes it out.
     *
     * Fire-and-forget: the bell on the card is driven by the queue's own state, so it lights when the
     * write lands rather than when the click is handled. A write that fails leaves the card exactly
     * as it was, which is the honest outcome — the collection is still not in the queue.
     */
    fun setEnabled(
        collectionId: Long,
        enabled: Boolean,
    ) {
        sources.setEnabled(collectionId, enabled)
    }

    /** Sets how likely a collection is to be skipped in a weighted cycle, as a percentage. */
    fun setDropChance(
        collectionId: Long,
        chance: Int,
    ) {
        sources.setDropChance(collectionId, chance)
    }

    /** The chance a collection is skipped, falling back to the queue's own default. */
    fun dropChance(collectionId: Long): Int =
        queueConfig.collectionDropChances[collectionId.toString()] ?: queueConfig.defaultDropChance

    companion object {
        /**
         * A state with no sources at all.
         *
         * For the previews and the tests that have no queue behind them. Without it every caller
         * that is not the running app would have to invent a fake source list, and a shelf that
         * showed one lit bell because a fixture had a collection id in it would be a fixture that
         * lies about the app.
         */
        fun empty(): CollectionReminderState = CollectionReminderState(EmptyReminderSources)
    }
}

/**
 * A source list that is always empty and never changes.
 *
 * An object rather than a class so that every empty state is the same instance, which keeps a
 * `remember` key over it stable.
 */
private object EmptyReminderSources : ReminderSources {
    override val enabledCollectionIds: Set<Long> = emptySet()

    override val queueConfig: ReminderQueueConfig = ReminderQueueConfig()

    override val queueProgress: ReminderQueueProgress = ReminderQueueProgress()

    override val isWeighted: Boolean = false

    override fun setEnabled(
        collectionId: Long,
        enabled: Boolean,
    ) = Unit

    override fun setDropChance(
        collectionId: Long,
        chance: Int,
    ) = Unit
}
