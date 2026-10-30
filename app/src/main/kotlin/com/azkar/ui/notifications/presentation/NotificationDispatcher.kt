package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.AudioPreferences
import com.azkar.ui.notifications.DesktopAudioPlayer
import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationContent
import com.azkar.ui.notifications.NotificationNotice
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The one thing the scheduler is allowed to ask for.
 *
 * [com.azkar.ui.notifications.NotificationRuntime] knows that something is due and produces a
 * [NotificationNotice]. It does not know, and must not learn, whether that notice becomes a Compose
 * card in a borderless window or — if the desktop cannot show one — nothing. Everything it would
 * otherwise need to know lives behind this interface, and everything that would otherwise have to
 * be a second scheduler lives behind [NotificationDispatcher]'s implementation.
 */
interface NotificationPublisher {
    /**
     * The action the most recently published notice carried.
     *
     * Read by the tray icon's primary click, which has no notion of what was last announced and so
     * needs somewhere to look it up.
     */
    val latestAction: NotificationAction

    /** Hands [notice] to the overlay. Safe to call from any thread. */
    fun publish(notice: NotificationNotice)

    /** Releases audio and overlay resources. Called once, from the app's Quit path. */
    suspend fun dispose()
}

/**
 * Routes a fired reminder to the overlay, and remembers where the last one wanted to go.
 *
 * There is one channel, and that is a decision rather than an absence. The operating system's own
 * notification service used to sit beside the overlay behind an opt-in: the same announcement drawn
 * by the desktop in the desktop's own styling, with a lifetime the app does not own and rules it
 * cannot read. Two drawings of one reminder is one reminder too many, and the second one is the one
 * that does not match the app — so the channel was removed rather than defaulted off. Everything
 * that survives here is what the overlay needs anyway: somewhere to hand a notice, and the last
 * notice's action for the tray icon's primary click.
 */
class NotificationDispatcher(
    private val overlay: NotificationPresenter,
    private val audioPreferences: StateFlow<AudioPreferences>? = null,
    private val audioPlayer: DesktopAudioPlayer? = null,
    private val audioScope: CoroutineScope? = null,
) : NotificationPublisher {
    @Volatile
    private var action: NotificationAction = NotificationAction.OpenApp

    override val latestAction: NotificationAction
        get() = action

    override fun publish(notice: NotificationNotice) {
        action = notice.action
        val sounds = audioPreferences?.value
        val audioContext =
            when (val content = notice.content) {
                is NotificationContent.Prayer -> "prayer ${content.prayerKey?.id ?: "unknown"}"
                is NotificationContent.Remembrance -> "remembrance notification"
                null -> null
            }
        val audioPath =
            when (notice.content) {
                is NotificationContent.Prayer -> sounds?.prayerFile
                is NotificationContent.Remembrance -> sounds?.notificationFile
                null -> null
            }
        audioScope?.launch {
            try {
                when (val content = notice.content) {
                    is NotificationContent.Prayer ->
                        if (
                            sounds?.prayerEnabled == true &&
                            (content.prayerKey?.let { sounds.prayerSoundsEnabled[it.id] == true } ?: true) &&
                            sounds.prayerFile.isNotBlank()
                        ) {
                            audioPlayer?.playPrayer(sounds.prayerFile, sounds.prayerVolume)
                        }
                    is NotificationContent.Remembrance ->
                        if (
                            sounds?.notificationsEnabled == true && sounds.notificationFile.isNotBlank()
                        ) {
                            audioPlayer?.playNotification(sounds.notificationFile, sounds.notificationVolume)
                        }
                    null -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger.warn(failure) {
                    "Unable to start ${audioContext ?: "notification"} audio " +
                        "(path='${audioPath?.ifBlank { "unset" } ?: "unset"}')"
                }
            }
        }
        overlay.present(notice)
    }

    override suspend fun dispose() {
        overlay.dispose()
        audioPlayer?.release()
        action = NotificationAction.OpenApp
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
