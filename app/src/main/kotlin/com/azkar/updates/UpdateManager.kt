package com.azkar.updates

import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore
import dev.nucleusframework.core.runtime.ExecutableRuntime
import dev.nucleusframework.core.runtime.ExecutableType
import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Path
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
    ) : UpdateStatus
}

class UpdateManager(
    private val settings: SettingsStore,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
    private val logger: KLogger = KotlinLogging.logger {},
) {
    private val preferenceKey = stringPreferencesKey("updates.channel")
    private val initialized = CompletableDeferred<Unit>()
    private val runningVersion = applicationVersion()
    private val checkMutex = Mutex()
    private val _channel = MutableStateFlow(defaultChannel(runningVersion))
    private val _latestChannelVersion = MutableStateFlow<String?>(null)
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    private var activeUpdater: NucleusUpdater? = null

    val channel: StateFlow<UpdateChannel> = _channel.asStateFlow()
    val latestChannelVersion: StateFlow<String?> = _latestChannelVersion.asStateFlow()
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()
    val currentVersion: String get() = runningVersion
    val isRepositoryManaged: Boolean
        get() = ExecutableRuntime.type() in setOf(ExecutableType.DEB, ExecutableType.RPM)

    init {
        scope.launch {
            try {
                settings.read(preferenceKey)?.let(::parseChannel)?.let { _channel.value = it }
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
        logger.debug { "Checking for updates automatically" }
        val target = _channel.value
        scope.launch { refreshLatestChannelVersion(target) }
        checkForUpdates(target, explicitChannelSwitch = false)
    }

    suspend fun selectChannel(target: UpdateChannel) {
        initialized.await()
        if (target == _channel.value) return
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
        _status.value = UpdateStatus.Checking
        delay(CHECK_FEEDBACK_DELAY_MILLIS)
        initialized.await()
        checkForUpdates(_channel.value, explicitChannelSwitch = false)
    }

    fun packageManagerCommands(
        target: UpdateChannel,
        version: String?,
        downgrade: Boolean,
    ): String {
        val suite = if (target == UpdateChannel.DEV) "dev" else "stable"
        return when (ExecutableRuntime.type()) {
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
                ?.takeUnless { isRepositoryManaged }
                ?.let { updater -> (_status.value as? UpdateStatus.Available)?.let { updater to it } }
                ?: return
        logger.info { "Downloading update ${info.version}" }
        try {
            updater.downloadUpdate(info).collect { progress ->
                progress.file?.let { downloadedFile ->
                    logger.info { "Update ${info.version} downloaded and is ready to install" }
                    _status.value =
                        UpdateStatus.ReadyToInstall(
                            info = info,
                            releaseNotes = available.releaseNotes,
                            file = downloadedFile.toPath(),
                            isDowngrade = available.isDowngrade,
                        )
                } ?: run {
                    _status.value = UpdateStatus.Downloading(info.version, progress.percent)
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            logger.error(failure) { "Unable to download update" }
            _status.value = UpdateStatus.Failed(failure.message ?: "Download failed")
        } catch (failure: IllegalStateException) {
            logger.error(failure) { "Unable to download update" }
            _status.value = UpdateStatus.Failed(failure.message ?: "Download failed")
        }
    }

    suspend fun installAndRestart(file: Path) {
        val updater = activeUpdater ?: return
        logger.info { "Starting update installation" }
        _status.value = UpdateStatus.Installing
        delay(INSTALL_ANIMATION_DELAY_MILLIS)
        try {
            updater.installAndRestart(file.toFile())
        } catch (failure: IOException) {
            logger.error(failure) { "Unable to start update installation" }
            _status.value = UpdateStatus.Failed(failure.message ?: "Update installation failed")
        } catch (failure: IllegalStateException) {
            logger.error(failure) { "Unable to start update installation" }
            _status.value = UpdateStatus.Failed(failure.message ?: "Update installation failed")
        }
    }

    private suspend fun checkForUpdates(
        target: UpdateChannel,
        explicitChannelSwitch: Boolean,
    ) {
        checkMutex.withLock {
            _status.value = UpdateStatus.Checking
            logger.debug { "Checking update track ${target.name.lowercase()} from $runningVersion" }
            val updater =
                NucleusUpdater {
                    currentVersion = runningVersion
                    provider = NucleusGitHubProvider(owner = "jamhour1g", repo = "Azkar-App")
                    channel = target.providerChannel
                    allowPrerelease = target == UpdateChannel.DEV
                    allowDowngrade = explicitChannelSwitch
                }
            activeUpdater = updater
            val result =
                try {
                    updater.checkForUpdates()
                } catch (failure: IOException) {
                    logger.error(failure) { "Update check failed" }
                    _status.value = UpdateStatus.Failed(failure.message ?: "Update check failed")
                    return@withLock
                } catch (failure: IllegalStateException) {
                    logger.error(failure) { "Update check failed" }
                    _status.value = UpdateStatus.Failed(failure.message ?: "Update check failed")
                    return@withLock
                }
            when (result) {
                is UpdateResult.Available -> {
                    if (_channel.value == target) _latestChannelVersion.value = result.info.version
                    val notes = releaseNotes(result.info.version)
                    logger.info { "Update ${result.info.version} is available" }
                    _status.value =
                        UpdateStatus.Available(
                            info = result.info,
                            releaseNotes = notes,
                            isDowngrade = compareVersions(result.info.version, runningVersion) < 0,
                        )
                }
                UpdateResult.NotAvailable -> {
                    logger.info { "No update is available" }
                    _status.value = UpdateStatus.Current
                }
                is UpdateResult.Error -> {
                    logger.warn(result.exception) { "Update check returned an error" }
                    _status.value = UpdateStatus.Failed(result.exception.message ?: "Update check failed")
                }
            }
        }
    }

    private suspend fun refreshLatestChannelVersion(target: UpdateChannel) {
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

    private suspend fun releaseNotes(version: String): String =
        runCatching {
            val response = httpClient.get("https://api.github.com/repos/jamhour1g/Azkar-App/releases/tags/v$version")
            if (response.status.value !in 200..299) return@runCatching ""
            Json
                .parseToJsonElement(response.bodyAsText())
                .jsonObject["body"]
                ?.jsonPrimitive
                ?.content
                .orEmpty()
        }.onFailure { failure -> logger.warn(failure) { "Unable to retrieve update release notes" } }
            .getOrDefault("")

    private fun parseChannel(value: String): UpdateChannel? = runCatching { UpdateChannel.valueOf(value) }.getOrNull()

    private fun defaultChannel(version: String): UpdateChannel =
        if (version.contains("-")) UpdateChannel.DEV else UpdateChannel.STABLE
}

private fun applicationVersion(): String {
    val buildVersion =
        UpdateManager::class.java.classLoader
            .getResourceAsStream("nucleus/nucleus-app.properties")
            ?.use { propertiesStream ->
                java.util
                    .Properties()
                    .apply { load(propertiesStream) }
                    .getProperty("app.version")
            }
    return resolveApplicationVersion(buildVersion, NucleusApp.version)
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
