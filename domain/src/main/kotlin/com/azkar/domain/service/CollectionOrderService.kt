package com.azkar.domain.service

import com.azkar.domain.repo.CollectionRepository

/**
 * The user's own order of their collections.
 *
 * Split out of [CollectionService] for the same reason the order was split out of the repository
 * port: the arrangement is not a property of any one collection, so it does not belong beside the
 * fields of the aggregate, and [CollectionService] had grown past the point where every method in it
 * was about a collection's own contents.
 *
 * A write like every other: one transaction, one statement, and nothing else touched.
 */
class CollectionOrderService(
    private val repository: CollectionRepository,
    private val transactions: TransactionRunner,
) {
    /**
     * Writes the user's own order of their collections.
     *
     * [orderedIds] is the whole order, first to last. Ids that are not in it keep the place they
     * had, and ids in it that name no collection are ignored: a drag that ended over a card which
     * has since been deleted should not fail the whole reorder.
     *
     * The ids are checked against the rows before anything is written, so a list holding a stale id
     * writes the order of the collections that do exist rather than failing.
     */
    suspend fun reorderCollections(orderedIds: List<Long>) {
        transactions.run {
            val known = repository.findAll().mapNotNull { it.id }.toSet()
            val order = orderedIds.distinct().filter { it in known }
            if (order.isNotEmpty()) {
                repository.reorder(order)
            }
        }
    }
}
