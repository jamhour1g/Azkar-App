package com.azkar.data.config

import com.azkar.data.seed.CollectionSeed
import com.azkar.data.seed.SeedDataLoader
import com.azkar.data.storage.CoverStorage
import com.azkar.data.table.RemembranceTable
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.repo.CollectionRepository
import com.azkar.domain.repo.LibrarySeedState
import com.azkar.domain.service.CollectionService
import com.azkar.domain.service.TransactionRunner
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.Locale

/**
 * Everything that has to happen to an installation before the library can be read, once.
 *
 * Three jobs, and they are not all the same kind:
 *
 *  - the shipped dataset is written into the database, guarded by [LibrarySeedState.isSeeded];
 *  - the shelf that mirrors the user's stars is created, because it cannot be derived from the
 *    dataset — there are no starred remembrances to begin with;
 *  - cover images nothing points at any more are deleted, which is the one thing here that has to
 *    run every launch rather than once, because replacing a cover or deleting a collection makes a
 *    new orphan every time.
 *
 * The gate is stored outside the database, so it outlives it — and a gate left behind by a database
 * that has since been deleted describes a dataset which is no longer there. The gate is therefore
 * never believed on its own: it is reconciled against the database before it is acted on, and both
 * answers are dropped together when the two disagree. The database, not the settings file, is the
 * authority on what it currently holds.
 *
 * The dataset is written once and only once, and that is a deliberate change of behaviour rather
 * than an optimisation. It used to be re-run on every start, which meant it had to recognise the
 * rows it had written before so it would not undo the user's edits — and recognising them meant
 * carrying an identity on each collection, which is exactly the thing the user is now free to
 * change. Removing the identity means the loader has nothing left to be idempotent against, so the
 * honest answer is to stop running it.
 *
 * The cost, stated plainly: a release that ships new azkar no longer flows into the shipped
 * collections by itself, because nothing is left watching for it. That has to be a migration
 * somebody writes, which shows up in a diff rather than silently doing nothing on the installs that
 * already exist. A collection the user has deleted also stays deleted, rather than coming back.
 */
class LibraryBootstrapper(
    private val transactions: TransactionRunner,
    private val seedState: LibrarySeedState,
    private val collectionService: CollectionService,
    private val collections: CollectionRepository,
    private val covers: CoverStorage,
    private val logger: KLogger = KotlinLogging.logger {},
) {
    /**
     * Serialises the work, because "before the first read" is enforced by callers rather than by this
     * class and more than one of them can arrive at once: the library shelf and the home rail both
     * read collections when they mount, and the shelf's own guard only stops that one screen reading
     * twice. Two concurrent bootstraps would otherwise both see an unseeded installation and both
     * write the dataset.
     */
    private val gate = Mutex()

    /**
     * Brings the installation up to date, then returns; cheap and safe to call on every read.
     *
     * Callers are expected to await this *before* reading the library rather than to race it, which
     * is why it is a plain suspending call rather than something that fires and forgets: a read that
     * did not wait would come back empty on a fresh install and cache that emptiness for the session.
     * See [com.azkar.ui.library.data.DomainCollectionsGateway.fetchSnapshot] for the read path.
     */
    suspend fun bootstrap() = gate.withLock { runOnce() }

    /** The body, called at most once per call to [bootstrap] and always under [gate]. */
    private suspend fun runOnce() {
        val seeded = seedState.isSeeded()
        val datasetIsPresent = isDatasetPresent()

        if (seeded && !datasetIsPresent) {
            logger.warn {
                "The database holds no remembrances although the settings say it was seeded; it " +
                    "has been deleted or replaced since, and is being rebuilt from the shipped dataset"
            }
            seedState.forget()
        }

        if (!seeded || !datasetIsPresent) {
            writeShippedDataset()
            createFavoritesShelf()
            // Recorded last, so a run that fails part way leaves the flag clear and the next launch
            // tries again rather than believing a dataset it never finished writing.
            seedState.markSeeded()
            logger.info { "Shipped library initialization completed" }
        }

        ensureUncategorizedShelf()
        sweepCovers()
    }

    /**
     * Whether the database in front of us actually holds the shipped dataset.
     *
     * The same question [SeedDataLoader] asks before it writes anything, asked here for the opposite
     * reason: the gate is stored outside the database and outlives it, so the database is the only
     * thing that can say whether the gate is still describing anything. Trusting the gate on its own
     * is what let a deleted database come back empty and stay empty.
     *
     * `remembrance` rather than `collection`, because a user can delete a collection and cannot
     * delete a remembrance. Keying on collections would hand the shipped shelves back to anyone who
     * emptied their own library, which is the one outcome the one-shot seed exists to prevent.
     */
    private suspend fun isDatasetPresent(): Boolean = transactions.run { RemembranceTable.selectAll().limit(1).any() }

    /**
     * One transaction, and in this order: the collections resolve their membership through tags that
     * loading the remembrances creates, so a dataset seeded after them would find nothing to resolve.
     */
    private suspend fun writeShippedDataset() {
        transactions.run {
            logger.info { "Writing the shipped library into an empty database" }
            SeedDataLoader.load()
            CollectionSeed.load()
        }
    }

    /**
     * Creates the shelf the user's stars are mirrored into, once.
     *
     * Created here rather than seeded with the catalogue because it is not part of the dataset: it
     * holds whatever the user has starred on this machine, which is a different thing on every
     * machine, and it grows after the dataset has been written.
     *
     * [FAVORITES_TRANSLATIONS] because "not part of the dataset" is about whose contents it holds, not
     * about whether it is shipped content. Nobody typed this name, and it ships in two languages all
     * the same, because the alternative is a home rail with one English word on it in an Arabic app.
     *
     * The guard is the stored id rather than a lookup by name, so a collection the user has renamed
     * is still recognised as the one the stars go into.
     */
    private suspend fun createFavoritesShelf() {
        if (seedState.favoritesCollectionId() != null) {
            return
        }
        val created =
            collectionService.create(
                name = FAVORITES_NAME,
                description = FAVORITES_DESCRIPTION,
                iconKey = FAVORITES_ICON_KEY,
                translations = FAVORITES_TRANSLATIONS,
            )
        val id = checkNotNull(created.id) { "A just-created collection must have an id" }
        seedState.setFavoritesCollectionId(id)
        logger.info { "Created the starred-remembrances shelf as collection $id" }
    }

    /** Ensures even an installation seeded by an older version has the untagged-remembrances shelf. */
    private suspend fun ensureUncategorizedShelf() {
        val storedId = seedState.uncategorizedCollectionId()
        if (storedId != null && collectionService.findById(storedId) != null) return

        val created =
            collectionService.create(
                name = UNCATEGORIZED_NAME,
                description = UNCATEGORIZED_DESCRIPTION,
                iconKey = UNCATEGORIZED_ICON_KEY,
                translations = UNCATEGORIZED_TRANSLATIONS,
            )
        val id = checkNotNull(created.id) { "A just-created Uncategorized collection must have an id" }
        seedState.setUncategorizedCollectionId(id)
        logger.info { "Ensured the Uncategorized shelf as collection $id" }
    }

    /**
     * Removes cover files no collection refers to.
     *
     * Deliberately outside the seeded block: it has to run on every launch, and folding it into the
     * once-ever seed would mean it only ever ran on an install that had one.
     */
    private suspend fun sweepCovers() {
        covers.sweepOrphans(collections.findAllCoverImagePaths())
    }

    private companion object {
        const val FAVORITES_NAME = "Favorites"
        const val FAVORITES_DESCRIPTION = "The remembrances you have starred"
        const val FAVORITES_ICON_KEY = "star"
        const val UNCATEGORIZED_NAME = "Uncategorized"
        const val UNCATEGORIZED_DESCRIPTION = "Remembrances without a category"
        const val UNCATEGORIZED_ICON_KEY = "inbox"

        val FAVORITES_TRANSLATIONS =
            mapOf(
                Locale.forLanguageTag("en") to LocalizedPair(FAVORITES_NAME, FAVORITES_DESCRIPTION),
                Locale.forLanguageTag("ar") to LocalizedPair("المفضّلة", "الأذكار التي حفظتها بنجمة"),
            )

        val UNCATEGORIZED_TRANSLATIONS =
            mapOf(
                Locale.ENGLISH to LocalizedPair(UNCATEGORIZED_NAME, UNCATEGORIZED_DESCRIPTION),
                Locale.forLanguageTag("ar") to LocalizedPair("غير مصنّف", "أذكار بلا تصنيف"),
            )
    }
}
