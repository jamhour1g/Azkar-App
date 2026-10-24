package com.azkar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.azkar.data.config.dataModule
import com.azkar.data.storage.CoverStorage
import com.azkar.i18n.LocaleStore
import com.azkar.i18n.activateLocale
import com.azkar.ui.home.HomeCollectionRail
import com.azkar.ui.home.HomeScreen
import com.azkar.ui.library.CollectionCoverCache
import com.azkar.ui.library.CollectionDetailScreen
import com.azkar.ui.library.CollectionReminderState
import com.azkar.ui.library.CollectionSessionScreen
import com.azkar.ui.library.CollectionsCatalogue
import com.azkar.ui.library.CollectionsScreen
import com.azkar.ui.library.RemembranceReaderScreen
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.data.HomeCollections
import com.azkar.ui.library.model.CollectionMemberView
import com.azkar.ui.library.rememberCollectionDetailState
import com.azkar.ui.library.rememberCollectionSessionState
import com.azkar.ui.library.rememberRemembranceReaderState
import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationContent
import com.azkar.ui.notifications.presentation.NotificationCard
import com.azkar.ui.notifications.presentation.PresentedNotice
import com.azkar.ui.preview.previewRailCards
import com.azkar.ui.preview.previewReminders
import com.azkar.ui.shell.AzkarApp
import com.azkar.ui.shell.SettingsGroup
import com.azkar.ui.shell.SettingsScreen
import com.azkar.ui.shell.ThemePreference
import com.azkar.ui.shell.rememberAzkarAppState
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.LocalWindowSizeClass
import com.azkar.ui.theme.rememberSystemTheme
import com.azkar.ui.theme.windowSizeClassFor
import org.jetbrains.skia.EncodedImageFormat
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/**
 * Off-screen screenshot renderer for UI verification on headless machines.
 *
 * Skia rasterises through Skiko without a windowing system, so this works over SSH / in CI. Run via
 * `./gradlew :app:runScreenshot -Pazkar.screenshot.screen=library`.
 *
 * `azkar.screenshot.locale` and `azkar.screenshot.dark` override the appearance axes that would
 * otherwise need interaction to reach, so RTL mirroring and the dark palette are verifiable in the
 * same headless pass.
 *
 * The screens read their content from the H2 database through a background coroutine, so the scene
 * is rendered until it goes idle — the file therefore shows loaded content, not the pre-load
 * placeholders. A static image is still only as good as its moment: it verifies layout, typography,
 * colours and RTL, and never verifies behaviour.
 *
 * This is also the one `println` in the repository. The line it prints is a `JavaExec` tool's
 * contract — it is how a caller of `./gradlew :app:runScreenshot` learns what was produced — and
 * this source set is excluded from the jar, so it is not a log line in the sense the logging rules
 * are about.
 */
@Suppress("ForbiddenMethodCall")
suspend fun main() {
    val out = File(System.getProperty("azkar.screenshot.out") ?: "build/screenshot.png")
    val width = System.getProperty("azkar.screenshot.width")?.toInt() ?: 1440
    val height = System.getProperty("azkar.screenshot.height")?.toInt() ?: 900
    val target = System.getProperty("azkar.screenshot.screen") ?: SCREEN_APP
    out.parentFile?.mkdirs()

    // The same container the real window gets, so a screenshot exercises the real graph rather than
    // a parallel one built for the harness: `AzkarApp` below resolves its gateways and its shell
    // state out of it, and those reach the same seeded database `Main.kt` does.
    startKoin {
        modules(dataModule, appModule)
    }

    KoinPlatform.getKoin().get<LocaleStore>().readAndActivate()
    // Deliberately not `persist`: rendering in another language must not rewrite the real
    // setting, so this only moves the JVM default the resources read.
    System
        .getProperty("azkar.screenshot.locale")
        ?.takeIf { it.isNotBlank() }
        ?.let { tag -> Locale.forLanguageTag(tag).activateLocale() }

    val scene =
        ImageComposeScene(width = width, height = height, density = Density(1f)) {
            ScreenshotContent(target = target, width = width, height = height)
        }

    // Both screens fill themselves from the H2 database on a background coroutine. A single render
    // catches the frame before any of that lands, so the image would only ever prove that the
    // placeholders line up — which is exactly the half of the layout that is not in question.
    //
    // `ImageComposeScene.render` drives the frame clock from the `nanoTime` it is handed, and its
    // default is `0` — so a naive render loop freezes every animation on its first frame. Passing
    // a real clock is what lets the crossfades finish, and draining the pending work is what lets
    // the background loads land. Without both, a screenshot shows the frame the screen started on.
    onEventDispatchThread { renderUntilIdle(scene) }

    val image = onEventDispatchThread { scene.render(frameNanos()) }
    val data = image.encodeToData(EncodedImageFormat.PNG, 100) ?: error("PNG encoding failed")
    out.writeBytes(data.bytes)
    println("wrote ${out.absolutePath} (${image.width}x${image.height}, ${data.bytes.size} bytes)")
}

/**
 * Runs [block] on the AWT event dispatch thread and rethrows whatever it threw, unwrapped.
 *
 * `NavDisplay` gives every entry in the back stack a `Lifecycle`, and `LifecycleRegistry` refuses to
 * move one off the main thread — which in a Compose Multiplatform app is the event dispatch thread,
 * not the JVM's `main`. A real window is created and composed there, so this only concerns the
 * harness; but the harness has to agree with it or the `app` screen cannot be rendered at all.
 */
private fun <T> onEventDispatchThread(block: () -> T): T {
    val result = AtomicReference<T?>()
    try {
        SwingUtilities.invokeAndWait { result.set(block()) }
    } catch (wrapped: InvocationTargetException) {
        // Rethrowing the wrapper would bury a composition failure under a reflection frame and read
        // as though the screenshot harness itself had thrown.
        throw wrapped.targetException
    }
    // A holder rather than a captured `var`, so the value crosses the thread boundary through
    // something that publishes it, and `null` is reported as a harness failure instead of being cast
    // to whatever `T` happens to be — which for a nullable type would be no failure at all.
    return checkNotNull(result.get()) { "The event dispatch thread returned without a result" }
}

/**
 * Renders frames on a real clock until the scene has nothing left to do, or the budget runs out.
 *
 * The screens resolve their data on a background coroutine and then crossfade into it, so "settled"
 * means both no pending recomposition and no running animation — neither of which a fixed sleep can
 * promise, since the frame clock only moves when a frame is rendered.
 */
private fun renderUntilIdle(scene: ImageComposeScene) {
    val deadline = System.nanoTime() + RENDER_BUDGET_NANOS
    var idleFrames = 0
    while (System.nanoTime() < deadline) {
        scene.render(frameNanos())
        if (!scene.hasPendingWork()) {
            if (++idleFrames >= IDLE_FRAMES_REQUIRED) return
        } else {
            idleFrames = 0
        }
        Thread.sleep(FRAME_INTERVAL_MILLIS)
    }
}

private fun frameNanos(): Long = System.nanoTime()

private fun ImageComposeScene.hasPendingWork(): Boolean = hasInvalidations() || Snapshot.current.hasPendingChanges()

private const val FRAME_INTERVAL_MILLIS = 40L
private const val RENDER_BUDGET_NANOS = 20_000_000_000L

/**
 * Consecutive idle frames that count as settled.
 *
 * One is not enough: a background load can be between writing to the database and publishing it, so
 * the scene looks idle for a frame before the coroutine that republishes it resumes.
 */
private const val IDLE_FRAMES_REQUIRED = 3

private const val SCREEN_APP = "app"
private const val SCREEN_HOME = "home"
private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_SETTINGS_PRAYER = "settings-prayer"
private const val SCREEN_COLLECTIONS = "collections"
private const val SCREEN_LIBRARY = "library"
private const val SCREEN_COLLECTION_DETAIL = "collection-detail"

/** `-Pazkar.screenshot.memberView=list` renders the collection's members as a list rather than a grid. */
private const val SCREENSHOT_MEMBER_VIEW = "azkar.screenshot.memberView"
private const val SCREEN_COLLECTION_SESSION = "collection-session"
private const val SCREEN_REMEMBRANCE = "remembrance"
private const val SCREEN_HOME_RAIL = "home-rail"
private const val SCREEN_NOTIFICATIONS = "notifications"

@Composable
private fun ScreenshotContent(
    target: String,
    width: Int,
    height: Int,
) {
    val override = System.getProperty("azkar.screenshot.dark")?.toBooleanStrictOrNull()
    if (target.substringAfterLast('.') == SCREEN_APP) {
        AzkarApp(rememberAzkarAppState(override.asThemePreference()))
        return
    }

    // The single-screen targets are normally rendered inside AzkarApp, so the shell has to be
    // re-supplied here — otherwise `dark` would silently do nothing for them.
    // Keyed on the locale for the same reason `AzkarApp` does: resources resolve against
    // `Locale.current`, which a `Locale.setDefault` alone does not invalidate.
    //
    // The window size class is re-supplied too: `AzkarApp` publishes it from its own
    // `BoxWithConstraints`, and without it a single-screen render would always measure against the
    // default window, so every responsive branch below the compact breakpoint would be unreachable
    // from a screenshot no matter how wide the image was.
    val locale = Locale.getDefault()
    // The one shared holder the screens that show reminders read. Built over a fixture rather than
    // over the database because a schedule cannot be in the local one — nothing sets a reminder until
    // a user does — so a screenshot of the real screen would only ever prove an absent row.
    val reminders = remember { previewReminders(SCREENSHOT_COLLECTION_ID) }
    // The catalogue the shell owns, reproduced here for the same reason as the reminders above: the
    // screens no longer build one of their own, so a screenshot that skipped this would render an
    // empty shelf. Built over the real gateway rather than a fixture because the library *is* the
    // seeded data — there is no way to show a populated shelf without reading it.
    val scope = rememberCoroutineScope()
    val gateway = koinInject<CollectionsGateway>()
    // The real covers directory rather than a throwaway one, because the gateway above is real too:
    // a cover chosen in the database has to resolve to the file it names, or the screenshot would
    // show the fallback wash for a collection that demonstrably has a picture.
    val coverStorage = koinInject<CoverStorage>()
    val covers = remember(coverStorage) { CollectionCoverCache(coverStorage) }
    val catalogue = remember(scope, locale, covers) { CollectionsCatalogue(scope, locale, gateway, covers) }
    AzkarTheme(
        darkTheme = override ?: rememberSystemTheme().isDark,
        uiLocale = locale,
    ) {
        key(locale) {
            CompositionLocalProvider(
                LocalWindowSizeClass provides windowSizeClassFor(width.dp, height.dp),
            ) {
                // As in the shell: the read is asked for once here rather than by each screen, and
                // the screens' own calls are no-ops because the catalogue has already been asked.
                LaunchedEffect(catalogue) { catalogue.loadLabels() }
                LaunchedEffect(catalogue) { catalogue.loadData() }
                ScreenshotScreen(
                    screen = target.substringAfterLast('.'),
                    catalogue = catalogue,
                    reminders = reminders,
                    themePreference = override.asThemePreference(),
                )
            }
        }
    }
}

/**
 * Which screen this run is a picture of.
 *
 * A dispatch of its own so that each shot below is a function whose name says what it proves, and so
 * that adding a target is adding a case rather than another twenty lines inside the theme wrapper.
 */
@Composable
private fun ScreenshotScreen(
    screen: String,
    catalogue: CollectionsCatalogue,
    reminders: CollectionReminderState,
    themePreference: ThemePreference,
) {
    when (screen) {
        SCREEN_HOME ->
            HomeScreen(
                onOpenLibrary = {},
                onOpenRemembrance = {},
                onOpenCollection = {},
                reminders = reminders,
            )

        // Rendered with the shell's own state holder rather than a bare one, because the
        // screen holds no fields of its own — that is the point of it, and a screenshot
        // built the other way would not prove it.
        SCREEN_SETTINGS -> SettingsScreen(rememberAzkarAppState(themePreference), onClose = {})
        SCREEN_SETTINGS_PRAYER ->
            SettingsScreen(rememberAzkarAppState(themePreference), onClose = {}, initialGroup = SettingsGroup.PRAYER)

        SCREEN_COLLECTION_DETAIL -> CollectionDetailShot(catalogue, reminders)

        SCREEN_COLLECTION_SESSION -> CollectionSessionShot(catalogue, reminders)

        SCREEN_REMEMBRANCE -> RemembranceShot(catalogue)

        SCREEN_HOME_RAIL ->
            Column(
                modifier = Modifier.width(380.dp).padding(AzkarSpacing.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(AzkarSpacing.dashboard),
            ) {
                HomeCollectionRail(
                    cards = HomeCollections.pick(previewRailCards(), RAIL_SHOT_LIMIT),
                    reminderIds = setOf(SCREENSHOT_COLLECTION_ID + 1L),
                    onOpen = {},
                )
            }

        // `library` is what the property has been called in every run of this harness, so
        // it stays as an alias for the screen that replaced it rather than becoming an
        // unknown name that renders the same thing by accident.
        SCREEN_COLLECTIONS, SCREEN_LIBRARY ->
            CollectionsScreen(
                onOpenCollection = {},
                onOpenRemembrance = {},
                onManageTags = {},
                showToast = { _, _, _ -> },
                catalogue = catalogue,
            )

        // The overlay is the only surface whose whole job is to be looked at, so it is the one that
        // most needs a picture: a card that lost the theme would still pass every assertion that
        // only asks whether text was drawn, and would look like an unstyled rectangle in practice.
        SCREEN_NOTIFICATIONS -> NotificationStackShot()

        else -> error("Unknown screenshot target \"$screen\"")
    }
}

/**
 * Three cards at once, which is the stack cap and the layout that has to hold.
 *
 * The first of them is a real passage card rather than a bare notice, because the bare notice is the
 * one body the overlay almost never shows: the scheduler publishes a passage or a prayer time, and
 * the passage is the body that carries the star. A screenshot of three bare notices would show
 * exactly the cards that cannot have the new control on them.
 */
@Composable
private fun NotificationStackShot() {
    Column(
        modifier = Modifier.width(380.dp).padding(AzkarSpacing.screenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.dashboard),
    ) {
        NotificationCard(
            item =
                PresentedNotice(
                    id = 0L,
                    title = "Azkar reminder",
                    message = "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
                    action = NotificationAction.Remembrance(0L),
                    content =
                        NotificationContent.Remembrance(
                            text = "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
                            position = 1,
                            total = 3,
                            isFavorite = true,
                        ),
                ),
            onOpen = {},
            onDismiss = {},
            onPauseChanged = {},
            onToggleFavorite = { _, _ -> true },
        )
        listOf(
            "After Fajr" to "Say it once, slowly, and mean it.",
            "Asr is due in 20 minutes" to "Reminder queued from your morning collection.",
        ).forEachIndexed { index, (title, message) ->
            NotificationCard(
                item =
                    PresentedNotice(
                        id = (index + 1).toLong(),
                        title = title,
                        message = message,
                        action = NotificationAction.Remembrance((index + 1).toLong()),
                    ),
                onOpen = {},
                onDismiss = {},
                onPauseChanged = {},
            )
        }
    }
}

@Composable
private fun CollectionDetailShot(
    catalogue: CollectionsCatalogue,
    reminders: CollectionReminderState,
) {
    val detail = rememberCollectionDetailState(catalogue, SCREENSHOT_COLLECTION_ID, reminders)
    // The grid's two axes are the one part of this page a screenshot of it as it first appears cannot
    // reach, so the list is named here rather than left unshot. Applied in an effect rather than in
    // the body because these are state writes, and a state write during composition is a write that
    // happens again on every recomposition for as long as the effect is absent.
    LaunchedEffect(detail) {
        when (System.getProperty(SCREENSHOT_MEMBER_VIEW)) {
            "list" -> detail.setMemberView(CollectionMemberView.LIST)
            "grid" -> Unit
            else -> Unit
        }
    }
    CollectionDetailScreen(
        detail = detail,
        onBack = {},
        onStartSession = {},
        onOpenRemembrance = {},
        showToast = { _, _, _ -> },
    )
}

@Composable
private fun CollectionSessionShot(
    catalogue: CollectionsCatalogue,
    reminders: CollectionReminderState,
) {
    CollectionSessionScreen(
        session = rememberCollectionSessionState(catalogue, SCREENSHOT_COLLECTION_ID, reminders),
        onBack = {},
        onOpenRemembrance = {},
        showToast = { _, _, _ -> },
    )
}

/**
 * The reader on its own. It is the only reading surface in the app that a screenshot can reach,
 * because it is a page rather than a dialog: the harness is not a friend of `main`, so everything
 * `internal` is invisible to it, and every dialog and popup in this app is `internal`. A passage with
 * a long meaning and a full citation is the case worth having on record — the panel that used to cap
 * this text at a few words is exactly what this shot is here to disprove.
 */
@Composable
private fun RemembranceShot(catalogue: CollectionsCatalogue) {
    // The first member of the collection every other shot uses, resolved here
    // rather than hardcoded so the shot follows the seed instead of a row id a
    // change to the catalogue would quietly invalidate. A shot of an empty page is
    // worse than no shot, and this cannot quietly become one.
    val firstMemberId =
        catalogue.allCollections
            .firstOrNull { it.id == SCREENSHOT_COLLECTION_ID }
            ?.members
            ?.firstOrNull()
            ?.id
            ?: 1L
    RemembranceReaderScreen(
        detail = rememberRemembranceReaderState(catalogue = catalogue, remembranceId = firstMemberId),
        onBack = {},
        showToast = { _, _, _ -> },
    )
}

/** `null` (the default) keeps [AzkarApp]'s own system-aware resolution in charge. */
private fun Boolean?.asThemePreference(): ThemePreference =
    when (this) {
        true -> ThemePreference.DARK
        false -> ThemePreference.LIGHT
        null -> ThemePreference.SYSTEM
    }

/**
 * The collection the detail screen is rendered on.
 *
 * Row `11` of a fresh seed — the shipped "Praising Allah", twenty-nine remembrances in it. It is an
 * official collection, which is the interesting case since it shows the shipped label and hides the
 * editing controls, and it is also the only row whose size exercises the grid: the members have to be
 * laid out in five columns, sorted, and given a progress bar with something in it, and a two-member
 * row like Favorites proves none of that. A custom collection would exercise the opposite branch, and
 * the harness has no way to create one before the render.
 */
private const val SCREENSHOT_COLLECTION_ID = 11L

/** Matches the rail's own limit, so a screenshot shows what the screen would actually draw. */
private const val RAIL_SHOT_LIMIT = 4
