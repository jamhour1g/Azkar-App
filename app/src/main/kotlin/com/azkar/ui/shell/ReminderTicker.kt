package com.azkar.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionReminderFiredEmpty
import com.azkar.generated.resources.collectionReminderFiredFormat
import com.azkar.generated.resources.collectionReminderFiredTitle
import com.azkar.ui.library.CollectionReminderState
import com.azkar.ui.library.FiredReminder
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The loop that notices a reminder has come round, and says so in both places the user can see.
 *
 * Driven from here rather than from a screen, because a reminder has to fire while the user is
 * somewhere else. Had it lived on the collection page it would only work on that page — the one place
 * a reminder is not needed, since a schedule is by definition about a time the app is not being looked
 * at.
 *
 * A plain `delay` loop rather than a platform timer, because the answer does not depend on the tick
 * landing exactly on a minute: [CollectionReminderState.tick] judges due-ness against the last time
 * each schedule came round, so a tick that arrives late, or twice, announces once.
 */
@Composable
internal fun ReminderTicker(
    reminders: CollectionReminderState,
    notifier: ReminderNotifier,
    showToast: ShowToast,
) {
    LaunchedEffect(reminders) {
        val title = getString(Res.string.collectionReminderFiredTitle)
        reminders.load()
        while (true) {
            delay(TICK_INTERVAL)
            for (fired in reminders.tick(Instant.now())) {
                val body = announcementFor(fired)
                notifier.show(ReminderNotice(title = title, message = body, collectionId = fired.collectionId))
                showToast(title, body, NotificationPriority.MEDIUM)
            }
        }
    }

    // A tray icon outlives the window that registered it, and one left behind would sit in the taskbar
    // opening nothing. Tearing it down with the composition is the only thing that keeps it honest.
    DisposableEffect(notifier) {
        onDispose { notifier.dispose() }
    }
}

/**
 * The sentence a notification is made of.
 *
 * The collection's name leads, because a notification is read at a glance by someone who is not looking
 * at the app, and "52 remembrances waiting" means nothing on its own.
 *
 * A collection emptied since the schedule was set gets its own wording rather than "0 remembrances",
 * which would read as a count the app ought to have had something to say about.
 */
private suspend fun announcementFor(fired: FiredReminder): String {
    val name = fired.collectionName.ifBlank { DEFAULT_NAME }
    return if (fired.memberCount > 0) {
        getString(Res.string.collectionReminderFiredFormat, name, fired.memberCount)
    } else {
        getString(Res.string.collectionReminderFiredEmpty, name)
    }
}

/** Half a minute: late enough to cost nothing, tight enough that a schedule is not missed by minutes. */
private val TICK_INTERVAL = 0.5.minutes

/** What a schedule with no readable collection name is called. It should never happen, and must not crash. */
private const val DEFAULT_NAME = "Azkar"
