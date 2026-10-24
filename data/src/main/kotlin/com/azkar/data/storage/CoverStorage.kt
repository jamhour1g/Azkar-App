package com.azkar.data.storage

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * The app's own directory of collection cover images, and the only place a cover path is resolved.
 *
 * A cover is stored as a copy rather than a path to wherever the user happened to pick it from.
 * That is what makes a deleted image an event this app can see: a user who removes the file they
 * chose leaves the rest of their library alone, and a cover that pointed into `~/Pictures` would
 * leave the app reading a file it does not own, cannot vouch for, and cannot tell apart from one it
 * wrote. Copying in means every cover is ours to delete, sweep and account for.
 *
 * The stored value is the file's name rather than its full path, so the whole library survives the
 * user moving their home directory — the same reason the database is opened relative to `~/.azkar`
 * and rebuilt from scratch if it is ever wrong.
 *
 * Sits beside the database directory rather than inside it: these are loose files a person might
 * reasonably go and look at, and the directory H2 owns should not also be the one holding pictures.
 */
class CoverStorage(
    private val directory: Path = defaultDirectory(),
    private val logger: KLogger = KotlinLogging.logger {},
) {
    /**
     * Copies [source] into the covers directory and returns the name to store against a collection.
     *
     * The extension is kept because it is the only thing that tells a person browsing this directory
     * what they are looking at, but nothing reads it: the decoder goes by the bytes.
     */
    fun store(source: Path): String {
        val name = UUID.randomUUID().toString() + extensionOf(source)
        val target = directory.resolve(name)
        try {
            Files.createDirectories(directory)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (failure: IOException) {
            logger.error(failure) { "Unable to store the cover image [$source]" }
            throw IllegalStateException("Unable to store the cover image [$source]", failure)
        }
        logger.debug { "Stored a cover image as [$name]" }
        return name
    }

    /** The file behind a stored cover name, whether or not it is still there. */
    fun resolve(coverImagePath: String): Path = directory.resolve(coverImagePath)

    /** Removes one stored cover. Missing is not a failure — there is nothing left to remove. */
    fun delete(coverImagePath: String) {
        runCatching { Files.deleteIfExists(resolve(coverImagePath)) }
            .onFailure { failure ->
                logger.warn(failure) { "Unable to delete the cover image [$coverImagePath]" }
            }
    }

    /** The name of every cover file currently on disk. */
    fun storedNames(): Set<String> =
        runCatching {
            if (!Files.isDirectory(directory)) {
                emptySet<String>()
            } else {
                Files.list(directory).use { entries ->
                    entries.filter { Files.isRegularFile(it) }.toList().mapTo(mutableSetOf()) { it.fileName.toString() }
                }
            }
        }.getOrElse { failure ->
            logger.warn(failure) { "Unable to list the cover images directory [$directory]" }
            emptySet()
        }

    /**
     * Deletes every cover file that no collection refers to, and reports how many went.
     *
     * Replacing a cover and deleting a collection leave a file behind that nothing points at any
     * more. Nothing would ever remove them, so this does — on every launch, which is cheap because
     * it is one listing and one query.
     *
     * A listing failure leaves the files alone. Sweeping on a guess about what is referenced would
     * delete pictures the user still has a collection pointing at, and an unreferenced file costs
     * disk, while a wrongly deleted one costs the picture.
     */
    fun sweepOrphans(referenced: Set<String>): Int {
        val orphans = storedNames() - referenced
        orphans.forEach(::delete)
        if (orphans.isNotEmpty()) {
            logger.info { "Removed ${orphans.size} cover image(s) no collection refers to" }
        }
        return orphans.size
    }

    private fun extensionOf(source: Path): String {
        val suffix = source.fileName.toString().substringAfterLast('.', missingDelimiterValue = "")
        return if (suffix.isEmpty() || suffix.length > MAX_EXTENSION_LENGTH) DEFAULT_EXTENSION else ".$suffix"
    }

    companion object {
        private const val DEFAULT_EXTENSION = ".png"

        // Long enough for `jpeg`; short enough that a name with no dot in it is not
        // mistaken for an extension.
        private const val MAX_EXTENSION_LENGTH = 5

        private fun defaultDirectory(): Path = Path.of(System.getProperty("user.home"), ".azkar", "covers")
    }
}
