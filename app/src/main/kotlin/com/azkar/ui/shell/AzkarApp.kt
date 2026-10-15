package com.azkar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.azkar.data.storage.CoverStorage
import com.azkar.ui.home.HomeScreen
import com.azkar.ui.library.CollectionCoverCache
import com.azkar.ui.library.CollectionDetailScreen
import com.azkar.ui.library.CollectionReminderState
import com.azkar.ui.library.CollectionSessionScreen
import com.azkar.ui.library.CollectionsCatalogue
import com.azkar.ui.library.CollectionsScreen
import com.azkar.ui.library.RemembranceReaderScreen
import com.azkar.ui.library.TagsScreen
import com.azkar.ui.library.TagsState
import com.azkar.ui.library.data.CollectionReminderGateway
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.data.TagGateway
import com.azkar.ui.library.rememberCollectionDetailState
import com.azkar.ui.library.rememberCollectionSessionState
import com.azkar.ui.library.rememberRemembranceReaderState
import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationActionRouter
import com.azkar.ui.notifications.ReminderManagerScreen
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.LocalWindowSizeClass
import com.azkar.ui.theme.isRtlLayout
import com.azkar.ui.theme.rememberSystemTheme
import com.azkar.ui.theme.windowSizeClassFor
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Root of the Compose tree, replacing `com.azkar.Home.start(Stage)` and the `BorderPane` from
 * `main_screen.fxml`.
 *
 * `top` is [MainScreenHeader], `center` is the [NavDisplay] over [AzkarNavigator]'s back stack, and
 * the whole subtree is keyed on the locale so a language change recomposes it (including the Amiri
 * typography) at once.
 *
 * [BoxWithConstraints] measures the window once here so both screens observe the same
 * [LocalWindowSizeClass] and lay themselves out accordingly.
 *
 * The `key(state.locale)` is load-bearing. Compose Multiplatform resources resolve against
 * `androidx.compose.ui.text.intl.Locale.current`, which is a snapshot-backed value that a
 * `java.util.Locale.setDefault` call does not notify, so switching language in place would leave
 * stale strings on screen. Keying throws the subtree away and rebuilds it, which re-reads the
 * locale from scratch. `activateLocale` is what actually sets the default locale.
 */
@Composable
fun AzkarApp(
    state: AzkarAppState = rememberAzkarAppState(),
    actionRouter: NotificationActionRouter? = null,
) {
    val darkTheme = rememberAzkarDarkTheme(state.themePreference)
    val toastController = rememberToastController(state)
    val showToast = toastController::show
    // Remembered *outside* the locale key below: a `remember` inside it would be discarded every
    // time the language changed, throwing away where the user was.
    val navigator = rememberAzkarNavigator()
    // Also outside the locale key, and for a second reason: the schedules and which ones are owed
    // belong to the app rather than to a language, so a language switch must not re-arm a reminder
    // that has already been announced, nor drop the badges for the ones that are waiting.
    val reminderScope = rememberCoroutineScope()
    // Both halves of the shelf come out of the container rather than being built here, so the
    // reminder ticker and the catalogue read and write through one gateway instead of two
    // independently constructed ones — and neither of them reaches into the data layer to do it.
    // The same object answers for both seams, which is what makes a reminder added on the settings
    // screen show up on the ticker's badges without either side knowing the other exists.
    val reminderGateway = koinInject<CollectionReminderGateway>()
    val gateway = koinInject<CollectionsGateway>()
    // The tag vocabulary is read and written through a seam of its own rather than through
    // `CollectionsGateway`: the Manage tags screen outlives every collection screen, and a tag is
    // something the library draws rather than something it contains.
    val tagsGateway = koinInject<TagGateway>()
    // Outside the locale key with the gateway beside it: cover pictures are bytes, not words, so a
    // language switch must not make fourteen collections decode the same fourteen files again.
    val coverStorage = koinInject<CoverStorage>()
    val covers = remember(coverStorage) { CollectionCoverCache(coverStorage) }
    val reminders = remember(reminderScope, reminderGateway) { CollectionReminderState(reminderScope, reminderGateway) }
    // Inside the locale key, because it is the locale that decides: the rows behind this holder are
    // language-free, but the strings the shelf renders them as are not, so switching language has to
    // rebuild the presenter even though the read itself could be kept.
    val catalogue = remember(state.locale) { CollectionsCatalogue(reminderScope, state.locale, gateway, covers) }

    RouteNotificationActions(actionRouter, navigator, reminderScope)

    AzkarTheme(darkTheme = darkTheme, uiLocale = state.locale) {
        key(state.locale) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(AzkarTheme.colors.background),
            ) {
                CompositionLocalProvider(
                    LocalWindowSizeClass provides windowSizeClassFor(maxWidth, maxHeight),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            MainScreenHeader(
                                activeTab = navigator.current,
                                callbacks =
                                    HeaderCallbacks(
                                        onHome = navigator::showHome,
                                        onLibrary = navigator::showLibrary,
                                        onNotifications = { navigator.showNotifications() },
                                        onSettings = navigator::showSettings,
                                    ),
                            )
                            HeaderBottomBorder()
                            AzkarContent(
                                navigator = navigator,
                                state = state,
                                showToast = showToast,
                                library = LibraryContent(reminders, catalogue, tagsGateway),
                            )
                        }

                        ToastHost(state)
                    }
                }
            }
        }
    }
}

/**
 * The palette the app is currently drawing in, from the stored preference and the desktop's answer.
 *
 * Its own composable rather than an inline `when` because the notification overlay window is a
 * * *separate* window that draws the same theme, and two copies of this decision would be two places
 * for them to disagree — the failure being a reminder card rendered in the light palette over a dark
 * desktop, or in the wrong language entirely.
 *
 * Read from the desktop rather than from `isSystemInDarkTheme()`, which reports light on every Linux
 * setup — see `SystemTheme` for what Skiko's lookup needs and does not find.
 */
@Composable
fun rememberAzkarDarkTheme(preference: ThemePreference): Boolean {
    val systemTheme = rememberSystemTheme()
    return when (preference) {
        ThemePreference.SYSTEM -> systemTheme.isDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
}

@Composable
private fun RouteNotificationActions(
    actionRouter: NotificationActionRouter?,
    navigator: AzkarNavigator,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    SideEffect {
        actionRouter?.setNavigator { action ->
            scope.launch {
                when (action) {
                    is NotificationAction.Remembrance -> navigator.showRemembrance(action.id)
                    NotificationAction.PrayerTimes, NotificationAction.OpenApp -> navigator.showHome()
                }
            }
        }
    }
}

/**
 * The `center` pane, which is where the screen swap moved to.
 *
 * This is the whole of the old `when (state.tab)`: a destination key instead of an enum case, a
 * list of them instead of one, and a provider that maps each key to the composable behind it.
 * [NavDisplay] takes over the parts that used to be hand-rolled — it renders only the entry on top
 * of the stack, keeps each entry's saved state alive while another is on screen, and wires the back
 * gesture and shortcut to popping the list.
 *
 * The callbacks are bound per entry rather than once outside, because what "go home" means depends
 * on the screen asking: from the library it is the empty state's escape hatch, and from the header
 * it is one of two peers. Both resolve to [AzkarNavigator.showHome], which is the point of routing
 * them through the navigator instead of letting a screen write the stack itself.
 *
 * [state] reaches the settings entry whole and the two library entries not at all. The settings
 * screen's values already live on the shell, since they outlive the window and so cannot live in
 * something remembered per entry; the library screens were given their own holder for the same reason,
 * and that turned out to be the wrong reason — see [LibraryContent.catalogue].
 *
 * @param library the reads the three library destinations share, bundled rather than threaded one by
 * one. The shelf, the two collection pages and the Manage tags page all draw from the same holder
 * and the same reminder state, so the alternative was growing this signature every time a library
 * screen gained a dependency — which is the same reason [CollectionsGatewayDependencies] exists one
 * layer down.
 */
@Composable
private fun ColumnScope.AzkarContent(
    navigator: AzkarNavigator,
    state: AzkarAppState,
    showToast: ShowToast,
    library: LibraryContent,
) {
    val catalogue = library.catalogue
    val reminders = library.reminders
    // Read inside the spec lambdas rather than passed in: they are re-evaluated on every transition,
    // and the shell already rebuilds this whole subtree on a locale change, so a captured `isRtl`
    // would only ever be a stale copy of a value the composition has already moved past.
    val isRtl = isRtlLayout()
    NavDisplay(
        backStack = navigator.entries,
        modifier = Modifier.weight(1f),
        transitionSpec = { pushPageTransition(isRtl) },
        popTransitionSpec = { popPageTransition(isRtl) },
        entryProvider =
            entryProvider {
                entry(AzkarDestination.Home) {
                    HomeScreen(
                        onOpenLibrary = navigator::showLibrary,
                        onOpenCollection = navigator::showCollection,
                        reminders = reminders,
                    )
                }
                entry(AzkarDestination.Library) {
                    CollectionsScreen(
                        onOpenCollection = navigator::showCollection,
                        onOpenRemembrance = navigator::showRemembrance,
                        onManageTags = navigator::showTags,
                        showToast = showToast,
                        catalogue = catalogue,
                    )
                }
                entry<AzkarDestination.Notifications> { destination ->
                    ReminderManagerScreen(
                        initialCollectionId = destination.initialCollectionId,
                        onClose = navigator::back,
                    )
                }
                collectionEntries(navigator, showToast, reminders, catalogue)
                tagsEntry(navigator, showToast, library)
                readerAndSettingsEntries(navigator, showToast, state, catalogue)
            },
    )
}

/**
 * The Manage tags screen, remembered beside the shelf rather than inside its own entry.
 *
 * Held here for the same reason [catalogue] is: `NavDisplay` renders only the entry on top of the
 * stack and disposes the one under it, so a holder remembered per entry would be thrown away and
 * rebuilt on every trip between the shelf and this page — which would re-read the vocabulary each
 * time and lose the half of it the user had scrolled to.
 *
 * The holder is told when the vocabulary changed rather than reloading the shelf itself, because
 * the shelf's label chips are derived from a copy taken when the shelf was last read. See
 * [CollectionsCatalogue.tagsChanged], which is the door that opens for exactly this.
 */
private fun EntryProviderScope<AzkarDestination>.tagsEntry(
    navigator: AzkarNavigator,
    showToast: ShowToast,
    library: LibraryContent,
) {
    entry(AzkarDestination.Tags) {
        val scope = rememberCoroutineScope()
        val catalogue = library.catalogue
        val gateway = library.tagsGateway
        // The locale is read here rather than captured so the whole entry — and the state it holds —
        // is rebuilt under the new language, the same way `key(state.locale)` rebuilds the tree. A
        // state remembered across a language switch would keep sorting by the name the list was
        // ordered in when it was created, which is the one artefact a language switch cannot show.
        val locale = AzkarTheme.locale
        val tags =
            remember(gateway, scope, locale) {
                TagsState(
                    scope = scope,
                    gateway = gateway,
                    onTagsChanged = catalogue::tagsChanged,
                    labelOf = { it.displayName(locale) },
                )
            }
        TagsScreen(
            state = tags,
            onBack = navigator::back,
            showToast = showToast,
            onOpenCollection = navigator::showCollection,
            onOpenRemembrance = navigator::showRemembrance,
        )
    }
}

/**
 * The reads every library destination shares.
 *
 * A bag rather than a six-line parameter list for the same reason [CollectionsGatewayDependencies] is
 * one: the destinations under the library tab are a family, they draw from one shelf and one set of
 * reminders, and every screen added to that family would otherwise mean a new parameter here and a
 * new argument at the one call site that builds it.
 *
 * [catalogue] is the one read of the shelf, remembered beside the navigation rather than inside any
 * entry. [NavDisplay] renders only the entry on top of the stack and disposes the one under it, so a
 * holder remembered per entry was thrown away and rebuilt on every hop: shelf → collection → back →
 * shelf read the whole database three times over. A holder here outlives the stack instead, so the
 * shelf is read once and every write made on any of the library pages lands in the same place.
 */
private data class LibraryContent(
    val reminders: CollectionReminderState,
    val catalogue: CollectionsCatalogue,
    val tagsGateway: TagGateway,
)

/**
 * The two screens a collection leads to: the detail page and the recitation session.
 *
 * Their own builder so the stack reads as the six destinations it holds rather than as one list in
 * which the two deepest ones happen to carry three lines of navigation each.
 */
private fun EntryProviderScope<AzkarDestination>.collectionEntries(
    navigator: AzkarNavigator,
    showToast: ShowToast,
    reminders: CollectionReminderState,
    catalogue: CollectionsCatalogue,
) {
    entry<AzkarDestination.CollectionDetail> { destination ->
        // Opening the collection is what the badge was asking for, so the reminder is
        // marked dealt with here rather than by the page: a user who clicks through and
        // does not read it has still answered the question the badge asked.
        reminders.acknowledge(destination.id)
        val detail = rememberCollectionDetailState(catalogue, destination.id, reminders)
        CollectionDetailScreen(
            detail = detail,
            onBack = navigator::back,
            onStartSession = navigator::showSession,
            onOpenRemembrance = navigator::showRemembrance,
            showToast = showToast,
            onManageReminders = navigator::showNotifications,
        )
    }
    entry<AzkarDestination.CollectionSession> { destination ->
        val session = rememberCollectionSessionState(catalogue, destination.id, reminders)
        CollectionSessionScreen(
            session = session,
            onBack = navigator::back,
            onOpenRemembrance = navigator::showRemembrance,
            showToast = showToast,
            onManageReminders = navigator::showNotifications,
        )
    }
}

/**
 * The passage on its own page, and the settings.
 *
 * The two destinations that are not part of walking the library: one is a leaf pushed on top of
 * whichever screen opened it, the other is not a child of anything.
 */
private fun EntryProviderScope<AzkarDestination>.readerAndSettingsEntries(
    navigator: AzkarNavigator,
    showToast: ShowToast,
    state: AzkarAppState,
    catalogue: CollectionsCatalogue,
) {
    entry<AzkarDestination.RemembranceReader> { destination ->
        // Pushed like every other child of the library, so the screen underneath keeps its
        // own state: the collection's scroll position when the reader was opened from a
        // tile, and the session's progress when it was opened mid-recitation.
        val reader =
            rememberRemembranceReaderState(
                catalogue = catalogue,
                remembranceId = destination.id,
            )
        RemembranceReaderScreen(
            detail = reader,
            onBack = navigator::back,
            showToast = showToast,
        )
    }
    entry(AzkarDestination.Settings) {
        SettingsScreen(
            state = state,
            onClose = navigator::back,
        )
    }
}
