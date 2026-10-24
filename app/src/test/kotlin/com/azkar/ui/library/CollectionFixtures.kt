package com.azkar.ui.library

import com.azkar.data.storage.CoverStorage
import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.RecitationProgress
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import com.azkar.domain.model.impl.RemembranceCollectionImpl
import com.azkar.domain.model.impl.RemembranceImpl
import com.azkar.domain.model.impl.TagImpl
import com.azkar.ui.library.data.CollectionFiltering
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.data.HomeCollection
import com.azkar.ui.library.data.ReminderSources
import com.azkar.ui.library.data.findById
import com.azkar.ui.library.data.withFavorite
import com.azkar.ui.library.model.CollectionsSnapshot
import com.azkar.ui.notifications.ReminderQueueConfig
import com.azkar.ui.notifications.ReminderQueueProgress
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import java.util.UUID

/** The instant every fixture is stamped with, so an ordering assertion cannot drift with the clock. */
internal val FixtureInstant: Instant = Instant.parse("2026-01-15T08:00:00Z")

/** An arbitrary later instant, for the "more recently updated" half of a fixture pair. */
internal val LaterFixtureInstant: Instant = Instant.parse("2026-02-20T19:30:00Z")

/**
 * A tag, for the fixture remembrances that need one.
 *
 * The id is a parameter because a fixture that always used the same one would make two tags
 * indistinguishable in an assertion about which of them matched.
 */
internal fun tag(
    id: Long?,
    name: String,
    translations: Map<Locale, String> = emptyMap(),
): Tag = TagImpl(id = id, name = name, createdAt = FixtureInstant, translations = translations)

/**
 * A remembrance with an English and an Arabic translation, so a test can assert that the search box
 * reaches a translation in a locale other than the one it was typed in.
 *
 * The id is nullable because a few rules only make sense for a remembrance that has never been
 * persisted: it can be shown, but nothing can be recorded against it.
 */
internal fun remembrance(
    id: Long?,
    text: String = "Remembrance $id",
    explanation: String = "Explanation $id",
    source: String? = "Sunan an-Nasa'i",
    favorite: Boolean = false,
    tags: Set<Tag> = emptySet(),
): Remembrance =
    RemembranceImpl(
        id = id,
        isFavorite = favorite,
        source = source,
        createdAt = FixtureInstant,
        updatedAt = FixtureInstant,
        translations =
            mapOf(
                Locale.ENGLISH to
                    Remembrance.Translations(
                        explanationPair = Remembrance.Translations.Pair(id = id, text = explanation),
                        translationPair = Remembrance.Translations.Pair(id = id, text = text),
                    ),
                Locale.forLanguageTag("ar") to
                    Remembrance.Translations(
                        explanationPair = Remembrance.Translations.Pair(id = id, text = "شرح $id"),
                        translationPair = Remembrance.Translations.Pair(id = id, text = "ذكر $id"),
                    ),
            ),
        tags = tags,
    )

/** A collection the app ships with, and therefore one that already had a name and labels before the user got it. */
internal fun officialCollection(
    id: Long,
    name: String,
    members: List<Remembrance> = emptyList(),
    description: String? = "The $name remembrances.",
    iconKey: String = "mosque",
    coverImagePath: String? = null,
    tags: Set<Tag> = emptySet(),
    pinnedAt: Instant? = null,
    updatedAt: Instant? = FixtureInstant,
    lastOpenedAt: Instant? = null,
    homePinnedAt: Instant? = null,
    translations: Map<Locale, LocalizedPair> = emptyMap(),
    isFavorite: Boolean = false,
    position: Int? = null,
): RemembranceCollection =
    RemembranceCollectionImpl(
        id = id,
        name = name,
        description = description,
        kind = CollectionKind.OFFICIAL,
        iconKey = iconKey,
        coverImagePath = coverImagePath,
        tags = tags,
        pinnedAt = pinnedAt,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        homePinnedAt = homePinnedAt,
        isFavorite = isFavorite,
        members = members,
        translations = translations,
        position = position,
    )

/** A collection the user made: custom, unpinned until they say otherwise, and owning its own membership. */
internal fun customCollection(
    id: Long,
    name: String,
    members: List<Remembrance> = emptyList(),
    description: String? = null,
    iconKey: String? = null,
    coverImagePath: String? = null,
    tags: Set<Tag> = emptySet(),
    pinnedAt: Instant? = null,
    updatedAt: Instant? = FixtureInstant,
    lastOpenedAt: Instant? = null,
    homePinnedAt: Instant? = null,
    translations: Map<Locale, LocalizedPair> = emptyMap(),
    isFavorite: Boolean = false,
    nameLocale: Locale = Locale.ENGLISH,
    position: Int? = null,
): RemembranceCollection =
    RemembranceCollectionImpl(
        id = id,
        name = name,
        nameLocale = nameLocale,
        description = description,
        kind = CollectionKind.CUSTOM,
        iconKey = iconKey,
        coverImagePath = coverImagePath,
        tags = tags,
        pinnedAt = pinnedAt,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        homePinnedAt = homePinnedAt,
        isFavorite = isFavorite,
        members = members,
        translations = translations,
        position = position,
    )

/**
 * Copies a collection, overriding only the fields a fake needs to change.
 *
 * `RemembranceCollectionImpl` has a private canonical constructor, so a copy has to be rebuilt
 * through its factory. Rebuilding through the factory rather than reaching for the generated `copy`
 * is deliberate: it trims and normalises the way the real mapper does, so a fixture cannot pass
 * because it skipped validation the production path would have applied.
 */
private fun RemembranceCollection.copyWith(
    name: String = this.name,
    nameLocale: Locale = this.nameLocale,
    description: String? = this.description,
    coverImagePath: String? = this.coverImagePath,
    tags: Set<Tag> = this.tags,
    pinnedAt: Instant? = this.pinnedAt,
    updatedAt: Instant? = this.updatedAt,
    lastOpenedAt: Instant? = this.lastOpenedAt,
    homePinnedAt: Instant? = this.homePinnedAt,
    isFavorite: Boolean = this.isFavorite,
    members: List<Remembrance> = this.members,
    translations: Map<Locale, LocalizedPair> = this.translations,
    position: Int? = this.position,
): RemembranceCollection =
    RemembranceCollectionImpl(
        id = id,
        name = name,
        nameLocale = nameLocale,
        description = description,
        kind = kind,
        iconKey = iconKey,
        coverImagePath = coverImagePath,
        tags = tags,
        pinnedAt = pinnedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        homePinnedAt = homePinnedAt,
        isFavorite = isFavorite,
        members = members,
        translations = translations,
        position = position,
    )

/**
 * Turns the tag names a caller wrote into labels for a fixture collection.
 *
 * Ids are left `null` on purpose. The real gateway resolves each name to a row through the tag
 * repository and the id that comes back is the database's business, not a test's; a fixture that
 * invented one would let a filter assertion pass against an id no such label would ever have. A test
 * that needs specific ids sets [officialCollection]'s or [customCollection]'s `tags` directly.
 */
private fun Collection<String>.toFixtureTags(nameLocale: Locale = Locale.ENGLISH): Set<Tag> =
    mapNotNull { name ->
        name.trim().takeIf(String::isNotEmpty)?.let { TagImpl(name = it, nameLocale = nameLocale) }
    }.toSet()

/** An in-memory [CollectionsGateway] with the same semantics as the Exposed one behind it.
 *
 * Every method really changes the fixture state rather than returning a canned value, because the
 * behaviour worth testing is the *sequence*: the state layer applies a change locally, then either
 * reports it or rolls it back. A stub that returned a pre-baked answer would let both halves of
 * those rules pass without either of them being exercised.
 *
 * [failure] is what makes a call fail. It applies to every method rather than to one, because a test
 * that failed only the write it cared about would first have to know the call order to set it up.
 */
internal class FakeCollectionsGateway(
    collections: List<RemembranceCollection> = emptyList(),
    remembrances: List<Remembrance> = emptyList(),
    progress: Map<Long, List<RecitationProgress>> = emptyMap(),
    /**
     * Which collection mirrors the user's stars, or `null` for none.
     *
     * Held as an id rather than a name because that is how the real snapshot carries it, and a fake
     * that let the tests name the shelf would quietly reinstate the lookup by name that production
     * deliberately does without — the one that stops mirroring the moment the user renames it.
     */
    favoritesCollectionId: Long? = null,
    uncategorizedCollectionId: Long? = null,
) : CollectionsGateway {
    var collections: List<RemembranceCollection> = collections

    var remembrances: List<Remembrance> = remembrances

    var favoritesCollectionId: Long? = favoritesCollectionId

    var uncategorizedCollectionId: Long? = uncategorizedCollectionId

    /** Progress per collection id, held the way the gateway serves it. Tests can seed it. */
    var progress: Map<Long, List<RecitationProgress>> = progress

    /** When set, every call throws it. Used for the rollback and the failure-notice rules. */
    var failure: Throwable? = null

    /** How many times the whole shelf has been read, so a background refresh can be asserted on. */
    var snapshotReads: Int = 0
        private set

    /**
     * Reads to hold open, keyed by the read's 1-based ordinal in [snapshotReads].
     *
     * A read parks here and does not resume until [releaseRead] completes its gate. This exists
     * because two concurrent reads cannot be ordered apart by a test scheduler: `runTest` runs its
     * dispatcher FIFO, so queued reads always finish in the order they were issued and "the older one
     * was discarded" is indistinguishable from "the older one happened to win anyway". Holding the
     * first open is what makes the second the one to arrive last.
     */
    val readGates: MutableMap<Int, CompletableDeferred<Unit>> = mutableMapOf()

    /** Lets the [ordinal]-th read go ahead, as counted by [snapshotReads]. */
    fun releaseRead(ordinal: Int) {
        readGates.remove(ordinal)?.complete(Unit)
    }

    /** Every `setFavorite` call, as id to value, in order. */
    val favoriteWrites: MutableList<Pair<Long, Boolean>> = mutableListOf()

    /** Every `saveRemembrance` call, as the desired state that reached the gateway, in order. */
    val savedRemembrances: MutableList<Remembrance> = mutableListOf()

    /** Every collection the detail screen reported as opened. */
    val openedCollections: MutableList<Long> = mutableListOf()

    /** Every recitation write, as collection id to the deltas it carried, in order. */
    val recitationWrites: MutableList<Pair<Long, Map<Long, Int>>> = mutableListOf()

    /**
     * The passages the user asked to be reminded about, by id.
     *
     * A set rather than a collection id, because the shelf that holds them is created on the first
     * pick and deleted when the last one goes — so there is no id to point at until there is
     * something picked, and [reminderPicksCollectionId] is the answer to "is there a shelf yet".
     */
    val reminderPicks: MutableSet<Long> = mutableSetOf()

    /** The id of the reserved shelf that holds the picks, or null while there is nothing picked. */
    var reminderPicksCollectionId: Long? = null

    /** Ids handed to the next created collection, so a fixture's own ids are never reused. */
    private var nextId: Long = (collections.mapNotNull { it.id }.maxOrNull() ?: 0L) + 1L

    /**
     * How many of each collection's members the snapshot claims have been recited.
     *
     * Held separately from [progress], which is the per-member truth a session writes against, so a
     * test can put a count on the shelf for a collection it has never recited a single member of.
     */
    var recitedCounts: Map<Long, Int> = emptyMap()

    override suspend fun fetchSnapshot(): CollectionsSnapshot {
        checkNotFailing()
        snapshotReads++
        reconcileUncategorizedMembers()
        reconcilePicksShelf()
        // Read the shelf before waiting, so a parked read still serves the shelf as it was when it
        // was asked for. That is what makes the stale result a test can assert on.
        val snapshot =
            CollectionsSnapshot(
                collections = collections,
                remembrances = remembrances,
                recitedCounts = recitedCounts,
                refreshedAt = FixtureInstant,
                favoritesCollectionId = favoritesCollectionId,
                uncategorizedCollectionId = uncategorizedCollectionId,
                reminderPicksCollectionId = reminderPicksCollectionId,
            )
        readGates[snapshotReads]?.await()
        return snapshot
    }

    override suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    ) {
        checkNotFailing()
        favoriteWrites += id to favorite
        remembrances = remembrances.map { if (it.id == id) it.withFavorite(favorite) else it }
        val saved = remembrances.firstOrNull { it.id == id } ?: return
        collections =
            collections.map { collection ->
                val updatedMembers = collection.members.map { member -> if (member.id == id) saved else member }
                val withUpdatedMembers = collection.copyWith(members = updatedMembers)
                when {
                    collection.id != favoritesCollectionId -> withUpdatedMembers
                    favorite && collection.members.none { it.id == id } ->
                        withUpdatedMembers.copyWith(members = updatedMembers + saved)

                    !favorite -> withUpdatedMembers.copyWith(members = updatedMembers.filterNot { it.id == id })
                    else -> withUpdatedMembers
                }
            }
    }

    override suspend fun saveRemembrance(remembrance: Remembrance): Remembrance {
        checkNotFailing()
        val saved =
            if (remembrance.id == null) {
                RemembranceImpl(
                    id = nextId++,
                    grade = remembrance.grade,
                    isFavorite = remembrance.isFavorite,
                    source = remembrance.source,
                    createdAt = FixtureInstant,
                    updatedAt = FixtureInstant,
                    translations = remembrance.translations,
                    tags = remembrance.tags,
                )
            } else {
                remembrance
            }
        savedRemembrances += saved
        remembrances =
            if (remembrances.any { it.id == saved.id }) {
                remembrances.map { if (it.id == saved.id) saved else it }
            } else {
                remembrances + saved
            }
        // The members inside a collection are rebuilt from this table on every read, so a fake that
        // left them alone would hand the next snapshot the old words — and a reload that undid the
        // edit would look like a failed save rather than like a gateway that never wrote.
        collections =
            collections.map { collection ->
                collection.copyWith(
                    members = collection.members.map { if (it.id == saved.id) saved else it },
                )
            }
        return saved
    }

    override suspend fun deleteRemembrance(id: Long) {
        checkNotFailing()
        remembrances = remembrances.filterNot { it.id == id }
        collections =
            collections.map { collection ->
                collection.copyWith(members = collection.members.filterNot { it.id == id })
            }
        // The real shelf is emptied by the cascade, and the pick goes with it.
        reminderPicks -= id
    }

    override suspend fun createCollection(
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>,
        nameLocale: Locale,
    ): RemembranceCollection {
        checkNotFailing()
        val created =
            customCollection(
                id = nextId++,
                name = name,
                nameLocale = nameLocale,
                description = description,
                coverImagePath = coverImagePath,
                tags = tagNames.toFixtureTags(nameLocale),
                translations = translations,
            )
        collections = collections + created
        return created
    }

    override suspend fun editCollection(
        id: Long,
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>?,
        nameLocale: Locale?,
        tagLocale: Locale?,
    ): RemembranceCollection =
        replace(
            id = id,
            change = {
                it.copyWith(
                    name = name,
                    nameLocale = nameLocale ?: it.nameLocale,
                    description = description,
                    coverImagePath = coverImagePath,
                    tags = tagNames.toFixtureTags(tagLocale ?: nameLocale ?: Locale.ENGLISH),
                    translations = translations ?: it.translations,
                )
            },
        )

    /**
     * Stand-in for the real copy, which hands back a name in the app's own covers directory.
     *
     * Deliberately not the file that was picked: a fixture that invented a real path would make the
     * snapshot claim a cover the fake never stored, and the card would render the fallback wash for a
     * collection the test believes it drew a picture on.
     */
    override suspend fun storeCoverImage(source: Path): String {
        checkNotFailing()
        return "fake-cover-${source.fileName}"
    }

    override suspend fun setCollectionPinned(
        id: Long,
        pinned: Boolean,
    ): RemembranceCollection =
        replace(
            id = id,
            change = { it.copyWith(pinnedAt = if (pinned) FixtureInstant else null) },
        )

    override suspend fun setCollectionFavorite(
        id: Long,
        favorite: Boolean,
    ): RemembranceCollection = replace(id = id, change = { it.copyWith(isFavorite = favorite) })

    override suspend fun duplicateCollection(
        id: Long,
        newName: String,
        nameLocale: Locale,
    ): RemembranceCollection {
        checkNotFailing()
        val source = requireNotNull(collections.findById(id)) { "No collection $id to duplicate" }
        val copy =
            customCollection(
                id = nextId++,
                name = newName,
                nameLocale = nameLocale,
                members = source.members,
                description = source.description,
                iconKey = source.iconKey,
            )
        collections = collections + copy
        return copy
    }

    override suspend fun deleteCollection(id: Long) {
        checkNotFailing()
        collections = collections.filterNot { it.id == id }
    }

    override suspend fun addMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int {
        checkNotFailing()
        val target = collections.findById(collectionId) ?: return 0
        val alreadyPresent = target.members.mapNotNull { it.id }.toSet()
        val additions = remembrances.filter { it.id in remembranceIds && it.id !in alreadyPresent }
        replace(id = collectionId, change = { it.copyWith(members = target.members + additions) })
        return additions.size
    }

    override suspend fun removeMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int {
        checkNotFailing()
        val target = collections.findById(collectionId) ?: return 0
        val dropped = target.members.filter { it.id in remembranceIds }
        replace(
            id = collectionId,
            change = { it.copyWith(members = target.members - dropped.toSet()) },
        )
        return dropped.size
    }

    override suspend fun moveMember(
        collectionId: Long,
        fromIndex: Int,
        toIndex: Int,
    ) {
        checkNotFailing()
        val target = collections.findById(collectionId) ?: return
        val ordered = target.members.toMutableList()
        if (fromIndex !in ordered.indices || toIndex !in ordered.indices || fromIndex == toIndex) return
        ordered.add(toIndex, ordered.removeAt(fromIndex))
        replace(id = collectionId, change = { it.copyWith(members = ordered) })
    }

    override suspend fun touchCollectionOpened(id: Long) {
        checkNotFailing()
        openedCollections += id
        replace(id = id, change = { it.copyWith(updatedAt = LaterFixtureInstant, lastOpenedAt = LaterFixtureInstant) })
    }

    override suspend fun fetchProgress(collectionId: Long): List<RecitationProgress> {
        checkNotFailing()
        return progress[collectionId].orEmpty()
    }

    override suspend fun recordRecitation(
        collectionId: Long,
        deltas: Map<Long, Int>,
    ): Int {
        checkNotFailing()
        recitationWrites += collectionId to deltas
        val stored =
            progress[collectionId].orEmpty().associate { it.remembranceId to it.repeatCount }.toMutableMap()
        val changed =
            deltas.entries.count { (remembranceId, delta) ->
                val updated = maxOf(0, (stored[remembranceId] ?: 0) + delta)
                if (updated == (stored[remembranceId] ?: 0) && stored.containsKey(remembranceId)) {
                    false
                } else {
                    stored[remembranceId] = updated
                    true
                }
            }
        progress =
            progress +
            (
                collectionId to
                    stored.map { (remembranceId, count) ->
                        RecitationProgress(remembranceId, count, FixtureInstant)
                    }
            )
        return changed
    }

    override suspend fun resetProgress(collectionId: Long): Int {
        checkNotFailing()
        val dropped = progress[collectionId].orEmpty().size
        progress = progress - collectionId
        return dropped
    }

    /**
     * Moves a collection on or off the Home screen.
     *
     * Stamps it with [LaterFixtureInstant] rather than the clock so a test can assert on an
     * ordering without depending on how fast the test ran, the same way the rest of this fake
     * keeps its timestamps fixed.
     */
    override suspend fun setCollectionOnHome(
        id: Long,
        onHome: Boolean,
    ): RemembranceCollection =
        replace(
            id = id,
            change = { it.copyWith(homePinnedAt = if (onHome) LaterFixtureInstant else null) },
        )

    /** The rail's read model, derived from the fake's own shelf and progress rather than stubbed. */
    override suspend fun fetchHomeCollections(): List<HomeCollection> {
        checkNotFailing()
        return collections.map { collection ->
            val stamps = progress[collection.id].orEmpty().mapNotNull { it.lastRecitedAt }
            HomeCollection(
                id = collection.id ?: 0L,
                name = collection.name,
                description = collection.description,
                iconKey = collection.iconKey.orEmpty(),
                memberCount = collection.members.size,
                recitedCount = progress[collection.id].orEmpty().size,
                pinnedAt = collection.homePinnedAt,
                lastRecitedAt = stamps.maxOrNull(),
            )
        }
    }

    /**
     * Picks or unpicks a passage, and keeps the reserved shelf in step with the picks.
     *
     * The shelf appears on the first pick and is deleted with the last one, because an empty shelf
     * on the library's own shelf is a row that says nothing. Its membership is exactly the picks.
     */
    override suspend fun setReminderPick(
        remembranceId: Long,
        picked: Boolean,
    ): Boolean {
        checkNotFailing()
        val shelfId = reminderPicksCollectionId
        if (picked) {
            reminderPicks += remembranceId
            val id = shelfId ?: nextId++.also { reminderPicksCollectionId = it }
            if (collections.none { it.id == id }) {
                collections = collections + customCollection(id = id, name = "Reminder picks")
            }
            val pickedRemembrance = remembrances.firstOrNull { it.id == remembranceId }
            if (pickedRemembrance != null) {
                collections =
                    collections.map { collection ->
                        if (collection.id != id) {
                            collection
                        } else if (collection.members.any { it.id == remembranceId }) {
                            collection
                        } else {
                            collection.copyWith(members = collection.members + pickedRemembrance)
                        }
                    }
            }
        } else {
            reminderPicks -= remembranceId
            if (reminderPicks.isEmpty()) {
                reminderPicksCollectionId = null
                collections = collections.filterNot { it.id == shelfId }
            } else if (shelfId != null) {
                collections =
                    collections.map { collection ->
                        if (collection.id == shelfId) {
                            collection.copyWith(members = collection.members.filterNot { it.id == remembranceId })
                        } else {
                            collection
                        }
                    }
            }
        }
        return picked
    }

    /** Stores the order the user gave the shelf, as a dense position per collection. */
    override suspend fun reorderCollections(orderedIds: List<Long>) {
        checkNotFailing()
        val positions = orderedIds.distinct().mapIndexed { index, id -> id to index }.toMap()
        collections =
            collections.map { collection ->
                val id = collection.id ?: return@map collection
                collection.copyWith(position = positions[id])
            }
    }

    /** Replaces one collection in place and returns it, so a caller can both store and return it. */
    private fun replace(
        id: Long,
        change: (RemembranceCollection) -> RemembranceCollection,
    ): RemembranceCollection {
        checkNotFailing()
        val existing = requireNotNull(collections.findById(id)) { "No collection $id" }
        val updated = change(existing)
        collections = collections.map { if (it.id == id) updated else it }
        return updated
    }

    private fun checkNotFailing() {
        failure?.let { throw it }
    }

    private fun reconcileUncategorizedMembers() {
        val id = uncategorizedCollectionId ?: return
        val expectedIds = CollectionFiltering.uncategorizedRemembranceIds(remembrances, collections, id)
        val existing = collections.findById(id) ?: return
        val currentIds = existing.members.mapNotNull { it.id }.toSet()
        val members =
            existing.members.filter { it.id in expectedIds } +
                remembrances.filter { it.id in expectedIds && it.id !in currentIds }
        collections =
            collections.map { collection ->
                if (collection.id == id) collection.copyWith(members = members) else collection
            }
    }

    /**
     * Applies the rule the real gateway applies on every read: a picks shelf with nothing in it is
     * deleted, and the id that pointed at it is forgotten.
     *
     * The real shelf is emptied by the database's cascade when a passage is deleted, so without this
     * the fake would keep a shelf the real app would have taken away — and a test would pass against
     * a state the app never reaches.
     */
    private fun reconcilePicksShelf() {
        val shelfId = reminderPicksCollectionId ?: return
        val shelf = collections.findById(shelfId)
        when {
            shelf == null -> {
                reminderPicksCollectionId = null
                reminderPicks.clear()
            }
            shelf.members.isEmpty() -> {
                reminderPicksCollectionId = null
                reminderPicks.clear()
                collections = collections.filterNot { it.id == shelfId }
            }
            else -> {
                // The shelf is the truth about what is picked: the passage list and the shelf cannot
                // both be right and disagree, and the shelf is the one the database keeps.
                reminderPicks.clear()
                reminderPicks += shelf.members.mapNotNull { it.id }
            }
        }
    }
}

/**
 * A cover cache over a unique, nonexistent path, for a test that constructs a [CollectionsCatalogue].
 *
 * The catalogue takes one as a required argument rather than defaulting it, because a default would
 * have to build a real storage and so would touch `~/.azkar` from a constructor. Every suite that
 * builds a catalogue therefore needs one of these, and pointing them all at the same kind of empty
 * unique path keeps that requirement from becoming a second thing to get right per test without
 * creating a directory that these tests do not use.
 */
internal fun fakeCovers(): CollectionCoverCache =
    CollectionCoverCache(
        CoverStorage(Path.of(System.getProperty("java.io.tmpdir"), "azkar-test-covers-${UUID.randomUUID()}")),
    )

/**
 * The queue's source list, as a plain set, for a test that only has to know what is in it.
 *
 * The real one reads the notification preferences, and a test that asserted against a real
 * preference file would be asserting against the storage rather than against the rule. The writes
 * are no-ops, because the rules under test here are the ones that read the set.
 */
internal class FakeReminderSources(
    override val enabledCollectionIds: Set<Long> = emptySet(),
) : ReminderSources {
    override val queueConfig: ReminderQueueConfig
        get() = ReminderQueueConfig(collectionIds = enabledCollectionIds.toList())

    override val queueProgress: ReminderQueueProgress get() = ReminderQueueProgress()

    override val isWeighted: Boolean get() = false

    override fun setEnabled(
        collectionId: Long,
        enabled: Boolean,
    ) = Unit

    override fun setDropChance(
        collectionId: Long,
        chance: Int,
    ) = Unit
}
