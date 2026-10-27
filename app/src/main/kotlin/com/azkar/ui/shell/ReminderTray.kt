package com.azkar.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.nextPrayerLabel
import com.azkar.generated.resources.title
import com.azkar.generated.resources.trayMenuOpen
import com.azkar.generated.resources.trayMenuPause
import com.azkar.generated.resources.trayMenuQuit
import com.azkar.generated.resources.trayMenuResume
import com.azkar.i18n.ENGLISH
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.notifications.DesktopPlatform
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarTheme
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.tray.api.NativeTray
import dev.nucleusframework.composenativetray.utils.IconRenderProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment
import com.azkar.ui.notifications.trayAvailable as isTrayAvailable

private val logger = KotlinLogging.logger {}

internal data class TrayIconSize(
    val width: Int,
    val height: Int,
)

internal fun trayIconSize(platform: DesktopPlatform): TrayIconSize =
    when (platform) {
        DesktopPlatform.LINUX -> TrayIconSize(22, 22)
        DesktopPlatform.WINDOWS -> TrayIconSize(16, 16)
        DesktopPlatform.MACOS -> TrayIconSize(22, 22)
        DesktopPlatform.UNKNOWN -> TrayIconSize(32, 32)
    }

private fun trayIconRenderProperties(platform: DesktopPlatform): IconRenderProperties {
    val iconSize = trayIconSize(platform)
    return IconRenderProperties(
        sceneWidth = TRAY_ICON_RENDER_SIZE,
        sceneHeight = TRAY_ICON_RENDER_SIZE,
        sceneDensity = Density(TRAY_ICON_RENDER_DENSITY),
        targetWidth = iconSize.width,
        targetHeight = iconSize.height,
        jvmOwnsDownscaling = false,
    )
}

private data class TrayMenuActions(
    val openApp: () -> Unit,
    val toggleReminders: () -> Unit,
    val quit: () -> Unit,
)

/**
 * The tray icon and its native context menu.
 *
 * Drawn from Compose rather than assembled on an AWT tray: the icon is the app's own brand mark, so
 * it is the same vector the window and the header use, and the menu is a DSL that recomposes with
 * the state it reads. That matters for a tray that lives longer than the window — the menu labels
 * follow the app's language, and nothing here has to be rebuilt by hand when they change. AWT could
 * show neither: it renders the Windows 95-era menu on every platform, and its labels are whatever
 * string the code passed in at construction time.
 *
 * The OS supplies the mechanics: `NSStatusItem` on macOS, `Shell_NotifyIcon` on Windows, and a
 * `StatusNotifierItem` over D-Bus on Linux. Left-click runs [primaryAction], which routes whatever
 * the last notification pointed at; right-click opens the menu. Without a primary action the menu
 * would open on every click, which is not what a click on the icon should do.
 *
 * Nucleus 2.1.7 rebuilds a supplied Linux menu on every tray update. The countdown still checks
 * the wall clock each second, but native tray updates happen only when the displayed minute or
 * menu state changes.
 *
 * The menu carries the same line as a disabled row at the top: reading is its only job, so nothing
 * is clickable that has nothing to do.
 *
 * This is a composable, so it exists exactly as long as the application composition does. That is
 * what makes close-to-tray safe: the icon is still registered while the window is hidden, and the
 * library removes it when the composition goes away, without a teardown step having to get it right.
 */
@Composable
fun ReminderTray(
    primaryAction: () -> Unit,
    onOpenApp: () -> Unit,
    remindersPaused: Boolean,
    onToggleReminders: () -> Unit,
    onQuit: () -> Unit,
    countdown: PrayerTrayCountdownState? = null,
) {
    val tooltip = countdown?.tooltipText?.takeIf { it.isNotEmpty() } ?: stringResource(Res.string.title)
    val menuPrayerName =
        countdown?.nextPrayerName?.takeIf { it.isNotEmpty() }
            ?: stringResource(Res.string.nextPrayerLabel)
    val menuPrayerLine = countdown?.tooltipText?.takeIf { it.isNotEmpty() } ?: menuPrayerName
    PrayerTrayCountdownTicker(countdown)
    PrayerTrayNativeHost(
        primaryAction = primaryAction,
        tooltip = tooltip,
        menuPrayerLine = menuPrayerLine,
        remindersPaused = remindersPaused,
        menuActions = TrayMenuActions(onOpenApp, onToggleReminders, onQuit),
    )
}

@Composable
private fun PrayerTrayCountdownTicker(countdown: PrayerTrayCountdownState?) {
    LaunchedEffect(countdown) {
        val state = countdown ?: return@LaunchedEffect
        val startFailure = runCatching { state.start() }.exceptionOrNull()
        if (startFailure is CancellationException) throw startFailure
        if (startFailure != null) {
            logger.error(startFailure) { "Unable to start prayer tray countdown ticker" }
            return@LaunchedEffect
        }
        logger.info { "Prayer tray countdown ticker is running" }
        var previousTickFailed = false
        while (true) {
            delay(COUNTDOWN_TICK_MS)
            val tickFailure = runCatching { state.tick() }.exceptionOrNull()
            if (tickFailure is CancellationException) throw tickFailure
            if (tickFailure != null && !previousTickFailed) {
                logger.error(tickFailure) { "Prayer tray countdown update failed; retrying" }
            } else if (tickFailure == null && previousTickFailed) {
                logger.info { "Prayer tray countdown recovered" }
            }
            previousTickFailed = tickFailure != null
        }
    }
    DisposableEffect(countdown) {
        onDispose {
            if (countdown != null) logger.debug { "Prayer tray countdown ticker stopped" }
        }
    }
}

@Composable
private fun PrayerTrayNativeHost(
    primaryAction: () -> Unit,
    tooltip: String,
    menuPrayerLine: String,
    remindersPaused: Boolean,
    menuActions: TrayMenuActions,
) {
    val openLabel = stringResource(Res.string.trayMenuOpen)
    val pauseLabel =
        stringResource(if (remindersPaused) Res.string.trayMenuResume else Res.string.trayMenuPause)
    val quitLabel = stringResource(Res.string.trayMenuQuit)
    val menuIconRenderProperties = trayMenuIconRenderProperties()
    val tray = remember { NativeTray() }
    val currentMenuActions by rememberUpdatedState(menuActions)
    val menuKey = "$menuPrayerLine|$openLabel|$pauseLabel|$quitLabel"
    val menu =
        PrayerTrayMenuContent(
            countdown = TrayMenuEntry(menuPrayerLine, AzkarIcons.Clock, {}, isEnabled = false),
            openApp = TrayMenuEntry(openLabel, AzkarIcons.Home, { currentMenuActions.openApp() }),
            reminders =
                TrayMenuEntry(
                    pauseLabel,
                    if (remindersPaused) AzkarIcons.Bell else AzkarIcons.BellOff,
                    { currentMenuActions.toggleReminders() },
                ),
            quit = TrayMenuEntry(quitLabel, ActionIcons.dismiss, { currentMenuActions.quit() }),
            iconRenderProperties = menuIconRenderProperties,
        )
    val currentMenu by rememberUpdatedState<TrayMenuBuilder.() -> Unit>({
        addPrayerTrayMenu(menu)
    })
    PrayerTrayNativeRegistration(tray, primaryAction, tooltip, menuKey, currentMenu)
}

private data class TrayMenuEntry(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isEnabled: Boolean = true,
)

private data class PrayerTrayMenuContent(
    val countdown: TrayMenuEntry,
    val openApp: TrayMenuEntry,
    val reminders: TrayMenuEntry,
    val quit: TrayMenuEntry,
    val iconRenderProperties: IconRenderProperties,
)

private fun TrayMenuBuilder.addPrayerTrayMenu(menu: PrayerTrayMenuContent) {
    addPrayerTrayMenuEntry(menu.countdown, menu.iconRenderProperties)
    Divider()
    addPrayerTrayMenuEntry(menu.openApp, menu.iconRenderProperties)
    addPrayerTrayMenuEntry(menu.reminders, menu.iconRenderProperties)
    Divider()
    addPrayerTrayMenuEntry(menu.quit, menu.iconRenderProperties)
}

private fun TrayMenuBuilder.addPrayerTrayMenuEntry(
    entry: TrayMenuEntry,
    iconRenderProperties: IconRenderProperties,
) {
    Item(
        entry.label,
        entry.icon,
        null,
        iconRenderProperties,
        isEnabled = entry.isEnabled,
        onClick = entry.onClick,
    )
}

@Composable
private fun PrayerTrayNativeRegistration(
    tray: NativeTray,
    primaryAction: () -> Unit,
    tooltip: String,
    menuKey: String,
    menuContent: TrayMenuBuilder.() -> Unit,
) {
    val currentPrimaryAction by rememberUpdatedState(primaryAction)
    val currentMenu by rememberUpdatedState(menuContent)
    var hasSubmittedTray by remember(tray) { mutableStateOf(false) }
    // Keep the colored vector instead of the monochrome ImageVector tray overload.
    val iconContent: @Composable () -> Unit = {
        Image(
            imageVector = AzkarIcons.Brand,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
    val iconRenderProperties = trayIconRenderProperties(DesktopPlatform.current)
    LaunchedEffect(tray, tooltip, menuKey) {
        if (hasSubmittedTray) {
            logger.debug { "Updating prayer tray menu" }
        } else {
            logger.info { "Registering prayer tray icon" }
        }
        val updateFailure =
            runCatching {
                tray.updateComposable(
                    iconContent = iconContent,
                    iconRenderProperties = iconRenderProperties,
                    tooltip = tooltip,
                    primaryAction = { currentPrimaryAction() },
                    menuContent = currentMenu,
                )
            }.exceptionOrNull()
        if (updateFailure != null) {
            logger.error(updateFailure) { "Unable to submit prayer tray update" }
        } else {
            hasSubmittedTray = true
        }
    }
    DisposableEffect(tray) {
        onDispose {
            logger.debug { "Disposing prayer tray icon" }
            runCatching { tray.dispose() }
                .onFailure { failure -> logger.warn(failure) { "Unable to dispose prayer tray icon" } }
        }
    }
}

@AzkarPreview
@Composable
internal fun ReminderTrayActionIconsPreview(
    @PreviewParameter(ReminderTrayThemeProvider::class) darkTheme: Boolean,
) {
    AzkarTheme(darkTheme = darkTheme, uiLocale = ENGLISH) {
        ReminderTrayActionIconsContent()
    }
}

@Composable
private fun ReminderTrayActionIconsContent() {
    Surface(shadowElevation = 4.dp) {
        Column(modifier = Modifier.width(280.dp).padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AzkarIcons.Clock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Asr in 5m",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            listOf(
                AzkarIcons.Home to stringResource(Res.string.trayMenuOpen),
                AzkarIcons.Bell to stringResource(Res.string.trayMenuResume),
                AzkarIcons.BellOff to stringResource(Res.string.trayMenuPause),
                ActionIcons.dismiss to stringResource(Res.string.trayMenuQuit),
            ).forEachIndexed { index, (icon, label) ->
                if (index == 3) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

internal class ReminderTrayThemeProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(false, true)
}

/** One tooltip tick per second, read off the wall clock the moment it lands. */
private const val COUNTDOWN_TICK_MS = 1_000L
private const val TRAY_ICON_RENDER_SIZE = 192
private const val TRAY_ICON_RENDER_DENSITY = 2f

private fun trayMenuIconRenderProperties() =
    IconRenderProperties(
        sceneWidth = TRAY_MENU_ICON_SIZE,
        sceneHeight = TRAY_MENU_ICON_SIZE,
        sceneDensity = Density(1f),
        targetWidth = TRAY_MENU_ICON_SIZE,
        targetHeight = TRAY_MENU_ICON_SIZE,
        jvmOwnsDownscaling = false,
    )

private const val TRAY_MENU_ICON_SIZE = 16

/**
 * Whether hiding the window would still leave the user a way back into the app.
 *
 * The tray library answers no such question — it registers the icon and logs when it cannot, with
 * no callback to report success — so the close handler has to decide before composing the icon,
 * and the safe answer is the conservative one: never hide into a state the user cannot leave. A
 * machine with no display has no tray by definition. On Linux the icon is a `StatusNotifierItem`,
 * which needs a session bus to exist at all, so a session without one has no tray host to draw it
 * and the window stays. An unrecognized OS is conservative too; only the three supported desktop
 * platforms are assumed to provide a native tray.
 *
 * This is deliberately not `SystemTray.isSupported()`: that is the JDK's own answer, it reports
 * false on Linux for every session, and this app no longer uses the JDK tray for anything.
 */
internal fun trayAvailable(): Boolean {
    val headless = runCatching { GraphicsEnvironment.isHeadless() }.getOrDefault(true)
    return isTrayAvailable(headless, DesktopPlatform.current, System.getenv("DBUS_SESSION_BUS_ADDRESS"))
}
