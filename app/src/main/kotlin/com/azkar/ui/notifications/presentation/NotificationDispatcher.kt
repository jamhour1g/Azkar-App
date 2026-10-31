package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.AudioPreferences
import com.azkar.ui.notifications.DesktopAudioPlayer
import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationContent
import com.azkar.ui.notifications.NotificationNotice
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.scene.media.MediaException
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
        val preferences = audioPreferences?.value
        notice.content?.let { audioRequest(it, preferences) }?.let { audio ->
            if (audio.enabled(preferences)) {
                audioScope?.launch { playAudio(audio, preferences) }
            }
        }
        overlay.present(notice)
    }

    private data class AudioRequest(
        val label: String,
        val path: String,
        val volume: Float,
        val enabled: (AudioPreferences?) -> Boolean,
        val play: suspend (DesktopAudioPlayer, String, Float) -> Unit,
    )

    private fun audioRequest(
        content: NotificationContent,
        preferences: AudioPreferences?,
    ): AudioRequest =
        when (content) {
            is NotificationContent.Prayer ->
                AudioRequest(
                    label = "prayer ${content.prayerKey?.id ?: "unknown"}",
                    path = preferences?.prayerFile.orEmpty(),
                    volume = preferences?.prayerVolume ?: 0f,
                    enabled = { prefs ->
                        prefs?.prayerEnabled == true &&
                            (content.prayerKey?.let(prefs::isPrayerSoundEnabled) ?: true)
                    },
                    play = { player, path, volume -> player.playPrayer(path, volume) },
                )
            is NotificationContent.Remembrance ->
                AudioRequest(
                    label = "remembrance notification",
                    path = preferences?.notificationFile.orEmpty(),
                    volume = preferences?.notificationVolume ?: 0f,
                    enabled = { it?.notificationsEnabled == true },
                    play = { player, path, volume -> player.playNotification(path, volume) },
                )
        }

    private suspend fun playAudio(
        request: AudioRequest,
        preferences: AudioPreferences?,
    ) {
        if (request.path.isBlank() || audioPlayer == null) return
        try {
            if (request.enabled(preferences)) request.play(audioPlayer, request.path, request.volume)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalArgumentException) {
            logAudioFailure(request, failure)
        } catch (failure: IllegalStateException) {
            logAudioFailure(request, failure)
        } catch (failure: MediaException) {
            logAudioFailure(request, failure)
        }
    }

    private fun logAudioFailure(
        request: AudioRequest,
        failure: Exception,
    ) {
        logger.warn(failure) { "Unable to start ${request.label} audio (path='${request.path}')" }
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
