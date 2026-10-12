package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.ui.library.data.CollectionReminderRow
import com.azkar.ui.library.data.CollectionStats
import com.azkar.ui.library.data.findById
import com.azkar.ui.library.data.nextMember
import com.azkar.ui.library.data.upcomingMembers
import java.time.LocalDateTime

/**
 * The state behind a recitation session: one member at a time, and where the session is.
 *
 * The position is **derived from progress rather than stored**. After every tap the next member is
 * whatever the collection says has not been recited yet, so the session cannot drift out of step with
 * what was actually recorded — closing the app halfway through and coming back lands on the right
 * passage because the database decided, not because a cursor was saved.
 *
 * That leaves one thing a derived position cannot express, which is why [pinnedId] exists: "show me
 * this one again". Without it, a passage the user wants a second time would be unreachable, because
 * by definition it has already been counted.
 */
@Stable
class CollectionSessionState(
    private val collections: CollectionsCatalogue,
    val collectionId: Long,
    reminders: CollectionReminderState,
) {
    private val detail = CollectionDetailState(collections, collectionId, reminders)

    /**
     * The shared shelf this session reads, handed back to whoever composes the session.
     *
     * The mirror of [CollectionDetailState.catalogue] and for the same reason: the page needs the
     * holder's locale and presenter, and asking for them separately would mean passing the shelf a
     * second time on top of the holder that already has it.
     */
    internal val catalogue: CollectionsCatalogue get() = collections

    /** The presenter's texts, resolved from the shelf's locale so a passage reads as it does there. */
    val presenter get() = collections.presenter

    /**
     * The member the user asked to stay on, or `null` for "wherever the collection says is next".
     *
     * Cleared by [resume], [complete] and [skip], so pinning is a one-off rather than a mode that has
     * to be remembered and unremembered.
     */
    private var pinnedId: Long? by mutableStateOf(null)

    val collection get() = detail.collection

    val stats: CollectionStats get() = detail.stats

    val totalMembers: Int get() = stats.totalMembers

    /**
     * The passage on screen, or `null` when the collection is finished or no longer on the shelf.
     *
     * A pinned member that has since left the collection reads as nothing rather than as a stale
     * passage: the fallback below it is the derived next one, and falling through to it keeps the
     * session usable when the shelf changed underneath.
     */
    val current: Remembrance?
        get() {
            val source = collection ?: return null
            val pinned = pinnedId ?: return source.let { nextMember(it, detail.progress) }
            return source.members.findById(pinned) ?: nextMember(source, detail.progress)
        }

    /** One-based position of [current] in the shelf's own order, or 0 when nothing is on screen. */
    val position: Int
        get() {
            val active = current ?: return 0
            val index = collection?.members?.indexOfFirst { it.id == active.id } ?: -1
            return if (index < 0) 0 else index + 1
        }

    /**
     * The passages still to come, so the session shows what is ahead rather than only what is now.
     *
     * The passage on screen is filtered out by id rather than by dropping the head of the list. The
     * two agree while the session follows the collection, where the head is by definition the passage
     * on screen; they part company as soon as the user pins one, where the pinned passage sits
     * *behind* the head and dropping the head would silently promote the next passage and hide a real
     * one the user has not reached.
     *
     * A passage the user skipped still appears here, because skipping is not recorded: derived
     * progress cannot tell "not yet" from "passed over". That is the cost of never storing a cursor,
     * and it is a preview list rather than a claim about what the session will do.
     */
    val upcoming: List<Remembrance>
        get() {
            val source = collection ?: return emptyList()
            val active = current?.id
            return upcomingMembers(source, detail.progress, UPCOMING_COUNT + 1)
                .filter { it.id != active }
                .take(UPCOMING_COUNT)
        }

    /** True once every member has been recited, or when the collection has no members at all. */
    val isFinished: Boolean
        get() {
            val source = collection ?: return true
            return current == null && source.members.isNotEmpty()
        }

    val hasMembers: Boolean get() = stats.hasMembers

    /** Reads the collection's progress, if it has not been read already. */
    fun loadProgress() = detail.loadProgress()

    /**
     * Counts the current passage and moves on.
     *
     * The write is optimistic, so the screen advances immediately; if it fails the count rolls back
     * and the notice says so. Leaving the user on the passage they just completed because a database
     * round trip was slow would make the whole session feel broken.
     */
    fun complete() {
        val id = current?.id ?: return
        detail.recordRecitation(id)
        pinnedId = null
    }

    /**
     * Counts the current passage again without leaving it.
     *
     * Pins the passage on the way, and that is not a detail: the position is derived from progress, so
     * a passage that has just been counted is by definition no longer the one the session would choose
     * next. Without the pin this would record a second count and then advance to a different passage,
     * which is the opposite of what the button says it does.
     */
    fun reciteAgain() {
        val id = current?.id ?: return
        detail.recordRecitation(id)
        pinnedId = id
    }

    /**
     * Moves past the current passage without counting it.
     *
     * Pinned to the next passage that has not been counted yet, found by scanning on from the
     * current one rather than by asking the whole collection — "the next one after this" is a question
     * about position, and asking globally would wrap round to the beginning.
     */
    fun skip() {
        val source = collection
        val active = current
        if (source == null || active == null) {
            return
        }
        pinnedId = nextUncountedAfter(source, active)?.id ?: active.id
    }

    /**
     * The first passage after [from] that has not been counted yet, or null at the end of the shelf.
     *
     * Scans on from the current passage rather than asking the whole collection, because "the next
     * one after this" is a question about position: a global query would wrap round to the beginning
     * and turn a skip at the end into a jump back to the start.
     *
     * A member with no id cannot be counted against, so it is never a candidate.
     */
    private fun nextUncountedAfter(
        source: RemembranceCollection,
        from: Remembrance,
    ): Remembrance? {
        val counts = detail.progress.associate { it.remembranceId to it.repeatCount }
        val index = source.members.indexOfFirst { it.id == from.id }
        if (index < 0) {
            return null
        }
        return source.members
            .drop(index + 1)
            .firstOrNull { member -> member.id != null && (counts[member.id] ?: 0) == 0 }
    }

    /** Gives up the pin and goes back to the derived next passage. */
    fun resume() {
        pinnedId = null
    }

    /**
     * Steps back to the passage before the one on screen.
     *
     * The mirror of [skip], and pinned the same way — which is the only reason it can work at all. There
     * is no cursor in this state to decrement, because the position is derived from what has been
     * counted and a passage that has been counted is by definition behind the session. So "the one
     * before this" has to be found by position in the collection's own order and then pinned, exactly as
     * "the one after this" is found and pinned, and for the same reason: the pin is the escape hatch
     * that lets the session show a passage its own progress would never choose.
     *
     * Steps back rather than un-counting. Going back to re-read something is a thing readers do; taking
     * back a count they genuinely made is not, and a Previous button that rewrote history would be a
     * button nobody could press twice. Counting again afterwards is what [reciteAgain] is for.
     *
     * Nothing happens at the head of the collection, which is why [canStepBack] exists — a control that
     * silently does nothing is worse than one that says it has nowhere to go.
     */
    fun previous() {
        val source = collection ?: return
        val index = position - SESSION_FIRST_POSITION
        if (index <= 0) {
            return
        }
        pinnedId = source.members[index - 1].id
    }

    /**
     * Whether there is a passage before the one on screen to go back to.
     *
     * Asked separately from [previous] rather than folded into it because the control and the action
     * answer different questions: one is "can I", the other is "do it", and a button that goes grey has
     * to be grey *before* it is pressed.
     */
    val canStepBack: Boolean
        get() = position > SESSION_FIRST_POSITION

    /** The reminder schedule for this collection, or `null` when it has none. */
    val reminder: CollectionReminderRow? get() = detail.reminder

    /** The store the schedule above lives in, so the page can save, pause and drop it. */
    val reminders: CollectionReminderState get() = detail.reminderState

    /**
     * When the schedule next fires, or `null` when there is none or it is paused.
     *
     * Forwarded from the same detail state rather than recomputed, so the time the session's bell prints
     * and the time the ticker will act on cannot be read two different ways.
     */
    fun nextReminderAt(now: LocalDateTime): LocalDateTime? = detail.nextReminderAt(now)

    /**
     * Stars or unstars the collection being recited.
     *
     * A collection-wide flag rather than anything about the passage on screen, and deliberately reached
     * through the same [CollectionDisplayPreference] the shelf's own star uses: a collection that is
     * starred from the session has to already be starred on the shelf when the session closes, or the
     * two screens would disagree about a fact about the collection itself.
     */
    fun setFavorite(favorite: Boolean) {
        collections.setDisplay(collectionId, CollectionDisplayPreference.FAVORITE, favorite)
    }

    /** Whether the session is showing a passage the user chose to stay on rather than the next one. */
    val isPinned: Boolean get() = pinnedId != null

    /**
     * The passage this screen would hand to the reader, or `null` when the session has run out.
     *
     * Asked for by id rather than held, and named `read` rather than `open` because opening is no longer
     * this class's business. The reader used to be a dialog this screen owned, which meant holding the
     * row it had opened and clearing it again on dismissal — two pieces of state whose only job was to
     * mirror whether a dialog was up. It is a page now, so the shell's stack says which passage is being
     * read and this class has nothing to remember about it: the session comes back exactly as it was,
     * because it was never more than the screen underneath.
     */
    fun readId(): Long? = current?.id

    /** Forgets the collection's progress and starts the session over. */
    fun restart() = detail.resetProgress()
}

private const val UPCOMING_COUNT = 3

/**
 * The position the first member of a collection holds.
 *
 * Named rather than written as `1` at the two places that need it, because both of them are asking the
 * same question — "is this the beginning?" — and a bare one in a subtraction is a number that has to be
 * re-derived by whoever reads it next.
 */
private const val SESSION_FIRST_POSITION = 1
