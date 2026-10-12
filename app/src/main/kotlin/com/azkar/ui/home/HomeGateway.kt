package com.azkar.ui.home

import com.azkar.domain.model.Remembrance
import com.azkar.domain.service.RemembranceService
import com.azkar.ui.library.data.CollectionHomeGateway
import com.azkar.ui.library.data.HomeCollection

/**
 * The database reads the home dashboard needs, behind one injectable interface.
 *
 * These reads used to be spelled out inline inside a `LaunchedEffect`, with the data graph assembled
 * in the middle of a composable. Routing them through this interface keeps the composition function
 * free of data access and lets the effect be exercised without a database.
 */
interface HomeGateway {
    /** Every remembrance marked as a favorite. */
    suspend fun findFavorites(): List<Remembrance>

    /** A single remembrance to show as the daily hadith, or `null` when the library is empty. */
    suspend fun findDailyHadith(): Remembrance?

    /**
     * Every collection the rail could offer: the ones the user pinned, then the rest.
     *
     * Deliberately un-narrowed. Which of these earn a place on the first screen the user sees is a
     * decision about reading, and it lives in [HomeCollections.pick] — one pure function, tested
     * without a database. A gateway that applied it would put a layout rule inside a data read and
     * leave no single place where the rule could be read on its own.
     */
    suspend fun findHomeCollections(): List<HomeCollection>
}

/** Production [HomeGateway], backed by the domain services. */
class DomainHomeGateway(
    private val remembranceService: RemembranceService,
    private val collections: CollectionHomeGateway,
) : HomeGateway {
    override suspend fun findFavorites(): List<Remembrance> = remembranceService.findFavorites()

    /**
     * A read of one row, not a whole table with the ends trimmed off.
     *
     * This used to be `findAll().firstOrNull()`, which assembled every remembrance in the library —
     * both locales of the translation and the explanation, plus tags, for all of them — to keep the
     * one that came first. It is the hadith on the first screen the user sees, so it was the read
     * that made the app feel slow to open, and the cost grew every time a remembrance was added.
     */
    override suspend fun findDailyHadith(): Remembrance? = remembranceService.findFirst()

    /**
     * Delegates rather than reimplementing.
     *
     * The rail's cards are the same read model the library gateway already assembles, including the
     * one grouped recitation query behind the progress figures. A second copy of that read here
     * would be a second set of rules for the same number, free to drift from the first.
     */
    override suspend fun findHomeCollections(): List<HomeCollection> = collections.fetchHomeCollections()
}
