package com.azkar.updates

import com.azkar.settings.SettingsStore
import com.azkar.ui.notifications.DesktopPlatform
import dev.nucleusframework.updater.provider.GenericProvider
import dev.nucleusframework.updater.testing.FeedFault
import dev.nucleusframework.updater.testing.UpdateFeedServer
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

internal class MockUpdateFixture(
    scenario: String,
    requestedFormat: String?,
    private val runningVersion: String,
) : AutoCloseable {
    init {
        require(scenario in setOf("success", "current", "check-failed", "download-failed")) {
            "Unknown mock update scenario '$scenario'. Use success, current, check-failed, or download-failed."
        }
    }

    val format: String = selectFormat(requestedFormat)
    private val updateVersion = "${runningVersion.substringBefore('.').toBigInteger() + 1.toBigInteger()}.0.0"
    private val suffix = if (format == "nsis") "-nsis.exe" else ".$format"
    private val feed = UpdateFeedServer()
    val baseUrl: String get() = feed.baseUrl
    val profile: File = Files.createTempDirectory("azkar-mock-update-").toFile()
    private val closed = AtomicBoolean()
    private val artifact = feed.directory.resolve("azkar-$updateVersion$suffix")

    init {
        artifact.writeBytes(ByteArray(2 * 1024 * 1024))
        val version = if (scenario == "current") runningVersion else updateVersion
        for (channel in UpdateChannel.entries) {
            feed.publish(version, listOf(artifact), channel.providerChannel)
        }
        when (scenario) {
            "check-failed" -> feed.fault(FeedFault.Status(503), "*.yml")
            "download-failed" -> feed.fault(FeedFault.Status(503), artifact.name)
            else -> feed.fault(FeedFault.Throttle(640 * 1024), artifact.name)
        }
    }

    fun clearFaults() = feed.clearFaults()

    fun manager(
        settings: SettingsStore,
        client: HttpClient,
        scope: CoroutineScope,
    ): UpdateManager =
        UpdateManager(
            settings,
            client,
            scope,
            provider = GenericProvider(baseUrl),
            packageFormat = format,
            initialVersion = runningVersion,
            onDownloaded = { file ->
                file.parent.toFile().deleteOnExit()
                file.toFile().deleteOnExit()
            },
            install = { _, file, info ->
                java.nio.file.Files
                    .deleteIfExists(file)
                java.nio.file.Files
                    .deleteIfExists(file.parent)
                info.version
            },
        )

    private fun selectFormat(requestedFormat: String?): String {
        val formats =
            when (DesktopPlatform.current) {
                DesktopPlatform.WINDOWS -> listOf("nsis")
                DesktopPlatform.MACOS -> listOf("zip")
                DesktopPlatform.LINUX -> listOf("deb", "rpm", "appimage")
                DesktopPlatform.UNKNOWN -> error("Mock updates require Windows, macOS, or Linux")
            }
        return (requestedFormat ?: formats.first()).also {
            require(
                it in formats,
            ) { "Mock update format '$it' is unavailable on this platform. Use ${formats.joinToString()}." }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            feed.close()
            profile.deleteRecursively()
        }
    }
}
