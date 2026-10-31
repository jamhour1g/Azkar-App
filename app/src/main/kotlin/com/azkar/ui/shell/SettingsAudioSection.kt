package com.azkar.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsAudioChoose
import com.azkar.generated.resources.settingsAudioFailed
import com.azkar.generated.resources.settingsAudioHint
import com.azkar.generated.resources.settingsAudioNotification
import com.azkar.generated.resources.settingsAudioNotificationFile
import com.azkar.generated.resources.settingsAudioPrayer
import com.azkar.generated.resources.settingsAudioPrayerFile
import com.azkar.generated.resources.settingsAudioPreview
import com.azkar.generated.resources.settingsAudioStop
import com.azkar.generated.resources.settingsAudioStopPreview
import com.azkar.generated.resources.settingsAudioTitle
import com.azkar.generated.resources.settingsAudioVolume
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.notifications.AudioPreferences
import com.azkar.ui.notifications.DesktopAudioPlayer
import com.azkar.ui.notifications.NotificationSettingsState
import com.azkar.ui.preview.rememberPreviewNotificationSettingsState
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarVariantsPreview
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@AzkarVariantsPreview
@Composable
internal fun SettingsAudioSectionPreview() {
    SettingsAudioSection(
        audio =
            AudioPreferences(
                notificationsEnabled = true,
                notificationFile = "/home/reader/Music/reminder.mp3",
                prayerEnabled = true,
                prayerFile = "/home/reader/Music/adhan.mp3",
            ),
        settings = rememberPreviewNotificationSettingsState(),
        player = remember { DesktopAudioPlayer() },
        modifier = Modifier.width(1040.dp).padding(AzkarSpacing.screenPaddingHorizontal),
    )
}

@Composable
internal fun SettingsAudioSection(
    audio: AudioPreferences,
    settings: NotificationSettingsState,
    player: DesktopAudioPlayer,
    modifier: Modifier = Modifier,
) {
    val previewPlayer = remember { DesktopAudioPlayer() }
    ReleasePreviewPlayer(previewPlayer)
    SettingsSectionBlock(
        title = stringResource(Res.string.settingsAudioTitle),
        subtitle = stringResource(Res.string.settingsAudioHint),
        modifier = modifier,
    ) {
        AudioChoiceLayout(audio, settings, previewPlayer, player)
    }
}

@Composable
private fun AudioChoiceLayout(
    audio: AudioPreferences,
    settings: NotificationSettingsState,
    previewPlayer: DesktopAudioPlayer,
    player: DesktopAudioPlayer,
) {
    BoxWithConstraints {
        val notificationChoice: @Composable (Modifier) -> Unit = { choiceModifier ->
            AudioChoice(
                modifier = choiceModifier,
                choice =
                    AudioChoiceModel(
                        title = stringResource(Res.string.settingsAudioNotification),
                        pathLabel = stringResource(Res.string.settingsAudioNotificationFile),
                        enabled = audio.notificationsEnabled,
                        path = audio.notificationFile,
                        volume = audio.notificationVolume,
                        onEnabled = { enabled -> settings.updateAudio { it.copy(notificationsEnabled = enabled) } },
                        onPath = { path -> settings.updateAudio { it.copy(notificationFile = path) } },
                        onVolume = { volume -> settings.updateAudio { it.copy(notificationVolume = volume) } },
                        onPreview = { onError ->
                            previewPlayer.playNotification(audio.notificationFile, audio.notificationVolume, onError)
                        },
                        onChoose = { path -> settings.updateAudio { it.copy(notificationFile = path) } },
                        onStop = { previewPlayer.release() },
                        stopLabel = stringResource(Res.string.settingsAudioStopPreview),
                    ),
            )
        }
        val prayerChoice: @Composable (Modifier) -> Unit = { choiceModifier ->
            AudioChoice(
                modifier = choiceModifier,
                choice =
                    AudioChoiceModel(
                        title = stringResource(Res.string.settingsAudioPrayer),
                        pathLabel = stringResource(Res.string.settingsAudioPrayerFile),
                        enabled = audio.prayerEnabled,
                        path = audio.prayerFile,
                        volume = audio.prayerVolume,
                        onEnabled = settings::setAllPrayerSoundsEnabled,
                        onPath = { path -> settings.updateAudio { it.copy(prayerFile = path) } },
                        onVolume = { volume -> settings.updateAudio { it.copy(prayerVolume = volume) } },
                        onPreview = { onError ->
                            previewPlayer.playPrayer(audio.prayerFile, audio.prayerVolume, onError)
                        },
                        onChoose = { path -> settings.updateAudio { it.copy(prayerFile = path) } },
                        onStop = {
                            previewPlayer.release()
                            player.stopPrayer()
                        },
                    ),
            )
        }
        ResponsiveAudioChoices(maxWidth, notificationChoice, prayerChoice)
    }
}

@Composable
private fun ResponsiveAudioChoices(
    width: Dp,
    notificationChoice: @Composable (Modifier) -> Unit,
    prayerChoice: @Composable (Modifier) -> Unit,
) {
    if (width >= 960.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md)) {
            notificationChoice(Modifier.weight(1f))
            prayerChoice(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AzkarSpacing.md)) {
            notificationChoice(Modifier.fillMaxWidth())
            prayerChoice(Modifier.fillMaxWidth())
        }
    }
}

internal data class AudioChoiceModel(
    val title: String,
    val pathLabel: String,
    val enabled: Boolean,
    val path: String,
    val volume: Float,
    val onEnabled: (Boolean) -> Unit,
    val onPath: (String) -> Unit,
    val onVolume: (Float) -> Unit,
    val onPreview: suspend (onError: (Throwable) -> Unit) -> Unit,
    val onChoose: (String) -> Unit,
    val onStop: (suspend () -> Unit)? = null,
    val stopLabel: String? = null,
)

@Composable
private fun ReleasePreviewPlayer(player: DesktopAudioPlayer) {
    LaunchedEffect(player) {
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { player.release() }
        }
    }
}

@Composable
internal fun AudioChoice(
    modifier: Modifier = Modifier,
    choice: AudioChoiceModel,
) {
    var previewFailed by remember(choice.path) { mutableStateOf(false) }
    SettingsGroupCard(modifier = modifier, contentPadding = 0.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AzkarSpacing.xl, vertical = AzkarSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
        ) {
            AudioChoiceHeader(choice)
            AudioFileSelector(choice)
            AudioVolumeControl(choice)
            if (previewFailed) {
                Text(stringResource(Res.string.settingsAudioFailed), color = AzkarTheme.colors.dialogValidation)
            }
            AudioPreviewButtons(choice, onFailure = { previewFailed = it })
        }
    }
}

@Composable
private fun AudioChoiceHeader(choice: AudioChoiceModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(choice.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Switch(checked = choice.enabled, onCheckedChange = choice.onEnabled)
    }
}

@Composable
private fun AudioFileSelector(choice: AudioChoiceModel) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = choice.path,
            onValueChange = choice.onPath,
            label = { Text(choice.pathLabel) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TooltipIconButton(
            icon = Icons.Filled.FolderOpen,
            tooltip = stringResource(Res.string.settingsAudioChoose),
            onClick = {
                scope.launch {
                    runCatching {
                        FileKit.openFilePicker(
                            type = FileKitType.File("mp3", "wav", "aif", "aiff", "m4a", "mp4"),
                            dialogSettings = FileKitDialogSettings(title = choice.title),
                        )
                    }.getOrNull()?.file?.absolutePath?.let(choice.onChoose)
                }
            },
            buttonSize = 36.dp,
            iconSize = 20.dp,
            tint = AzkarTheme.colors.primaryStrong,
        )
    }
}

@Composable
private fun AudioVolumeControl(choice: AudioChoiceModel) {
    Column(verticalArrangement = Arrangement.spacedBy(AzkarSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.settingsAudioVolume), color = AzkarTheme.colors.textSecondary)
            Text("${(choice.volume * 100).roundToInt()}%", color = AzkarTheme.colors.textSecondary)
        }
        Slider(value = choice.volume, onValueChange = choice.onVolume, valueRange = 0f..1f)
    }
}

@Composable
private fun AudioPreviewButtons(
    choice: AudioChoiceModel,
    onFailure: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.xs)) {
        TooltipIconButton(
            icon = Icons.Filled.PlayArrow,
            tooltip = stringResource(Res.string.settingsAudioPreview),
            onClick = {
                scope.launch {
                    onFailure(false)
                    try {
                        choice.onPreview { scope.launch { onFailure(true) } }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: RuntimeException) {
                        onFailure(true)
                    }
                }
            },
            enabled = choice.path.isNotBlank(),
            buttonSize = 36.dp,
            iconSize = 20.dp,
            tint = AzkarTheme.colors.primaryStrong,
        )
        choice.onStop?.let { stop ->
            TooltipIconButton(
                icon = Icons.Filled.Stop,
                tooltip = choice.stopLabel ?: stringResource(Res.string.settingsAudioStop),
                onClick = {
                    scope.launch {
                        try {
                            stop()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: RuntimeException) {
                            onFailure(true)
                        }
                    }
                },
                buttonSize = 36.dp,
                iconSize = 20.dp,
            )
        }
    }
}
