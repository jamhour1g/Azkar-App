package com.azkar.ui.library.data

import com.azkar.data.config.LibraryBootstrapper
import com.azkar.data.storage.CoverStorage
import com.azkar.domain.model.CollectionSummary
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.RecitationProgress
import com.azkar.domain.model.RecitationSummary
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.Remembrance.Translations
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import com.azkar.domain.model.impl.RemembranceImpl
import com.azkar.domain.model.impl.TagImpl
import com.azkar.domain.model.withMembers
import com.azkar.domain.repo.LibrarySeedState
import com.azkar.domain.service.CollectionOrderService
import com.azkar.domain.service.CollectionService
import com.azkar.domain.service.RecitationService
import com.azkar.domain.service.RemembranceService
import com.azkar.ui.library.data.CollectionPresenter.Companion.ARABIC_LOCALE
import com.azkar.ui.library.data.CollectionPresenter.Companion.ENGLISH_LOCALE
import com.azkar.ui.library.model.CollectionsSnapshot
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.time.Instant
import java.util.Locale

/**
 * Returns a copy of this remembrance with a different favorite flag, leaving every other field
 * untouched.
 *
 * Lets the UI apply a toggle locally instead of re-reading the whole catalogue from the database.
 */
fun Remembrance.withFavorite(favorite: Boolean): Remembrance =
    RemembranceImpl(
        id = id,
        grade = grade,
        isFavorite = favorite,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt,
        translations = translations,
        tags = tags,
    )

/**
 * A copy of this remembrance whose Arabic text reads [arabic] and whose English reads [english],
 * leaving the attribution, the grade, the favourite flag and every other field alone.
 *
 * An [english] of `null` keeps the translation that is already there rather than erasing it: the
 * editor's translation box is optional, and a user who fixes a typo in the Arabic has not said the
 * English was wrong. Passing an empty string is a different instruction and does clear it.
 *
 * The pair ids ride along untouched so a save updates the two existing rows rather than inserting
 * new ones, which is what keeps a corrected passage from turning into a second copy of itself.
 */
fun Remembrance.withTexts(
    arabic: String,
    english: String?,
): Remembrance {
    fun retexted(
        current: Translations?,
        text: String,
    ): Translations {
        val base = current ?: Translations(Translations.Pair(null, ""), Translations.Pair(null, ""))
        return base.copy(translationPair = base.translationPair.copy(text = text))
    }
    val patched =
        translations +
            (ARABIC_LOCALE to retexted(translations[ARABIC_LOCALE], arabic)) +
            (
                if (english == null) {
                    emptyMap()
                } else {
                    mapOf(ENGLISH_LOCALE to retexted(translations[ENGLISH_LOCALE], english))
                }
            )
    return RemembranceImpl(
        id = id,
        grade = grade,
        isFavorite = isFavorite,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt,
        translations = patched,
        tags = tags,
    )
}

/**
 * A copy of this collection whose member [remembranceId] carries the edited texts, and whose other
 * members are left exactly as they were.
 *
 * The same reason [withMemberFavorite] exists, and it must be remembered every time a field is
 * added to a member: a collection holds its own instances, so a patch that only touches the
 * catalogue's flat list leaves every card on a collection page showing the old text until
 * something reloads the page.
 */
fun RemembranceCollection.withMemberTexts(
    remembranceId: Long,
    arabic: String,
    english: String?,
): RemembranceCollection =
    if (members.none { it.id == remembranceId }) {
        this
    } else {
        withMembers(members.map { if (it.id == remembranceId) it.withTexts(arabic, english) else it })
    }

/**
 * A copy of this collection whose member [remembranceId] carries [favorite], and whose other members
 * are left exactly as they were.
 *
 * The counterpart to [Remembrance.withFavorite], and it is needed because a collection holds its *own*
 * copy of each member rather than a reference into the catalogue's flat list. Stamping the flag over
 * there therefore does nothing for a member read out of here — which is how a star could be written to
 * the database and show up correctly in search and in the reader, and still refuse to fill in on the
 * very card it was pressed on.
 *
 * Returns the receiver unchanged when it does not hold that member, so the common case of a
 * collection of twenty unrelated remembrances costs one scan and no rebuild.
 */
fun RemembranceCollection.withMemberFavorite(
    remembranceId: Long,
    favorite: Boolean,
): RemembranceCollection =
    if (members.none { it.id == remembranceId }) {
        this
    } else {
        withMembers(members.map { if (it.id == remembranceId) it.withFavorite(favorite) else it })
    }

/**
 * How far through a collection the user has got — the counting half of the library's storage needs.
 *
 * A separate interface from [CollectionsGateway] because it is a different kind of question about
 * the same data: the shelf is about *which* remembrances are grouped together, this is about *how
 * many times* they have been said. Splitting them lets a caller that only renders counts depend on
 * three methods instead of fourteen, and it is where the recitation-rework stage will grow without
 * the shelf-crud list having to grow with it.
 */
interface CollectionProgressGateway {
    /** Returns how far through the collection the user has got, one entry per counted member. */
    suspend fun fetchProgress(collectionId: Long): List<RecitationProgress>

    /**
     * Adds each value of [deltas] to the matching member's count and returns how many changed.
     *
     * Deltas rather than whole counts because the session screen taps without reading first, and a
     * tap that read would lose a repeat to the next tap arriving at the same time.
     */
    suspend fun recordRecitation(
        collectionId: Long,
        deltas: Map<Long, Int>,
    ): Int

    /** Forgets the collection's progress, returning how many members were reset. */
    suspend fun resetProgress(collectionId: Long): Int
}

/**
 * Putting a collection on the Home screen's rail.
 *
 * A fourth concern, split for the same reason as the other two: the Home screen reads this and
 * nothing else of the library's storage, and keeping it apart means the Home gateway depends on
 * two methods rather than fourteen.
 *
 * Allowed on a collection we ship as well as one the user owns. Being on Home is a per-device
 * preference — the same family as the shelf pin and the cover art — and it cannot contradict the
 * catalogue, because membership is still resolved from the collection's own system key.
 */
interface CollectionHomeGateway {
    /** Puts [id] on the Home rail or takes it off, whichever the boolean says. */
    suspend fun setCollectionOnHome(
        id: Long,
        onHome: Boolean,
    ): RemembranceCollection

    /**
     * Every collection the Home rail could offer: the ones pinned to it, then the rest.
     *
     * A dedicated read rather than a filter over [CollectionsGateway.fetchSnapshot] because the rail
     * shows a handful of cards; loading every member of every collection to pick three of them is
     * the wrong shape of query, and the Home screen is the first thing the app paints.
     *
     * Deliberately un-narrowed and un-ordered. Which of these earn a place on that screen is a
     * decision about reading, and it belongs in [HomeCollections.pick] — one pure function that can
     * be read and tested on its own. A gateway that applied it would bury a layout rule inside a
     * data read.
     */
    suspend fun fetchHomeCollections(): List<HomeCollection>
}

/**
 * Editing what is inside a collection.
 *
 * A sixth concern, split for the same reason as the other five: the member picker reaches only for
 * these two methods, while the shelf screen reads and writes the collection row itself. Keeping them
 * apart means neither caller is handed the whole library to get at a list of identifiers.
 *
 * Supertype rather than a sibling so that every existing [CollectionsGateway] parameter — the state
 * classes, the previews, the test fixtures — keeps taking one argument. Kotlin rejects an
 * intersection type on a constructor parameter, which is what a sibling would have required.
 *
 * There used to be a third method here, one that pinned a member as the collection's cover. A cover
 * is an image the user picks now, so the picker this interface was split for is gone entirely rather
 * than reduced to two methods.
 */
interface CollectionMembershipGateway {
    /** Appends remembrances to a collection, returning how many were not already members. */
    suspend fun addMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int

    /** Removes remembrances from a collection, returning how many were members. */
    suspend fun removeMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int

    /**
     * Moves the member at [fromIndex] to [toIndex], leaving the rest in order around it.
     *
     * Indices rather than identifiers, because the thing being ordered is the sequence and not the
     * members: two drags that ask for the same pair of positions mean the same thing whether the user
     * reached them in one gesture or two. The repository renumbers every row it displaces so the
     * sequence stays dense, and rejects indices that are not on it.
     */
    suspend fun moveMember(
        collectionId: Long,
        fromIndex: Int,
        toIndex: Int,
    )
}

/**
 * Writing a collection, and the cover image that goes with it.
 *
 * A seventh concern, split for the same reason as the other six: the collection dialogs are the only
 * caller of any of this, and the shelf screen and the member picker never create, edit, duplicate or
 * delete a row. Handing either of them eight write methods it would refuse to call is the shape of a
 * dependency that quietly grows, because nothing stops the next method from being added to the same
 * place.
 *
 * Supertype of [CollectionsGateway] rather than a sibling, for the reason given on
 * [CollectionMembershipGateway]: Kotlin rejects an intersection type on a constructor parameter, and
 * every state class, preview and fixture already takes one gateway argument.
 *
 * Every method returns the collection as stored, or the name of the file the cover now lives under,
 * rather than nothing. That is what lets the dialog seed its next open from what was actually
 * written instead of from what it hoped it wrote.
 */
interface CollectionWriteGateway {
/**
     * Creates a collection and returns it.
     *
     * [tagNames] are resolved to real tags on the way in, creating any that do not exist yet, so a
     * caller types what it means rather than looking up an id first. [coverImagePath] is the name of a
     * file the app already owns — see [storeCoverImage] — or `null` for the generated wash.
     */
    suspend fun createCollection(
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair> = emptyMap(),
        nameLocale: Locale = Locale.ENGLISH,
    ): RemembranceCollection

/**
     * Writes every editable field of a collection and returns it as stored.
     *
     * Takes the whole desired state rather than only what changed: the editor dialog opens seeded with
     * the collection's current values, so "leave the cover alone" and "remove the cover" are both
     * ordinary values of [coverImagePath] rather than a tri-state the signature would have to invent
     * for them.
     */
    suspend fun editCollection(
        id: Long,
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>? = null,
        nameLocale: Locale? = null,
        tagLocale: Locale? = null,
    ): RemembranceCollection

/**
     * Takes a copy of [source] into the app's own cover storage and returns the name to store.
     *
     * Separate from [createCollection] and [editCollection] because the file has to be copied before
     * anything references it — a collection pointing at a file the app does not own would break the
     * moment the user moved or deleted the original — and because the dialog wants the copy in hand
     * while it is still open, so it can show the picture it is about to save.
     */
    suspend fun storeCoverImage(source: Path): String

/** Pins a collection to the top of the shelf, or unpins it, returning it as stored. */
    suspend fun setCollectionPinned(
        id: Long,
        pinned: Boolean,
    ): RemembranceCollection

/**
     * Stars a collection the user comes back to, or takes the star off, returning it as stored.
     *
     * Named for its subject rather than simply `setFavorite` because [setFavorite] above already
     * means the same word about a *remembrance*, and two favourites in one interface — one starred
     * passage, one starred collection — is exactly the pair of names that gets a caller wrong.
     */
    suspend fun setCollectionFavorite(
        id: Long,
        favorite: Boolean,
    ): RemembranceCollection

/** Copies a collection into a new one and returns the copy. */
    suspend fun duplicateCollection(
        id: Long,
        newName: String,
        nameLocale: Locale = Locale.ENGLISH,
    ): RemembranceCollection

/** Deletes a collection, and the cover image it owned if it had one. */
    suspend fun deleteCollection(id: Long)

/** Records that the collection was opened just now, so the shelf can lead with it. */
    suspend fun touchCollectionOpened(id: Long)

    /**
     * Writes the user's own order of their collections.
     *
     * The list is the order the collections should sit in, most important first. Ids the library does
     * not know are ignored, so a stale drag cannot write a row that does not exist.
     */
    suspend fun reorderCollections(orderedIds: List<Long>)
}

/**
 * The passages the user asked to be reminded about one at a time.
 *
 * A single passage is reminded by joining the reserved "Reminder picks" shelf — a real collection,
 * created the first time something is picked and deleted when the last pick goes. It is a source by
 * itself rather than by being listed in the queue's config, so picking a passage writes one row and
 * unpicking it writes one row, and there is no second list of ids to keep in step with the first.
 *
 * The shelf is the same kind of thing the starred mirror is: a consequence of what the user did,
 * rather than a thing the user did directly. That is why these are writes to membership and not to
 * the queue's settings.
 */
interface ReminderPicksGateway {
    /**
     * Picks a passage for reminding, or unpicks it, and reports whether it is picked afterwards.
     *
     * One call rather than two, because the state the passage is in is a fact the caller has to
     * read back rather than assume: the shelf may not exist yet, and the passage may already be in it.
     */
    suspend fun setReminderPick(
        remembranceId: Long,
        picked: Boolean,
    ): Boolean
}

/**
 * Everything the library needs from the database, behind one injectable interface.
 *
 * The screen used to reach for the domain services directly from inside its state mutators, which
 * put a service locator in the middle of the UI layer and made every one of those mutators
 * impossible to exercise without a live database. Depending on this interface instead lets the
 * state machine be driven by a fake in tests.
 *
 * Mutating calls return the affected collection, or the number of rows changed, rather than nothing.
 * That is what lets the state layer tell "added three" from "added nothing because they were already
 * members" and say so, instead of reloading the shelf to find out.
 *
 * Extends the four concern interfaces above it — [CollectionWriteGateway],
 * [CollectionProgressGateway], [CollectionHomeGateway] and [CollectionMembershipGateway]: creating
 * and editing a collection, how many times a passage has been said, which collection the Home screen
 * leads with and how its contents are edited are all part of what the library needs from the database,
 * and having the state classes take one dependency rather than five is worth more than a longer list of
 * method names on one interface.
 *
 * What is left here is the catalogue itself: read the shelf, mark a passage, write a passage's texts,
 * take a passage away. Those four have no narrower caller to be split out for, so they stay.
 */
interface CollectionsGateway :
    CollectionWriteGateway,
    CollectionProgressGateway,
    CollectionHomeGateway,
    CollectionMembershipGateway,
    ReminderPicksGateway {
    /** Reads the whole shelf plus the catalogue behind it. */
    suspend fun fetchSnapshot(): CollectionsSnapshot

    /** Marks or unmarks a single remembrance. Must not require a reload to be observed. */
    suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    )

    /**
     * Writes the corrected texts of an existing remembrance and returns it as stored.
     *
     * Takes the whole remembrance rather than the two strings because it is the same shape as the
     * collection editor's write, and because the repository decides for itself whether a row is
     * being inserted or replaced. The caller has already built the desired state; this is the one
     * call that commits it.
     *
     * Not an upsert in the UI's hands. An id that is not in the catalogue is a bug in the caller and
     * the write simply finds nothing to change, because a "save" that silently creates a new passage
     * when the old one was meant is worse than one that does nothing.
     */
    suspend fun saveRemembrance(remembrance: Remembrance): Remembrance

    /** Permanently removes a remembrance and all of its collection memberships. */
    suspend fun deleteRemembrance(id: Long)
}

/**
 * What [DomainCollectionsGateway] needs to be the answer to.
 *
 * A bag of dependencies rather than a seven-line constructor parameter list: the constructor is the
 * DI seam, and the seam must not become a place where adding a concern quietly becomes a change to
 * every composition root that assembles it.
 */
data class CollectionsGatewayDependencies(
    val remembranceService: RemembranceService,
    val collectionService: CollectionService,
    val orderService: CollectionOrderService,
    val recitationService: RecitationService,
    val bootstrapper: LibraryBootstrapper,
    val covers: CoverStorage,
    val seedState: LibrarySeedState,
)

/**
 * Production [CollectionsGateway], backed by the domain services over Exposed.
 *
 * The services arrive through the constructor — from `appModule` in production, and from a fake in
 * a test — rather than being assembled per call. There is no `Dispatchers.IO` hop here on purpose:
 * the domain ports are `suspend` and the data layer moves itself off the caller's thread, so the
 * UI's only job is to stay inside a coroutine scope.
 *
 * [bootstrapper] is here rather than called from `main` because the read has to wait for it. A fresh
 * install has an empty `collection` table until the shipped dataset is written, and the shelf's read
 * guard means a read that arrives too early is not retried — it would show an empty library for the
 * whole session and look like a working app with nothing in it. Waiting here also means the home rail
 * and the shelf cannot disagree about whether the library has been loaded, and that a test driving
 * this gateway through its interface gets the same guarantee a user does.
 */
class DomainCollectionsGateway(
    private val dependencies: CollectionsGatewayDependencies,
    private val logger: KLogger = KotlinLogging.logger {},
) : CollectionsGateway {
    private val remembranceService = dependencies.remembranceService
    private val collectionService = dependencies.collectionService
    private val orderService = dependencies.orderService
    private val recitationService = dependencies.recitationService
    private val bootstrapper = dependencies.bootstrapper
    private val covers = dependencies.covers
    private val seedState = dependencies.seedState

    override suspend fun fetchSnapshot(): CollectionsSnapshot {
        bootstrapper.bootstrap()
        val remembrances =
            remembranceService
                .findAll()
                .sortedByDescending { it.id ?: Long.MIN_VALUE }
        val uncategorizedId =
            checkNotNull(seedState.uncategorizedCollectionId()) {
                "Bootstrap completed without recording the Uncategorized shelf"
            }
        var collections = collectionService.findAll()
        val uncategorized =
            checkNotNull(collections.firstOrNull { it.id == uncategorizedId }) {
                "The recorded Uncategorized shelf $uncategorizedId is missing"
            }
        val expectedUncategorizedIds =
            CollectionFiltering.uncategorizedRemembranceIds(
                remembrances = remembrances,
                collections = collections,
                uncategorizedCollectionId = uncategorizedId,
            )
        val currentUncategorizedIds = uncategorized.members.mapNotNull { it.id }.toSet()
        val removals = currentUncategorizedIds - expectedUncategorizedIds
        val additions = expectedUncategorizedIds - currentUncategorizedIds
        if (removals.isNotEmpty()) collectionService.removeMembers(uncategorizedId, removals.toList())
        if (additions.isNotEmpty()) collectionService.addMembers(uncategorizedId, additions.toList())
        if (removals.isNotEmpty() || additions.isNotEmpty()) {
            collections = collectionService.findAll()
        }
        val picksId = reconcilePicksShelf(collections)
        if (picksId != null) {
            collections = collectionService.findAll()
        }
        // One grouped read of the progress table for every collection on the shelf, because
        // every card now draws how far through its collection the user is. Reading it one
        // collection at a time would be a query per card on the screen the app opens first.
        val recitedCounts =
            recitationService
                .findSummaries(collections.mapNotNull { it.id })
                .mapValues { (_, summary) -> summary.recitedCount }
        return CollectionsSnapshot(
            collections = collections,
            remembrances = remembrances,
            recitedCounts = recitedCounts,
            refreshedAt = Instant.now(),
            favoritesCollectionId = seedState.favoritesCollectionId(),
            uncategorizedCollectionId = uncategorizedId,
            reminderPicksCollectionId = picksId,
        )
    }

    /**
     * Tidies the reserved "Reminder picks" shelf and reports whether it survives.
     *
     * The shelf exists exactly while it holds something. An empty one is deleted, because a shelf
     * that reminds about nothing is a row the user has to look at and wonder about, and because the
     * queue's "in reminders" answer would otherwise have to count its members to know.
     *
     * A recorded id whose row is gone is a settings file that outlived its database — the
     * installation was replaced under the app — and the id is dropped rather than the row recreated,
     * because recreating it would be a guess about what the user had picked.
     */
    private suspend fun reconcilePicksShelf(collections: List<RemembranceCollection>): Long? {
        val recorded = seedState.reminderPicksCollectionId() ?: return null
        val shelf = collections.firstOrNull { it.id == recorded }
        val survives =
            when {
                shelf == null -> {
                    seedState.clearReminderPicksCollectionId()
                    false
                }
                shelf.members.isEmpty() -> {
                    collectionService.deleteById(recorded)
                    seedState.clearReminderPicksCollectionId()
                    false
                }
                else -> true
            }
        return if (survives) recorded else null
    }

    /**
     * Stars or unstars a remembrance, and keeps the starred mirror in step.
     *
     * The star itself is one column, and the mirror is a second row written or deleted to match. Both
     * happen here rather than in the state layer because the mirror is a consequence of the star
     * rather than a thing the user did: nobody stars a passage *into* a collection, they star it and
     * then it is in the collection. Making it a consequence means the two cannot drift — there is no
     * path that writes one without the other.
     *
     * An unset mirror id is skipped rather than treated as an error, and the stale id is cleared. It
     * can only be unset on an installation whose database was deleted underneath a settings file that
     * still remembers the old row, and re-creating the collection on the spot would be a guess: the
     * bootstrap owns that collection's identity.
     */
    override suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    ) {
        if (favorite) remembranceService.markFavorite(id) else remembranceService.unmarkFavorite(id)
        mirrorStar(id, favorite)
    }

    override suspend fun saveRemembrance(remembrance: Remembrance): Remembrance = remembranceService.save(remembrance)

    override suspend fun deleteRemembrance(id: Long) {
        remembranceService.deleteById(id)
    }

    /**
     * Adds or removes [id] from the collection the stars are mirrored into.
     *
     * Positions are left to the repository's append, so a starred passage joins the end of the mirror
     * rather than being numbered by a guess made here.
     */
    private suspend fun mirrorStar(
        id: Long,
        favorite: Boolean,
    ) {
        val mirrorId =
            seedState.favoritesCollectionId()
                ?: run {
                    logger.warn {
                        "No starred-remembrances shelf is recorded; the star on $id will not be mirrored"
                    }
                    return
                }
        if (favorite) {
            collectionService.addMembers(mirrorId, listOf(id))
        } else {
            collectionService.removeMembers(mirrorId, listOf(id))
        }
    }

    override suspend fun createCollection(
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>,
        nameLocale: Locale,
    ): RemembranceCollection =
        collectionService.create(
            name = name,
            description = description,
            coverImagePath = coverImagePath,
            translations = translations,
            nameLocale = nameLocale,
            tags = tagNames.toTags(nameLocale),
        )

    override suspend fun editCollection(
        id: Long,
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>?,
        nameLocale: Locale?,
        tagLocale: Locale?,
    ): RemembranceCollection {
        val previousCover = collectionService.findById(id)?.coverImagePath
        collectionService.rename(
            id,
            name = name,
            description = description,
            translations = translations,
            nameLocale = nameLocale,
        )
        collectionService.setTags(
            id,
            tagNames.toTags(tagLocale ?: nameLocale ?: Locale.ENGLISH),
        )
        collectionService.setCoverImage(id, coverImagePath)
        releaseReplacedCover(previousCover, coverImagePath)
        return collectionService.findById(id)
            ?: error("Collection $id was there a moment ago and is not now")
    }

    override suspend fun storeCoverImage(source: Path): String = covers.store(source)

    override suspend fun setCollectionPinned(
        id: Long,
        pinned: Boolean,
    ): RemembranceCollection {
        collectionService.setPinned(id, pinned)
        return collectionService.findById(id)
            ?: error("Collection $id was there a moment ago and is not now")
    }

    override suspend fun setCollectionFavorite(
        id: Long,
        favorite: Boolean,
    ): RemembranceCollection {
        collectionService.setFavorite(id, favorite)
        // Read back rather than patched by hand. The star is a field on an immutable aggregate, and
        // the only thing that knows what it became is the row that was just written.
        return collectionService.findById(id)
            ?: error("Collection $id was there a moment ago and is not now")
    }

    override suspend fun duplicateCollection(
        id: Long,
        newName: String,
        nameLocale: Locale,
    ): RemembranceCollection = collectionService.duplicate(id, newName = newName, nameLocale = nameLocale)

    /**
     * Deletes a collection and the cover image it owned.
     *
     * The file is released here rather than left to the startup sweep, because the sweep is a safety
     * net and the user is waiting: a cover they picked should be gone when they come back, not
     * whenever the app next happens to start.
     */
    override suspend fun deleteCollection(id: Long) {
        val cover = collectionService.findById(id)?.coverImagePath
        collectionService.deleteById(id)
        cover?.let(covers::delete)
    }

    override suspend fun addMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int = collectionService.addMembers(collectionId, remembranceIds)

    override suspend fun removeMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int = collectionService.removeMembers(collectionId, remembranceIds)

    override suspend fun moveMember(
        collectionId: Long,
        fromIndex: Int,
        toIndex: Int,
    ) {
        collectionService.moveMember(collectionId, fromIndex, toIndex)
    }

    /**
     * Picks a passage for reminding, or unpicks it.
     *
     * Picking creates the reserved shelf if it is not there and adds the passage to it; unpicking
     * takes the passage out and deletes the shelf when that leaves it empty. The shelf is therefore
     * never a row the user has to look at and wonder about, and its presence is the answer to
     * "is anything picked".
     *
     * The queue is not written here, and that is the point: the shelf *is* the queue's source for
     * these passages, so a pick is one membership row rather than a membership row and a change to
     * the queue's settings that would have to be kept in step with it.
     */
    override suspend fun setReminderPick(
        remembranceId: Long,
        picked: Boolean,
    ): Boolean {
        val shelfId =
            if (picked) {
                ensurePicksShelf()
            } else {
                seedState.reminderPicksCollectionId()
            }
        if (shelfId == null) {
            // There was no shelf to pick into, and none to unpick from either.
            return false
        }
        if (picked) {
            collectionService.addMembers(shelfId, listOf(remembranceId))
        } else {
            collectionService.removeMembers(shelfId, listOf(remembranceId))
            val shelf = collectionService.findById(shelfId)
            if (shelf == null || shelf.members.isEmpty()) {
                collectionService.deleteById(shelfId)
                seedState.clearReminderPicksCollectionId()
            }
        }
        return picked
    }

    /**
     * The reserved shelf, created the first time a passage is picked.
     *
     * A recorded id whose row has gone is dropped rather than trusted: the settings file can outlive
     * the database it names, and adding a member to a row that is not there would fail on the
     * foreign key rather than quietly do nothing.
     */
    private suspend fun ensurePicksShelf(): Long? {
        val recorded = seedState.reminderPicksCollectionId()
        if (recorded != null) {
            if (collectionService.findById(recorded) != null) return recorded
            seedState.clearReminderPicksCollectionId()
        }
        val created =
            collectionService.create(
                name = PICKS_NAME,
                description = PICKS_DESCRIPTION,
                iconKey = PICKS_ICON_KEY,
                translations = PICKS_TRANSLATIONS,
            )
        val id = checkNotNull(created.id) { "A just-created shelf must have an id" }
        seedState.setReminderPicksCollectionId(id)
        return id
    }

    override suspend fun touchCollectionOpened(id: Long) {
        collectionService.touchLastOpened(id)
    }

    override suspend fun reorderCollections(orderedIds: List<Long>) {
        orderService.reorderCollections(orderedIds)
    }

    /**
     * Deletes [previous] when the save replaced it with a different cover.
     *
     * Skipped when the two are equal — which includes both "kept the same cover" and "cleared it,
     * having had none" — so the ordinary save never touches a file the collection still points at.
     * A file the user has just replaced is not referenced by anything, and the sweep would find it,
     * but it would find it on some later launch rather than now.
     */
    private fun releaseReplacedCover(
        previous: String?,
        current: String?,
    ) {
        if (previous != null && previous != current) {
            covers.delete(previous)
        }
    }

    override suspend fun fetchProgress(collectionId: Long): List<RecitationProgress> =
        recitationService.findByCollection(collectionId)

    override suspend fun recordRecitation(
        collectionId: Long,
        deltas: Map<Long, Int>,
    ): Int = recitationService.record(collectionId, deltas)

    override suspend fun resetProgress(collectionId: Long): Int = recitationService.reset(collectionId)

    /**
     * Moves a collection on or off the Home rail and returns it as stored.
     *
     * Returns the collection rather than nothing so the shelf tile behind the menu item can be
     * repainted without a second read.
     */
    override suspend fun setCollectionOnHome(
        id: Long,
        onHome: Boolean,
    ): RemembranceCollection {
        collectionService.setHomePinned(id, onHome)
        return collectionService.findById(id)
            ?: error("Collection $id was there a moment ago and is not now")
    }

    /**
     * Reads the rail's candidates.
     *
     * Two reads rather than one joined query, because they are two different questions: what each
     * card says, and how far the user has got through it. Each is a handful of rows, so the round
     * trips are cheaper than a query shaped for neither.
     *
     * Nothing is loaded that a card does not paint. The rail names a collection and counts its
     * passages, and reading the collections themselves to do that meant paying for every member of
     * every one of them, each fully translated — the whole library, to fill in three cards. The
     * summaries carry the label and the count and stop there.
     */
    override suspend fun fetchHomeCollections(): List<HomeCollection> {
        // The rail mounts independently of the shelf, so it cannot assume the shelf's read already
        // waited for the library to be there.
        bootstrapper.bootstrap()
        val candidates = collectionService.findSummaries()
        // One grouped read for every candidate at once. Reading each collection's progress
        // separately would be a round trip per card, and the Home screen is the first thing
        // the app paints.
        val recited = recitationService.findSummaries(candidates.map { it.id })
        return candidates.map { candidate -> candidate.toHomeCollection(recited[candidate.id]) }
    }

    /**
     * The rail's card, read without needing its members.
     *
     * [progress] carries what the recitation table knows — how many members are counted and when one
     * was last recited. It is `null` for a collection with no recorded progress, which is reported
     * as a count of zero rather than as a missing card: the user asked for the collection to be
     * there, and "you have not started it" is the honest reading, not "this does not exist".
     */
    private fun CollectionSummary.toHomeCollection(progress: RecitationSummary?): HomeCollection =
        HomeCollection(
            id = id,
            name = name,
            description = description,
            iconKey = iconKey,
            memberCount = memberCount,
            recitedCount = progress?.recitedCount ?: 0,
            pinnedAt = homePinnedAt,
            lastRecitedAt = progress?.lastRecitedAt,
        )

    private companion object {
        /**
         * The reserved shelf's name, written once and stored.
         *
         * Hard-coded rather than drawn from the interface's resources because the shelf is created by
         * the data layer, below the UI, and because the name is written to the row once and read back
         * from there afterwards — a resource that changed later would not change the row.
         */
        const val PICKS_NAME = "Reminder picks"
        const val PICKS_DESCRIPTION = "The remembrances you asked to be reminded about"
        const val PICKS_ICON_KEY = "bell"

        /** Both languages, so the shelf reads in the language the user is reading the app in. */
        val PICKS_TRANSLATIONS: Map<Locale, LocalizedPair> =
            mapOf(
                Locale.forLanguageTag("en") to
                    LocalizedPair(PICKS_NAME, PICKS_DESCRIPTION),
                Locale.forLanguageTag("ar") to
                    LocalizedPair("مختارات التذكير", "الأذكار التي طلبت تذكيرها"),
            )
    }
}

/**
 * The tags the user typed, as the untagged-by-id values the domain models use.
 *
 * `null` ids and `null` timestamps are not placeholders to be filled in later: the tag a collection
 * carries is a *name* until it is saved, and it is the repository that turns a name into a row. A
 * caller that already had an id would be telling the repository to skip the lookup it needs for a
 * name typed on this device.
 *
 * Blank entries are dropped here rather than left for the tag repository, which would fail on them:
 * an empty chip in the editor is an absent chip, and the user did not type one.
 */
private fun Collection<String>.toTags(nameLocale: Locale): Set<Tag> =
    map { TagImpl(name = it, nameLocale = nameLocale) }
        .filter { it.name.isNotBlank() }
        .toSet()
