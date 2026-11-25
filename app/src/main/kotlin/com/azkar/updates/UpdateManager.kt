package com.azkar.updates

import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsUpdateInstallCancelled
import com.azkar.generated.resources.settingsUpdateInstallFailed
import com.azkar.settings.SettingsStore
import com.azkar.util.runCatchingCancellable
import dev.nucleusframework.core.runtime.ExecutableRuntime
import dev.nucleusframework.core.runtime.ExecutableType
import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.updater.InstallFailure
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.exception.UpdateException
import dev.nucleusframework.updater.provider.UpdateProvider
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import dev.nucleusframework.updater.provider.GitHubProvider as NucleusGitHubProvider

enum class UpdateChannel(
    val providerChannel: String,
) {
    STABLE("latest"),
    DEV("beta"),
}

internal data class ChannelReleaseVersion(
    val version: String,
    val isPrerelease: Boolean,
    val isDraft: Boolean = false,
)

internal fun latestChannelVersion(
    releases: List<ChannelReleaseVersion>,
    channel: UpdateChannel,
): String? =
    releases
        .asSequence()
        .filter { !it.isDraft && it.isPrerelease == (channel == UpdateChannel.DEV) }
        .maxWithOrNull { left, right -> compareVersions(left.version, right.version) }
        ?.version

sealed interface UpdateStatus {
    data object Idle : UpdateStatus

    data object Checking : UpdateStatus

    data object Current : UpdateStatus

    data object Installing : UpdateStatus

    data class Available(
        val info: UpdateInfo,
        val releaseNotes: String,
        val isDowngrade: Boolean,
    ) : UpdateStatus

    data class Downloading(
        val version: String,
        val percent: Double,
    ) : UpdateStatus

    data class ReadyToInstall(
        val info: UpdateInfo,
        val releaseNotes: String,
        val file: Path,
        val isDowngrade: Boolean,
    ) : UpdateStatus

    data class Failed(
        val message: String,
        val previousInstall: Boolean = false,
        val retryVersion: String? = null,
        val retryChannelSwitch: Boolean = false,
    ) : UpdateStatus
}

class UpdateManager(
    private val settings: SettingsStore,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
    private val logger: KLogger = KotlinLogging.logger {},
    private val provider: UpdateProvider? = null,
    private val packageFormat: String? = null,
    initialVersion: String = applicationVersion(useRuntimeVersion = true),
    private val onDownloaded: (Path) -> Unit = {},
    private val readInstallFailure: (NucleusUpdater) -> InstallFailure? = {
        if (provider == null) it.consumeInstallFailure() else null
    },
    private val install: (NucleusUpdater, Path, UpdateInfo) -> String? = { updater, file, _ ->
        updater.installAndRestart(file.toFile())
        null
    },
) {
    private val preferenceKey = stringPreferencesKey("updates.channel")
    private val initialized = CompletableDeferred<Unit>()

    @Volatile
    private var runningVersion = initialVersion
    private val userActionMutex = Mutex()
    private val checkMutex = Mutex()
    private val _channel = MutableStateFlow(defaultChannel(runningVersion))
    private val _latestChannelVersion = MutableStateFlow<String?>(null)
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    private var activeUpdater: NucleusUpdater? = null

    val channel: StateFlow<UpdateChannel> = _channel.asStateFlow()
    val latestChannelVersion: StateFlow<String?> = _latestChannelVersion.asStateFlow()
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()
    val currentVersion: String get() = runningVersion
    private val executableType: ExecutableType
        get() = packageFormat?.let(ExecutableRuntime::parseType) ?: ExecutableRuntime.type()

    internal fun requiresPackageManager(update: UpdateStatus.Available): Boolean =
        executableType == ExecutableType.RPM && update.isDowngrade

    internal fun launchUpdateAction(action: suspend () -> Unit) {
        scope.launch {
            if (!userActionMutex.tryLock()) return@launch
            try {
                action()
            } finally {
                userActionMutex.unlock()
            }
        }
    }

    internal fun requestChannel(target: UpdateChannel) {
        scope.launch { userActionMutex.withLock { selectChannel(target) } }
    }

    val isRepositoryManaged: Boolean
        get() = executableType in setOf(ExecutableType.DEB, ExecutableType.RPM)

    init {
        scope.launch {
            try {
                settings.read(preferenceKey)?.let(::parseChannel)?.let { _channel.value = it }
                restoreInstallFailure()
            } finally {
                initialized.complete(Unit)
            }
            settings.changes.collect { preferences ->
                preferences[preferenceKey]?.let(::parseChannel)?.let { _channel.value = it }
            }
        }
    }

    suspend fun checkAutomatically() {
        initialized.await()
        if (!userActionMutex.tryLock()) return
        try {
            if (canCheckAutomatically(_status.value)) {
                logger.debug { "Checking for updates automatically" }
                val target = _channel.value
                scope.launch { refreshLatestChannelVersion(target) }
                checkForUpdates(target, explicitChannelSwitch = false)
            }
        } finally {
            userActionMutex.unlock()
        }
    }

    private fun canCheckAutomatically(status: UpdateStatus): Boolean =
        when (status) {
            is UpdateStatus.Downloading, is UpdateStatus.ReadyToInstall, UpdateStatus.Installing -> false
            is UpdateStatus.Available -> !status.isDowngrade
            is UpdateStatus.Failed ->
                !status.previousInstall &&
                    status.retryVersion == null &&
                    !status.retryChannelSwitch
            else -> true
        }

    private suspend fun restoreInstallFailure() {
        if (provider == null && !isRepositoryManaged) return
        val failure = readInstallFailure(createUpdater(_channel.value, explicitChannelSwitch = false)) ?: return
        logger.warn {
            "Update installation to ${failure.version} exited with code ${failure.exitCode}: ${failure.output}"
        }
        val message =
            if (failure.exitCode == 126) {
                getString(Res.string.settingsUpdateInstallCancelled)
            } else {
                getString(Res.string.settingsUpdateInstallFailed, failure.exitCode)
            }
        _status.value = UpdateStatus.Failed(message, previousInstall = true, retryVersion = failure.version)
    }

    suspend fun selectChannel(target: UpdateChannel) {
        initialized.await()
        if (target == _channel.value) return
        (_status.value as? UpdateStatus.ReadyToInstall)?.file?.let { discardStagedUpdate(it, logger) }
        _status.value = UpdateStatus.Checking
        _channel.value = target
        _latestChannelVersion.value = null
        settings.edit { this[preferenceKey] = target.name }
        logger.info { "Update track changed to ${target.name.lowercase()}" }
        scope.launch { refreshLatestChannelVersion(target) }
        checkForUpdates(target, explicitChannelSwitch = true)
    }

    suspend fun checkManually() {
        logger.info { "User requested an update check" }
        initialized.await()
        val failure = _status.value as? UpdateStatus.Failed
        _status.value = UpdateStatus.Checking
        delay(CHECK_FEEDBACK_DELAY_MILLIS.milliseconds)
        checkForUpdates(
            _channel.value,
            explicitChannelSwitch = failure?.retryChannelSwitch == true,
            retryVersion = failure?.retryVersion,
        )
    }

    fun packageManagerCommands(
        target: UpdateChannel,
        version: String?,
        downgrade: Boolean,
    ): String {
        val suite = if (target == UpdateChannel.DEV) "dev" else "stable"
        return when (executableType) {
            ExecutableType.DEB ->
                buildString {
                    appendLine(
                        "curl -fsSL https://jamhour1g.github.io/Azkar-App/keys/azkar-repo.asc | " +
                            "gpg --dearmor | sudo tee /usr/share/keyrings/azkar-repo.gpg >/dev/null",
                    )
                    appendLine(
                        "echo 'deb [signed-by=/usr/share/keyrings/azkar-repo.gpg] " +
                            "https://jamhour1g.github.io/Azkar-App/apt $suite main' | " +
                            "sudo tee /etc/apt/sources.list.d/azkar.list",
                    )
                    appendLine("sudo apt update")
                    val debVersion = version?.let(::debianPackageVersion)
                    if (debVersion != null) {
                        append("sudo apt install --allow-downgrades azkar=$debVersion")
                    } else {
                        append("sudo apt install azkar")
                    }
                }
            ExecutableType.RPM ->
                buildString {
                    appendLine("sudo rpm --import https://jamhour1g.github.io/Azkar-App/keys/azkar-repo.asc")
                    appendLine("sudo tee /etc/yum.repos.d/azkar.repo >/dev/null <<'EOF'")
                    appendLine("[azkar-$suite]")
                    appendLine("name=Azkar ${target.name.lowercase().replaceFirstChar(Char::uppercase)}")
                    appendLine("baseurl=https://jamhour1g.github.io/Azkar-App/rpm/$suite/")
                    appendLine("enabled=1")
                    appendLine("gpgcheck=0")
                    appendLine("repo_gpgcheck=1")
                    appendLine("gpgkey=https://jamhour1g.github.io/Azkar-App/keys/azkar-repo.asc")
                    appendLine("EOF")
                    append("sudo dnf ${if (downgrade) "downgrade" else "upgrade"} azkar")
                }
            else -> ""
        }
    }

    suspend fun download(info: UpdateInfo) {
        val (updater, available) =
            activeUpdater
                ?.let { updater -> (_status.value as? UpdateStatus.Available)?.let { updater to it } }
                ?: return
        logger.info { "Downloading update ${info.version}" }
        try {
            val downloadInfo = if (requiresPackageManager(available)) null else prepareDownloadInfo(available)
            if (downloadInfo == null) return
            updater.downloadUpdate(downloadInfo).collect { progress ->
                updateDownloadStatus(progress.file, progress.percent, info, available)
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: UpdateException) {
            logger.error(failure) { "Unable to download update" }
            _status.value =
                UpdateStatus.Failed(
                    failure.message ?: "Download failed",
                    retryVersion = info.version.takeIf { available.isDowngrade },
                )
        } catch (failure: IOException) {
            logger.error(failure) { "Unable to download update" }
            _status.value =
                UpdateStatus.Failed(
                    failure.message ?: "Download failed",
                    retryVersion = info.version.takeIf { available.isDowngrade },
                )
        } catch (failure: IllegalStateException) {
            logger.error(failure) { "Unable to download update" }
            _status.value =
                UpdateStatus.Failed(
                    failure.message ?: "Download failed",
                    retryVersion = info.version.takeIf { available.isDowngrade },
                )
        }
    }

    private fun prepareDownloadInfo(available: UpdateStatus.Available): UpdateInfo? {
        val info = available.info
        return if (!isRepositoryManaged) {
            info
        } else {
            val extension = if (executableType == ExecutableType.DEB) ".deb" else ".rpm"
            val packageFile = info.files.singleOrNull { it.fileName.endsWith(extension) }
            packageFile?.let { info.copy(currentFile = it) }
                ?: run {
                    _status.value =
                        UpdateStatus.Failed(
                            "No matching Linux package is available",
                            retryVersion = info.version.takeIf { available.isDowngrade },
                        )
                    null
                }
        }
    }

    private fun updateDownloadStatus(
        downloadedFile: File?,
        percent: Double,
        info: UpdateInfo,
        available: UpdateStatus.Available,
    ) {
        if (downloadedFile == null) {
            _status.value = UpdateStatus.Downloading(info.version, percent)
        } else {
            onDownloaded(downloadedFile.toPath())
            logger.info { "Update ${info.version} downloaded and is ready to install" }
            _status.value =
                UpdateStatus.ReadyToInstall(
                    info = info,
                    releaseNotes = available.releaseNotes,
                    file = downloadedFile.toPath(),
                    isDowngrade = available.isDowngrade,
                )
        }
    }

    suspend fun installAndRestart(file: Path) {
        val updater = activeUpdater ?: return
        val ready = _status.value as? UpdateStatus.ReadyToInstall ?: return
        logger.info { "Starting update installation" }
        _status.value = UpdateStatus.Installing
        delay(INSTALL_ANIMATION_DELAY_MILLIS.milliseconds)
        try {
            install(updater, file, ready.info)?.let { installedVersion ->
                runningVersion = installedVersion
                _status.value = UpdateStatus.Current
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            logger.error(failure) { "Unable to start update installation" }
            _status.value =
                UpdateStatus.Failed(
                    failure.message ?: "Update installation failed",
                    retryVersion =
                        ready.info.version
                            .takeIf {
                                ready.isDowngrade
                            },
                )
        } catch (failure: IllegalStateException) {
            logger.error(failure) { "Unable to start update installation" }
            _status.value =
                UpdateStatus.Failed(
                    failure.message ?: "Update installation failed",
                    retryVersion =
                        ready.info.version
                            .takeIf {
                                ready.isDowngrade
                            },
                )
        }
    }

    private fun createUpdater(
        target: UpdateChannel,
        explicitChannelSwitch: Boolean,
    ): NucleusUpdater =
        NucleusUpdater {
            currentVersion = runningVersion
            provider =
                this@UpdateManager.provider ?: NucleusGitHubProvider(owner = "jamhour1g", repo = "Azkar-App")
            packageFormat?.let { executableType = it }
            differentialDownload = this@UpdateManager.provider == null
            channel = target.providerChannel
            allowPrerelease = target == UpdateChannel.DEV
            allowDowngrade = explicitChannelSwitch
        }

    private suspend fun checkForUpdates(
        target: UpdateChannel,
        explicitChannelSwitch: Boolean,
        retryVersion: String? = null,
    ) {
        checkMutex.withLock {
            _status.value = UpdateStatus.Checking
            logger.debug { "Checking update track ${target.name.lowercase()} from $runningVersion" }
            val updater = createUpdater(target, explicitChannelSwitch || retryVersion != null)
            activeUpdater = updater
            val result =
                try {
                    updater.checkForUpdates()
                } catch (failure: IOException) {
                    logger.error(failure) { "Update check failed" }
                    _status.value =
                        UpdateStatus.Failed(
                            failure.message ?: "Update check failed",
                            retryVersion = retryVersion,
                            retryChannelSwitch = explicitChannelSwitch,
                        )
                    return@withLock
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: IllegalStateException) {
                    logger.error(failure) { "Update check failed" }
                    _status.value =
                        UpdateStatus.Failed(
                            failure.message ?: "Update check failed",
                            retryVersion = retryVersion,
                            retryChannelSwitch = explicitChannelSwitch,
                        )
                    return@withLock
                }
            applyCheckResult(result, target, retryVersion, explicitChannelSwitch)
        }
    }

    private suspend fun applyCheckResult(
        result: UpdateResult,
        target: UpdateChannel,
        retryVersion: String?,
        explicitChannelSwitch: Boolean,
    ) {
        when (result) {
            is UpdateResult.Available -> {
                val isDowngrade = compareVersions(result.info.version, runningVersion) < 0
                if (isDowngrade && retryVersion != null && result.info.version != retryVersion) {
                    _status.value = UpdateStatus.Current
                    return
                }
                if (_channel.value == target) _latestChannelVersion.value = result.info.version
                val notes = releaseNotes(result.info.version)
                logger.info { "Update ${result.info.version} is available" }
                _status.value =
                    UpdateStatus.Available(
                        info = result.info,
                        releaseNotes = notes,
                        isDowngrade = isDowngrade,
                    )
            }
            UpdateResult.NotAvailable -> {
                logger.info { "No update is available" }
                _status.value = UpdateStatus.Current
            }
            is UpdateResult.Error -> {
                logger.warn(result.exception) { "Update check returned an error" }
                _status.value =
                    UpdateStatus.Failed(
                        result.exception.message ?: "Update check failed",
                        retryVersion = retryVersion,
                        retryChannelSwitch = explicitChannelSwitch,
                    )
            }
        }
    }

    private suspend fun refreshLatestChannelVersion(target: UpdateChannel) {
        if (provider != null) return
        try {
            val response = httpClient.get("https://api.github.com/repos/jamhour1g/Azkar-App/releases?per_page=100")
            if (response.status.value !in 200..299) {
                logger.warn {
                    "Unable to retrieve ${target.name.lowercase()} channel version: HTTP ${response.status.value}"
                }
                return
            }
            val releases =
                Json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonArray
                    .mapNotNull { element ->
                        val release = element.jsonObject
                        val version =
                            release["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return@mapNotNull null
                        ChannelReleaseVersion(
                            version = version,
                            isPrerelease = release["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false,
                            isDraft = release["draft"]?.jsonPrimitive?.booleanOrNull ?: false,
                        )
                    }
            if (_channel.value == target) _latestChannelVersion.value = latestChannelVersion(releases, target)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            logger.warn(failure) { "Unable to retrieve ${target.name.lowercase()} channel version" }
        } catch (failure: SerializationException) {
            logger.warn(failure) { "GitHub returned malformed JSON for ${target.name.lowercase()} channel versions" }
        } catch (failure: IllegalArgumentException) {
            logger.warn(
                failure,
            ) { "GitHub returned an unexpected JSON structure for ${target.name.lowercase()} channel versions" }
        } catch (failure: IllegalStateException) {
            logger.warn(
                failure,
            ) { "GitHub returned an invalid field value for ${target.name.lowercase()} channel versions" }
        }
    }

    private suspend fun releaseNotes(version: String): String {
        if (provider != null) return ""
        return runCatchingCancellable {
            val response = httpClient.get("https://api.github.com/repos/jamhour1g/Azkar-App/releases/tags/v$version")
            if (response.status.value !in 200..299) return@runCatchingCancellable ""
            Json
                .parseToJsonElement(response.bodyAsText())
                .jsonObject["body"]
                ?.jsonPrimitive
                ?.content
                .orEmpty()
        }.onFailure { failure -> logger.warn(failure) { "Unable to retrieve update release notes" } }
            .getOrDefault("")
    }

    private fun parseChannel(value: String): UpdateChannel? = runCatching { UpdateChannel.valueOf(value) }.getOrNull()

    private fun defaultChannel(version: String): UpdateChannel =
        if (version.contains("-")) UpdateChannel.DEV else UpdateChannel.STABLE
}

private fun discardStagedUpdate(
    file: Path,
    logger: KLogger,
) {
    try {
        Files.deleteIfExists(file.resolveSibling("${file.fileName}.asc"))
        Files.deleteIfExists(file)
        Files.deleteIfExists(file.parent)
    } catch (failure: IOException) {
        logger.warn(failure) { "Unable to remove staged update $file" }
    }
}

internal fun applicationVersion(useRuntimeVersion: Boolean): String {
    val buildVersion =
        UpdateManager::class.java.classLoader
            .getResourceAsStream("nucleus/nucleus-app.properties")
            ?.use { propertiesStream ->
                java.util
                    .Properties()
                    .apply { load(propertiesStream) }
                    .getProperty("app.version")
            }
    return resolveApplicationVersion(buildVersion, NucleusApp.version.takeIf { useRuntimeVersion })
}

internal fun resolveApplicationVersion(
    buildVersion: String?,
    runtimeVersion: String?,
): String =
    runtimeVersion?.takeIf(String::isNotBlank)
        ?: buildVersion?.takeIf(String::isNotBlank)
        ?: "0.0.0-dev"

private const val INSTALL_ANIMATION_DELAY_MILLIS = 350L
private const val CHECK_FEEDBACK_DELAY_MILLIS = 250L

internal fun compareVersions(
    left: String,
    right: String,
): Int {
    val leftParts = parseVersion(left)
    val rightParts = parseVersion(right)
    return compareCoreVersions(leftParts.core, rightParts.core)
        .takeIf { it != 0 }
        ?: comparePrereleaseVersions(leftParts.prerelease, rightParts.prerelease)
}

internal fun debianPackageVersion(version: String): String = version.replaceFirst('-', '~')

private data class VersionParts(
    val core: List<BigInteger>,
    val prerelease: List<String>?,
)

private fun parseVersion(version: String): VersionParts =
    VersionParts(
        core =
            version
                .substringBefore('-')
                .substringBefore('+')
                .split('.')
                .map { it.toBigIntegerOrNull() ?: BigInteger.ZERO },
        prerelease =
            version
                .substringAfter('-', "")
                .substringBefore('+')
                .takeIf(String::isNotEmpty)
                ?.split('.'),
    )

private fun compareCoreVersions(
    left: List<BigInteger>,
    right: List<BigInteger>,
): Int {
    var result = 0
    for (index in 0 until maxOf(left.size, right.size)) {
        result =
            left
                .getOrElse(index) { BigInteger.ZERO }
                .compareTo(right.getOrElse(index) { BigInteger.ZERO })
        if (result != 0) break
    }
    return result
}

private fun comparePrereleaseVersions(
    left: List<String>?,
    right: List<String>?,
): Int =
    when {
        left == null && right == null -> 0
        left == null -> 1
        right == null -> -1
        else -> comparePrereleaseIdentifiers(left, right)
    }

private fun comparePrereleaseIdentifiers(
    left: List<String>,
    right: List<String>,
): Int {
    var result = 0
    for (index in 0 until maxOf(left.size, right.size)) {
        val leftPart = left.getOrNull(index)
        val rightPart = right.getOrNull(index)
        result =
            when {
                leftPart == null -> -1
                rightPart == null -> 1
                else -> comparePrereleaseIdentifier(leftPart, rightPart)
            }
        if (result != 0) break
    }
    return result
}

private fun comparePrereleaseIdentifier(
    left: String,
    right: String,
): Int {
    val leftNumber = left.toBigIntegerOrNull()
    val rightNumber = right.toBigIntegerOrNull()
    return when {
        leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
        leftNumber != null -> -1
        rightNumber != null -> 1
        else -> left.compareTo(right)
    }
}
