package com.azkar.domain.repo

/**
 * What this installation remembers about its own library, as distinct from what the library
 * contains.
 *
 * Two facts live here and they are the same kind of fact. [isSeeded] records that the shipped
 * dataset has already been written into the database, so the loader can be a one-shot bootstrap
 * rather than something that has to work out whether its own output is already there. And
 * [favoritesCollectionId] records which collection mirrors the user's stars.
 *
 * Neither belongs in the database. Both outlive it, and outliving the database is not the same
 * thing as being right about it: the favourites id is a pointer to a row, and it only points
 * anywhere while that row is still there, and the gate says the dataset is in place when the
 * database holding it may be the one that was deleted.
 *
 * [forget] is what resolves that, and it is one method rather than two because the two facts have
 * to be dropped together. Leaving the id behind while clearing the gate would have the bootstrap
 * create a new starred shelf and never write down its number; clearing the id while leaving the
 * gate would keep the stars pointing at a row that is gone. Whoever holds the database asks the
 * two questions together, so it can only be told to drop the answers together.
 *
 * Both are per-installation, which is why they are asked for rather than derived: a collection
 * starred on one machine is not a collection starred on another, and the dataset was written once
 * rather than once per machine.
 */
interface LibrarySeedState {
    /** Whether the shipped dataset has already been written into this installation's database. */
    suspend fun isSeeded(): Boolean

    /** Records that the shipped dataset is present, so it is never written a second time. */
    suspend fun markSeeded()

    /**
     * Forgets both answers, for a database they no longer describe.
     *
     * A database can be deleted or replaced while this file is untouched, which leaves the gate
     * claiming a dataset that is not there and the shelf id claiming a row that is not either.
     * Asking the implementation to drop both is the only way to be sure neither half is left
     * describing the previous database.
     */
    suspend fun forget()

    /**
     * The collection that mirrors the user's starred remembrances, or `null` when the user has none
     * of the sort yet.
     */
    suspend fun favoritesCollectionId(): Long?

    /** Records which collection mirrors the user's stars, so its contents can be kept in step. */
    suspend fun setFavoritesCollectionId(collectionId: Long)
}
