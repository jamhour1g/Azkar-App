package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.RecitationProgress
import com.azkar.ui.library.data.CollectionProgressGateway
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * How far through each collection the user has got, and the writes that change it.
 *
 * Held beside [CollectionsState] rather than inside it because the two answer different questions
 * about the same data. The shelf is about *which* remembrances are grouped together; this is about
 * *how many times* they have been said. Progress is keyed by (collection, remembrance) while a
 * `RemembranceCollection` knows only its members, so folding it in would mean rebuilding the whole
 * aggregate on every tap of a recitation session.
 *
 * Loading is idempotent per collection: the detail page and the session both ask on entry, and a
 * second read of a row already in hand is a second statement for the same answer.
 */
@Stable
class CollectionProgressState(
    private val scope: CoroutineScope,
    private val gateway: CollectionProgressGateway,
    private val logger: KLogger = KotlinLogging.logger {},
) {
    private var byCollection by mutableStateOf<Map<Long, List<RecitationProgress>>>(emptyMap())
        private set

    /** The progress recorded against one collection, or nothing when it has never been recited. */
    fun of(collectionId: Long): List<RecitationProgress> = byCollection[collectionId].orEmpty()

    /**
     * Reads a collection's progress in the background, unless it is already loaded.
     *
     * A read that is already in flight is not repeated, and neither is one that has finished: the
     * collection is seeded with an empty list on the way in, so a second caller arriving while the
     * first query runs sees "asked for" rather than "never asked for".
     */
    fun load(collectionId: Long) {
        if (byCollection.containsKey(collectionId)) {
            return
        }
        byCollection = byCollection + (collectionId to emptyList())
        scope.launch {
            runCatching { gateway.fetchProgress(collectionId) }
                .onSuccess { loaded -> byCollection = byCollection + (collectionId to loaded) }
                .onFailure { failure ->
                    logger.warn(failure) { "Unable to read the recitation progress of collection $collectionId" }
                }
        }
    }

    /**
     * Counts [remembranceId] once, or [times] over, applying the change here and persisting it.
     *
     * Applied optimistically for the same reason the star is: a tap in a recitation session that waits
     * for a database round trip feels broken, and the count on screen is the thing the user is watching.
     * A failed write rolls the local count back and reports it through [onFailure], so the number on
     * screen never quietly disagrees with what was stored.
     *
     * Returns false when the change would be a no-op — counting a passage that has reached zero, or
     * counting it a negative number of times — so the caller can skip the write entirely.
     */
    fun record(
        collectionId: Long,
        remembranceId: Long,
        times: Int = 1,
        onFailure: () -> Unit = {},
    ): Boolean {
        val stored = of(collectionId)
        val before = stored.firstOrNull { it.remembranceId == remembranceId }?.repeatCount ?: 0
        val after = maxOf(0, before + times)
        if (after == before) {
            return false
        }
        write(collectionId, stored, remembranceId, after, hadRow = true)
        scope.launch {
            runCatching { gateway.recordRecitation(collectionId, mapOf(remembranceId to times)) }
                .onSuccess {
                    logger.debug {
                        "Recorded $times recitations for remembrance $remembranceId in collection $collectionId"
                    }
                }.onFailure { failure ->
                    logger.error(failure) { "Unable to record $times recitations in collection $collectionId" }
                    // A count that was zero before the write has no row to restore, so the row the
                    // optimistic write created is taken away again rather than left claiming that
                    // something was recited.
                    write(collectionId, of(collectionId), remembranceId, before, hadRow = before > 0)
                    onFailure()
                }
        }
        return true
    }

    /**
     * Forgets a collection's progress, and tells the caller whether it worked.
     *
     * Optimistic and rolled back like [record], because a reset that appears to work and does not
     * would leave the page showing an empty progress bar over a collection that is still fully
     * counted. The entry is cleared rather than removed, so a later [load] is still a no-op: a reset
     * is the authoritative answer for that collection, and re-reading it would be a second statement
     * for the same answer.
     */
    fun reset(
        collectionId: Long,
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        val previous = byCollection[collectionId]
        byCollection = byCollection + (collectionId to emptyList())
        scope.launch {
            runCatching { gateway.resetProgress(collectionId) }
                .onSuccess {
                    logger.info { "Reset recitation progress for collection $collectionId" }
                    onSuccess()
                }.onFailure { failure ->
                    logger.error(failure) { "Unable to reset the recitation progress of collection $collectionId" }
                    if (previous != null) {
                        byCollection = byCollection + (collectionId to previous)
                    }
                    onFailure()
                }
        }
    }

    /**
     * Replaces one member's row in place, so the surrounding rows keep their order.
     *
     * [hadRow] is what a rollback needs: a member that had a stored count gets its row back, and one
     * that had none has its row removed again rather than left behind holding a zero that was never
     * recorded.
     */
    private fun write(
        collectionId: Long,
        stored: List<RecitationProgress>,
        remembranceId: Long,
        count: Int,
        hadRow: Boolean,
    ) {
        val others = stored.filterNot { it.remembranceId == remembranceId }
        val replacement =
            if (hadRow) {
                listOf(RecitationProgress(remembranceId, count, Instant.now()))
            } else {
                emptyList()
            }
        byCollection = byCollection + (collectionId to others + replacement)
    }
}
