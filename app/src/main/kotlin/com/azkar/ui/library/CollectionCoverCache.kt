package com.azkar.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.azkar.data.storage.CoverStorage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.skia.Image
import java.util.Base64

private val logger = KotlinLogging.logger {}

/**
 * Decodes collection cover pictures, once each.
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
 * **Decoding needs no dependency.** Skia is already on the classpath because it draws every pixel this
 * app has, and `Image.makeFromEncoded` is the decoder underneath the image library one would otherwise
 * add — along with its own cache to invalidate, its own lifecycle to reason about, and a second set of
 * answers to "what does this app do with an image".
 *
 * The cache is keyed by the file name and is deliberately not invalidated by time or size. A cover's
 * bytes cannot change under a name the app invented: replacing a picture writes a *new* name, so an
 * entry going stale would mean the app wrote over a file it had promised nobody would.
 */
@Stable
class CollectionCoverCache(
    private val covers: CoverStorage,
) {
    private val decoded: SnapshotStateMap<String, ImageBitmap> = mutableStateMapOf()

    /**
     * The decoded picture for [coverImagePath], or `null` when it cannot be decoded or does not exist.
     *
     * `null` is the answer for both, and it is not an error worth reporting upward: a card that cannot
     * draw a photograph falls back to the generated wash, which is a complete card. A missing file is a
     * normal state here precisely because the app owns these files and a user can delete one.
     */
    fun bitmap(coverImagePath: String): ImageBitmap? =
        decoded[coverImagePath] ?: covers.read(coverImagePath)?.let { bytes ->
            runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
                .onFailure { failure ->
                    logger.warn(failure) { "Unable to decode the cover image $coverImagePath" }
                }.getOrNull()
                ?.also { bitmap -> decoded[coverImagePath] = bitmap }
        }

    /**
     * The bytes of a stored cover, or `null` when the file is gone or unreadable.
     *
     * Separate from [bitmap] because the two answer different questions: the UI needs a decoded
     * picture, and the exporter needs the original file so the other end receives this image rather
     * than a re-encode of it. A path with no file behind it is `null` here for the same reason it
     * renders as a wash there, and both are the normal state after the user deletes a picture out
     * from under a collection.
     */
    fun bytes(coverImagePath: String): ByteArray? = covers.read(coverImagePath)

    /** The cover behind [coverImagePath], base64-encoded for a share document. */
    fun base64Of(coverImagePath: String?): String? =
        coverImagePath?.let { path -> bytes(path)?.let { Base64.getEncoder().encodeToString(it) } }

    /** Forgets [coverImagePath], so the next read decodes it again. */
    fun forget(coverImagePath: String) {
        decoded.remove(coverImagePath)
    }

    /** Forgets everything. Used by tests, and by a cover being deleted out from under the shelf. */
    fun forgetAll() {
        decoded.clear()
    }

    /**
     * The picture for [coverImagePath] as compose state, or `null` while it is being read.
     *
     * `produceState` rather than `remember`, because reading the file is a disk operation and
     * `remember`'s block runs during composition — which is how a shelf of fourteen covers would come to
     * do fourteen reads on the main thread, once each per recomposition.
     *
     * The key includes [coverImagePath] so replacing a cover re-reads it, and [covers] so two caches in
     * one composition cannot answer for each other.
     */
    @Composable
    fun image(coverImagePath: String): State<ImageBitmap?> =
        produceState(initialValue = decoded[coverImagePath], coverImagePath, covers) {
            value = bitmap(coverImagePath)
        }
}
