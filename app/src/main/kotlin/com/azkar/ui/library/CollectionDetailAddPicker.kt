package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.Remembrance
import com.azkar.ui.library.data.CollectionFiltering
import com.azkar.ui.library.data.findById
import com.azkar.ui.library.model.RemembranceTile

/**
 * The "add to this collection" dialog: its own query, its own ticking, and its own lifecycle.
 *
 * Split out of [CollectionDetailState] because it is the one piece of that screen which is a whole
 * screen's worth of state pretending to be three fields. The query, the selection and the open flag
 * only mean anything between the dialog opening and the user confirming or dismissing it, and every
 * one of them is reset the moment it closes — so holding them on the page state made the page carry a
 * seven-method mini-screen that nothing outside [CollectionDetailDialogs] ever reads.
 *
 * Kept reading the shelf through [CollectionsCatalogue] rather than being handed a snapshot, so a
 * picker opened after a membership change offers what the collection actually holds now.
 */
@Stable
class CollectionDetailAddPicker(
    private val collections: CollectionsCatalogue,
    private val collectionId: Long,
) {
    var isOpen: Boolean by mutableStateOf(false)
        private set

    /** Remembrances ticked in the picker, as ids. A set so ticking twice cannot double-add. */
    var selected: Set<Long> by mutableStateOf(emptySet())
        private set

    /** The picker's own filter, which narrows the *candidates*, not the catalogue. */
    var query: String by mutableStateOf("")

    /**
     * What the picker is offering: everything in the catalogue that is not already a member and
     * that matches [query].
     *
     * Members are filtered out here rather than after the query so that a search for something the
     * collection already has returns the remaining near-matches instead of an empty list that looks
     * like the search found nothing.
     */
    val candidates: List<Remembrance>
        get() {
            val current = collections.allCollections.findById(collectionId) ?: return emptyList()
            val pool = CollectionFiltering.addableRemembrances(current, collections.allRemembrances)
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return pool
            return pool.filter { CollectionFiltering.matchesRemembrance(it, trimmed.lowercase()) }
        }

    val tiles: List<RemembranceTile>
        get() =
            candidates.map {
                collections.presenter.toTile(
                    it,
                    collections.membershipCounts.collectionsIn(it.id ?: -1L),
                )
            }

    fun open() {
        selected = emptySet()
        query = ""
        isOpen = true
    }

    fun close() {
        isOpen = false
        selected = emptySet()
        query = ""
    }

    fun onQueryChanged(value: String) {
        query = value
    }

    fun toggleSelection(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAllCandidates() {
        selected = candidates.mapNotNullTo(mutableSetOf()) { it.id }
    }

    fun clearSelection() {
        selected = emptySet()
    }

    /** Hands the ticked ids to [CollectionsCatalogue] and closes the picker, whether or not any land. */
    fun confirm() {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        close()
        collections.addMembers(collectionId, ids) { }
    }
}
