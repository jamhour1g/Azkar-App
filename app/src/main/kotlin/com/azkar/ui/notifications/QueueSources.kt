package com.azkar.ui.notifications

import com.azkar.domain.model.RemembranceCollection

/**
 * Which passages the queue can draw from, and in what order.
 *
 * The queue has exactly one kind of source: a collection. A passage that is reminded on its own is a
 * member of the reserved "Reminder picks" collection, which is itself one of the queue's
 * collections — so there is no second list of ids to keep in step with the first.
 *
 * The order is the library's order. The selected collections are taken in the order the library
 * lists them — the order the user arranged their shelf in — and each contributes its passages in the
 * order the collection holds them. A passage that belongs to two collections appears once, at the
 * position of the first of them.
 *
 * One implementation, used by the runtime that fires the queue and by the library that previews it,
 * so the preview cannot promise an order the runtime does not keep.
 */
data class QueueSelection(
    /** The passage ids, in recitation order, each one once. */
    val orderedIds: List<Long>,
    /** The collections that hold each passage, in the order they were selected. */
    val owningCollections: Map<Long, List<Long>>,
)

/**
 * Resolves the queue's sources from the library.
 *
 * A function rather than a class because it holds nothing: the selection is a fact about the
 * library and the config at one moment, and the caller owns both.
 */
object QueueSources {
    /**
     * The passages the selected collections hold, in recitation order.
     *
     * The collections are filtered by the config's own source list rather than by a caller's
     * selection, so the same function answers for the runtime and for the preview.
     *
     * [picksCollectionId] is the reserved "Reminder picks" shelf, and it is a source by itself
     * rather than by being listed in the config: a single passage is reminded by joining that
     * shelf, and nothing about picking a passage should have to write a second list of ids
     * somewhere else. An empty shelf contributes nothing, so the rule needs no reconciliation.
     */
    fun select(
        collections: List<RemembranceCollection>,
        config: ReminderQueueConfig,
        picksCollectionId: Long? = null,
    ): QueueSelection {
        val owners = mutableMapOf<Long, MutableList<Long>>()
        val ordered = mutableListOf<Long>()
        collections
            .filter { it.id in config.collectionIds || it.id == picksCollectionId }
            .forEach { collection ->
                val collectionId = collection.id ?: return@forEach
                collection.members.mapNotNull { it.id }.forEach { passageId ->
                    val holders = owners.getOrPut(passageId) { mutableListOf() }
                    if (collectionId !in holders) holders.add(collectionId)
                    if (passageId !in ordered) ordered.add(passageId)
                }
            }
        return QueueSelection(
            orderedIds = ordered,
            owningCollections = owners.mapValues { (_, value) -> value.toList() },
        )
    }
}
