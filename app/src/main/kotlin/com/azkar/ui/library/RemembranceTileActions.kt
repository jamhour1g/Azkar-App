package com.azkar.ui.library

/**
 * What a remembrance tile can be asked to do.
 *
 * Four separate callbacks rather than a generic "action" enum the tile maps back to a lambda: the
 * call sites differ (the hub's search results and the detail page's member grid want different
 * things in the overflow), and a single enum would push a `when` into every caller to undo what the
 * signature hid.
 *
 * [onEdit] is `null` wherever there is no catalogue behind the tile to write to. A tile that cannot
 * be edited shows no pencil rather than one that opens an editor with nowhere to save.
 *
 * [onToggleReminder] is `null` where the tile is not offered to the queue at all, and [inReminders]
 * says which way round the action is: whether this passage is one of the ones the user asked to be
 * reminded about. The two travel together because a menu entry that says "add" when the passage is
 * already in the list is a lie the user only finds out by clicking it.
 */
internal data class RemembranceTileActions(
    val onRead: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onCopy: () -> Unit,
    val onEdit: (() -> Unit)? = null,
    val onRemoveFromCollection: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val onToggleReminder: (() -> Unit)? = null,
    val inReminders: Boolean = false,
)
