package com.azkar.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.ui.library.data.ReminderSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The queue, as the library sees it.
 *
 * The library is not allowed to know how the queue is stored — it asks which collections are in it
 * and it asks for one to be added or taken out. The rest of the schedule (the window, the interval,
 * the days) belongs to the settings page, and is read here only so the library's summary can ask
 * the schedule the same question the runtime asks it.
 *
 * The config and the progress are mirrored into snapshot-backed properties rather than read straight
 * off the preferences flows, because the library reads them inside a composition: a card that shows
 * a lit bell has to be recomposed when the bell goes out, and that means the read has to be
 * observable.
 */
class QueueReminderSources(
    private val preferences: NotificationPreferencesRepository,
    private val scope: CoroutineScope,
) : ReminderSources {
    private var config by mutableStateOf(ReminderQueueConfig())
    private var progress by mutableStateOf(ReminderQueueProgress())

    override val queueConfig: ReminderQueueConfig get() = config
    override val queueProgress: ReminderQueueProgress get() = progress
    override val enabledCollectionIds: Set<Long> get() = config.collectionIds.toSet()
    override val isWeighted: Boolean get() = config.mode == QueueMode.WEIGHTED

    init {
        scope.launch {
            preferences.queue.collect { config = it }
        }
        scope.launch {
            preferences.queueProgress.collect { progress = it }
        }
    }

    override fun setEnabled(
        collectionId: Long,
        enabled: Boolean,
    ) {
        scope.launch {
            preferences.setQueueCollectionEnabled(collectionId, enabled)
        }
    }

    override fun setDropChance(
        collectionId: Long,
        chance: Int,
    ) {
        scope.launch {
            preferences.updateQueue { current ->
                current.copy(
                    collectionDropChances =
                        current.collectionDropChances +
                            (collectionId.toString() to chance.coerceIn(0, 100)),
                )
            }
        }
    }
}
