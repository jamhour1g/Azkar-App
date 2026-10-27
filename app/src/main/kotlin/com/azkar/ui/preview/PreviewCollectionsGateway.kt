package com.azkar.ui.preview

import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.RecitationProgress
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.data.HomeCollection
import com.azkar.ui.library.model.CollectionsSnapshot
import java.nio.file.Path
import java.time.Instant
import java.util.Locale

/**
 * An in-memory [CollectionsGateway] for previews that need a whole screen rather than one card.
 *
 * The three screens that take a catalogue cannot be previewed from tiles alone, because the catalogue
 * is the thing that turns domain collections into tiles and it will only do that for a gateway it has
 * been handed. This is that gateway: it answers `fetchSnapshot` from a fixed list and treats every
 * write as a no-op that reports success.
 *
 * **The writes lie, on purpose.** A preview has no assertion to fail and no second frame in which a
 * change would be visible, so a write that returned "nothing changed" would leave a button looking
 * broken for no reason, and one that actually mutated the snapshot would mean carrying copy logic
 * through all twenty-odd methods to support a panel nobody clicks. Returning the collection or the
 * count keeps every call site's `?:` and error branches compiling and unreachable-looking, which is
 * what makes the preview render the same pixels the running app does.
 *
 * Like the rest of this package it is never registered in `appModule`, so resolving a
 * [CollectionsGateway] from the container still reaches the real database.
 *
 * It is one long class rather than several because it answers a twenty-four-member interface, most
 * of whose methods a preview never reaches. Splitting it would mean a preview naming two objects to
 * get a shelf; see `TooManyFunctions.ignoreOverridden` in `config/detekt/detekt-compose.yml`, which is
 * set for exactly this shape — a class whose every method is somebody else's declaration.
 */
internal class PreviewCollectionsGateway(
    private val collections: List<RemembranceCollection>,
    private val remembrances: List<Remembrance>,
    private val recitedCounts: Map<Long, Int> = emptyMap(),
    private val progressByCollection: Map<Long, List<RecitationProgress>> = emptyMap(),
    private val failSnapshot: Boolean = false,
    private val refreshedAt: Instant = previewFixtureInstant(),
) : CollectionsGateway {
    override suspend fun fetchSnapshot(): CollectionsSnapshot {
        check(!failSnapshot) { "Preview collection read failed" }
        return CollectionsSnapshot(
            collections = collections,
            remembrances = remembrances,
            recitedCounts = recitedCounts,
            refreshedAt = refreshedAt,
        )
    }

    override suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    ) = Unit

    override suspend fun saveRemembrance(remembrance: Remembrance): Remembrance = remembrance

    override suspend fun deleteRemembrance(id: Long) = Unit

    override suspend fun createCollection(
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>,
        nameLocale: Locale,
    ): RemembranceCollection =
        requireNotNull(collections.firstOrNull()) { "a preview cannot invent a collection to return" }

    override suspend fun editCollection(
        id: Long,
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>?,
        nameLocale: Locale?,
        tagLocale: Locale?,
    ): RemembranceCollection = find(id)

    override suspend fun setCollectionPinned(
        id: Long,
        pinned: Boolean,
    ): RemembranceCollection = find(id)

    override suspend fun setCollectionFavorite(
        id: Long,
        favorite: Boolean,
    ): RemembranceCollection = find(id)

    override suspend fun duplicateCollection(
        id: Long,
        newName: String,
        nameLocale: Locale,
    ): RemembranceCollection = find(id)

    override suspend fun deleteCollection(id: Long) = Unit

    override suspend fun addMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int = 0

    override suspend fun removeMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int = 0

    override suspend fun moveMember(
        collectionId: Long,
        fromIndex: Int,
        toIndex: Int,
    ) = Unit

    /**
     * A preview never picks a cover, so there is nothing to copy and nothing to name.
     *
     * Returning a plausible name rather than throwing keeps the editor's preview branch reachable in
     * a preview: it renders the photo path it is handed through the same composable the shelf uses,
     * which is the point of previewing it. The file is not there, so the wash shows — the same thing a
     * user sees after deleting a picture out from under a collection.
     */
    override suspend fun storeCoverImage(source: Path): String = "preview-cover"

    override suspend fun touchCollectionOpened(id: Long) = Unit

    override suspend fun fetchProgress(collectionId: Long): List<RecitationProgress> =
        progressByCollection[collectionId].orEmpty()

    override suspend fun recordRecitation(
        collectionId: Long,
        deltas: Map<Long, Int>,
    ): Int = 0

    override suspend fun resetProgress(collectionId: Long): Int = 0

    override suspend fun setCollectionOnHome(
        id: Long,
        onHome: Boolean,
    ): RemembranceCollection = find(id)

    override suspend fun fetchHomeCollections(): List<HomeCollection> =
        collections.map { collection ->
            HomeCollection(
                id = collection.id ?: 0L,
                name = collection.name,
                description = collection.description,
                iconKey = collection.iconKey.orEmpty(),
                memberCount = collection.members.size,
                recitedCount = recitedCounts[collection.id] ?: 0,
                pinnedAt = collection.homePinnedAt,
                lastRecitedAt = null,
            )
        }

    /** A preview has no order to keep: the shelf shows the fixture's own order. */
    override suspend fun reorderCollections(orderedIds: List<Long>) = Unit

    /** A preview has no picks shelf to write to. */
    override suspend fun setReminderPick(
        remembranceId: Long,
        picked: Boolean,
    ): Boolean = picked

    private fun find(id: Long): RemembranceCollection =
        collections.firstOrNull { it.id == id }
            ?: error("no preview collection with id $id")
}
