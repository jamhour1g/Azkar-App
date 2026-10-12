package com.azkar.ui.library

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger {}

/**
 * Asks the platform for a picture and hands back a path to it.
 *
 * Kept as its own file with the AWT import in it because it is the only place in the app that talks
 * to a native dialog. Everything else either stores cover files or reads them; choosing one is a
 * desktop concern that no other layer should have to know exists.
 *
 * The two functions are separable on purpose. [choose] is the only part that can be cancelled and
 * is the only part worth substituting in a test; [ingest] is the part with the interesting failure
 * modes, so it takes a plain [Path] and can be exercised without a screen.
 */
internal object CoverImageChooser {
    /**
     * Opens the file dialog, returning the chosen file or `null` if the user cancelled.
     *
     * The dialog is created and shown rather than reused across calls: a chooser remembers the
     * directory the user last browsed to, and a collection editor opened much later would then open
     * somewhere the user cannot see, which reads as the app having lost the picture.
     */
    suspend fun choose(parent: java.awt.Component? = null): Path? =
        withContext(Dispatchers.Swing) {
            val chooser =
                JFileChooser().apply {
                    dialogTitle = "Choose a cover image"
                    fileSelectionMode = JFileChooser.FILES_ONLY
                    isAcceptAllFileFilterUsed = false
                    isMultiSelectionEnabled = false
                    fileFilter =
                        FileNameExtensionFilter(
                            "Images (*.png, *.jpg, *.jpeg, *.gif, *.webp)",
                            "png",
                            "jpg",
                            "jpeg",
                            "gif",
                            "webp",
                        )
                }
            chooser.showOpenDialog(parent).takeIf { it == JFileChooser.APPROVE_OPTION }?.let {
                chooser.selectedFile?.toPath()
            }
        }

    /**
     * Copies a chosen picture into the app's own covers directory and returns the stored name.
     *
     * Copying is the point. Storing the path the user happened to pick would leave the collection
     * pointing at a file they are free to move, rename or delete, and the app would have no way to
     * tell a cover that has gone missing from one the user simply chose not to see. A copy makes
     * deleting the picture the app's own business, which is what lets the fallback be honest.
     *
     * Returns `null` — rather than throwing — when the file cannot be read, because the caller is a
     * dialog button and a failed pick should leave the dialog open and usable, not take the screen
     * down with it.
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
        }
}
