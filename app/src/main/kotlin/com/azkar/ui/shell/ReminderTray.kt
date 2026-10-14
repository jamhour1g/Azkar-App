package com.azkar.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.title
import com.azkar.generated.resources.trayMenuOpen
import com.azkar.generated.resources.trayMenuPause
import com.azkar.generated.resources.trayMenuQuit
import com.azkar.generated.resources.trayMenuResume
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.notifications.DesktopPlatform
import dev.nucleusframework.composenativetray.tray.api.Tray
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

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
) {
    Tray(
        // The `iconContent` overload rather than the `ImageVector` one, because that one flattens
        // the mark to a single tint to match the menu bar it is drawn in. This one is a gradient
        // khatim with a white mosque inside it, and it has to keep its own colours to stay legible
        // as an icon this small.
        iconContent = {
            Image(
                imageVector = AzkarIcons.Brand,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        },
        tooltip = stringResource(Res.string.title),
        primaryAction = primaryAction,
    ) {
        Item(label = stringResource(Res.string.trayMenuOpen), onClick = onOpenApp)
        // The honest answer to "I am about to play a game that takes the whole screen": the overlay
        // cannot be pinned over an exclusive fullscreen app, so the only thing that can stop the
        // reminders arriving is the user saying so. Two taps from anywhere, which is why it lives in
        // the tray rather than three screens deep in settings.
        Item(
            label =
                stringResource(
                    if (remindersPaused) Res.string.trayMenuResume else Res.string.trayMenuPause,
                ),
            onClick = onToggleReminders,
        )
        Divider()
        Item(label = stringResource(Res.string.trayMenuQuit), onClick = onQuit)
    }
}

/**
 * Whether hiding the window would still leave the user a way back into the app.
 *
 * The tray library answers no such question — it registers the icon and logs when it cannot, with
 * no callback to report success — so the close handler has to decide before composing the icon,
 * and the safe answer is the conservative one: never hide into a state the user cannot leave. A
 * machine with no display has no tray by definition. On Linux the icon is a `StatusNotifierItem`,
 * which needs a session bus to exist at all, so a session without one has no tray host to draw it
 * and the window stays. Everywhere else the OS owns the tray and a desktop session always has one.
 *
 * This is deliberately not `SystemTray.isSupported()`: that is the JDK's own answer, it reports
 * false on Linux for every session, and this app no longer uses the JDK tray for anything.
 */
internal fun trayAvailable(): Boolean {
    val headless = runCatching { GraphicsEnvironment.isHeadless() }.getOrDefault(true)
    val linuxWithoutSessionBus =
        DesktopPlatform.current == DesktopPlatform.LINUX &&
            System.getenv("DBUS_SESSION_BUS_ADDRESS").isNullOrBlank()
    return !headless && !linuxWithoutSessionBus
}
