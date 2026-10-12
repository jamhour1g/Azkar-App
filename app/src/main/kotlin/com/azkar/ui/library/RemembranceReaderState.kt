package com.azkar.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.azkar.domain.model.Remembrance
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.library.data.findById

/**
 * What the reader page knows about the one remembrance it is showing.
 *
 * A holder rather than a bag of parameters because the page reads the same shared
 * [CollectionsCatalogue] the rest of the library does, and the reason it does is the whole reason this
 * page is worth having: the star on the tile that was clicked, the star on the reader, and the star in
 * the database are then three readings of one fact rather than two copies that can disagree.
 *
 * It holds no copy of the [Remembrance] for the same reason — [remembrance] is resolved against the
 * catalogue on every read, so a rename or a correction made anywhere shows up here without this page
 * having been told.
 *
 * Its absence is a state rather than an error. A remembrance can be deleted, or its database removed,
 * while this page is on the stack, and the page's job then is to say so and offer the way back rather
 * than to render an empty column.
 *
 * @param collections the shared shelf this page reads, handed back to whoever composes the page so a
 * screenshot or a test can build the state without rebuilding the catalogue around it.
 */
@Stable
class RemembranceReaderState(
    private val collections: CollectionsCatalogue,
    val remembranceId: Long,
) {
    /**
     * The shared shelf this page reads, handed back to whoever composes the page.
     *
     * Exposed for the same reason [CollectionDetailState.catalogue] is: without it every caller would
     * have to pass the catalogue alongside the holder that already holds it, which is one more thing
     * that can be a different instance from the one the page is reading.
     */
    val catalogue: CollectionsCatalogue get() = collections

    /** The remembrance being read, or `null` when its id is no longer in the database. */
    val remembrance: Remembrance? get() = collections.allRemembrances.findById(remembranceId)

    /** Resolves the texts, so the page and the tiles cannot each pick their own language rules. */
    val presenter: CollectionPresenter get() = collections.presenter

    /**
     * Stars or unstars the remembrance, through the shared catalogue.
     *
     * There is no local mirror of the flag. An earlier copy of this idea would have been one `mutableStateOf`
     * so that the button could respond instantly, and it would have been wrong the first time the write
     * failed — the catalogue rolls a failed write back and says so, and a local copy would have kept the
     * lie until the page was rebuilt.
     */
    fun toggleFavorite() {
        collections.toggleFavorite(remembranceId)
    }
}

/**
 * The reader's holder, alive for exactly as long as the composition that asked for it.
 *
 * Keyed on the catalogue and the id, so a *different* remembrance arriving on the same back-stack entry
 * produces the holder that matches what is on screen rather than the one built when the entry was
 * pushed.
 */
@Composable
fun rememberRemembranceReaderState(
    catalogue: CollectionsCatalogue,
    remembranceId: Long,
): RemembranceReaderState =
    remember(catalogue, remembranceId) {
        RemembranceReaderState(
            collections = catalogue,
            remembranceId = remembranceId,
        )
    }
