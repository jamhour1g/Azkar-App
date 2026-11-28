package com.azkar.ui.shell

import com.azkar.settings.SettingsStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}
private const val EXTENSION_UUID = "azkar-prayers@azkar.app"
private const val EXTENSION_DIRECTORY = "gnome-shell/extensions/$EXTENSION_UUID"
internal const val BUNDLE_MARKER = ".azkar-bundle"
private const val METADATA = "metadata.json"
private val extensionFiles =
    listOf("panelState.js", "extension.js", "stylesheet.css", "azkar.png", METADATA) +
        listOf(
            "bell",
            "bell-off",
            "calendar",
            "house",
            "moon",
            "sun",
            "sunrise",
            "sunset",
            "x",
            "auto-awesome",
            "open-with",
            "visibility",
            "visibility-off",
        ).map { "$it-symbolic.svg" }

internal enum class GnomeExtensionStatus { UNAVAILABLE, AVAILABLE, LOCAL_OVERRIDE, BACKUP_FAILED, SIGN_IN_REQUIRED }

private val extensionStatus = MutableStateFlow(GnomeExtensionStatus.UNAVAILABLE)
internal val gnomeExtensionStatus = extensionStatus.asStateFlow()

@Synchronized
internal fun provisionGnomePrayerExtension(usePackagedCopy: Boolean = false) {
    runCatching {
        val systemDirectories =
            System.getenv("XDG_DATA_DIRS")?.takeIf { it.isNotBlank() } ?: "/usr/local/share:/usr/share"
        val bundledExtension =
            System.getProperty("compose.application.resources.dir")?.let { Path.of(it, "gnome", EXTENSION_UUID) }
        val status =
            prepareGnomePrayerExtension(
                gnomeUserDataHome(),
                systemDirectories.split(':').filter { it.isNotBlank() }.map(Path::of),
                bundledExtension,
                isAppImage = !System.getenv("APPIMAGE").isNullOrBlank(),
                usePackagedCopy = usePackagedCopy,
            )
        extensionStatus.value = status
        if (status != GnomeExtensionStatus.UNAVAILABLE) enableBundledExtensionOnce()
    }.onFailure {
        if (usePackagedCopy) extensionStatus.value = GnomeExtensionStatus.BACKUP_FAILED
        logger.debug(it) { "Could not set up the bundled GNOME prayer extension" }
    }
}

private fun gnomeUserDataHome(): Path =
    System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".local", "share")

internal fun prepareGnomePrayerExtension(
    dataHome: Path,
    systemDataDirectories: List<Path>,
    bundledExtension: Path?,
    isAppImage: Boolean,
    usePackagedCopy: Boolean = false,
): GnomeExtensionStatus {
    val userExtension = dataHome.resolve(EXTENSION_DIRECTORY)
    val systemExtension =
        systemDataDirectories.asSequence().map { it.resolve(EXTENSION_DIRECTORY) }.firstOrNull {
            Files.isRegularFile(it.resolve(METADATA))
        }
    if (systemExtension == null) {
        if (isAppImage && bundledExtension != null) {
            installAppImageExtension(bundledExtension, userExtension)
        }
        val available = Files.isRegularFile(userExtension.resolve(METADATA))
        return if (available) GnomeExtensionStatus.AVAILABLE else GnomeExtensionStatus.UNAVAILABLE
    }
    val packageOwnsExtension = !isAppImage && bundledExtension != null && Files.exists(bundledExtension)
    val hasOverride = Files.isDirectory(userExtension) && !Files.isSameFile(userExtension, systemExtension)
    return if (packageOwnsExtension && hasOverride &&
        Files.isSameFile(systemExtension, bundledExtension)
    ) {
        resolveUserExtension(userExtension, systemExtension, usePackagedCopy)
    } else {
        GnomeExtensionStatus.AVAILABLE
    }
}

private fun resolveUserExtension(
    userExtension: Path,
    systemExtension: Path,
    usePackagedCopy: Boolean,
): GnomeExtensionStatus {
    if (extensionFiles.any { !Files.isRegularFile(systemExtension.resolve(it)) }) return GnomeExtensionStatus.AVAILABLE
    val backups = userExtension.parent.parent.resolve("azkar-extension-backups")
    // After migration, a restored override is an explicit user choice.
    val migrate = usePackagedCopy || (!hasExtensionBackup(backups) && isAzkarUserExtension(userExtension))
    return if (migrate) {
        backUpUserExtension(userExtension, backups)
        GnomeExtensionStatus.SIGN_IN_REQUIRED
    } else {
        GnomeExtensionStatus.LOCAL_OVERRIDE
    }
}

private fun hasExtensionBackup(backups: Path): Boolean =
    Files.exists(backups) && Files.list(backups).use { it.findAny().isPresent }

private fun backUpUserExtension(
    userExtension: Path,
    backups: Path,
) {
    Files.createDirectories(backups)
    val backup = Files.createTempDirectory(backups, "backup-")
    val savedExtension =
        try {
            Files.move(userExtension, backup.resolve(userExtension.fileName), ATOMIC_MOVE)
        } catch (failure: IOException) {
            Files.deleteIfExists(backup)
            throw failure
        }
    logger.info { "Backed up GNOME prayer extension to $savedExtension. Sign in again to load the system copy." }
}

private fun installAppImageExtension(
    source: Path,
    destination: Path,
) {
    val marker = destination.resolve(BUNDLE_MARKER)
    if (Files.isSymbolicLink(destination)) return
    val managed = Files.isRegularFile(marker, NOFOLLOW_LINKS) || Files.notExists(destination.resolve(METADATA))
    if (!managed || extensionFiles.any { !Files.isRegularFile(source.resolve(it)) }) return
    Files.createDirectories(destination)
    Files.writeString(marker, "")
    // Publish metadata last so a new installation is discoverable only after its files are in place.
    extensionFiles.forEach { name ->
        val bundled = source.resolve(name)
        val installed = destination.resolve(name)
        if (Files.notExists(installed) || Files.mismatch(bundled, installed) != -1L) {
            val staged = Files.createTempFile(destination, ".azkar-", ".tmp")
            try {
                Files.copy(bundled, staged, REPLACE_EXISTING)
                Files.move(staged, installed, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(staged)
            }
        }
    }
}

private fun enableBundledExtensionOnce() {
    val marker = SettingsStore.appDataDirectory().resolve("gnome-prayer-extension-enabled")
    // An explicit disable in GNOME takes precedence over Azkar's default.
    val disabled = runGnomeCommand("gsettings", "get", "org.gnome.shell", "disabled-extensions") ?: return
    if (Files.exists(marker) || "'$EXTENSION_UUID'" in disabled) return
    // A running shell may discover the files only after the next sign-in.
    if (runGnomeCommand("gnome-extensions", "enable", EXTENSION_UUID) != null) {
        Files.createDirectories(marker.parent)
        Files.writeString(marker, "")
    }
}

private fun runGnomeCommand(vararg command: String): String? {
    val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    return try {
        if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) {
            process.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
    } finally {
        process.destroyForcibly()
    }
}
