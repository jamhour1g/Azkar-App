package com.azkar.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.prayerNotificationTurnOff
import com.azkar.generated.resources.prayerNotificationTurnOn
import com.azkar.generated.resources.prayerSoundTurnOff
import com.azkar.generated.resources.prayerSoundTurnOn
import com.azkar.generated.resources.settingsPrayerAlertsHint
import com.azkar.generated.resources.settingsPrayerAlertsTitle
import com.azkar.generated.resources.settingsPrayerNotifications
import com.azkar.generated.resources.settingsPrayerSound
import com.azkar.ui.home.PrayerKey
import com.azkar.ui.home.nameResource
import com.azkar.ui.notifications.AudioPreferences
import com.azkar.ui.notifications.NotificationSettingsState
import com.azkar.ui.preview.rememberPreviewNotificationSettingsState
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarVariantsPreview
import org.jetbrains.compose.resources.stringResource

@AzkarVariantsPreview
@Composable
internal fun SettingsPrayerAlertsSectionPreview() {
    SettingsPrayerAlertsSection(
        prayerNotifications = PrayerKey.entries.associateWith { true },
        audio = AudioPreferences(),
        settings = rememberPreviewNotificationSettingsState(),
        modifier = Modifier.width(820.dp),
    )
}

@Composable
internal fun SettingsPrayerAlertsSection(
    prayerNotifications: Map<PrayerKey, Boolean>,
    audio: AudioPreferences,
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    SettingsSectionBlock(
        title = stringResource(Res.string.settingsPrayerAlertsTitle),
        subtitle = stringResource(Res.string.settingsPrayerAlertsHint),
        modifier = modifier,
    ) {
        SettingsGroupCard(
            contentPadding = 12.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f))
                PrayerAlertColumnHeader(stringResource(Res.string.settingsPrayerNotifications))
                PrayerAlertColumnHeader(stringResource(Res.string.settingsPrayerSound))
            }
            PrayerKey.entries.forEachIndexed { index, key ->
                if (index > 0) SettingsRowDivider()
                PrayerAlertRow(
                    key = key,
                    notificationEnabled = prayerNotifications[key] ?: true,
                    soundEnabled = audio.isPrayerSoundEnabled(key),
                    onNotificationChanged = { settings.setPrayerNotificationEnabled(key, it) },
                    onSoundChanged = { settings.setPrayerSoundEnabled(key, it) },
                )
            }
        }
    }
}

@Composable
private fun PrayerAlertColumnHeader(label: String) {
    Box(modifier = Modifier.width(92.dp), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = AzkarTheme.colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PrayerAlertRow(
    key: PrayerKey,
    notificationEnabled: Boolean,
    soundEnabled: Boolean,
    onNotificationChanged: (Boolean) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
) {
    val name = stringResource(key.nameResource)
    val notificationLabel =
        stringResource(
            if (notificationEnabled) Res.string.prayerNotificationTurnOff else Res.string.prayerNotificationTurnOn,
            name,
        )
    val soundLabel =
        stringResource(
            if (soundEnabled) Res.string.prayerSoundTurnOff else Res.string.prayerSoundTurnOn,
            name,
        )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.width(92.dp), contentAlignment = Alignment.Center) {
            Switch(
                checked = notificationEnabled,
                onCheckedChange = onNotificationChanged,
                modifier = Modifier.semantics { contentDescription = notificationLabel },
            )
        }
        Box(modifier = Modifier.width(92.dp), contentAlignment = Alignment.Center) {
            Switch(
                checked = soundEnabled,
                onCheckedChange = onSoundChanged,
                modifier = Modifier.semantics { contentDescription = soundLabel },
            )
        }
    }
}
