package com.azkar.ui.notifications

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Owns the two independent JavaFX players used by desktop reminder and prayer audio. */
class DesktopAudioPlayer {
    private val mutex = Mutex()
    private var notificationPlayer: MediaPlayer? = null
    private var prayerPlayer: MediaPlayer? = null

    suspend fun playNotification(
        path: String,
        volume: Float,
        onError: ((Throwable) -> Unit)? = null,
    ) = mutex.withLock {
        withContext(NonCancellable) {
            if (notificationPlayer != null) onFx { dispose(notificationPlayer) }
            notificationPlayer = player("remembrance notification", path, volume, onError)
        }
    }

    suspend fun playPrayer(
        path: String,
        volume: Float,
        onError: ((Throwable) -> Unit)? = null,
    ) = mutex.withLock {
        withContext(NonCancellable) {
            if (prayerPlayer != null) onFx { dispose(prayerPlayer) }
            prayerPlayer = player("prayer", path, volume, onError)
        }
    }

    suspend fun stopPrayer() =
        mutex.withLock {
            withContext(NonCancellable) {
                if (prayerPlayer != null) onFx { dispose(prayerPlayer) }
                prayerPlayer = null
            }
        }

    suspend fun release() =
        mutex.withLock {
            withContext(NonCancellable) {
                if (notificationPlayer != null || prayerPlayer != null) {
                    onFx {
                        dispose(notificationPlayer)
                        dispose(prayerPlayer)
                    }
                }
                notificationPlayer = null
                prayerPlayer = null
            }
        }

    private fun dispose(player: MediaPlayer?) {
        player?.dispose()
    }

    private suspend fun player(
        audioKind: String,
        path: String,
        volume: Float,
        onError: ((Throwable) -> Unit)?,
    ): MediaPlayer =
        onFx {
            val uri = File(path).toURI().toString()
            val media =
                Media(uri).apply {
                    setOnError {
                        val failure = error
                        logger.warn(failure) {
                            "JavaFX could not load $audioKind media (path='$path', uri='$uri', " +
                                "mediaError='${failure ?: "unknown JavaFX media error"}')"
                        }
                        onError?.invoke(failure ?: IllegalStateException("Unknown JavaFX media error"))
                    }
                }
            MediaPlayer(media).apply {
                this.volume = volume.coerceIn(0f, 1f).toDouble()
                setOnError {
                    val failure = error ?: media.error
                    logger.warn(failure) {
                        "JavaFX $audioKind playback failed (path='$path', uri='$uri', " +
                            "playerError='${failure ?: "unknown JavaFX player error"}', " +
                            "mediaError='${media.error ?: "none"}')"
                    }
                    onError?.invoke(failure ?: IllegalStateException("Unknown JavaFX player error"))
                    dispose()
                }
                setOnEndOfMedia { dispose() }
                // JavaFX honors play requests and volume set before the READY transition.
                play()
            }
        }

    private suspend fun <T> onFx(action: () -> T): T {
        withContext(Dispatchers.IO) { fxRuntime }
        return suspendCancellableCoroutine { continuation ->
            Platform.runLater {
                if (!continuation.isActive) return@runLater
                try {
                    continuation.resume(action())
                } catch (failure: Throwable) {
                    continuation.resumeWith(Result.failure(failure))
                }
            }
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}

        // Keep the toolkit alive while the tray app has no visible windows.
        val fxRuntime by lazy {
            JFXPanel()
            Platform.setImplicitExit(false)
        }
    }
}
