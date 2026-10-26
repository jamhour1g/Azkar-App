package com.azkar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.azkar.data.config.dataModule
import com.azkar.data.storage.CoverStorage
import com.azkar.i18n.LocaleStore
import com.azkar.i18n.activateLocale
import com.azkar.ui.home.HomeCollectionRail
import com.azkar.ui.home.HomeGateway
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
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.data.HomeCollections
import com.azkar.ui.library.data.TagGateway
import com.azkar.ui.library.model.CollectionMemberView
import com.azkar.ui.library.rememberCollectionDetailState
import com.azkar.ui.library.rememberCollectionSessionState
import com.azkar.ui.library.rememberRemembranceReaderState
import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationContent
import com.azkar.ui.notifications.presentation.NotificationCard
import com.azkar.ui.notifications.presentation.OverlayCardSizing
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
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
    val width = System.getProperty("azkar.screenshot.width")?.toInt() ?: 1440
    val height = System.getProperty("azkar.screenshot.height")?.toInt() ?: 900
    require(width > 0 && height > 0) { "Screenshot width and height must be positive: ${width}x$height" }

    if (System.getProperty("azkar.screenshot.list") == "true") {
        println(SCREENSHOT_TARGETS.joinToString("\n") { (name, description) -> "$name\t$description" })
        return
    }

    val config = screenshotConfig(width, height)
    prepareScreenshotEnvironment(config)

    config.targets.forEach { target ->
        config.themes.forEach { theme -> renderScreenshot(config, target, theme) }
    }
}

private data class ScreenshotConfig(
    val out: Path,
    val width: Int,
    val height: Int,
    val home: Path,
    val targets: List<ScreenshotTarget>,
    val themes: List<ScreenshotTheme>,
    val outputDirectory: Path?,
)

private data class ScreenshotSelection(
    val targets: List<ScreenshotTarget>,
    val themes: List<ScreenshotTheme>,
)

private fun screenshotConfig(
    width: Int,
    height: Int,
): ScreenshotConfig {
    val out = Path.of(System.getProperty("azkar.screenshot.out") ?: "build/screenshot.png")
    val home = Path.of(System.getProperty("azkar.screenshot.home") ?: "build/screenshot-home")
    val selection = screenshotSelection()
    val batch =
        System.getProperty("azkar.screenshot.targets") != null || System.getProperty("azkar.screenshot.themes") != null
    val outputDirectory =
        System.getProperty("azkar.screenshot.outDir")?.let(Path::of)
            ?: if (batch) out.parent ?: Path.of("build") else null

    outputDirectory?.let(Files::createDirectories)
    if (outputDirectory == null) out.parent?.let(Files::createDirectories)

    return ScreenshotConfig(out, width, height, home, selection.targets, selection.themes, outputDirectory)
}

private fun screenshotSelection(): ScreenshotSelection {
    val requestedTargets =
        System
            .getProperty("azkar.screenshot.targets")
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: listOf(System.getProperty("azkar.screenshot.screen") ?: SCREEN_APP)
    require(requestedTargets.isNotEmpty()) { "Specify at least one screenshot target" }

    val targets = requestedTargets.map(::resolveTarget)
    require(targets.map(ScreenshotTarget::outputName).distinct().size == targets.size) {
        "Screenshot targets resolve to duplicate output names"
    }

    val legacyDark =
        System.getProperty("azkar.screenshot.dark")?.let { raw ->
            raw.toBooleanStrictOrNull() ?: error("Invalid azkar.screenshot.dark value '$raw'; expected true or false")
        }
    val requestedThemes =
        System
            .getProperty("azkar.screenshot.themes")
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: listOf(legacyDark?.let { if (it) THEME_DARK else THEME_LIGHT } ?: THEME_SYSTEM)
    require(requestedThemes.isNotEmpty()) { "Specify at least one screenshot theme" }

    val themes = requestedThemes.map(::resolveTheme)
    require(themes.map(ScreenshotTheme::name).distinct().size == themes.size) {
        "Specify each screenshot theme at most once"
    }

    return ScreenshotSelection(targets, themes)
}

private suspend fun prepareScreenshotEnvironment(config: ScreenshotConfig) {
    if (System.getProperty("azkar.screenshot.home") == null) deleteTree(config.home)
    Files.createDirectories(config.home)
    System.setProperty("user.home", config.home.toAbsolutePath().toString())
    System.setProperty("user.name", System.getProperty("azkar.screenshot.userName") ?: "Azkar")

    // Use the same dependency graph as the real window so screenshots exercise actual app state.
    startKoin {
        modules(dataModule, appModule)
    }

    if (config.targets.any { it.name == SCREEN_APP || it.name == SCREEN_HOME }) {
        val homeGateway = KoinPlatform.getKoin().get<HomeGateway>()
        homeGateway.findHomeCollections()
        homeGateway.findFavorites()
        homeGateway.findDailyRemembrance()
    }

    KoinPlatform.getKoin().get<LocaleStore>().readAndActivate()

    // Deliberately do not persist this locale; screenshot rendering must not rewrite app settings.
    System
        .getProperty("azkar.screenshot.locale")
        ?.takeIf { it.isNotBlank() }
        ?.let { tag -> Locale.forLanguageTag(tag).activateLocale() }
}

private fun renderScreenshot(
    config: ScreenshotConfig,
    target: ScreenshotTarget,
    theme: ScreenshotTheme,
) {
    val density = if (target.name == SCREEN_REMINDER_CARD) 3f else 1f
    val scene =
        ImageComposeScene(
            width = config.width,
            height = config.height,
            density = Density(density),
        ) {
            ScreenshotContent(target = target.name, width = config.width, height = config.height, dark = theme.dark)
        }

    try {
        // Wait for H2-backed content and Compose animations before saving the frame.
        onEventDispatchThread { renderUntilIdle(scene, target.name, theme.name) }
        saveScreenshot(config, target, theme, scene)
    } finally {
        onEventDispatchThread { scene.close() }
    }
}

@Suppress("ForbiddenMethodCall")
private fun saveScreenshot(
    config: ScreenshotConfig,
    target: ScreenshotTarget,
    theme: ScreenshotTheme,
    scene: ImageComposeScene,
) {
    val image = onEventDispatchThread { scene.render(frameNanos()) }
    val data = image.encodeToData(EncodedImageFormat.PNG, 100) ?: error("PNG encoding failed")
    val destination = config.outputDirectory?.resolve("${target.outputName}-${theme.name}.png") ?: config.out
    val temporary = destination.resolveSibling(".${destination.fileName}.tmp")

    try {
        Files.write(temporary, data.bytes)
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary)
    }

    println("wrote ${destination.toAbsolutePath()} (${image.width}x${image.height}, ${data.bytes.size} bytes)")
}

private data class ScreenshotTarget(
    val name: String,
    val outputName: String,
    val description: String,
)

private data class ScreenshotTheme(
    val name: String,
    val dark: Boolean?,
)

private fun deleteTree(root: Path) {
    if (!Files.exists(root)) return
    Files.walk(root).use { paths ->
        paths.sorted(Comparator<Path> { first, second -> second.compareTo(first) }).forEach(Files::deleteIfExists)
    }
}

private val SCREENSHOT_TARGETS =
    listOf(
        "app" to "Full app shell",
        "home" to "Home screen",
        "settings" to "Settings screen",
        "settings-prayer" to "Prayer settings screen",
        "settings-reminders" to "Reminder settings screen",
        "collections" to "Collections library screen",
        "library" to "Collections library screen (legacy alias)",
        "collection-detail" to "Collection detail screen",
        "collection-session" to "Recitation session screen",
        "remembrance" to "Remembrance reader screen",
        "tags" to "Tag management screen",
        "home-rail" to "Component: home collection rail",
        "notifications" to "Component: notification stack",
        "reminder-card" to "Component: passage reminder at presentation scale",
    )

private val SCREENSHOT_TARGET_ALIASES =
    mapOf(
        "component.home-rail" to "home-rail",
        "component.notification-stack" to "notifications",
    )

private val SCREENSHOT_OUTPUT_NAMES =
    mapOf(
        "app" to "screen-home",
        "home" to "screen-home-content",
        "collections" to "screen-library",
        "remembrance" to "screen-reader",
        "collection-session" to "screen-session",
        "settings-reminders" to "screen-schedule",
        "settings-prayer" to "screen-prayer",
        "collection-detail" to "screen-collection",
        "reminder-card" to "screen-reminder",
    )

private fun resolveTarget(requested: String): ScreenshotTarget {
    val name = SCREENSHOT_TARGET_ALIASES[requested] ?: requested
    val description =
        SCREENSHOT_TARGETS.firstOrNull { it.first == name }?.second
            ?: error(
                "Unknown screenshot target '$requested'. Set azkar.screenshot.list=true to list available targets.",
            )
    return ScreenshotTarget(name, SCREENSHOT_OUTPUT_NAMES[name] ?: name, description)
}

private fun resolveTheme(requested: String): ScreenshotTheme =
    when (requested) {
        THEME_LIGHT -> ScreenshotTheme(THEME_LIGHT, false)
        THEME_DARK -> ScreenshotTheme(THEME_DARK, true)
        THEME_SYSTEM -> ScreenshotTheme(THEME_SYSTEM, null)
        else -> error("Unknown screenshot theme '$requested'; expected light, dark, or system")
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
private fun renderUntilIdle(
    scene: ImageComposeScene,
    target: String,
    theme: String,
) {
    val started = System.nanoTime()
    val deadline = started + RENDER_BUDGET_NANOS
    var idleFrames = 0
    while (System.nanoTime() < deadline) {
        scene.render(frameNanos())
        if (!scene.hasPendingWork()) {
            if (++idleFrames >= IDLE_FRAMES_REQUIRED && System.nanoTime() - started >= MINIMUM_RENDER_NANOS) return
        } else {
            idleFrames = 0
        }
        Thread.sleep(FRAME_INTERVAL_MILLIS)
    }
    val timeoutSeconds = RENDER_BUDGET_NANOS / 1_000_000_000
    error(
        "Screenshot '$target' ($theme) did not settle within $timeoutSeconds seconds; " +
            "last idle run was $idleFrames frames",
    )
}

private fun frameNanos(): Long = System.nanoTime()

private fun ImageComposeScene.hasPendingWork(): Boolean = hasInvalidations() || Snapshot.current.hasPendingChanges()

private const val FRAME_INTERVAL_MILLIS = 40L
private const val MINIMUM_RENDER_NANOS = 1_000_000_000L
private const val RENDER_BUDGET_NANOS = 20_000_000_000L

/**
 * Consecutive idle frames that count as settled.
 *
 * One is not enough: a background load can be between writing to the database and publishing it, so
 * the scene looks idle for a frame before the coroutine that republishes it resumes.
 */
private const val IDLE_FRAMES_REQUIRED = 3

private const val SCREEN_APP = "app"
private const val THEME_LIGHT = "light"
private const val THEME_DARK = "dark"
private const val THEME_SYSTEM = "system"
private const val SCREEN_HOME = "home"
private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_SETTINGS_PRAYER = "settings-prayer"
private const val SCREEN_SETTINGS_REMINDERS = "settings-reminders"
private const val SCREEN_COLLECTIONS = "collections"
private const val SCREEN_LIBRARY = "library"
private const val SCREEN_COLLECTION_DETAIL = "collection-detail"
private const val SCREEN_TAGS = "tags"

/** `-Pazkar.screenshot.memberView=list` renders the collection's members as a list rather than a grid. */
private const val SCREENSHOT_MEMBER_VIEW = "azkar.screenshot.memberView"
private const val SCREEN_COLLECTION_SESSION = "collection-session"
private const val SCREEN_REMEMBRANCE = "remembrance"
private const val SCREEN_HOME_RAIL = "home-rail"
private const val SCREEN_NOTIFICATIONS = "notifications"
private const val SCREEN_REMINDER_CARD = "reminder-card"

@Composable
private fun ScreenshotContent(
    target: String,
    width: Int,
    height: Int,
    dark: Boolean?,
) {
    if (target == SCREEN_APP) {
        AzkarApp(rememberAzkarAppState(dark.asThemePreference()))
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
    val reminders = remember { previewReminders(SCREENSHOT_REMINDER_FIXTURE_ID) }
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
        darkTheme = dark ?: rememberSystemTheme().isDark,
        uiLocale = locale,
    ) {
        key(locale) {
            CompositionLocalProvider(
                LocalWindowSizeClass provides windowSizeClassFor(width.dp, height.dp),
            ) {
                Box(Modifier.fillMaxSize().background(AzkarTheme.colors.background)) {
                    // As in the shell: the read is asked for once here rather than by each screen, and
                    // the screens' own calls are no-ops because the catalogue has already been asked.
                    LaunchedEffect(catalogue) { catalogue.loadLabels() }
                    LaunchedEffect(catalogue) { catalogue.loadData() }
                    ScreenshotScreen(
                        screen = target,
                        catalogue = catalogue,
                        reminders = reminders,
                        themePreference = dark.asThemePreference(),
                    )
                }
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
        SCREEN_SETTINGS_REMINDERS ->
            SettingsScreen(rememberAzkarAppState(themePreference), onClose = {}, initialGroup = SettingsGroup.REMINDERS)

        SCREEN_COLLECTION_DETAIL -> CollectionDetailShot(catalogue)

        SCREEN_COLLECTION_SESSION -> CollectionSessionShot(catalogue)

        SCREEN_REMEMBRANCE -> RemembranceShot(catalogue)

        SCREEN_TAGS -> TagsShot(catalogue)

        SCREEN_HOME_RAIL ->
            Column(
                modifier = Modifier.width(380.dp).padding(AzkarSpacing.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(AzkarSpacing.dashboard),
            ) {
                HomeCollectionRail(
                    cards = HomeCollections.pick(previewRailCards(), RAIL_SHOT_LIMIT),
                    reminderIds = setOf(SCREENSHOT_REMINDER_FIXTURE_ID + 1L),
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

        SCREEN_REMINDER_CARD ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.width(420.dp).padding(16.dp)) { PassageReminderShot() }
            }

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
        PassageReminderShot()
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
private fun PassageReminderShot() {
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
        sizing = OverlayCardSizing.FIT,
        onOpen = {},
        onDismiss = {},
        onPauseChanged = {},
        onToggleFavorite = { _, _ -> true },
    )
}

@Composable
private fun CollectionDetailShot(catalogue: CollectionsCatalogue) {
    val collectionId = featuredCollectionId(catalogue) ?: return
    val collectionReminders = remember(collectionId) { previewReminders(collectionId) }
    val detail = rememberCollectionDetailState(catalogue, collectionId, collectionReminders)
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
private fun CollectionSessionShot(catalogue: CollectionsCatalogue) {
    val collectionId = featuredCollectionId(catalogue) ?: return
    val collectionReminders = remember(collectionId) { previewReminders(collectionId) }
    CollectionSessionScreen(
        session = rememberCollectionSessionState(catalogue, collectionId, collectionReminders),
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
        featuredCollectionId(catalogue)
            ?.let { id ->
                catalogue.allCollections
                    .firstOrNull { it.id == id }
                    ?.members
                    ?.firstOrNull()
                    ?.id
            }
            ?: return
    RemembranceReaderScreen(
        detail = rememberRemembranceReaderState(catalogue = catalogue, remembranceId = firstMemberId),
        onBack = {},
        showToast = { _, _, _ -> },
    )
}

@Composable
private fun TagsShot(catalogue: CollectionsCatalogue) {
    val scope = rememberCoroutineScope()
    val gateway = koinInject<TagGateway>()
    val tags =
        remember(gateway, scope, catalogue) {
            TagsState(scope = scope, gateway = gateway, onTagsChanged = catalogue::tagsChanged)
        }
    LaunchedEffect(tags) { tags.load() }
    TagsScreen(
        state = tags,
        onBack = {},
        showToast = { _, _, _ -> },
        onOpenCollection = {},
        onOpenRemembrance = {},
    )
}

private fun featuredCollectionId(catalogue: CollectionsCatalogue): Long? =
    (
        catalogue.allCollections.firstOrNull { it.name == "Praising Allah" }
            ?: catalogue.allCollections.firstOrNull { it.members.isNotEmpty() }
    )?.id

/** `null` (the default) keeps [AzkarApp]'s own system-aware resolution in charge. */
private fun Boolean?.asThemePreference(): ThemePreference =
    when (this) {
        true -> ThemePreference.DARK
        false -> ThemePreference.LIGHT
        null -> ThemePreference.SYSTEM
    }

/** Synthetic reminder identifiers for the preview-only Home rail and notification state. */
private const val SCREENSHOT_REMINDER_FIXTURE_ID = 11L

/** Matches the rail's own limit, so a screenshot shows what the screen would actually draw. */
private const val RAIL_SHOT_LIMIT = 4
