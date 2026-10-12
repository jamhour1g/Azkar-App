package com.azkar.ui.library

import com.azkar.data.storage.CoverStorage
import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.CollectionReminder
import com.azkar.domain.model.RecitationProgress
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.ReminderCadence
import com.azkar.domain.model.Tag
import com.azkar.domain.model.impl.RemembranceCollectionImpl
import com.azkar.domain.model.impl.RemembranceImpl
import com.azkar.domain.model.impl.TagImpl
import com.azkar.ui.library.data.CollectionImport
import com.azkar.ui.library.data.CollectionReminderRow
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.data.HomeCollection
import com.azkar.ui.library.data.ImportPlan
import com.azkar.ui.library.data.ImportReport
import com.azkar.ui.library.data.TransferCollection
import com.azkar.ui.library.data.findById
import com.azkar.ui.library.data.withFavorite
import com.azkar.ui.library.model.CollectionsSnapshot
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Instant
import java.util.Locale

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
): Tag = TagImpl(id = id, name = name, createdAt = FixtureInstant)

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
    isFavorite: Boolean = false,
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
    isFavorite: Boolean = false,
): RemembranceCollection =
    RemembranceCollectionImpl(
        id = id,
        name = name,
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
    description: String? = this.description,
    coverImagePath: String? = this.coverImagePath,
    tags: Set<Tag> = this.tags,
    pinnedAt: Instant? = this.pinnedAt,
    updatedAt: Instant? = this.updatedAt,
    lastOpenedAt: Instant? = this.lastOpenedAt,
    homePinnedAt: Instant? = this.homePinnedAt,
    isFavorite: Boolean = this.isFavorite,
    members: List<Remembrance> = this.members,
): RemembranceCollection =
    RemembranceCollectionImpl(
        id = id,
        name = name,
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
    )

/**
 * Turns the tag names a caller wrote into labels for a fixture collection.
 *
 * Ids are left `null` on purpose. The real gateway resolves each name to a row through the tag
 * repository and the id that comes back is the database's business, not a test's; a fixture that
 * invented one would let a filter assertion pass against an id no such label would ever have. A test
 * that needs specific ids sets [officialCollection]'s or [customCollection]'s `tags` directly.
 */
private fun Collection<String>.toFixtureTags(): Set<Tag> =
    mapNotNull { name -> name.trim().takeIf(String::isNotEmpty)?.let { TagImpl(name = it) } }.toSet()

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
) : CollectionsGateway {
    var collections: List<RemembranceCollection> = collections

    var remembrances: List<Remembrance> = remembrances

    var favoritesCollectionId: Long? = favoritesCollectionId

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
     * The schedules held, keyed by collection id, exactly as the gateway serves them.
     *
     * A separate map from [collections] because a schedule outlives the shelf it was set on — a test
     * that deletes a collection and then reads the reminder for its id is asking a question about the
     * reminder, and a fake that silently dropped it with the collection would answer it wrongly.
     */
    var reminders: Map<Long, CollectionReminder> = emptyMap()

    /** The name and size a reminder is announced with, when a fixture has not supplied its own. */
    var reminderDetails: Map<Long, Pair<String, Int>> = emptyMap()

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
        // Read the shelf before waiting, so a parked read still serves the shelf as it was when it
        // was asked for. That is what makes the stale result a test can assert on.
        val snapshot =
            CollectionsSnapshot(
                collections = collections,
                remembrances = remembrances,
                recitedCounts = recitedCounts,
                refreshedAt = FixtureInstant,
                favoritesCollectionId = favoritesCollectionId,
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
    }

    override suspend fun saveRemembrance(remembrance: Remembrance): Remembrance {
        checkNotFailing()
        savedRemembrances += remembrance
        remembrances = remembrances.map { if (it.id == remembrance.id) remembrance else it }
        // The members inside a collection are rebuilt from this table on every read, so a fake that
        // left them alone would hand the next snapshot the old words — and a reload that undid the
        // edit would look like a failed save rather than like a gateway that never wrote.
        collections =
            collections.map { collection ->
                collection.copyWith(
                    members = collection.members.map { if (it.id == remembrance.id) remembrance else it },
                )
            }
        return remembrance
    }

    override suspend fun createCollection(
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
    ): RemembranceCollection {
        checkNotFailing()
        val created =
            customCollection(
                id = nextId++,
                name = name,
                description = description,
                coverImagePath = coverImagePath,
                tags = tagNames.toFixtureTags(),
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
    ): RemembranceCollection =
        replace(
            id = id,
            change = {
                it.copyWith(
                    name = name,
                    description = description,
                    coverImagePath = coverImagePath,
                    tags = tagNames.toFixtureTags(),
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
    ): RemembranceCollection {
        checkNotFailing()
        val source = requireNotNull(collections.findById(id)) { "No collection $id to duplicate" }
        val copy =
            customCollection(
                id = nextId++,
                name = newName,
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
     * Runs the real planner over the fake's shelf and catalogue, then applies it.
     *
     * Deliberately not a stub that returns a canned report: the point of the state tests is that the
     * numbers a dialog shows come from the same decisions the production gateway makes, and a stub
     * would let the two disagree without anything failing.
     */
    override suspend fun importCollections(transfers: List<TransferCollection>): ImportReport {
        checkNotFailing()
        val plan = CollectionImport.plan(transfers, collections, remembrances)
        var membersAdded = 0
        plan.plans.forEach { step ->
            when (step) {
                is ImportPlan.Create -> {
                    // The cover is dropped rather than invented. The real gateway decodes the
                    // transfer's base64 into the covers directory and stores the name it wrote; a fake
                    // has no directory, and naming a file that was never written would let a test
                    // believe the imported collection had a picture.
                    val created =
                        createCollection(
                            name = step.transfer.name,
                            description = step.transfer.description,
                            tagNames = step.transfer.tagNames,
                            coverImagePath = null,
                        )
                    membersAdded += addMembers(requireNotNull(created.id), step.memberIds)
                }
                is ImportPlan.Merge -> membersAdded += addMembers(step.collectionId, step.memberIds)
                is ImportPlan.SkipOfficial -> Unit
            }
        }
        return ImportReport(
            created = plan.created,
            merged = plan.merged,
            membersAdded = membersAdded,
            skippedOfficial = plan.skippedOfficial,
            unmatchedMembers = plan.unmatched,
        )
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
     * Every schedule the gateway will serve, rebuilt with a collection's name and size.
     *
     * A missing collection yields an unnamed reminder rather than dropping it, matching the real
     * gateway: a schedule whose collection vanished should have been cascaded away, so the absence
     * means the shelf changed mid-read and the reminder is the thing that must survive it.
     */
    override suspend fun fetchReminders(): List<CollectionReminderRow> {
        checkNotFailing()
        return reminders.values.map(::rowFor)
    }

    override suspend fun fetchReminder(collectionId: Long): CollectionReminderRow? {
        checkNotFailing()
        return reminders[collectionId]?.let(::rowFor)
    }

    /**
     * Stores a schedule, carrying over the two things the real service preserves.
     *
     * Preserved here as well so a test cannot pass against a fake that quietly re-arms a reminder the
     * editing screen deliberately did not re-arm.
     */
    override suspend fun saveReminder(
        collectionId: Long,
        cadence: ReminderCadence,
        minuteOfDay: Int,
        dayOfWeek: DayOfWeek?,
    ): CollectionReminderRow {
        checkNotFailing()
        val existing = reminders[collectionId]
        val stored =
            CollectionReminder(
                collectionId = collectionId,
                cadence = cadence,
                minuteOfDay = minuteOfDay,
                dayOfWeek = dayOfWeek,
                enabled = existing?.enabled ?: true,
                lastTriggeredAt = existing?.lastTriggeredAt,
            )
        reminders += collectionId to stored
        return rowFor(stored)
    }

    override suspend fun setReminderEnabled(
        collectionId: Long,
        enabled: Boolean,
    ): CollectionReminderRow {
        checkNotFailing()
        val stored =
            requireNotNull(reminders[collectionId]).copy(enabled = enabled)
        reminders += collectionId to stored
        return rowFor(stored)
    }

    override suspend fun deleteReminder(collectionId: Long) {
        checkNotFailing()
        reminders -= collectionId
    }

    /** Acknowledges a firing. Records nothing else — the real write touches one column and no more. */
    override suspend fun markReminderTriggered(
        collectionId: Long,
        at: Instant,
    ) {
        checkNotFailing()
        reminders += collectionId to
            requireNotNull(reminders[collectionId]).copy(lastTriggeredAt = at)
    }

    /** One schedule plus whatever the fixture knows about the collection it belongs to. */
    private fun rowFor(reminder: CollectionReminder): CollectionReminderRow {
        val details = reminderDetails[reminder.collectionId]
        return CollectionReminderRow(
            reminder = reminder,
            collectionName = details?.first.orEmpty(),
            memberCount = details?.second ?: 0,
        )
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
}

/**
 * A cover cache over a throwaway directory, for a test that constructs a [CollectionsCatalogue].
 *
 * The catalogue takes one as a required argument rather than defaulting it, because a default would
 * have to build a real storage and so would touch `~/.azkar` from a constructor. Every suite that
 * builds a catalogue therefore needs one of these, and pointing them all at the same kind of empty
 * temporary directory keeps that requirement from becoming a second thing to get right per test.
 */
internal fun fakeCovers(): CollectionCoverCache =
    CollectionCoverCache(CoverStorage(Files.createTempDirectory("azkar-test-covers")))
