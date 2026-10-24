package com.azkar.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.azkar.domain.model.Remembrance
import com.azkar.generated.resources.*
import com.azkar.i18n.ENGLISH
import com.azkar.i18n.RemembranceI18n
import com.azkar.settings.SettingsStore
import com.azkar.ui.home.FavoritesState
import com.azkar.ui.home.HadithState
import com.azkar.ui.home.NotificationSettingsState
import com.azkar.ui.home.PrayerTimesState
import com.azkar.ui.library.CollectionReminderState
import com.azkar.ui.notifications.NotificationPreferencesRepository
import com.azkar.ui.preview.previewHadithState
import com.azkar.ui.preview.previewRailCards
import com.azkar.ui.preview.previewReminders
import com.azkar.ui.preview.rememberPreviewSettingsStore
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.AzkarWindowSize
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private val logger = KotlinLogging.logger {}

private const val COUNTDOWN_INTERVAL_MILLIS = 1_000L

/**
 * Fixed width of the favourites rail.
 *
 * Narrow enough that the prayer table beside it still fits "Isha" and its 24-hour time on one line
 * at [com.azkar.ui.theme.AzkarBreakpoints.compactWidth], wide enough for the favourite's dhikr to
 * wrap over two or three lines instead of running the card taller than the azkar card next to it.
 * The rail's heading is a 15sp label now, so the width is set by the dhikr alone.
 */
private val RAIL_WIDTH = 380.dp

/**
 * The three texts `DailyHadithCardComponent.applyDailyHadith` pushed into its FXML nodes.
 *
 * `internal` rather than `private` because [HomeContent] takes one, and a `@Preview` of that
 * content has to be able to supply it.
 */
internal data class HadithState(
    val remembranceId: Long?,
    val arabic: String,
    val english: String,
    /**
     * Nullable because the domain's `source` is optional. The cards resolve a missing source to the
     * localized "not available" string themselves, which is only possible inside a composition —
     * this is why the fallback is not baked in here the way the Arabic and English ones are.
     */
    val source: String?,
)

/**
 * The three observable holders the JavaFX controllers became, plus the dashboard's navigation
 * callbacks.
 *
 * [HomeScreen] builds all of them once and none varies independently below it, so the sub-composables
 * take this instead of a separate argument each. The callbacks are bundled rather than listed
 * individually because there are now three of them and a dashboard reaching for a seventh constructor
 * argument is how a column quietly stops being given what it needs.
 *
 * `internal` rather than `private` so that a `@Preview` can hand [HomeContent] a set of these
 * without going through the screen's own Koin reads. Nothing outside this file builds one.
 */
internal class HomeInputs(
    val prayerTimes: PrayerTimesState,
    val favorites: FavoritesState,
    val notifications: NotificationSettingsState,
    val rail: HomeRailState,
    val reminders: CollectionReminderState,
    val navigation: HomeNavigation,
)

/**
 * Where each part of the dashboard can take the user.
 *
 * Grouped so that adding a destination does not touch the signature of every holder bundle on the
 * way down — the rail reached for the shelf this way.
 */
internal class HomeNavigation(
    val onOpenLibrary: () -> Unit,
    val onOpenRemembrance: (Long) -> Unit,
    val onOpenCollection: (Long) -> Unit,
)

/**
 * `com.azkar.components.home.HomeComponent` — the whole home dashboard.
 *
 * The JavaFX root was a `ScrollPane` holding a `VBox.main-content` (greeting + a two-column
 * dashboard); the Compose port keeps the same scroll container, spacing and column split. The
 * three controllers (`PrayerTimesController`, `FavoritesController`,
 * `NotificationSettingsController`) became the observable holders carried by [HomeInputs].
 */
@Composable
fun HomeScreen(
    onOpenLibrary: () -> Unit,
    onOpenRemembrance: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    reminders: CollectionReminderState,
    modifier: Modifier = Modifier,
) {
    val uiLocale = AzkarTheme.locale

    val favorites = remember(uiLocale) { FavoritesState(uiLocale) }
    val rail = remember { HomeRailState() }
    val store = koinInject<SettingsStore>()
    val notificationPreferences = koinInject<NotificationPreferencesRepository>()
    val prayerLocation by notificationPreferences.prayerLocation.collectAsState()
    val prayerTimes = remember(uiLocale, prayerLocation) { PrayerTimesState(uiLocale, prayerLocation) }
    val notificationScope = rememberCoroutineScope()
    // Both of these come from the container: the settings switches observe the stored file, and the
    // home dashboard reads through the same gateway the library shelf writes through, so a
    // collection added on one screen is on the other's rail without either screen reloading it.
    val notifications =
        remember(notificationScope, store) { NotificationSettingsState(store = store, scope = notificationScope) }
    val gateway = koinInject<HomeGateway>()
    // Hoisted out of the `remember` because that block is not a composable context. The three
    // texts are static per locale, so reading them once per composition is the whole cost.
    val initialHadith = placeholderHadith()
    var hadith by remember { mutableStateOf(initialHadith) }

    // Rebuilt on every recomposition, which is free: the three holders are remembered instances and
    // nothing below keys a `LaunchedEffect` off `inputs` itself.
    val inputs =
        HomeInputs(
            prayerTimes = prayerTimes,
            favorites = favorites,
            notifications = notifications,
            rail = rail,
            reminders = reminders,
            navigation =
                HomeNavigation(
                    onOpenLibrary = onOpenLibrary,
                    onOpenRemembrance = onOpenRemembrance,
                    onOpenCollection = onOpenCollection,
                ),
        )

    HomeScreenEffects(
        inputs = inputs,
        uiLocale = uiLocale,
        gateway = gateway,
        onHadithLoaded = { hadith = it },
    )

    val dateText = remember(uiLocale) { formatGreetingDate(LocalDate.now(), uiLocale) }

    HomeContent(
        inputs = inputs,
        dateText = dateText,
        hadith = hadith,
        modifier = modifier,
    )
}

/** The three `LaunchedEffect`s that used to live in `HomeComponent.initialize()`. */
@Composable
private fun HomeScreenEffects(
    inputs: HomeInputs,
    uiLocale: Locale,
    gateway: HomeGateway,
    onHadithLoaded: (HadithState) -> Unit,
) {
    val prayerTimes = inputs.prayerTimes
    val notifications = inputs.notifications
    LaunchedEffect(notifications) {
        notifications.observe()
    }

    // The hadith card's two fallback texts, read here so [resolveHadith] can stay a plain function
    // and be called from the suspending block below.
    val hadithArabicFallback = stringResource(Res.string.hadithExample)
    val hadithEnglishFallback = stringResource(Res.string.explanationExample)
    // One background read of favourites and one of a single random remembrance, both through the
    // injected gateway. `LaunchedEffect` is already a coroutine scope and the domain ports are
    // `suspend`, so neither read needs an explicit dispatcher here.
    LaunchedEffect(uiLocale) {
        // The favourites placeholder texts are static per locale but come from the resource store
        // now, so they are resolved here; the carousel's own handlers stay synchronous by reading
        // them back out of `FavoritesState` as plain fields.
        inputs.favorites.loadPlaceholders()

        // Both reads are allowed to come back empty — an unavailable dashboard is a valid state,
        // not a crash — so the failure is swallowed rather than rethrown. That is exactly why each
        // one is logged here: a favourites strip that silently renders nothing and a hadith card
        // that silently never appears are otherwise indistinguishable from a genuine empty result.
        val loaded =
            runCatching {
                gateway.findFavorites()
            }.onFailure { failure -> logger.error(failure) { "Unable to load the favorites" } }
                .getOrElse { emptyList() }
        inputs.favorites.loadFavorites(loaded)

        val single =
            runCatching {
                gateway.findDailyRemembrance()
            }.onFailure { failure -> logger.error(failure) { "Unable to load the daily hadith" } }
                .getOrNull()
        if (single != null) {
            onHadithLoaded(resolveHadith(single, uiLocale, hadithArabicFallback, hadithEnglishFallback))
        }

        // A third read, same shape as the two above: allowed to come back empty — most people have
        // not pinned or started a collection the first time they open the app — and logged when it
        // does not, because a rail that silently never appears is otherwise indistinguishable from a
        // genuine empty result.
        val railCards =
            runCatching {
                gateway.findHomeCollections()
            }.onFailure { failure -> logger.error(failure) { "Unable to load the collection rail" } }
                .getOrElse { emptyList() }
        inputs.rail.replace(railCards)
    }

    // Replaces the JavaFX Timeline that ticked PrayerTimesController every second.
    LaunchedEffect(prayerTimes) {
        prayerTimes.start()
        while (prayerTimes.running) {
            delay(COUNTDOWN_INTERVAL_MILLIS)
            prayerTimes.tick()
        }
    }

    // Replaces NotificationSettingsController.initialize(...). The settings store emits the stored
    // switches and keeps emitting after every write, so keying off `flags` covers both the initial
    // load and any later change — there is no second path that has to apply them.
    LaunchedEffect(notifications.flags) {
        prayerTimes.applyAllFlags(notifications.flags)
    }

    DisposableEffect(prayerTimes) {
        onDispose { prayerTimes.stop() }
    }
}

/** The `.main` ScrollPane + `.main-content` VBox wrapper. */
@Composable
internal fun HomeContent(
    inputs: HomeInputs,
    dateText: String,
    hadith: HadithState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AzkarSpacing.screenPaddingHorizontal,
                        end = AzkarSpacing.screenPaddingHorizontal,
                        top = AzkarSpacing.screenPaddingTop,
                        bottom = AzkarSpacing.screenPaddingBottom,
                    ).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
        ) {
            HomeHero(dateText = dateText, modifier = Modifier.fillMaxWidth())
            HomeDashboard(inputs = inputs, hadith = hadith)
        }
    }
}

@Preview
@Composable
private fun HomeContentPreview() {
    AzkarThemePreview {
        HomeContent(
            inputs =
                HomeInputs(
                    prayerTimes = PrayerTimesState(ENGLISH),
                    favorites = FavoritesState(ENGLISH),
                    notifications = NotificationSettingsState(rememberPreviewSettingsStore(), rememberCoroutineScope()),
                    rail = remember { HomeRailState().apply { replace(previewRailCards()) } },
                    reminders = previewReminders(),
                    navigation = HomeNavigation(onOpenLibrary = {}, onOpenRemembrance = {}, onOpenCollection = {}),
                ),
            dateText = "Friday, 20 February",
            hadith = previewHadithState(),
        )
    }
}

/**
 * The `.dashboard-content` grid: today's azkar and the prayer table, with the favourite in the rail.
 *
 * The dashboard is two columns again, but only because the favourite moved into the rail. The rail's
 * earlier occupants — a look-ahead at the next two prayers and a promotional card for the very
 * remembrance the heading already shows — were removed precisely because each repeated what the
 * prayer table or the azkar card next to them already said.
 *
 * The rail drops below the stack under [AzkarWindowSize.isCompactWidth]: at that width a fixed
 * [RAIL_WIDTH] would leave the prayer table too narrow to keep its times on one line.
 */
@Composable
private fun HomeDashboard(
    inputs: HomeInputs,
    hadith: HadithState,
) {
    if (AzkarWindowSize.isCompactWidth) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AzkarSpacing.dashboard),
        ) {
            HomeMainColumn(inputs, hadith, modifier = Modifier.fillMaxWidth())
            HomeRailColumn(inputs, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.dashboard),
        verticalAlignment = Alignment.Top,
    ) {
        HomeMainColumn(inputs, hadith, modifier = Modifier.weight(1f))
        HomeRailColumn(inputs, modifier = Modifier.width(RAIL_WIDTH))
    }
}

/**
 * The rail column: the collections on offer, then the favourites carousel.
 *
 * Grouped into one column rather than two slots in the row so the compact-width branch stacks the
 * same two pieces in the same order — the collections rail is above the carousel on a phone and
 * beside the prayer table on a desktop, and it is the same rail in both.
 */
@Composable
private fun HomeRailColumn(
    inputs: HomeInputs,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.dashboard),
    ) {
        HomeCollectionRail(
            cards = inputs.rail.cards,
            reminderIds = inputs.reminders.enabledCollectionIds,
            onOpen = inputs.navigation.onOpenCollection,
        )
        if (inputs.favorites.state.total > 0) {
            HomeFavoritesCard(inputs, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Today's azkar over the prayer table — everything the rail does not answer. */
@Composable
private fun HomeMainColumn(
    inputs: HomeInputs,
    hadith: HadithState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.dashboard),
    ) {
        TodaysAzkarCard(
            arabic = hadith.arabic,
            english = hadith.english,
            source = hadith.source,
            onClick = { hadith.remembranceId?.let(inputs.navigation.onOpenRemembrance) },
        )
        val snapshot = inputs.prayerTimes.snapshot
        PrayerTimesCard(
            rows = snapshot.rows,
            countdown = snapshot.countdown,
            locationLabel = inputs.prayerTimes.location.city,
            notificationsEnabled = inputs.notifications.globalEnabled,
            onGlobalToggle = inputs.notifications::onGlobalToggleChanged,
            onNotificationToggled = inputs.notifications::onPrayerToggled,
        )
    }
}

@Composable
private fun HomeFavoritesCard(
    inputs: HomeInputs,
    modifier: Modifier = Modifier,
) {
    FavoritesCard(
        state = inputs.favorites.state,
        onPrevious = inputs.favorites::showPrevious,
        onNext = inputs.favorites::showNext,
        onBrowse = inputs.navigation.onOpenLibrary,
        modifier = modifier,
    )
}

@Composable
private fun placeholderHadith() =
    HadithState(
        remembranceId = null,
        arabic = stringResource(Res.string.hadithExample),
        english = stringResource(Res.string.explanationExample),
        source = stringResource(Res.string.sourceExample),
    )

/** Mirrors `DailyHadithCardComponent.applyDailyHadith`. */
private fun resolveHadith(
    remembrance: Remembrance,
    uiLocale: Locale,
    arabicFallback: String,
    englishFallback: String,
): HadithState {
    val texts =
        RemembranceI18n.resolveTexts(
            remembrance,
            uiLocale,
            arabicFallback,
            englishFallback,
        )
    return HadithState(
        remembranceId = remembrance.id,
        arabic = texts.translation,
        english = texts.explanation,
        source = remembrance.source,
    )
}

/** Mirrors the two static formatters in `GreetingComponent`. */
private fun formatGreetingDate(
    date: LocalDate,
    locale: Locale,
): String {
    val hijri =
        runCatching {
            val formatter =
                DateTimeFormatter
                    .ofPattern("dd MMMM yyyy G", locale)
                    .withChronology(HijrahChronology.INSTANCE)
                    .withResolverStyle(ResolverStyle.STRICT)
            formatter.format(HijrahDate.from(date))
        }.getOrNull().orEmpty()
    val gregorian = DateTimeFormatter.ofPattern("dd MMM yyyy", locale).format(date)
    return listOf(hijri, gregorian).filter { it.isNotBlank() }.joinToString(" | ")
}
