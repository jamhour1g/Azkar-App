package com.azkar.ui.library

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import java.io.IOException
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Asks the platform for a picture and hands back a path to it.
 *
 * **The dialog the user gets is the platform's own**, because FileKit's JVM implementation reaches
 * the real one on all three desktop platforms rather than a Java widget styled to look like it:
 *
 *  - **Linux** goes to the XDG Desktop Portal over the session bus, which is what GNOME, KDE and
 *    every other freedesktop-compliant desktop implements its file chooser behind. There is no JDK
 *    binding to the bus at all, which is why the previous version of this file ran `zenity` and
 *    dispatched on `os.name` by hand: `zenity` is what the portal's backends call, and calling it
 *    directly was the shortest honest route to the same window. It is now one dependency deeper --
 *    D-Bus and GSSAPI instead of a process spawn -- and one thing fewer to get wrong, because the
 *    portal answers with real MIME types and honours the user's own bookmarks and recent places.
 *  - **Windows** is `IFileOpenDialog` through JNA, the same dialog `File > Open` opens.
 *  - **macOS** is `NSOpenPanel` through JNA.
 *
 * FileKit falls back to AWT where a platform has no native chooser to reach. That is the same
 * fallback the previous version had, arrived at from a library rather than from a platform test.
 *
 * The two functions are separable on purpose. [choose] is the only part that can be cancelled and
 * the only part worth substituting in a test; [ingest] is the part with the interesting failure
 * modes, so it takes a plain [Path] and can be exercised without a screen.
 */
internal object CoverImageChooser {
    /**
     * Opens the platform's file dialog, returning the chosen file or `null` if the user cancelled.
     *
     * [title] arrives already translated. The dialog is a separate window with its own title bar
     * that the app never composes, so a `StringResource` cannot be read inside this function --
     * it takes a plain string and the caller resolves it against the current locale.
     */
    suspend fun choose(title: String): PlatformFile? =
        runCatching {
            FileKit.openFilePicker(
                type = FileKitType.Image,
                dialogSettings = FileKitDialogSettings(title = title),
            )
        }.onFailure { failure ->
            // A portal that refuses to answer, a bus that is not there, a headless run. All of them
            // are ordinary on a machine with no desktop session, and none of them may become an
            // exception out of a button press: the caller's answer to `null` is "the user declined",
            // which leaves the dialog open and usable, and an exception would take the screen down.
            logger.warn(failure) { "The platform file dialog could not be opened" }
        }.getOrNull()

    /**
     * Copies a chosen picture into the app's own covers directory and returns the stored name.
     *
     * Copying is the point. Storing the path the user happened to pick would leave the collection
     * pointing at a file they are free to move, rename or delete, and the app would have no way to
     * tell a cover that has gone missing from one the user simply chose not to see. A copy makes
     * deleting the picture the app's own business, which is what lets the fallback be honest.
     *
     * Returns `null` -- rather than throwing -- when the file cannot be copied, because the caller is a
     * dialog button and a failed pick should leave the dialog open and usable, not take the screen
     * down with it. Two types are caught for that, not one, and the second is the one this catches in
     * practice: `CoverStorage` logs the `IOException` and rethrows it wrapped, so a chooser that
     * caught only the cause would let a copy failure escape into the composition's coroutine -- which
     * is exactly the crash this was added to stop, and the reason a pick that cannot be stored used
     * to read as the cover being silently ignored.
     */
    suspend fun ingest(
        source: Path,
        store: suspend (Path) -> String,
    ): String? =
        try {
            store(source)
        } catch (failure: IOException) {
            logger.warn(failure) { "Could not copy the chosen cover image from $source" }
            null
        } catch (failure: IllegalStateException) {
            logger.warn(failure) { "The cover storage refused the chosen image [$source]" }
            null
        }
}
