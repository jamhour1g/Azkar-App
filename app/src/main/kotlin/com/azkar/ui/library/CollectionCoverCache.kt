package com.azkar.ui.library

import androidx.compose.runtime.Stable
import com.azkar.data.storage.CoverStorage
import io.github.vinceglb.filekit.PlatformFile

/**
 * The one place a collection cover picture is turned back into something that can be drawn.
 *
 * Two facts about this that are worth stating before the code.
 *
 * **The bytes come from a copy, never from the path the user picked.** [CoverStorage.store] copies the
 * picture into `~/.azkar/covers` under a name the app invents, and the collection stores that name. That
 * is what makes "the user deleted the image" an event the app can see and answer: the library owns one
 * file per cover and sweeps the ones nothing points at any more. A remembered path into the user's own
 * pictures folder would instead leave a column of references that go quietly stale the first time a file
 * moves, with nothing to sweep and nothing to report.
 *
 * **This class no longer decodes anything, and that is the whole of what it is for.** It used to hold a
 * `SnapshotStateMap` of `ImageBitmap`s and reach Skia for `Image.makeFromEncoded`, which meant a cache
 * to invalidate, a `produceState` per card to keep the decode off the UI thread, and a second answer to
 * "what does this app do with an image" sitting next to the one every icon already used. Coil is that
 * answer now: it caches by file, decodes off the UI thread, and needs nobody to remember to forget
 * anything. What is left is the question none of those answered -- *which file is this cover?* -- and
 * that is a path lookup.
 *
 * So the holder survives only because the answer is worth having in one place. [file] turns a stored
 * name into a file the loader can read, and nothing else in the app knows that covers live in a
 * directory at all.
 *
 * [file] returns a path and not a *checked* one. Coil is the thing that decides whether a picture
 * decodes, and it answers that with its own state rather than with a boolean here; a cover whose file
 * has been deleted behind the app's back therefore reports success-with-nothing-to-show, which lands
 * on the generated wash. That is the same answer the old `null` produced, arrived at by asking the
 * one component whose job it is.
 */
@Stable
class CollectionCoverCache(
    private val covers: CoverStorage,
) {
    /**
     * The file behind [coverImagePath], or `null` when nothing is stored there.
     *
     * A plain function rather than composable state, because there is nothing to suspend for and
     * nothing to invalidate: a stored name always means the same file, since replacing a cover writes
     * a *new* name rather than over an old one.
     */
    fun file(coverImagePath: String): PlatformFile? =
        covers.resolve(coverImagePath).takeIf { it.toFile().isFile }?.let { PlatformFile(it.toFile()) }
}
