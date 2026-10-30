package com.azkar.ui.notifications

import com.azkar.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Copies the shipped audio choices to app data so preferences and JavaFX always use file paths. */
internal object BundledAudioDefaults {
    private const val PRAYER_ASSET = "/audio/muhammad-jazy-abdullah.mp3"
    private const val NOTIFICATION_ASSET = "/audio/notification-sound.mp3"
    private val mutex = Mutex()

    data class Fileset(
        val prayer: String,
        val notification: String,
    )

    suspend fun install(
        directory: Path = SettingsStore.appDataDirectory().resolve("audio"),
        openResource: (String) -> InputStream = { resource ->
            checkNotNull(BundledAudioDefaults::class.java.getResourceAsStream(resource)) {
                "Bundled audio resource is missing: $resource"
            }
        },
    ): Fileset =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                Files.createDirectories(directory)
                Fileset(
                    prayer = installAsset(PRAYER_ASSET, directory, openResource),
                    notification = installAsset(NOTIFICATION_ASSET, directory, openResource),
                )
            }
        }

    private fun installAsset(
        resource: String,
        directory: Path,
        openResource: (String) -> InputStream,
    ): String {
        val target = directory.resolve(resource.substringAfterLast('/'))
        if (Files.notExists(target)) {
            val temporary = Files.createTempFile(directory, ".audio-", ".tmp")
            try {
                openResource(resource).use { Files.copy(it, temporary, StandardCopyOption.REPLACE_EXISTING) }
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        return target.toAbsolutePath().toString()
    }
}
