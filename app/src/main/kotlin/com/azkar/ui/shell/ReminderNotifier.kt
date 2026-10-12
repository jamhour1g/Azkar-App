package com.azkar.ui.shell

import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.image.BufferedImage

/**
 * One reminder coming round, as the notifier is handed it.
 *
 * The collection id travels with the text because the notification is actionable: clicking it has to
 * open the collection that came due, and a title and a line of prose cannot say which of four
 * collections they belong to.
 */
data class ReminderNotice(
    val title: String,
    val message: String,
    val collectionId: Long,
)

/**
 * How a due reminder reaches the user when the app is not the window in front.
 *
 * Behind an interface for the same reason every other data seam in this app is: the tick fires from
 * a coroutine, and a test has to be able to assert that it fired without a system tray.
 *
 * [show] is expected to be safe to call from any thread and is expected to do nothing at all rather
 * than throw when notifications cannot be shown. A reminder that cannot be announced is still worth
 * recording as fired — the in-app toast is the other half of the delivery — so a notifier that
 * failed loudly would turn a missing desktop service into a crash on a background thread.
 */
interface ReminderNotifier {
    /** Whether this machine can show anything at all. False means [show] is a no-op. */
    val isSupported: Boolean

    /** Announces [notice], or does nothing if it cannot. */
    fun show(notice: ReminderNotice)

    /**
     * Releases anything the notifier is holding outside this process's own objects.
     *
     * A no-op for most, but a tray icon is visible for as long as it exists and outlives the window
     * that registered it, so whatever creates one has to be able to take it back down.
     */
    fun dispose() = Unit
}

/**
 * A notifier that announces nothing.
 *
 * The default for the screenshot harness and for anything composed outside a real desktop session,
 * so that no code path has to ask whether a notifier exists before using one.
 */
object SilentReminderNotifier : ReminderNotifier {
    override val isSupported: Boolean = false

    override fun show(notice: ReminderNotice) = Unit
}

/**
 * Desktop notifications through the JDK's own system tray.
 *
 * `java.awt.SystemTray` is the only notification route available without taking a dependency, and it
 * is the one the user asked for. It is unevenly supported: on Linux it needs a notification daemon
 * running, on macOS it needs the app to be bundled rather than run from a jar (jpackage does bundle
 * it), and on a machine with no display there is no tray at all. Rather than treat those as
 * failures, [tray] returns `null` and every call becomes a no-op — the in-app toast still fires, so
 * a reminder is never silently swallowed, and nothing about the schedule depends on it working.
 *
 * The icon is drawn here rather than loaded from the app's resources: AWT wants a `BufferedImage`
 * and Skia's resource loader produces something else. A flat tile with an open book on it is enough
 * to recognise in a crowded tray, and a tray icon nobody can identify is no worse than a wrong one.
 *
 * @param onOpenCollection called with the collection's id when the notification is clicked. The
 * tray icon itself carries a click listener as well, so the tray is a way back into the app even
 * for a notice that has already gone.
 */
class SystemTrayReminderNotifier(
    private val onOpenCollection: (Long) -> Unit,
) : ReminderNotifier {
    private var icon: java.awt.TrayIcon? = null
    private var unavailable = false

    /**
     * The collection the tray icon should open, or `null` when it should open nothing.
     *
     * Held in a field rather than captured in a listener because a listener per notification would
     * stack up: after ten reminders a single click would open ten collections, and the first one to
     * answer would be the oldest.
     */
    private var openRequest: Long? = null

    override val isSupported: Boolean
        get() = !unavailable && tray() != null

    /**
     * Registers the tray icon the first time it is needed.
     *
     * Lazy because a tray icon is visible the whole time it exists, and the app has no reason to
     * show one until a reminder actually comes round — or indeed to show one at all if nothing ever
     * does. Once a failure has been seen, [unavailable] stops the retry: a tray that was not there
     * a moment ago is not going to arrive inside a thirty-second tick.
     */
    private fun ensureIcon(): java.awt.TrayIcon? =
        if (unavailable) {
            null
        } else {
            icon ?: registerIcon()
        }

    /** One attempt at registering, with a failure remembered rather than retried. */
    private fun registerIcon(): java.awt.TrayIcon? {
        val tray = tray()
        if (tray == null) {
            unavailable = true
            return null
        }
        return runCatching {
            java.awt
                .TrayIcon(brandIcon())
                .apply {
                    isImageAutoSize = true
                    addActionListener { openRequest?.let(onOpenCollection) }
                    tray.add(this)
                }.also { icon = it }
        }.getOrElse {
            unavailable = true
            null
        }
    }

    override fun show(notice: ReminderNotice) {
        openRequest = notice.collectionId
        val target = ensureIcon() ?: return
        runCatching {
            target.displayMessage(
                notice.title,
                notice.message,
                java.awt.TrayIcon.MessageType.INFO,
            )
        }
    }

    /**
     * Takes the tray icon back out of the tray.
     *
     * Called when the window closes. A tray icon outlives its window by default, so without this a
     * user who quit the app would be left with an icon that opens nothing.
     */
    override fun dispose() {
        openRequest = null
        val shown = icon ?: return
        icon = null
        runCatching { SystemTray.getSystemTray().remove(shown) }
    }

    private companion object {
        /**
         * The tray, or `null` when this machine has none.
         *
         * `isHeadless` is checked first because `SystemTray` reaches for a display before it looks
         * at anything else, and asking a headless JVM for one throws rather than answering.
         */
        fun tray(): SystemTray? =
            if (GraphicsEnvironment.isHeadless()) {
                null
            } else {
                runCatching { if (SystemTray.isSupported()) SystemTray.getSystemTray() else null }.getOrNull()
            }
    }
}

/**
 * The tray icon, drawn rather than loaded.
 *
 * A brand-coloured tile with an open book, which is the same mark the app's brand tile uses. The
 * light theme's primary is spelled out rather than read from the Compose theme because AWT cannot
 * see it, and a tray icon that changed with the app's theme would be a second thing to keep in step
 * for no gain in a 32-pixel square.
 */
private fun brandIcon(): BufferedImage {
    val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
    val paint = image.createGraphics()
    try {
        paint.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        paint.color = Color(0x3B82F6)
        paint.fillRoundRect(0, 0, 32, 32, 10, 10)
        paint.color = Color.WHITE
        paint.fillPolygon(intArrayOf(15, 15, 15, 3, 3), intArrayOf(9, 22, 15, 12, 22), 4)
        paint.fillPolygon(intArrayOf(17, 17, 17, 29, 29), intArrayOf(9, 22, 15, 12, 22), 4)
    } finally {
        paint.dispose()
    }
    return image
}
