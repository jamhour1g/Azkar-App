package com.azkar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.notificationChannelSystem
import com.azkar.generated.resources.notificationChannelSystemNote
import com.azkar.generated.resources.notificationDuration
import com.azkar.generated.resources.notificationOverlayPolicy
import com.azkar.generated.resources.notificationPlacement
import com.azkar.generated.resources.notificationStackSize
import com.azkar.generated.resources.settingsLanguageBody
import com.azkar.generated.resources.settingsLanguageHeader
import com.azkar.generated.resources.settingsNotificationDesktopTitle
import com.azkar.generated.resources.settingsNotificationOverlayHint
import com.azkar.generated.resources.settingsNotificationOverlayTitle
import com.azkar.generated.resources.settingsNotificationStartupFailed
import com.azkar.generated.resources.settingsNotificationStartupHint
import com.azkar.generated.resources.settingsNotificationStartupTitle
import com.azkar.generated.resources.settingsNotificationsHint
import com.azkar.generated.resources.settingsPrayerCityLabel
import com.azkar.generated.resources.settingsPrayerDetect
import com.azkar.generated.resources.settingsPrayerDetectFailed
import com.azkar.generated.resources.settingsPrayerDetected
import com.azkar.generated.resources.settingsPrayerHint
import com.azkar.generated.resources.settingsPrayerInvalidCoordinates
import com.azkar.generated.resources.settingsPrayerInvalidZone
import com.azkar.generated.resources.settingsPrayerLatitudeLabel
import com.azkar.generated.resources.settingsPrayerLongitudeLabel
import com.azkar.generated.resources.settingsPrayerMethodLabel
import com.azkar.generated.resources.settingsPrayerSave
import com.azkar.generated.resources.settingsPrayerSaved
import com.azkar.generated.resources.settingsPrayerTitle
import com.azkar.generated.resources.settingsPrayerZoneHint
import com.azkar.generated.resources.settingsPrayerZoneLabel
import com.azkar.generated.resources.settingsThemeBody
import com.azkar.generated.resources.settingsThemeDark
import com.azkar.generated.resources.settingsThemeHeader
import com.azkar.generated.resources.settingsThemeLight
import com.azkar.generated.resources.settingsThemeSystem
import com.azkar.i18n.displayNameIn
import com.azkar.i18n.isSameLanguageAs
import com.azkar.i18n.supportedLocales
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarToggleSwitch
import com.azkar.ui.components.HintText
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.components.SectionCard
import com.azkar.ui.components.SelectRow
import com.azkar.ui.components.SettingRow
import com.azkar.ui.components.StepperRow
import com.azkar.ui.components.TextFieldRow
import com.azkar.ui.components.placementLabel
import com.azkar.ui.components.pressableClick
import com.azkar.ui.notifications.LocationNotice
import com.azkar.ui.notifications.NotificationSettingsState
import com.azkar.ui.notifications.PrayerCalculationMethod
import com.azkar.ui.notifications.PrayerLocationSettings
import com.azkar.ui.notifications.ReminderQueueConfig
import com.azkar.ui.notifications.StartupNotice
import com.azkar.ui.notifications.presentation.NotificationPresentationSettings
import com.azkar.ui.notifications.presentation.OverlayPlacement
import com.azkar.ui.preview.rememberPreviewNotificationSettingsState
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/*
 * The three bodies behind the settings tabs.
 *
 * They live beside [SettingsScreen] rather than inside it because that composable now reads as
 * exactly three things — a header, a tab strip, and a `when` — and the alternative was a 600-line
 * function holding five unrelated cards. Split by tab, not by card, so each body is a page in its own
 * right: a tab that has to route to a composable per card is a tab whose tab is meaningless.
 *
 * The notification and prayer bodies moved here from the reminders screen, which used to carry them
 * as two more tabs. Both describe the app rather than any one queue: how an overlay is drawn, whether
 * the tray channel is on, where the app starts, and which city the prayer times come from are all
 * still true when no queue is enabled at all. Keeping them next to the theme and language — the other
 * two preferences that used to be scattered — is the point of the page.
 */

// ---------------------------------------------------------------------------------------------
// General
// ---------------------------------------------------------------------------------------------

/**
 * Appearance and language, the two preferences that were scattered before this page existed.
 *
 * The theme lived on a sun/moon in the header and the language in a modal that could not show both, so
 * neither read as a setting and there was nowhere to put a third.
 */
@Composable
internal fun SettingsGeneralTab(
    state: AzkarAppState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section)) {
        ThemeSection(selected = state.themePreference, onSelect = state::selectTheme)
        LanguageSection(selected = state.locale, onSelect = state::applyLocale)
    }
}

/**
 * The theme, as the three options rather than a toggle.
 *
 * A toggle has to answer "what does pressing this do?" from the glyph alone, and it cannot offer the
 * third option at all — which is how [ThemePreference.SYSTEM], still the launch default, became
 * unreachable once the bar stopped cycling through it. A named choice in a row of three answers the
 * question with the label instead, which is also what lets the system option exist honestly: "System"
 * is not a glyph anyone would have recognised on its own.
 *
 * The glyphs are the header's, unchanged. They were fine; what could not carry the meaning was a single
 * one of them standing for the whole preference.
 */
@Composable
private fun ThemeSection(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = stringResource(Res.string.settingsThemeHeader),
        body = stringResource(Res.string.settingsThemeBody),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.sm)) {
            ThemeOption(
                preference = ThemePreference.SYSTEM,
                label = stringResource(Res.string.settingsThemeSystem),
                icon = AzkarIcons.Compass,
                selected = selected,
                onSelect = onSelect,
            )
            ThemeOption(
                preference = ThemePreference.LIGHT,
                label = stringResource(Res.string.settingsThemeLight),
                icon = AzkarIcons.Sun,
                selected = selected,
                onSelect = onSelect,
            )
            ThemeOption(
                preference = ThemePreference.DARK,
                label = stringResource(Res.string.settingsThemeDark),
                icon = AzkarIcons.Moon,
                selected = selected,
                onSelect = onSelect,
            )
        }
    }
}

/**
 * One theme choice, from the same two parts as the header's toggle: a glyph and now a word for it.
 *
 * [AzkarFilterChip] rather than `PillToggle` because the difference has to be unmistakable. PillToggle
 * marks the selection by tinting its background, which reads as a hover — and here the whole page
 * repaints underneath the user's finger, so a tint is exactly the kind of change that gets mistaken for
 * the palette moving instead of the selection.
 */
@Composable
private fun ThemeOption(
    preference: ThemePreference,
    label: String,
    icon: ImageVector,
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
) {
    AzkarFilterChip(
        text = label,
        selected = preference == selected,
        onClick = { onSelect(preference) },
        leadingIcon = icon,
    )
}

/**
 * The language, as a list rather than a dropdown.
 *
 * Two options behind a dropdown is two clicks and a menu to learn for the sake of a setting people
 * change once, and the list has room for the one thing the dropdown would have had to abbreviate: which
 * language is current. Picking applies immediately, so there is no OK to press and no way to leave
 * having changed nothing — the dialog had both, and both were ways to walk away from a decision.
 *
 * Each option is named by its endonym via [displayNameIn] against the *active* locale rather than
 * translated, which is the one piece of self-description a language list can give that a localised
 * label cannot: "English" written in Arabic is a claim about the option, and "English" written in
 * English is a claim about the reader.
 */
@Composable
private fun LanguageSection(
    selected: Locale,
    onSelect: (Locale) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = AzkarTheme.locale
    SectionCard(
        title = stringResource(Res.string.settingsLanguageHeader),
        body = stringResource(Res.string.settingsLanguageBody),
        modifier = modifier,
    ) {
        supportedLocales.forEach { candidate ->
            LanguageRow(
                label = candidate.displayNameIn(current),
                selected = candidate.isSameLanguageAs(selected),
                onClick = { onSelect(candidate) },
            )
        }
    }
}

/**
 * One language, kept on the fill the dialog gave it.
 *
 * A surface of its own rather than a bare list row, because these rows sit *inside* a card: transparent
 * ones leave nothing to aim at against a background that already reads as a surface. The selection
 * changes weight and colour as well as the fill, since the fill alone is a 5% shift between the two
 * palettes and has to survive being read at a glance.
 */
@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (selected) colors.highlight else Color.Transparent)
                .border(1.dp, if (selected) colors.primary.copy(alpha = 0.45f) else colors.border, shape)
                .pressableClick(scale = 1f, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) colors.primaryStrong else colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------------------------

/*
 * The ranges the overlay settings are bounded by, mirroring the `require` in
 * [NotificationPresentationSettings]. Written out here rather than read off the settings object so the
 * stepper's limits are visible in the same file as the stepper; the config still validates on write, so
 * a stale number here would clamp rather than crash.
 */
private const val MIN_DURATION_SECONDS = 3

private const val MAX_DURATION_SECONDS = 120

private const val MAX_VISIBLE_NOTIFICATIONS = 5

/**
 * How a reminder reaches the user, and when it is due.
 *
 * Four cards rather than one, because these fail differently. An overlay that renders in the wrong
 * corner still appears; a desktop channel that is off means the reminder appears nowhere while the
 * window is behind something else; a startup registration that the platform rejects is silent.
 * Grouping them by *where the reminder appears* rather than by how it is configured is what makes
 * that legible.
 *
 * The schedule leads, because it is the one that decides whether any of the other three have
 * anything to draw. It used to be the top card of the reminders screen, sitting above the very
 * passages it was scheduling — a page whose first answer was "every hour, between eight and ten"
 * and whose last was a search box, with the search box doing nothing until the answer above it was
 * understood. Here it answers first and on its own terms, and what the queue actually contains stays
 * on the page that previews it.
 *
 * What is *not* here is the queue's contents: which passages rotate and in what order are answers to
 * "what will I see", so they belong beside the summary that shows the answer.
 */
@Composable
internal fun SettingsNotificationsTab(
    queue: ReminderQueueConfig,
    presentation: NotificationPresentationSettings,
    startOnLogin: Boolean,
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section)) {
        ReminderScheduleCard(config = queue, schedule = settings.schedule)
        SectionCard(
            title = stringResource(Res.string.settingsNotificationOverlayTitle),
            body = stringResource(Res.string.settingsNotificationOverlayHint),
        ) {
            StepperRow(
                label = stringResource(Res.string.notificationDuration, presentation.durationSeconds),
                value = presentation.durationSeconds,
                onValueChange = settings.notifications::setOverlayDuration,
                range = MIN_DURATION_SECONDS..MAX_DURATION_SECONDS,
            )
            StepperRow(
                label = stringResource(Res.string.notificationStackSize, presentation.maxVisible),
                value = presentation.maxVisible,
                onValueChange = settings.notifications::setOverlayStackSize,
                range = 1..MAX_VISIBLE_NOTIFICATIONS,
            )
            SelectRow(
                label = stringResource(Res.string.notificationPlacement),
                selected = presentation.placement,
                options = OverlayPlacement.entries,
                optionLabel = ::placementLabel,
                onSelect = settings.notifications::setPlacement,
            )
            HintText(stringResource(Res.string.notificationOverlayPolicy))
        }
        DesktopChannelCard(
            enabled = presentation.systemNotificationsEnabled,
            onChange = settings.notifications::setSystemNotifications,
        )
        StartupCard(enabled = startOnLogin, notice = settings.notifications.notice, settings = settings)
    }
}

/**
 * The OS notification channel, which is what a reminder uses when the overlay cannot be drawn.
 *
 * Its note sits under the switch rather than above it: the reason this toggle exists at all is that
 * the overlay is only visible when the Azkar window is on top, so the note has to be read by someone
 * who has already decided they want the other one too.
 */
@Composable
private fun DesktopChannelCard(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = stringResource(Res.string.settingsNotificationDesktopTitle),
        body = stringResource(Res.string.settingsNotificationsHint),
        modifier = modifier,
    ) {
        SettingRow(
            label = stringResource(Res.string.notificationChannelSystem),
            hint = stringResource(Res.string.notificationChannelSystemNote),
        ) {
            AzkarToggleSwitch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

/**
 * Launch at login, with its failure stated rather than swallowed.
 *
 * The switch reports what the platform actually did: on a host with no login manager the preference
 * is not written either, so the row cannot claim to be on because the database says so. That failure
 * text is a [StartupNotice], a different type from the prayer tab's location notices — the two used to
 * share one nullable `locationError` string on the old screen, which is how a failed auto-detect once
 * rendered under the wrong prayer.
 */
@Composable
private fun StartupCard(
    enabled: Boolean,
    notice: StartupNotice?,
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = stringResource(Res.string.settingsNotificationStartupTitle),
        body = stringResource(Res.string.settingsNotificationStartupHint),
        modifier = modifier,
    ) {
        SettingRow(label = stringResource(Res.string.settingsNotificationStartupTitle)) {
            AzkarToggleSwitch(
                checked = enabled,
                onCheckedChange = { settings.notifications.setStartOnLogin(it) },
            )
        }
        StartupNoticeText(notice)
    }
}

/**
 * The startup failure, in the validation colour the rest of the app uses for "this did not work".
 *
 * A separate composable rather than a `when` branch inside [StartupCard] so the notice cannot grow a
 * second case later: this function accepts a [StartupNotice], and a location notice is not one.
 */
@Composable
internal fun StartupNoticeText(
    notice: StartupNotice?,
    modifier: Modifier = Modifier,
) {
    val failure = notice as? StartupNotice.Unavailable ?: return
    val reason = failure.reason?.let { ": $it" }.orEmpty()
    Text(
        text = stringResource(Res.string.settingsNotificationStartupFailed) + reason,
        color = AzkarTheme.colors.dialogValidation,
        fontSize = 12.sp,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------------------------
// Prayer
// ---------------------------------------------------------------------------------------------

/*
 * The dropdown's width on the location card, wider than the shared default because the method names
 * are sentences ("Muslim World League", "Umm al-Qura") and the shared 210dp truncated all of them.
 */
private val LOCATION_FIELD_WIDTH = 230.dp

/**
 * Where the prayer times come from.
 *
 * The five per-prayer switches are *not* here. They live on the home screen, on the row of the prayer
 * they gate, which is the only place the time itself is shown — a switch on a settings page turns a
 * prayer off against a number the reader cannot see, and had to be duplicated on the reminders screen
 * before this moved. [LocationDetector] still fills the city from the network, but the user is not
 * asked to confirm it there.
 */
@Composable
internal fun SettingsPrayerTab(
    location: PrayerLocationSettings,
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section)) {
        SectionCard(
            title = stringResource(Res.string.settingsPrayerTitle),
            body = stringResource(Res.string.settingsPrayerHint),
        ) {
            TextFieldRow(
                label = stringResource(Res.string.settingsPrayerCityLabel),
                value = settings.locationDraft.city,
                onValueChange = { value -> settings.prayer.editDraft { draft -> draft.copy(city = value) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md)) {
                CoordinateField(
                    label = stringResource(Res.string.settingsPrayerLatitudeLabel),
                    value = settings.locationDraft.latitude,
                    onValueChange = { value ->
                        settings.prayer.editDraft { draft -> draft.copy(latitude = value) }
                    },
                    modifier = Modifier.weight(1f),
                )
                CoordinateField(
                    label = stringResource(Res.string.settingsPrayerLongitudeLabel),
                    value = settings.locationDraft.longitude,
                    onValueChange = { value ->
                        settings.prayer.editDraft { draft -> draft.copy(longitude = value) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            TextFieldRow(
                label = stringResource(Res.string.settingsPrayerZoneLabel),
                value = settings.locationDraft.zoneId,
                onValueChange = { value -> settings.prayer.editDraft { draft -> draft.copy(zoneId = value) } },
                supportingText = stringResource(Res.string.settingsPrayerZoneHint),
            )
            SelectRow(
                label = stringResource(Res.string.settingsPrayerMethodLabel),
                selected = location.method,
                options = PrayerCalculationMethod.entries,
                optionLabel = { it.displayName },
                onSelect = settings.prayer::setMethod,
                fieldWidth = LOCATION_FIELD_WIDTH,
            )
            LocationButtons(settings = settings)
            LocationNoticeText(settings.prayer.notice)
        }
    }
}

/**
 * A coordinate, as free text.
 *
 * Free text rather than a pair of steppers because the value is a decimal degree the user may well be
 * copying from somewhere else, and a stepper that moves in 0.001 increments is a slower way to type a
 * number than typing it. [NotificationSettingsState.PrayerEditor.saveLocation] is the only thing that
 * parses it, so a half-typed "-3" is a step towards a coordinate rather than a rejected value.
 */
@Composable
private fun CoordinateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextFieldRow(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
    )
}

/**
 * Save and auto-detect, side by side and in that order.
 *
 * Save first because it is what the user came here to do — the draft is only a draft until it is
 * written. Detect second because it *replaces* what they typed, and a button that overwrites typing
 * should not be the one that reads as the primary action.
 */
@Composable
private fun LocationButtons(
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
    ) {
        PrimaryButton(
            text = stringResource(Res.string.settingsPrayerSave),
            onClick = { settings.prayer.saveLocation() },
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(
            text = stringResource(Res.string.settingsPrayerDetect),
            onClick = { settings.prayer.detectLocation() },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The outcome of saving or detecting a location.
 *
 * Typed as [LocationNotice], which is why the location card cannot render a startup failure and the
 * startup card cannot render a bad coordinate: the two were one nullable string on the reminders
 * screen, and the prayer location row was the thing that displayed "could not update the startup
 * setting" when the login manager refused.
 */
@Composable
internal fun LocationNoticeText(
    notice: LocationNotice?,
    modifier: Modifier = Modifier,
) {
    val text =
        when (notice) {
            null -> null
            LocationNotice.InvalidCoordinates -> stringResource(Res.string.settingsPrayerInvalidCoordinates)
            LocationNotice.InvalidTimeZone -> stringResource(Res.string.settingsPrayerInvalidZone)
            LocationNotice.Saved -> stringResource(Res.string.settingsPrayerSaved)
            is LocationNotice.Detected -> stringResource(Res.string.settingsPrayerDetected, notice.city)
            is LocationNotice.Unavailable ->
                stringResource(Res.string.settingsPrayerDetectFailed) +
                    notice.reason?.let { ": $it" }.orEmpty()
        }
    if (text != null) {
        Text(
            text = text,
            color = AzkarTheme.colors.dialogValidation,
            fontSize = 12.sp,
            modifier = modifier,
        )
    }
}

@Preview
@Composable
private fun SettingsNotificationsTabPreview() {
    AzkarThemePreview {
        Box(modifier = Modifier.width(600.dp).padding(16.dp)) {
            SettingsNotificationsTab(
                queue = ReminderQueueConfig(enabled = true),
                presentation = NotificationPresentationSettings(),
                startOnLogin = true,
                settings = rememberPreviewNotificationSettingsState(),
            )
        }
    }
}

@Preview
@Composable
private fun SettingsPrayerTabPreview() {
    AzkarThemePreview {
        Box(modifier = Modifier.width(600.dp).padding(16.dp)) {
            SettingsPrayerTab(
                location = PrayerLocationSettings(),
                settings = rememberPreviewNotificationSettingsState(),
            )
        }
    }
}
