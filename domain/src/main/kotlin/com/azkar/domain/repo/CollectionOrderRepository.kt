package com.azkar.domain.repo

/**
 * The user's own order of their collections.
 *
 * Split out of [CollectionRepository] for the same reason [CollectionHomeRepository] was: the order
 * is not a property of any one collection, so it does not belong to the port that stores them, and
 * carrying it there would make every caller of that port care about an arrangement only the shelf
 * and the queue ever read.
 */
interface CollectionOrderRepository {
    /**
     * Writes the user's own order of their collections.
     *
     * [orderedIds] is the whole order, from first to last. Ids that are not in it are left where
     * they are, and ids that are in it but unknown are ignored: a drag that ended over a card that
     * has since been deleted should not fail the whole reorder.
     *
     * A statement of its own rather than a field of [CollectionRepository.save] for the same reason
     * [CollectionRepository.setFavorite] is: reordering is not an edit to a collection, so a rename
     * must not be the moment the order moves, and a full-row write would make the two inseparable.
     */
    suspend fun reorder(orderedIds: List<Long>)
}
