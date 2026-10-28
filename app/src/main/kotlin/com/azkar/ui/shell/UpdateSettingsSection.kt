package com.azkar.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsUpdateCheck
import com.azkar.generated.resources.settingsUpdateChecking
import com.azkar.generated.resources.settingsUpdateCopyCommands
import com.azkar.generated.resources.settingsUpdateDev
import com.azkar.generated.resources.settingsUpdateDowngradeWarning
import com.azkar.generated.resources.settingsUpdateDownload
import com.azkar.generated.resources.settingsUpdateDownloading
import com.azkar.generated.resources.settingsUpdateInstall
import com.azkar.generated.resources.settingsUpdateInstalling
import com.azkar.generated.resources.settingsUpdateManaged
import com.azkar.generated.resources.settingsUpdateStable
import com.azkar.generated.resources.settingsUpdateTrackHint
import com.azkar.generated.resources.settingsUpdateTrackTitle
import com.azkar.generated.resources.settingsUpdateVersionHint
import com.azkar.generated.resources.settingsUpdateVersionTitle
import com.azkar.generated.resources.settingsUpdatesBody
import com.azkar.generated.resources.settingsUpdatesTitle
import com.azkar.ui.components.AzkarTooltip
import com.azkar.ui.components.SelectRow
import com.azkar.ui.library.setPlainText
import com.azkar.ui.theme.AzkarComponentVariantsPreview
import com.azkar.ui.theme.AzkarTheme
import com.azkar.updates.UpdateChannel
import com.azkar.updates.UpdateManager
import com.azkar.updates.UpdateStatus
import dev.nucleusframework.updater.UpdateFile
import dev.nucleusframework.updater.UpdateInfo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UpdateSettingsSection(manager: UpdateManager) {
    val channel by manager.channel.collectAsState()
    val status by manager.status.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    SettingsSectionBlock(
        title = stringResource(Res.string.settingsUpdatesTitle),
        subtitle = stringResource(Res.string.settingsUpdatesBody),
        content = {
            UpdateSettingsCard(
                state = UpdateSettingsCardState(manager.currentVersion, manager.isRepositoryManaged, channel, status),
                onSelectChannel = { scope.launch { manager.selectChannel(it) } },
                onAction = {
                    scope.launch {
                        when (val current = manager.status.value) {
                            UpdateStatus.Idle, UpdateStatus.Current, is UpdateStatus.Failed -> manager.checkManually()
                            is UpdateStatus.Available ->
                                if (manager.isRepositoryManaged) {
                                    clipboard.setPlainText(
                                        manager.packageManagerCommands(
                                            channel,
                                            current.info.version,
                                            current.isDowngrade,
                                        ),
                                    )
                                } else {
                                    manager.download(current.info)
                                }
                            is UpdateStatus.ReadyToInstall -> manager.installAndRestart(current.file)
                            UpdateStatus.Checking, UpdateStatus.Installing, is UpdateStatus.Downloading -> Unit
                        }
                    }
                },
            )
        },
    )
}

@Composable
private fun UpdateSettingsCard(
    state: UpdateSettingsCardState,
    onSelectChannel: (UpdateChannel) -> Unit,
    onAction: () -> Unit,
) {
    SettingsGroupCard(contentPadding = 0.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            UpdateVersionRow(state.currentVersion, state.isRepositoryManaged, state.status, onAction)
            SettingsRowDivider()
            UpdateTrackSelector(state.channel, onSelectChannel)
            SettingsRowDivider()
            UpdateStatusDetails(state)
        }
    }
}

private data class UpdateSettingsCardState(
    val currentVersion: String,
    val isRepositoryManaged: Boolean,
    val channel: UpdateChannel,
    val status: UpdateStatus,
)

@Composable
private fun UpdateVersionRow(
    currentVersion: String,
    isRepositoryManaged: Boolean,
    status: UpdateStatus,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.settingsUpdateVersionTitle),
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(currentVersion, color = AzkarTheme.colors.textSecondary, fontSize = 14.sp)
            }
            Text(
                stringResource(Res.string.settingsUpdateVersionHint),
                color = AzkarTheme.colors.textSecondary,
                fontSize = 12.sp,
            )
            if (status is UpdateStatus.Failed) {
                Text(status.message, color = AzkarTheme.colors.dialogValidation, fontSize = 12.sp)
            }
        }
        UpdateActionIconButton(status, isRepositoryManaged, onAction)
    }
}

@Composable
private fun UpdateActionIconButton(
    status: UpdateStatus,
    isRepositoryManaged: Boolean,
    onAction: () -> Unit,
) {
    val buttonLabel = updateButtonLabel(status, isRepositoryManaged)
    AzkarTooltip(tooltip = buttonLabel) {
        IconButton(
            onClick = onAction,
            enabled = status.canTriggerUpdateAction(),
            modifier =
                Modifier.size(40.dp).semantics(mergeDescendants = true) {
                    contentDescription = buttonLabel
                },
        ) {
            UpdateActionIndicator(status, isRepositoryManaged)
        }
    }
}

private fun UpdateStatus.canTriggerUpdateAction() =
    this == UpdateStatus.Idle ||
        this == UpdateStatus.Current ||
        this is UpdateStatus.Failed ||
        this is UpdateStatus.Available ||
        this is UpdateStatus.ReadyToInstall

@Composable
private fun updateButtonLabel(
    status: UpdateStatus,
    isRepositoryManaged: Boolean,
): String =
    when (status) {
        UpdateStatus.Idle, UpdateStatus.Current, is UpdateStatus.Failed ->
            stringResource(
                Res.string.settingsUpdateCheck,
            )
        UpdateStatus.Checking -> stringResource(Res.string.settingsUpdateChecking)
        is UpdateStatus.Available ->
            stringResource(
                if (isRepositoryManaged) Res.string.settingsUpdateCopyCommands else Res.string.settingsUpdateDownload,
            )
        is UpdateStatus.Downloading ->
            stringResource(
                Res.string.settingsUpdateDownloading,
                status.version,
                status.percent.toInt(),
            )
        is UpdateStatus.ReadyToInstall -> stringResource(Res.string.settingsUpdateInstall)
        UpdateStatus.Installing -> stringResource(Res.string.settingsUpdateInstalling)
    }

@Composable
private fun UpdateActionIndicator(
    status: UpdateStatus,
    isRepositoryManaged: Boolean,
) {
    when (status) {
        UpdateStatus.Checking, UpdateStatus.Installing ->
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        is UpdateStatus.Downloading ->
            CircularProgressIndicator(
                progress = { (status.percent / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        else ->
            Icon(
                imageVector = updateActionIcon(status, isRepositoryManaged),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
    }
}

private fun updateActionIcon(
    status: UpdateStatus,
    isRepositoryManaged: Boolean,
) = when (status) {
    is UpdateStatus.Available ->
        if (isRepositoryManaged) Icons.Filled.ContentCopy else Icons.Filled.FileDownload
    is UpdateStatus.ReadyToInstall -> Icons.Filled.RestartAlt
    else -> Icons.Filled.Refresh
}

@Composable
private fun UpdateTrackSelector(
    channel: UpdateChannel,
    onSelectChannel: (UpdateChannel) -> Unit,
) {
    SelectRow(
        label = stringResource(Res.string.settingsUpdateTrackTitle),
        selected = channel,
        options = listOf(UpdateChannel.STABLE, UpdateChannel.DEV),
        optionLabel = {
            stringResource(
                if (it == UpdateChannel.STABLE) {
                    Res.string.settingsUpdateStable
                } else {
                    Res.string.settingsUpdateDev
                },
            )
        },
        onSelect = onSelectChannel,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        hint = stringResource(Res.string.settingsUpdateTrackHint),
        fieldWidth = 150.dp,
    )
}

@Composable
private fun UpdateStatusDetails(state: UpdateSettingsCardState) {
    if (state.status is UpdateStatus.Available && state.status.isDowngrade) DowngradeWarning()
    if (state.status is UpdateStatus.ReadyToInstall && state.status.isDowngrade) DowngradeWarning()
    if (state.isRepositoryManaged && state.status is UpdateStatus.Available) {
        Text(
            stringResource(Res.string.settingsUpdateManaged),
            color = AzkarTheme.colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DowngradeWarning() {
    Text(
        stringResource(Res.string.settingsUpdateDowngradeWarning),
        color = AzkarTheme.colors.dialogValidation,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

private fun updatePreviewInfo(): UpdateInfo {
    val file = UpdateFile("https://example.invalid/app", "sha512", 1L, null, "app.tar.gz")
    return UpdateInfo("0.2.0", "2026-10-28", listOf(file), file)
}

@AzkarComponentVariantsPreview
@Composable
internal fun UpdateIdlePreview() = UpdateStatePreview(UpdateStatus.Idle)

@AzkarComponentVariantsPreview
@Composable
internal fun UpdateAvailablePreview() =
    UpdateStatePreview(UpdateStatus.Available(updatePreviewInfo(), "", isDowngrade = false))

@AzkarComponentVariantsPreview
@Composable
internal fun UpdateDownloadingStatePreview() = UpdateStatePreview(UpdateStatus.Downloading("0.2.0", 63.0))

@AzkarComponentVariantsPreview
@Composable
internal fun UpdateFailedPreview() {
    UpdateStatePreview(UpdateStatus.Failed("Unable to connect. Check your connection and try again."))
}

@AzkarComponentVariantsPreview
@Composable
internal fun UpdateReadyPreview() =
    UpdateStatePreview(
        UpdateStatus.ReadyToInstall(
            info = updatePreviewInfo(),
            releaseNotes = "",
            file =
                java.nio.file.Path
                    .of("update-preview"),
            isDowngrade = false,
        ),
    )

@Composable
private fun UpdateStatePreview(status: UpdateStatus) {
    UpdateSettingsCard(
        state = UpdateSettingsCardState("0.1.0", false, UpdateChannel.STABLE, status),
        onSelectChannel = {},
        onAction = {},
    )
}
