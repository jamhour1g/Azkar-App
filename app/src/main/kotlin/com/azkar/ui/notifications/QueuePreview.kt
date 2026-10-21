package com.azkar.ui.notifications

import androidx.compose.runtime.Immutable
import com.azkar.domain.model.Remembrance
import com.azkar.i18n.RemembranceI18n
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * The queue's next delivery, resolved for the summary the library shows.
 *
 * [passage] is the passage the *next* slot would show, not the one after it: the runtime advances
 * [ReminderQueueProgress.nextIndex] only after publishing, so the progress it wrote is still
 * pointing at the passage that has not been shown yet.
 *
 * [slot] is null when the queue is off, has no sources, or the window has closed for the day. All
 * three read as "nothing scheduled" rather than as an error, because none of them is one — see
 * [QueueSchedule.nextSlot] for why the closed-window case deliberately does not roll forward.
 */
@Immutable
data class QueuePreview(
    val enabled: Boolean,
    val hasSources: Boolean,
    val slot: LocalDateTime?,
    val passage: Remembrance?,
)

/**
 * The queue's passages, in the order the library holds them, and the passages themselves.
 *
 * The two travel together because a preview needs both and the caller has both: the order says
 * which passage is next and the list says what it says. Handing them over as one value also keeps
 * the preview from being asked about a passage the library no longer has.
 */
@Immutable
data class QueueReading(
    val orderedIds: List<Long>,
    val remembrances: List<Remembrance>,
)

/**
 * Builds the "up next" summary from one clock reading.
 *
 * An [Instant] rather than a [LocalDateTime] so the caller reads the system clock once and hands in
 * the same reading, rather than each part of the summary deciding for itself. It is on the desktop's
 * zone and nothing else, so there is one clock to agree about.
 *
 * The schedule is asked about through the same two questions the runtime asks, in the same order, so
 * the preview cannot disagree with what fires. [QueueCadence.CONTINUOUS] in particular must be
 * routed to [ContinuousSchedule.nextSlotOnActiveDays] rather than to the windowed question: routing
 * both through the windowed one is what made a queue that never stops report its next passage
 * nineteen hours away, because the answer it was getting was tomorrow's window rather than the slot
 * a minute or two from now.
 *
 * A pure function rather than a state holder, because everything it needs is handed to it: the
 * library's catalogue, the queue's config and the runtime's progress.
 */
fun queuePreview(
    instant: Instant,
    zone: ZoneId,
    config: ReminderQueueConfig,
    progress: ReminderQueueProgress,
    reading: QueueReading,
): QueuePreview {
    val now = LocalDateTime.ofInstant(instant, zone)
    return QueuePreview(
        enabled = config.enabled,
        hasSources = reading.orderedIds.isNotEmpty(),
        slot =
            if (config.enabled && reading.orderedIds.isNotEmpty()) {
                if (config.cadence == QueueCadence.CONTINUOUS) {
                    ContinuousSchedule.nextSlotOnActiveDays(
                        now = now,
                        intervalMinutes = config.effectiveIntervalMinutes,
                        activeDays = config.activeDays,
                    )
                } else {
                    QueueSchedule.nextSlotOnActiveDays(
                        now = now,
                        startMinute = config.windowStartMinute,
                        endMinute = config.windowEndMinute,
                        intervalMinutes = config.effectiveIntervalMinutes,
                        activeDays = config.activeDays,
                    )
                }
            } else {
                null
            },
        passage = nextPassage(reading, progress),
    )
}

/**
 * The passage the next slot would show.
 *
 * Read from [ReminderQueueProgress] first, because that is what the runtime will use and the
 * preview claiming a different passage than the one about to arrive would be worse than no preview.
 * The fallback — the head of the library's own order — is deliberately *not*
 * [QueueSchedule.cycleOrder]: that shuffles on every call for the two non-manual modes, so the
 * summary would change its mind on every recomposition until the first real delivery wrote a
 * progress record. A stable placeholder that is occasionally wrong beats a correct one that
 * flickers.
 */
private fun nextPassage(
    reading: QueueReading,
    progress: ReminderQueueProgress,
): Remembrance? {
    if (reading.orderedIds.isEmpty()) return null
    val scheduled = progress.cycleOrder.getOrNull(progress.nextIndex)?.takeIf { it in reading.orderedIds }
    val id = scheduled ?: reading.orderedIds.first()
    return id.let { candidate -> reading.remembrances.firstOrNull { it.id == candidate } }
}

/**
 * The passage text as a single line, falling back through the translations and then to nothing.
 *
 * The old version ended in `"Remembrance ${remembrance.id}"` — an English sentence built by string
 * concatenation, which is the same mistake the prayer names made, and it is also why a passage with
 * no translation at all used to be listed under a name nobody had asked for. Returning blank hands
 * that case to the caller, which substitutes its own placeholder and can therefore say it in the
 * reader's language.
 */
internal fun notificationLabel(
    remembrance: Remembrance,
    locale: Locale,
): String {
    val texts = RemembranceI18n.resolveTexts(remembrance, locale, "", "")
    return texts.translation
        .ifBlank {
            remembrance.translations.values
                .firstOrNull()
                ?.translationPair
                ?.text
                .orEmpty()
        }.replace('\n', ' ')
        .trim()
}
