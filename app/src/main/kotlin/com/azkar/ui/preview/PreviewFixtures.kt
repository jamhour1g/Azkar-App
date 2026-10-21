/**
 * Sample states shared by the IDE `@Preview` functions and the screenshot harness.
 *
 * These live in `src/main` rather than in either of the two places that want them because the two
 * places are different source sets: `src/test` is invisible to `src/main`, and `src/screenshot`
 * cannot see `src/test`'s `internal`s either. That is why `Screenshot.kt` ended up with its own
 * hand-rolled copies of the rail cards and the reminder gateway. Lifting them here means the states
 * a developer sees while iterating on a component and the states a screenshot is graded against are
 * the same objects, so a preview cannot quietly disagree with the rendered PNG.
 *
 * **Nothing in this file may be registered in `appModule`.** These are in-memory objects with no
 * database behind them: a preview that resolved one of them from the container would show fixture
 * data and look like it worked. They are reachable only from a `@Preview` body or from
 * `Screenshot.kt`, both of which name the fake they want at the call site.
 *
 * They do ship in the jar, which is the price of not adding a fourth source set for a few hundred
 * lines of literals. Nothing here opens a file, a socket or a database, so the cost is a little
 * class-loading weight and no behaviour.
 */

package com.azkar.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.azkar.data.storage.CoverStorage
import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import com.azkar.domain.model.impl.RemembranceCollectionImpl
import com.azkar.domain.model.impl.RemembranceImpl
import com.azkar.i18n.ARABIC
import com.azkar.i18n.ENGLISH
import com.azkar.settings.SettingsStore
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.home.CountdownState
import com.azkar.ui.home.FavoriteState
import com.azkar.ui.home.HadithState
import com.azkar.ui.home.HomeGateway
import com.azkar.ui.home.PrayerKey
import com.azkar.ui.home.PrayerRowState
import com.azkar.ui.library.CollectionCoverCache
import com.azkar.ui.library.CollectionHeroActions
import com.azkar.ui.library.CollectionReminderState
import com.azkar.ui.library.CollectionsCatalogue
import com.azkar.ui.library.LibraryHeroControls
import com.azkar.ui.library.MosaicSnippet
import com.azkar.ui.library.RemembranceTileActions
import com.azkar.ui.library.ShelfNarrowing
import com.azkar.ui.library.data.CollectionStats
import com.azkar.ui.library.data.HomeCollection
import com.azkar.ui.library.data.ReminderSources
import com.azkar.ui.library.model.CollectionScope
import com.azkar.ui.library.model.CollectionSort
import com.azkar.ui.library.model.CollectionTile
import com.azkar.ui.library.model.RemembranceTile
import com.azkar.ui.notifications.ReminderQueueConfig
import com.azkar.ui.notifications.ReminderQueueProgress
import com.azkar.ui.shell.AzkarAppState
import com.azkar.ui.shell.ThemePreference
import com.azkar.ui.theme.AppTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** The instant fixtures are stamped with, so nothing shifts when the clock does. */
private val FIXTURE_INSTANT: Instant = Instant.parse("2026-02-20T08:00:00Z")

/** [FIXTURE_INSTANT] for the sibling gateway fake, which cannot reach a private top-level val. */
internal fun previewFixtureInstant(): Instant = FIXTURE_INSTANT

/** A collection id nothing in the shipped seed owns, so a fixture cannot collide with real data. */
const val PREVIEW_COLLECTION_ID: Long = 11L

// ---------------------------------------------------------------------------
// Home rail
// ---------------------------------------------------------------------------

/**
 * The three states a rail card can be in, plus one that must never reach the rail.
 *
 * Built by hand rather than read from the database because none of them is reachable on a fresh
 * install: a card is only pinned or partly recited after the user has done something, which is
 * exactly what neither a screenshot nor a preview can ask them to do. The last entry is empty and is
 * here to be dropped by `HomeCollections.pick` — a card with nothing in it must not reach the rail.
 *
 * The five entries are in the order `pick` prefers them, so a caller that does not narrow gets the
 * four it should see.
 */
fun previewRailCards(): List<HomeCollection> =
    listOf(
        HomeCollection(
            id = 1L,
            name = "Morning Azkar",
            description = null,
            iconKey = "sunrise",
            memberCount = 52,
            recitedCount = 0,
            pinnedAt = Instant.parse("2026-02-20T07:15:00Z"),
            lastRecitedAt = null,
        ),
        HomeCollection(
            id = 2L,
            name = "Evening Azkar",
            description = null,
            iconKey = "sunset",
            memberCount = 54,
            recitedCount = 31,
            pinnedAt = Instant.parse("2026-01-15T20:40:00Z"),
            lastRecitedAt = Instant.parse("2026-02-19T21:05:00Z"),
        ),
        HomeCollection(
            id = 3L,
            name = "After Prayer",
            description = null,
            iconKey = "mosque",
            memberCount = 61,
            recitedCount = 61,
            pinnedAt = Instant.parse("2026-01-10T06:30:00Z"),
            lastRecitedAt = Instant.parse("2026-02-21T13:10:00Z"),
        ),
        HomeCollection(
            id = 4L,
            name = "For the car",
            description = null,
            iconKey = "compass",
            memberCount = 12,
            recitedCount = 3,
            pinnedAt = null,
            lastRecitedAt = Instant.parse("2026-02-18T08:00:00Z"),
        ),
        HomeCollection(
            id = 5L,
            name = "Nothing in here yet",
            description = null,
            iconKey = "khatim",
            memberCount = 0,
            recitedCount = 0,
            pinnedAt = Instant.parse("2026-02-22T09:00:00Z"),
            lastRecitedAt = null,
        ),
    )

/** The ids in [previewRailCards] that should render with the due badge. */
fun previewDueCollectionIds(): Set<Long> = setOf(2L, 4L)

// ---------------------------------------------------------------------------
// Reminders
// ---------------------------------------------------------------------------

/**
 * A [ReminderSources] holding a fixed set of collections, and never writing anywhere.
 *
 * The preview and the screenshot harness both need to show the bell lit on a collection that is in
 * the queue, and the real holder reads that out of the notification preferences — a store neither of
 * them has. A fake with one set in it is the whole of what they need.
 */
class PreviewReminderSources(
    private val enabled: Set<Long> = setOf(PREVIEW_COLLECTION_ID),
) : ReminderSources {
    override val enabledCollectionIds: Set<Long> get() = enabled

    override val queueConfig: ReminderQueueConfig get() = ReminderQueueConfig(collectionIds = enabled.toList())

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

/** The reminder holder the previews and the harness use. */
fun previewReminders(collectionId: Long = PREVIEW_COLLECTION_ID): CollectionReminderState =
    CollectionReminderState(PreviewReminderSources(setOf(collectionId)))

// ---------------------------------------------------------------------------
// Home dashboard
// ---------------------------------------------------------------------------

/** The five prayer rows, with Dhuhr marked as the next one so the countdown has something to name. */
fun previewPrayerRows(): List<PrayerRowState> =
    listOf(
        PrayerRowState(PrayerKey.FAJR, "Fajr", "05:12", "05:12", isNext = false, notificationsEnabled = true),
        PrayerRowState(PrayerKey.DHUHR, "Dhuhr", "13:04", "01:12 PM", isNext = true, notificationsEnabled = true),
        PrayerRowState(PrayerKey.ASR, "Asr", "16:31", "04:31 PM", isNext = false, notificationsEnabled = false),
        PrayerRowState(PrayerKey.MAGHRIB, "Maghrib", "19:44", "07:44 PM", isNext = false, notificationsEnabled = true),
        PrayerRowState(PrayerKey.ISHA, "Isha", "21:03", "09:03 PM", isNext = false, notificationsEnabled = true),
    )

/** A countdown for [previewPrayerRows], naming the same next prayer the table marks. */
fun previewCountdown(): CountdownState =
    CountdownState(
        text = "Dhuhr in 1h 12m",
        remaining = Duration.ofMinutes(72),
    )

/** A favourites carousel sitting on the third of five, so both arrows are live. */
fun previewFavoriteState(): FavoriteState =
    FavoriteState(
        arabic = "أَحَبُّ الْأَعْمَالِ إِلَى اللَّهِ أَدْوَامُهَا",
        english = "The most beloved of deeds to Allah are the most consistent, even if small.",
        source = "Sahih al-Bukhari 6464",
        navigationEnabled = true,
        position = 3,
        total = 5,
    )

/**
 * A [HomeGateway] with the home dashboard's three reads answered from literals.
 *
 * A preview of `HomeScreen` needs this because `HomeScreen` resolves its gateway from Koin inside
 * the composition, and there is no Koin graph in a preview. Every read returns a fixture rather than
 * throwing, so a preview that reaches for data it was not given shows an empty card instead of a
 * red screen — which is the more useful failure while laying out a screen for the first time.
 */
class PreviewHomeGateway(
    private val favorites: List<Remembrance> = listOf(previewRemembrance(1L)),
    private val dailyHadith: Remembrance? = previewRemembrance(2L, favorite = true),
    private val homeCollections: List<HomeCollection> = previewRailCards(),
) : HomeGateway {
    override suspend fun findFavorites(): List<Remembrance> = favorites

    override suspend fun findDailyHadith(): Remembrance? = dailyHadith

    override suspend fun findHomeCollections(): List<HomeCollection> = homeCollections
}

/**
 * A remembrance with an English and an Arabic rendering.
 *
 * Both locales are populated because a preview that shows only one of them cannot tell an English
 * preview apart from a broken translation lookup: `RemembranceI18n` falls back across the chain
 * rather than failing, so a missing Arabic entry renders as English text and looks deliberate.
 */
fun previewRemembrance(
    id: Long,
    arabic: String = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَافِيَةَ",
    english: String = "O Allah, I ask You for wellbeing.",
    source: String? = "Sunan an-Nasa'i 2875",
    favorite: Boolean = false,
): Remembrance =
    RemembranceImpl(
        id = id,
        isFavorite = favorite,
        source = source,
        createdAt = FIXTURE_INSTANT,
        updatedAt = FIXTURE_INSTANT,
        translations =
            mapOf(
                ENGLISH to
                    Remembrance.Translations(
                        explanationPair =
                            Remembrance.Translations.Pair(
                                id = id,
                                text = "A supplication for safety in body and faith.",
                            ),
                        translationPair = Remembrance.Translations.Pair(id = id, text = english),
                    ),
                ARABIC to
                    Remembrance.Translations(
                        explanationPair = Remembrance.Translations.Pair(id = id, text = "دعاءٌ لطلب العافية"),
                        translationPair = Remembrance.Translations.Pair(id = id, text = arabic),
                    ),
            ),
        tags = emptySet(),
    )

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

/**
 * A [SettingsStore] over a throwaway file, for the screens that read notification preferences.
 *
 * A file rather than an in-memory double because DataStore's only constructor takes a
 * `produceFile`, and because the value arrives through a `Flow` fed by file IO — a preview that
 * faked the flow would not exercise the same path the app does.
 *
 * The file is deleted when the preview leaves the composition, so a long session re-rendering
 * previews does not leave a trail of them in the temp directory, and nothing here can reach the
 * developer's real `settings.preferences_pb`: the whole point of naming a path is that this store
 * and the app's are not the same store.
 */
@Composable
fun rememberPreviewSettingsStore(): SettingsStore {
    val path = remember { Files.createTempFile("azkar-preview-", ".preferences_pb") }
    val store =
        remember(path) {
            SettingsStore.create(
                file = path,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
        }
    DisposableEffect(path) {
        onDispose { runCatching { Files.deleteIfExists(path) } }
    }
    return store
}

// ---------------------------------------------------------------------------
// Library tiles
//
// Both tile types are the presentation-facing, resource-free projections the cards actually draw
// from, which is what makes them the cheapest thing to preview: a preview can hand a card a literal
// tile without a gateway, a database, or a resource lookup anywhere in the picture.
// ---------------------------------------------------------------------------

/**
 * A [CollectionTile] for the cards that take one.
 *
 * The defaults describe a collection that is mid-progress and unremarkable, because that is the
 * state most cards spend most of their time in and therefore the state whose regressions nobody
 * notices until a user reports them. The named arguments are the interesting axes: [isPinned],
 * [isOnHome] and [isFavorite] are three separate claims the card prints differently, [recitedCount]
 * moves the completion track, and [memberCount] of zero is the empty-collection edge the completion
 * calculation has to survive.
 */
fun previewCollectionTile(
    id: Long = PREVIEW_COLLECTION_ID,
    name: String = "Morning Azkar",
    description: String = "Short supplications said after the dawn prayer.",
    kind: CollectionKind = CollectionKind.OFFICIAL,
    iconKey: String = "sunrise",
    isPinned: Boolean = false,
    memberCount: Int = 52,
    coverImagePath: String? = null,
    tagIds: Set<Long> = emptySet(),
    isOnHome: Boolean = true,
    isFavorite: Boolean = false,
    inCustomCollections: Int = 1,
    recitedCount: Int = 17,
): CollectionTile =
    CollectionTile(
        id = id,
        name = name,
        description = description,
        kind = kind,
        iconKey = iconKey,
        isPinned = isPinned,
        memberCount = memberCount,
        coverImagePath = coverImagePath,
        tagIds = tagIds,
        updatedAt = FIXTURE_INSTANT,
        lastOpenedAt = FIXTURE_INSTANT,
        isOnHome = isOnHome,
        isFavorite = isFavorite,
        inCustomCollections = inCustomCollections,
        recitedCount = recitedCount,
    )

/**
 * The same card's neighbour states, side by side.
 *
 * Worth having as a set rather than as one tile with three previews of itself: the three claims a
 * card can make about a collection are mutually independent, and the bug this catches is two of
 * them colliding on one card.
 */
fun previewCollectionTiles(): List<CollectionTile> =
    listOf(
        previewCollectionTile(id = 1L, name = "Morning Azkar", iconKey = "sunrise", isPinned = true),
        previewCollectionTile(
            id = 2L,
            name = "Evening Azkar",
            description = "What the evening prayer is read over.",
            iconKey = "sunset",
            isFavorite = true,
            inCustomCollections = 2,
            recitedCount = 31,
            memberCount = 54,
        ),
        previewCollectionTile(
            id = 3L,
            name = "My night route",
            description = "Remembrances for driving.",
            kind = CollectionKind.CUSTOM,
            iconKey = "compass",
            isOnHome = false,
            inCustomCollections = 0,
            recitedCount = 3,
            memberCount = 12,
        ),
        previewCollectionTile(
            id = 4L,
            name = "Nothing in here yet",
            description = "",
            iconKey = "khatim",
            memberCount = 0,
            recitedCount = 0,
            inCustomCollections = 0,
        ),
    )

/** A [RemembranceTile] for the member cards, the search rows and the picker. */
fun previewRemembranceTile(
    id: Long = 101L,
    category: String = "Dhikr",
    primaryPreview: String = "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
    secondaryPreview: String = "I seek Allah's forgiveness and turn to Him in repentance.",
    source: String = "Sahih al-Bukhari 6307",
    isFavorite: Boolean = false,
    inCustomCollections: Int = 2,
    position: Int = 4,
    categories: List<String> = listOf("Dhikr", "Dua"),
    iconKey: String = "book",
): RemembranceTile =
    RemembranceTile(
        id = id,
        category = category,
        primaryPreview = primaryPreview,
        secondaryPreview = secondaryPreview,
        source = source,
        isFavorite = isFavorite,
        inCustomCollections = inCustomCollections,
        position = position,
        categories = categories,
        iconKey = iconKey,
    )

/** Progress figures for the hero, the strip and the progress bar. */
fun previewCollectionStats(
    totalMembers: Int = 54,
    completedMembers: Int = 31,
    totalRepeats: Int = 93,
    remainingRepeats: Int = 62,
    estimatedRemaining: Duration = Duration.ofMinutes(41),
): CollectionStats =
    CollectionStats(
        totalMembers = totalMembers,
        completedMembers = completedMembers,
        totalRepeats = totalRepeats,
        remainingRepeats = remainingRepeats,
        estimatedRemaining = estimatedRemaining,
    )

/**
 * The three progress states the same bar has to draw.
 *
 * [CollectionStats.isComplete] is true only when there are members *and* all of them are counted,
 * which is why an empty collection is deliberately not in here: it reads as nothing to do rather
 * than as finished, and a bar cannot tell the difference on its own.
 */
fun previewCollectionStatsVariants(): List<CollectionStats> =
    listOf(
        previewCollectionStats(),
        previewCollectionStats(
            totalMembers = 61,
            completedMembers = 61,
            totalRepeats = 183,
            remainingRepeats = 0,
            estimatedRemaining = Duration.ZERO,
        ),
        previewCollectionStats(
            totalMembers = 12,
            completedMembers = 0,
            totalRepeats = 0,
            remainingRepeats = 12,
            estimatedRemaining = Duration.ofMinutes(16),
        ),
    )

/** Cover-mosaic members, for the grid a collection card paints when no cover is pinned. */
fun previewMosaicSnippets(): List<MosaicSnippet> =
    listOf(
        MosaicSnippet(id = 1L, text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"),
        MosaicSnippet(id = 2L, text = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَافِيَةَ"),
        MosaicSnippet(id = 3L, text = "سُبْحَانَ اللَّهِ"),
    )

/**
 * A domain [RemembranceCollection] for the two heroes, which take the domain object rather than a
 * tile.
 *
 * Built through `RemembranceCollectionImpl`'s companion `invoke` because the canonical constructor
 * is private — the same door every other caller in the app goes through, so a preview fixture cannot
 * produce a collection the app itself would refuse to build.
 *
 * [members] defaults to four [previewRemembrance]s so a hero that renders a count has something true
 * to count, and [homePinnedAt] is set so the "pinned" pill has something to show.
 */
fun previewCollection(
    id: Long = PREVIEW_COLLECTION_ID,
    name: String = "Morning Azkar",
    description: String? = "Short supplications said after the dawn prayer.",
    kind: CollectionKind = CollectionKind.OFFICIAL,
    iconKey: String? = "sunrise",
    memberCount: Int? = null,
    coverImagePath: String? = null,
    tags: Set<Tag> = emptySet(),
    pinnedAt: Instant? = null,
    homePinnedAt: Instant? = FIXTURE_INSTANT,
    isFavorite: Boolean = false,
    members: List<Remembrance> =
        listOf(
            previewRemembrance(id = 101L),
            previewRemembrance(
                id = 102L,
                arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
                english = "Glory to Allah, and praise be to Him.",
                source = "Muslim 2691",
                favorite = true,
            ),
            previewRemembrance(
                id = 103L,
                arabic = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَافِيَةَ",
                english = "O Allah, I ask You for wellbeing.",
                source = "Ibn Majah 3871",
            ),
            previewRemembrance(
                id = 104L,
                arabic = "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
                english = "I seek Allah's forgiveness and turn to Him in repentance.",
                source = "Abu Dawud 1516",
            ),
        ),
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
        createdAt = FIXTURE_INSTANT,
        updatedAt = FIXTURE_INSTANT,
        lastOpenedAt = FIXTURE_INSTANT,
        homePinnedAt = homePinnedAt,
        isFavorite = isFavorite,
        members = repeatMembers(members, memberCount),
    )

/**
 * Pads or trims [members] to [count], cycling the seed passages when there are more of them than
 * the collection was given.
 *
 * A screen preview needs a member count the hero and the progress bar can quote, and building that
 * many distinct remembrances by hand for every fixture is noise.
 *
 * The repeated passages are given ids of their own, which is the whole point of this having been
 * rewritten. `collection_remembrance` is keyed on `(collection_id, remembrance_id)`, so a
 * collection **cannot** hold the same passage twice — a fixture that cycles identity is describing
 * a collection the app could not build, and the members grid caught it the moment it became a lazy
 * container and asked for a key per item. The words repeat, which is what a preview wants to look
 * like; the identity does not, because identity is what the data model actually constrains.
 */
private fun repeatMembers(
    members: List<Remembrance>,
    count: Int?,
): List<Remembrance> {
    if (count == null || members.isEmpty()) return members
    val seeds = members.take(count)
    return seeds +
        List(count - seeds.size) { index ->
            val seed = members[index % members.size]
            RemembranceImpl(
                id = REPEAT_ID_BASE + index,
                grade = seed.grade,
                isFavorite = seed.isFavorite,
                source = seed.source,
                createdAt = seed.createdAt,
                updatedAt = seed.updatedAt,
                translations = seed.translations,
                tags = seed.tags,
            )
        }
}

/**
 * Ids for the padded members. Started far above the low numbers the hand-written seeds use, so a
 * padded member can never collide with a seed sitting in the same collection.
 */
private const val REPEAT_ID_BASE = 900_000L

/** Callbacks and menu rows for the heroes and the member tiles; every one a no-op. */
internal fun previewCollectionHeroActions(): CollectionHeroActions =
    CollectionHeroActions(
        onBack = {},
        onAddMembers = {},
        onCreateRemembrance = {},
        onTogglePin = {},
        onToggleReminder = {},
        menuItems =
            listOf(
                AzkarMenuItem(label = "Edit", onClick = {}, icon = ActionIcons.edit),
                AzkarMenuItem(label = "Duplicate", onClick = {}, icon = ActionIcons.duplicate),
                AzkarMenuItem(label = "Delete", onClick = {}, icon = ActionIcons.delete),
            ),
    )

/** The four callbacks a remembrance tile can be asked to honour. */
internal fun previewRemembranceTileActions(removable: Boolean = false): RemembranceTileActions =
    RemembranceTileActions(
        onRead = {},
        onToggleFavorite = {},
        onCopy = {},
        onRemoveFromCollection = if (removable) ({ }) else null,
        onDelete = if (removable) ({ }) else null,
    )

/**
 * The banner's search field, filter umbrella and its two buttons.
 *
 * One value because the banner draws all four from one place, so a preview that set them
 * separately would be describing a control surface the banner cannot actually be in.
 *
 * [query] is a parameter rather than always empty because the field's focused and populated states
 * are the ones with layout bugs: an empty field has no text to clip, and the clear button only
 * appears once there is something to clear.
 */
fun previewLibraryHeroControls(
    query: String = "",
    narrowing: ShelfNarrowing =
        ShelfNarrowing(
            scope = CollectionScope.ALL,
            onScopeChanged = {},
            sort = CollectionSort.RECENTLY_UPDATED,
            onSortSelected = {},
            filtersOpen = false,
            onFiltersToggle = {},
        ),
): LibraryHeroControls =
    LibraryHeroControls(
        query = query,
        onQueryChanged = {},
        narrowing = narrowing,
        onImport = {},
        onNew = {},
        onManageTags = {},
    )

/**
 * The catalogue-shaped fixture the three library screens preview against.
 *
 * Two collections and five remembrances, chosen so that the shelf is not uniform: one official set
 * that is part-way through, one of the user's own that has barely started, and a favourite. A screen
 * previewed with one collection per row proves the row works and nothing about how the row behaves
 * next to a row that is a different kind of thing.
 */
internal fun previewGateway(): PreviewCollectionsGateway =
    PreviewCollectionsGateway(
        collections =
            listOf(
                previewCollection(
                    id = PREVIEW_COLLECTION_ID,
                    name = "Morning Azkar",
                    iconKey = "sunrise",
                    memberCount = 54,
                ),
                previewCollection(
                    id = 12L,
                    name = "My night route",
                    description = "Remembrances for driving.",
                    kind = CollectionKind.CUSTOM,
                    iconKey = "compass",
                    homePinnedAt = null,
                    isFavorite = true,
                ),
            ),
        remembrances =
            listOf(
                previewRemembrance(101L),
                previewRemembrance(
                    102L,
                    arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
                    english = "Glory to Allah, and praise be to Him.",
                    source = "Muslim 2691",
                    favorite = true,
                ),
                previewRemembrance(
                    103L,
                    arabic = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَافِيَةَ",
                    english = "O Allah, I ask You for wellbeing.",
                    source = "Ibn Majah 3871",
                ),
                previewRemembrance(
                    104L,
                    arabic = "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
                    english = "I seek Allah's forgiveness and turn to Him in repentance.",
                    source = "Abu Dawud 1516",
                ),
                previewRemembrance(
                    105L,
                    arabic = "رَبِّ زِدْنِي عِلْمًا",
                    english = "My Lord, increase me in knowledge.",
                    source = "Tirmidhi 3161",
                ),
            ),
        recitedCounts = mapOf(PREVIEW_COLLECTION_ID to 31, 12L to 3),
    )

/**
 * An [AzkarAppState] with all three persistence callbacks stubbed out.
 *
 * The callbacks are required rather than defaulted precisely so that nothing can write to the
 * developer's own `settings.preferences_pb` by accident; a preview that passed real ones would be the
 * one caller in the app that quietly did, so it passes `{}` for each and says so here rather than at
 * every call site.
 */
fun previewAzkarAppState(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    locale: Locale = ENGLISH,
    typography: AppTypography = AppTypography(),
): AzkarAppState =
    AzkarAppState(
        initialThemePreference = themePreference,
        initialLocale = locale,
        initialTypography = typography,
        persistTheme = {},
        persist = {},
        persistTypography = {},
    )

/**
 * A [CoverStorage] pointed at a directory no preview will ever write to.
 *
 * Previews render covers but never choose one, and [CollectionCoverCache] only ever *reads*. Giving
 * it the real [CoverStorage.defaultDirectory] would mean a preview reaching into `~/.azkar` for
 * pictures that do not exist, so it is pointed at an empty temp directory instead and every preview
 * cover falls through to the generated wash.
 */
private fun previewCoverStorage(): CoverStorage = CoverStorage(Files.createTempDirectory("azkar-preview-covers"))

/**
 * The shelf, wired to [previewGateway].
 *
 * `remember`ed rather than rebuilt per composition so that a preview which re-renders does not hand
 * the catalogue a new gateway and lose whatever it had loaded, which is the same reason the shell
 * remembers it against the locale.
 *
 * `loadLabels` and `loadData` are deliberately *not* called here. They are `suspend`, the preview
 * renderer does run effects, and letting the screen's own effects drive them is closer to what the
 * running app does than having the fixture pre-load them — if a screen forgets to load, that is a bug
 * the preview is supposed to be able to show.
 */
@Composable
fun rememberPreviewCatalogue(uiLocale: Locale = ENGLISH): CollectionsCatalogue {
    val scope = rememberCoroutineScope()
    val gateway = remember { previewGateway() }
    val covers = remember { CollectionCoverCache(previewCoverStorage()) }
    return remember(uiLocale, scope, gateway, covers) { CollectionsCatalogue(scope, uiLocale, gateway, covers) }
}

/** A [HadithState] for the home dashboard's daily-hadith card and its details dialog. */
internal fun previewHadithState(
    arabic: String = "إِنَّ الدِّينَ يُسْرٌ، وَلَنْ يُشَادَّ الدِّينَ أَحَدٌ إِلَّا غَلَبَهُ",
    english: String =
        "Indeed, the religion is ease, and no one will be overcome by the religion except through being overcome.",
    source: String? = "Sahih al-Bukhari 39",
): HadithState =
    HadithState(
        arabic = arabic,
        english = english,
        source = source,
    )
