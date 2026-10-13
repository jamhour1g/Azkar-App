package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import com.azkar.domain.model.withMember
import com.azkar.domain.model.withMembers
import com.azkar.domain.model.withoutMember
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.libraryLoadFailed
import com.azkar.generated.resources.libraryLoading
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.data.ImportReport
import com.azkar.ui.library.data.MembershipCounts
import com.azkar.ui.library.data.TransferCollection
import com.azkar.ui.library.data.findById
import com.azkar.ui.library.data.membershipCountsOf
import com.azkar.ui.library.data.withFavorite
import com.azkar.ui.library.data.withMemberFavorite
import com.azkar.ui.library.data.withMemberTexts
import com.azkar.ui.library.data.withTexts
import com.azkar.ui.library.model.CollectionsSnapshot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

private val logger = KotlinLogging.logger {}

/**
 * What a shelf mutation is asking the screen to say.
 *
 * The state cannot raise a toast itself — the host owns that — so every mutation reports back with
 * one of these instead of writing a message into the footer. Putting them in a sealed type rather
 * than a bare string is what lets the screen translate them *after* the locale switch that rebuilds
 * the tree, instead of baking one language's text into state that outlives it.
 */
sealed interface CollectionNotice {
    data class Message(
        val key: CollectionNoticeKey,
    ) : CollectionNotice
}

enum class CollectionNoticeKey {
    CREATED,
    EDITED,
    DELETED,
    DUPLICATED,
    PINNED,
    UNPINNED,
    MEMBERS_ADDED,
    MEMBERS_REMOVED,
    FAVORITE_ADDED,
    FAVORITE_REMOVED,
    PROGRESS_RESET,
    ON_HOME,
    OFF_HOME,
    FAVORITED,
    UNFAVORITED,
    REMINDER_SAVED,
    REMINDER_REMOVED,
    TAG_RENAMED,
    TAG_DELETED,
    TAG_RESTORED,
    FAILED,
}

/**
 * The per-device display preferences a collection carries.
 *
 * Named as a type rather than left as two near-identical state methods, because the two are the same
 * kind of decision — where the user wants to see the collection — and both are allowed on a shipped
 * collection for the same reason: neither can disagree with what the catalogue says is in it. See
 * [CollectionsCatalogue.setDisplay].
 */
enum class CollectionDisplayPreference {
    /** Keeps the collection at the top of the shelf, above everything not pinned. */
    PINNED,

    /** Adds the collection to the rail on the Home screen. */
    ON_HOME,

    /**
     * Marks the collection as one the user comes back to.
     *
     * A member of this family rather than a separate method on the catalogue, because it is answered
     * the same way and belongs in the same place: a per-device claim about how this device shows the
     * collection, written through one mutation that logs, applies and reports. What it is *not* is
     * [FAVORITE_ADDED] — that notice is about a starred remembrance, and reusing it here would give
     * one word two meanings.
     */
    FAVORITE,
    ;

    /** What the log line says was attempted, worded so a failure report names the actual change. */
    fun logAction(
        id: Long,
        on: Boolean,
    ): String =
        when (this) {
            PINNED -> if (on) "pin collection $id" else "unpin collection $id"
            ON_HOME -> if (on) "show collection $id on Home" else "hide collection $id from Home"
            FAVORITE -> if (on) "star collection $id" else "unstar collection $id"
        }

    /** The toast each direction reports, kept beside the log line so the two cannot disagree. */
    fun loggedNotice(on: Boolean): CollectionNoticeKey =
        when (this) {
            PINNED -> if (on) CollectionNoticeKey.PINNED else CollectionNoticeKey.UNPINNED
            ON_HOME -> if (on) CollectionNoticeKey.ON_HOME else CollectionNoticeKey.OFF_HOME
            FAVORITE -> if (on) CollectionNoticeKey.FAVORITED else CollectionNoticeKey.UNFAVORITED
        }
}

/**
 * The whole library's data, read once and shared by every screen that draws any of it.
 *
 * This is the holder that used to be [CollectionsState] in full, split out from it because the two
 * halves have nothing to do with each other. What lives here is a fact about the *library* — these
 * are the collections, these are the remembrances, this is how far through each one the user has got
 * — and it is the same answer for the shelf, the detail page, the session and the dialogs raised
 * from any of them. What stayed behind in [CollectionsState] is a fact about the *shelf*: what the
 * user has narrowed it by, which only the shelf ever asks.
 *
 * The split is what lets one instance live at the top of the tree. Before it, each library screen
 * built its own holder inside its own composition and asked for a whole-shelf read on the way in, so
 * opening a collection and starting a session re-read the entire catalogue — every collection with
 * every member, every member with every translation — three times over. A single holder that the
 * shell remembers turns that into one read for the whole visit, and it is also what makes the pages
 * unable to disagree about what a collection holds: there is only one copy of the answer to be out
 * of date.
 *
 * Reads are still the database's job on every [loadData] call; what this stops is re-reading
 * something already in memory, which is a different thing from a stale cache and is why there is no
 * TTL or invalidation scheme here. The shelf re-reads whenever it is asked to — through [loadData]
 * after a language change, and through the mutations below after anything is written — so the only
 * thing reused is a read whose result is still there.
 *
 * @param uiLocale exposed because the read dialog and the detail screen need the same locale the
 * presenter was built with — a locale they read once and then draw from, rather than asking the
 * system for a fresh default that could disagree with it.
 * @param gateway required rather than defaulted to a `DomainCollectionsGateway`. The default used to
 * make every caller that forgot to pass one silently open the real database, which is exactly the
 * mistake a test is most likely to make and the one it would least notice; a caller now either names
 * the gateway it wants or says so by construction at the composition root.
 *
 * The method count is suppressed rather than reduced. This is the app's one façade over the whole
 * collections domain, and each method is one gateway call, one notice key and one local patch — the
 * shape a façade is supposed to have. The extraction the rule is really asking for was tried and
 * rejected: the three functions that look most movable (starring a remembrance, correcting its text,
 * rearranging a collection's members) each reach the flat list, every collection's own copy of its
 * members, the membership counts and the reload, so moving them behind a holder would hand that
 * holder the entire private surface of this class and leave the behaviour identical. The count is the
 * symptom; the alternative is the smell.
 */
@OptIn(ExperimentalAtomicApi::class)
@Stable
@Suppress("TooManyFunctions")
class CollectionsCatalogue(
    private val scope: CoroutineScope,
    val uiLocale: Locale,
    private val gateway: CollectionsGateway,
    /**
     * Decodes cover pictures, and holds one per file for the life of the shelf.
     *
     * Held here rather than resolved per card because a shelf of fourteen collections is painted by
     * fourteen separate calls that do not see each other, and a cache each of them owned would decode the
     * same file fourteen times and know none of the others had. Required rather than defaulted: a default
     * would have to reach into `~/.azkar` to build a real one, which is a filesystem side effect in a
     * constructor argument — in a test, or a preview, or anywhere else that never meant to touch disk.
     */
    val covers: CollectionCoverCache,
) {
    private val loadVersion = AtomicLong(0L)

    /** Turns domain objects into the strings the tiles draw. Owned here so every screen shares it. */
    val presenter: CollectionPresenter = CollectionPresenter(uiLocale)

    // ---- localized labels ----------------------------------------------------------------------

    private var loadingLabel: String = ""
    private var loadFailedLabel: String = ""

    /** Resolves every label the tiles bake into their state. */
    suspend fun loadLabels() {
        presenter.load()
        loadingLabel = getString(Res.string.libraryLoading)
        loadFailedLabel = getString(Res.string.libraryLoadFailed)
    }

    // ---- data ----------------------------------------------------------------------------------

    var allCollections: List<RemembranceCollection> by mutableStateOf(emptyList())
        private set

    var allRemembrances: List<Remembrance> by mutableStateOf(emptyList())
        private set

    /**
     * Every tag in use anywhere in the library, alphabetically and with no duplicates.
     *
     * Collected from the loaded collections rather than read from the tag table, so a picker can
     * never offer a tag that matches nothing — which is the failure a "create a new one" affordance
     * sits right next to, and the user pays for when storage refuses the save. Ordered by name
     * because nothing here has a natural order, and one that shifted as a tag was renamed would move
     * a chip out from under the pointer mid-click.
     *
     * Lives on the catalogue rather than on [CollectionsState] because two screens now need it: the
     * shelf's label chips, and the editor dialog's tag picker, which the detail page opens too and
     * where there is no [CollectionsState] to ask.
     */
    val shelfTags: List<Tag>
        get() =
            allCollections
                .flatMap { it.tags }
                .distinctBy { it.id }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName(uiLocale) })

    var loading: Boolean by mutableStateOf(false)
        private set

    /**
     * The only text this library ever puts at the bottom of a screen, and only when it has something
     * to say: "Loading…" while a read is in flight, "Couldn't load…" when one failed.
     *
     * Empty the rest of the time, on purpose. It used to also carry a "Updated <timestamp>" stamp on
     * success, which was a fact about the *database* rather than about anything the user did — the
     * library is a local file that this app owns, there is no server that could have been out of
     * date, and a timestamp that changes every read teaches the reader to look for staleness where
     * there is none. A screen that has just been told it is current has not been told anything. The
     * failure and the in-flight case survive because those *are* about the user's side of it: one
     * explains an empty shelf, the other explains a slow one.
     */
    var infoLine: String by mutableStateOf("")
        private set

    /**
     * How many of the user's own collections hold each remembrance. Recomputed with the shelf.
     */
    var membershipCounts: MembershipCounts by mutableStateOf(MembershipCounts.EMPTY)
        private set

    /**
     * How many of each collection's members have been recited, keyed by collection.
     *
     * Held beside [progress] rather than folded into it because the two answer different questions
     * at different costs: this is one figure per collection, read once for the whole shelf, while
     * [progress] is one row per recited member and is read for the single collection a session is
     * about. A collection that is not a key here has nothing counted, which is not the same answer as
     * a collection counted zero times.
     */
    var recitedCounts: Map<Long, Int> by mutableStateOf(emptyMap())
        private set

    /**
     * The collection that mirrors the user's stars, or `null` when the library has no such mirror.
     *
     * Held beside the data it describes rather than reached for from the gateway, because the only
     * thing that needs it is [setFavoriteOn], and that runs on the spot where a star is flipped —
     * before any write has returned, let alone a re-read. A `null` here means the star was flipped but
     * there is nothing to mirror it into, which is a state worth surviving rather than crashing on.
     */
    var favoritesCollectionId: Long? by mutableStateOf(null)
        private set

    /**
     * How far through each collection the user has got.
     *
     * A separate holder rather than four more members of this class: progress is per (collection,
     * remembrance) while a [RemembranceCollection] knows only its members, and the shelf's own
     * concerns — which collections, filtered how, sorted which way — have nothing to say about how
     * many times a passage has been said.
     */
    val progress = CollectionProgressState(scope, gateway)

    // ---- loading -------------------------------------------------------------------------------

    /**
     * Reads the whole library in the background, unless it is already in memory.
     *
     * A read that is already under way is not repeated, and neither is one that has finished. That
     * guard is the whole point of this holder existing: with one shared instance every screen that
     * needs the catalogue asks for it on the way in, and without it three screens entering in a row
     * would mean three identical whole-library reads — the 270-odd remembrances with every
     * translation and explanation, plus every collection with every member — for one answer.
     *
     * A read that *fails* clears the guard again, so a screen that mounts after a failure still gets
     * a second try rather than being left permanently empty by a blip it never saw.
     */
    fun loadData() {
        if (readStarted) {
            return
        }
        readStarted = true
        startRead(showSpinner = true)
    }

    /** Whether a whole-library read has been asked for and has not been thrown away by a failure. */
    private var readStarted: Boolean by mutableStateOf(false)

    /**
     * A background read guarded by a monotonic version so a slow earlier request can never
     * overwrite a newer one.
     *
     * There is no `Dispatchers.IO` hop here: the domain ports are `suspend` and the data layer
     * already moves itself off the caller's thread, so adding another would only be a second
     * dispatch for work that is already asynchronous.
     */
    private fun startRead(showSpinner: Boolean) {
        val version = loadVersion.incrementAndFetch()
        if (showSpinner) {
            loading = true
            infoLine = loadingLabel
        }
        scope.launch {
            val result = runCatching { gateway.fetchSnapshot() }
            if (version != loadVersion.load()) return@launch
            applyResult(result)
        }
    }

    /**
     * Commits a finished read, whichever way it went.
     *
     * A failed load empties the shelf rather than leaving the last good one on screen: the shelf the
     * user is looking at would no longer match the database, and every tile on it is a link into it.
     * The info line is what the user sees, and it is a single localized sentence for every underlying
     * cause — which is exactly why [applyResult] logs the failure as well. Without it the screen says
     * "load failed" and the reason never reaches the console.
     */
    private fun applyResult(result: Result<CollectionsSnapshot>) {
        val snapshot =
            result.getOrElse { failure ->
                logger.error(failure) { "Unable to load the library shelf" }
                readStarted = false
                allCollections = emptyList()
                allRemembrances = emptyList()
                recitedCounts = emptyMap()
                favoritesCollectionId = null
                membershipCounts = MembershipCounts.EMPTY
                loading = false
                infoLine = loadFailedLabel
                return
            }
        allCollections = snapshot.collections
        allRemembrances = snapshot.remembrances
        recitedCounts = snapshot.recitedCounts
        favoritesCollectionId = snapshot.favoritesCollectionId
        membershipCounts = membershipCountsOf(snapshot.collections)
        loading = false
        // Cleared rather than set to a success sentence: see [infoLine]. A read that worked says
        // nothing that the shelf it just filled does not already say.
        infoLine = ""
    }

    /** Looks up a collection for the detail screen, and refreshes its "last opened" stamp. */
    fun findCollection(id: Long): RemembranceCollection? {
        val collection = allCollections.findById(id) ?: return null
        scope.launch { runCatching { gateway.touchCollectionOpened(id) } }
        return collection
    }

    // ---- mutations -----------------------------------------------------------------------------

    /**
     * Flips a star, applying it locally first and rolling back if the write fails.
     *
     * The starred mirror is patched here rather than re-read. Its membership is one row either way,
     * and the answer is already in hand once the flag is patched — going back to the database for it
     * would re-read the entire catalogue to learn something the UI just decided. The rollback restores
     * both the star and the mirror, so a failed write leaves nothing showing that did not happen.
     */
    fun toggleFavorite(id: Long) {
        val target = allRemembrances.findById(id) ?: return
        val nextValue = !target.isFavorite
        setFavoriteOn(id, nextValue)
        scope.launch {
            runCatching { gateway.setFavorite(id, nextValue) }
                .onFailure { failure ->
                    // The star is rolled back below, so the screen recovers on its own. The cause of
                    // the rollback is still a failed write, and that is what has to survive.
                    logger.error(failure) { "Unable to persist the favorite flag for remembrance $id" }
                    setFavoriteOn(id, target.isFavorite)
                    infoLine = loadFailedLabel
                }
        }
    }

    /**
     * Patches the star, and the starred mirror's membership to match, in local state only.
     *
     * Does nothing to the mirror when [favoritesCollectionId] is null, which is the honest answer:
     * there is no collection to add the member to, and inventing a second definition of "the starred
     * collection" to fall back on is exactly the bug that a rename would then cause.
     *
     * The flag is stamped into *every* collection's copy of the member, not just the flat list, and
     * that is the whole of it: a collection carries its own [Remembrance] instances, so patching
     * [allRemembrances] alone leaves the collection pages — which is where a star is pressed — still
     * holding the old value. A star that is written to the database and does not fill in on the card
     * that was pressed reads to the user as a broken button, and no amount of re-reading afterwards
     * is a fix for it.
     */
    private fun setFavoriteOn(
        id: Long,
        favorite: Boolean,
    ) {
        val star = allRemembrances.findById(id) ?: return
        allRemembrances = allRemembrances.map { if (it.id == id) it.withFavorite(favorite) else it }
        val mirror = favoritesCollectionId
        allCollections =
            allCollections.map { collection ->
                val stamped = collection.withMemberFavorite(id, favorite)
                when {
                    stamped.id != mirror -> stamped
                    favorite -> stamped.withMember(star)
                    else -> stamped.withoutMember(id)
                }
            }
        membershipCounts = membershipCountsOf(allCollections)
    }

    /**
     * Runs a shelf mutation and reports the outcome.
     *
     * Every write goes through here so the three things that must happen together always do: the
     * change is applied to local state, a failure is logged with the id that caused it, and the
     * screen is told which sentence to show. A mutation that only reported success would let a
     * rename fail silently, which for a rename looks exactly like a mis-click.
     */
    fun mutate(
        description: String,
        onSuccess: CollectionNoticeKey?,
        operation: suspend () -> Unit,
    ) {
        // Held outside the callback because `runCatching` yields a `Result<Unit>`, whose `onSuccess`
        // hands back that `Unit` — naming the notice in the lambda would shadow it with the wrong type.
        val notice = onSuccess
        scope.launch {
            runCatching { operation() }
                .onSuccess {
                    if (notice != null) {
                        onNotice = CollectionNotice.Message(notice)
                    }
                }.onFailure { failure ->
                    logger.error(failure) { "Unable to $description" }
                    infoLine = loadFailedLabel
                    onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED)
                }
        }
    }

    /**
     * The one-shot message the screen should raise, consumed by reading it once and clearing.
     *
     * The type argument is explicit because `mutableStateOf(null)` on its own infers
     * `MutableState<Nothing?>`, and an invariant `MutableState` narrowed to `Nothing?` then refuses
     * every assignment the rest of the class actually makes.
     */
    var onNotice: CollectionNotice? by mutableStateOf<CollectionNotice?>(null)

    fun createCollection(
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair> = emptyMap(),
        onCreated: (Long) -> Unit,
        nameLocale: Locale = Locale.ENGLISH,
    ) {
        mutate(
            description = "create the collection \"$name\"",
            onSuccess = CollectionNoticeKey.CREATED,
        ) {
            val created =
                gateway.createCollection(
                    name = name,
                    description = description,
                    tagNames = tagNames,
                    coverImagePath = coverImagePath,
                    translations = translations,
                    nameLocale = nameLocale,
                )
            val id = requireNotNull(created.id) { "A saved collection came back without an identifier" }
            allCollections = allCollections + created
            membershipCounts = membershipCountsOf(allCollections)
            onCreated(id)
        }
    }

    /**
     * Writes every field of a collection that already exists.
     *
     * Named for what it does rather than for the name it takes: this used to be the rename, and a
     * method called `renameCollection` that also replaces the description, the tags and the cover is
     * how the dialog ended up labelled "Rename Collection" while offering four fields. The write is
     * still one — a collection has no partial update — so the name is one of four arguments rather
     * than one of one.
     */
    fun editCollection(
        id: Long,
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>? = null,
        nameLocale: Locale? = null,
        tagLocale: Locale? = null,
    ) = mutate(
        description = "edit collection $id",
        onSuccess = CollectionNoticeKey.EDITED,
    ) {
        val saved =
            gateway.editCollection(
                id = id,
                name = name,
                description = description,
                tagNames = tagNames,
                coverImagePath = coverImagePath,
                translations = translations,
                nameLocale = nameLocale,
                tagLocale = tagLocale,
            )
        replaceCollection(saved)
    }

    fun deleteCollection(id: Long) =
        mutate(
            description = "delete collection $id",
            onSuccess = CollectionNoticeKey.DELETED,
        ) {
            gateway.deleteCollection(id)
            allCollections = allCollections.filterNot { it.id == id }
            membershipCounts = membershipCountsOf(allCollections)
        }

    /**
     * Writes corrected texts over a single passage, everywhere the app holds a copy of it.
     *
     * Both graphs, for the reason [setFavoriteOn] has to touch both: the flat catalogue and each
     * collection's own members are separate instances, so patching only the flat list would leave
     * every card on a collection page showing the words the user just corrected.
     *
     * The patch is local and immediate, the way a favourite is, because the editor closes over the
     * card the user was looking at. A reload afterwards is what tells the truth if the write failed.
     */
    fun saveRemembranceTexts(
        id: Long,
        arabic: String,
        english: String?,
    ) {
        val current = allRemembrances.findById(id) ?: return
        val patched = current.withTexts(arabic, english)
        allRemembrances = allRemembrances.map { if (it.id == id) patched else it }
        allCollections = allCollections.map { it.withMemberTexts(id, arabic, english) }
        membershipCounts = membershipCountsOf(allCollections)
        scope.launch {
            mutate(
                description = "save texts of remembrance $id",
                onSuccess = null,
            ) {
                gateway.saveRemembrance(patched)
                reloadQuietly()
            }
        }
    }

    /**
     * Copies a picture the user just chose into the app's own covers directory.
     *
     * The copy happens here rather than in the dialog because the dialog has to hold the stored name
     * in order to preview it, and holding a name nothing references yet would leak a file every time
     * the user cancelled the form afterwards.
     */
    suspend fun storeCoverImage(source: java.nio.file.Path): String = gateway.storeCoverImage(source)

    /**
     * Asks the user for a picture and returns the name the app stored it under, or `null`.
     *
     * Lives beside [storeCoverImage] rather than in either screen because both the shelf editor and
     * the detail editor need the same two steps in the same order, and a null here means both "the
     * user cancelled" and "the file could not be read" — the dialog shows the same thing either way,
     * so the screens have nothing to do with the difference.
     *
     * The dialog's own title is passed in rather than read here. This holder is remembered across a
     * language switch, so a caption resolved down here would be the language the app launched in; the
     * dialog, which is composed inside the keyed subtree, is the one place that sees the current one.
     */
    suspend fun pickCoverImage(title: String): String? =
        CoverImageChooser
            .choose(title)
            ?.file
            ?.toPath()
            ?.let { CoverImageChooser.ingest(it, ::storeCoverImage) }

    fun duplicateCollection(
        id: Long,
        newName: String,
        nameLocale: Locale = Locale.ENGLISH,
    ) = mutate(
        description = "duplicate collection $id",
        onSuccess = CollectionNoticeKey.DUPLICATED,
    ) {
        val copy = gateway.duplicateCollection(id, newName = newName, nameLocale = nameLocale)
        allCollections = allCollections + copy
        membershipCounts = membershipCountsOf(allCollections)
    }

    /**
     * Turns one of a collection's per-device display preferences on or off.
     *
     * Allowed on a collection the user does not own. Both preferences — the shelf pin and the Home
     * rail — say where the user wants to *see* a collection rather than anything about what is in
     * it, so neither can contradict a shipped collection's catalogue-derived contents. That is the
     * whole reason they are one function and not two: they are the same kind of decision, and a
     * caller that had to pick between two near-identical methods could pick the one that enforces
     * ownership by accident.
     */
    fun setDisplay(
        id: Long,
        preference: CollectionDisplayPreference,
        on: Boolean,
    ) = mutate(
        description = preference.logAction(id, on),
        onSuccess = preference.loggedNotice(on),
    ) {
        replaceCollection(
            when (preference) {
                CollectionDisplayPreference.PINNED -> gateway.setCollectionPinned(id, on)
                CollectionDisplayPreference.ON_HOME -> gateway.setCollectionOnHome(id, on)
                CollectionDisplayPreference.FAVORITE -> gateway.setCollectionFavorite(id, on)
            },
        )
    }

    fun addMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
        onAdded: (Int) -> Unit,
    ) = mutate(
        description = "add ${remembranceIds.size} remembrances to collection $collectionId",
        onSuccess = CollectionNoticeKey.MEMBERS_ADDED,
    ) {
        val added = gateway.addMembers(collectionId, remembranceIds)
        onAdded(added)
        reloadQuietly()
    }

    fun removeMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ) = mutate(
        description = "remove ${remembranceIds.size} remembrances from collection $collectionId",
        onSuccess = CollectionNoticeKey.MEMBERS_REMOVED,
    ) {
        gateway.removeMembers(collectionId, remembranceIds)
        reloadQuietly()
    }

    /**
     * Puts the member at [fromIndex] where [toIndex] was, in the order the user arranged.
     *
     * The local patch comes first and the read comes after, which is the opposite of every other
     * write here. The others are asked for by a button and are answered by a toast; this one is
     * asked for by a drag the user has just finished watching, and a tile that snapped back to its
     * old slot while the database caught up would read as the drop having failed. So the shelf is
     * told first and re-read to confirm, and a failure leaves the notice saying so.
     *
     * No notice on success: the row that moved *is* the notice, and a toast over the top of it would
     * be telling the user about something they are already looking at.
     */
    fun moveMember(
        collectionId: Long,
        fromIndex: Int,
        toIndex: Int,
    ) {
        reorderMembers(collectionId, fromIndex, toIndex)
        mutate(
            description = "move member $fromIndex to $toIndex in collection $collectionId",
            onSuccess = null,
        ) {
            gateway.moveMember(collectionId, fromIndex, toIndex)
            reloadQuietly()
        }
    }

    /**
     * The optimistic half of [moveMember]: swaps two members inside the collection, locally.
     *
     * Guarded the same way the repository guards it, so a drag released over a list that has since
     * changed under the pointer writes nothing.
     */
    private fun reorderMembers(
        collectionId: Long,
        fromIndex: Int,
        toIndex: Int,
    ) {
        val target = allCollections.findById(collectionId) ?: return
        val ordered = target.members.toMutableList()
        if (fromIndex !in ordered.indices || toIndex !in ordered.indices || fromIndex == toIndex) return
        ordered.add(toIndex, ordered.removeAt(fromIndex))
        allCollections =
            allCollections.map { if (it.id == collectionId) it.withMembers(ordered) else it }
    }

    /**
     * Swaps in a freshly-read collection, keeping its position on the shelf.
     *
     * Position is preserved rather than appended to: after a rename the collection must not jump to
     * the bottom of a name-sorted shelf, which is the most visible way a save can look broken.
     */
    private fun replaceCollection(updated: RemembranceCollection) {
        allCollections =
            allCollections.map { if (it.id == updated.id) updated else it }.ifEmpty {
                listOf(updated)
            }
        membershipCounts = membershipCountsOf(allCollections)
    }

    /**
     * Re-reads the shelf in the background, leaving the spinner and the footer alone.
     *
     * A membership change is only visible in the database, so the shelf has to be re-read — but the
     * user is mid-flow (they have just opened a detail screen), and flashing the whole screen back
     * into a loading state to add one tile would be worse than the moment's staleness. A failure here
     * is deliberately swallowed: the mutation that triggered it has already reported its own
     * outcome, and a second error would be about the read, not the write.
     *
     * This deliberately bypasses the [loadData] guard. It is the one read that must always run,
     * because it is the one that follows a write.
     */
    private fun reloadQuietly() {
        startRead(showSpinner = false)
    }

    /**
     * Re-reads the shelf because the tag vocabulary changed underneath it.
     *
     * Public, unlike [reloadQuietly], because the write that needs it happened somewhere else. A tag
     * renamed on the Manage tags screen is reached from the shelf's own label chips, and the chips are
     * derived from [shelfTags] — which is a copy taken when the catalogue was last read. Without this,
     * a tag renamed in the next screen over would keep its old name on every chip behind it until
     * something unrelated triggered a read, which is the one thing a rename cannot be allowed to do:
     * the whole reason to rename a tag is that the name is wrong everywhere at once.
     *
     * The same reason it does not reload itself: this catalogue owns collections and their members,
     * not tags. It is told the vocabulary moved rather than reaching for the tag service to find out,
     * so the shelf keeps asking its own gateway for the shelf's own facts.
     */
    fun tagsChanged() {
        reloadQuietly()
    }

    /**
     * Applies a decoded transfer payload and re-reads the shelf behind it.
     *
     * This is the shelf's own door to the import gateway for the same reason [CollectionImportState]
     * is not handed one: the gateway is private, and a state class reaching through another state
     * class's private field to get at it would be a worse coupling than one delegating method. The
     * gateway re-plans against the live shelf, so a merge that became a create while the dialog was
     * open lands where it belongs rather than where it was previewed.
     *
     * Suspends because the gateway does, and is expected to be called from a coroutine the import
     * flow owns.
     *
     * Returned as a [Result] rather than a bare report because "nothing was imported" and "the
     * import failed" are different answers, and a dialog that cannot tell them apart will tell the
     * user the wrong one.
     */
    suspend fun importCollections(transfers: List<TransferCollection>): Result<ImportReport> {
        val outcome =
            runCatching { gateway.importCollections(transfers) }
                .onFailure { failure -> logger.error(failure) { "Unable to import ${transfers.size} collections" } }
        if (outcome.isSuccess) {
            reloadQuietly()
        }
        return outcome
    }
}
