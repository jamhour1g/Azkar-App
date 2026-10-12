package com.azkar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.azkar.data.config.DatabaseManager
import com.azkar.data.config.dataModule
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.title
import com.azkar.i18n.LocaleStore
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.shell.AzkarApp
import com.azkar.ui.shell.ThemePreferenceStore
import com.azkar.ui.shell.rememberAzkarAppState
import com.azkar.ui.theme.DEFAULT_WINDOW_HEIGHT
import com.azkar.ui.theme.DEFAULT_WINDOW_WIDTH
import com.azkar.ui.theme.windowSizeFor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform
import java.awt.Toolkit
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * Compose Multiplatform replacement for `com.azkar.Launcher` + `com.azkar.Home`.
 *
 * The JavaFX application warmed the database ([DatabaseManager.warmUp], which creates the schema
 * and opens the connection pool) off the UI thread before showing the stage and closed it again in
 * `stop()`. The Compose entry point keeps that contract — [DatabaseWarmUp] runs the warm-up in a
 * coroutine scope it owns, so nothing outlives the window; the stage built from `main_screen.fxml`
 * is now a single [Window] hosting [AzkarApp].
 */
suspend fun main() {
    logger.info { "Starting Azkar" }

    // Before anything else can ask for a dependency. Everything below resolves out of the container
    // — the settings store included — and Koin 4 resolves lazily, so nothing here opens the database
    // until a screen actually reads from it.
    startKoin {
        modules(dataModule, appModule)
    }

    val koin = KoinPlatform.getKoin()

    /**
     * Must precede everything that reads a resource: this resolves the stored language and installs
     * it as the JVM default, which is what Compose resources resolve against. The resolved locale
     * is not kept here either: [com.azkar.ui.shell.AzkarAppState] starts from `Locale.getDefault()`,
     * so the value this installs is already where the tree reads it from.
     */
    koin.get<LocaleStore>().readAndActivate()

    val warmUp = DatabaseWarmUp().apply { start() }

    /**
     * Resolved before the window opens: the palette is applied by [com.azkar.ui.theme.AzkarTheme] while the tree is
     * built, so a stored dark preference has to be in hand before the first frame or the app opens
     * light and repaints. The same blocking trade as the locale read above.
     */
    val startupTheme = koin.get<ThemePreferenceStore>().read()

    /**
     * Must precede the first composition, and the window icon is what makes that non-negotiable.
     *
     * Compose 1.13 stopped letting [androidx.compose.ui.graphics.Path] talk to Skiko directly and
     * routed every platform primitive — `Path`, `Canvas`, `Paint`, text measurement — through
     * `PlatformGraphicsRegistry`, which the Skiko implementation registers into from
     * `ComposeContainer`'s constructor. That constructor runs when a window is realised, but
     * `icon = rememberVectorPainter(AzkarIcons.Brand)` is an argument to `Window(...)`: Kotlin
     * evaluates arguments before it calls, and `rememberVectorPainter` builds its `PathComponent`s
     * on the spot. The window icon was therefore the first thing in the process to ask for a
     * platform graphics implementation and it lost the race with its own window, dying on launch
     * with `IllegalStateException: No Compose UI graphics implementation is registered`.
     *
     * Doing it here is the same work `ComposeContainer` was a moment away from doing anyway, and
     * the call is idempotent, so the container's own registration on construction is now a
     * no-op re-registration rather than the load-bearing one. [InternalComposeUiApi] is opted into
     * because this is Compose's own entry point with no public equivalent — the public alternative
     * is a window icon built without vectors, which would mean rasterising the brand mark just to
     * work around a startup-ordering hazard.
     */
    @OptIn(InternalComposeUiApi::class)
    registerSkikoComposeImplementation()

    application {
        // The window is sized from the screen it opens on rather than from a fixed number, so it
        // fills a laptop properly and does not open off the edge of a small display. `density` is
        // read from the same composition the window state is measured in, which is the only way to
        // convert AWT's pixel screen size into the dp space `rememberWindowState` expects.
        val screen = screenSizeInDp(LocalDensity.current)
        val windowState =
            rememberWindowState(
                size =
                    screen?.let { windowSizeFor(it.width, it.height) }
                        ?: DpSize(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT),
                position = WindowPosition(Alignment.Center),
            )
        // The shell state is built here rather than inside `Window` because the window title is a
        // `Window` parameter and the language is the one thing `applyLocale` changes at runtime. A
        // title resolved inside the window's own composition would have been built from the launch
        // locale and never revisited, so the title bar would have stayed English while the rest of
        // the app switched.
        val state = rememberAzkarAppState(initialThemePreference = startupTheme)
        // The window manager keeps the title bar. Drawing our own meant undecorating the window,
        // which cost the edge snapping and the desktop's own button layout — trading a cosmetic
        // mismatch for real behaviour. System decorations are worth the grey strip.
        Window(
            onCloseRequest = {
                warmUp.close()
                // Nothing here needs releasing — Koin 4 does not close what it creates, and the only
                // closeable the app owns is the pool, which `DatabaseWarmUp.stop()` has just shut.
                // Stopping the container is still right: it drops the definitions so a second launch
                // in the same process starts from a clean graph rather than the stale one.
                stopKoin()
                logger.info { "Azkar is shutting down" }
                exitApplication()
            },
            state = windowState,
            title = windowTitle(state.locale),
            // The taskbar entry, the alt-tab row and the title bar all read this, so it is drawn
            // from the same vector the header lockup uses rather than from a rasterised copy: one
            // mark, and it stays right at whatever size the platform asks for. The packaged
            // installers cannot do this — jpackage takes an image file, not draw commands — so
            // `assets/icons` holds the same mark rasterised for the three desktop formats.
            icon = rememberVectorPainter(AzkarIcons.Brand),
        ) {
            AzkarApp(state = state)
        }
    }
}

/**
 * The window title, in the language the app is currently in.
 *
 * Read in the composition rather than resolved once at launch, which is what the original
 * `runBlocking { getString(Res.string.title) }` did and why the title never changed language: the
 * JavaFX original set `stage.setTitle` once in `start()` and had no live switch to miss, but
 * [com.azkar.ui.shell.AzkarAppState.applyLocale] retranslates in place, so the title had to follow.
 *
 * The `key` is the same device `AzkarApp` uses for the tree, and for the same reason. Compose
 * Multiplatform resources resolve against `androidx.compose.ui.text.intl.Locale.current`, which is
 * snapshot-backed and is not notified by the `Locale.setDefault` that [activateLocale] performs, so
 * a plain read would hand back the language the process started in. Keying discards the cached
 * resource and re-reads it, and dropping the cache is the only part that is actually needed here —
 * the title is a leaf, so unlike the tree it has no subtree to throw away.
 */
@Composable
private fun windowTitle(locale: Locale): String = key(locale) { stringResource(Res.string.title) }

/**
 * The primary screen's size in the same dp space the window state is measured in, or `null` when
 * there is no screen to ask.
 *
 * `Toolkit` throws [java.awt.HeadlessException] on a machine with no display rather than answering,
 * and the primary screen is the right one to ask about: it is the screen the window is centred on,
 * and a window is never positioned across displays before the user moves it.
 */
private fun screenSizeInDp(density: Density): DpSize? =
    runCatching {
        val size = Toolkit.getDefaultToolkit().screenSize
        DpSize(
            width = (size.width / density.density).dp,
            height = (size.height / density.density).dp,
        )
    }.getOrNull()
