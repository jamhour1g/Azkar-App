package com.azkar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsPrayerCalculationHint
import com.azkar.generated.resources.settingsPrayerCalculationTitle
import com.azkar.generated.resources.settingsPrayerCityLabel
import com.azkar.generated.resources.settingsPrayerCurrentLocation
import com.azkar.generated.resources.settingsPrayerDetect
import com.azkar.generated.resources.settingsPrayerDetectFailed
import com.azkar.generated.resources.settingsPrayerDetected
import com.azkar.generated.resources.settingsPrayerInvalidCoordinates
import com.azkar.generated.resources.settingsPrayerInvalidZone
import com.azkar.generated.resources.settingsPrayerLatitudeLabel
import com.azkar.generated.resources.settingsPrayerLocationHint
import com.azkar.generated.resources.settingsPrayerLocationTitle
import com.azkar.generated.resources.settingsPrayerLongitudeLabel
import com.azkar.generated.resources.settingsPrayerManualToggle
import com.azkar.generated.resources.settingsPrayerMethodEgyptian
import com.azkar.generated.resources.settingsPrayerMethodKarachi
import com.azkar.generated.resources.settingsPrayerMethodMuslimWorldLeague
import com.azkar.generated.resources.settingsPrayerMethodNorthAmerica
import com.azkar.generated.resources.settingsPrayerMethodUmmAlQura
import com.azkar.generated.resources.settingsPrayerNoResults
import com.azkar.generated.resources.settingsPrayerPerPrayerHint
import com.azkar.generated.resources.settingsPrayerPlaceSelected
import com.azkar.generated.resources.settingsPrayerSave
import com.azkar.generated.resources.settingsPrayerSaved
import com.azkar.generated.resources.settingsPrayerSearchFailed
import com.azkar.generated.resources.settingsPrayerSearchLabel
import com.azkar.generated.resources.settingsPrayerSearchPlaceholder
import com.azkar.generated.resources.settingsPrayerSearching
import com.azkar.generated.resources.settingsPrayerZoneHint
import com.azkar.generated.resources.settingsPrayerZoneLabel
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.HintText
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.AzkarDropdown
import com.azkar.ui.notifications.LocationNotice
import com.azkar.ui.notifications.NotificationSettingsState
import com.azkar.ui.notifications.PrayerCalculationMethod
import com.azkar.ui.notifications.PrayerLocationSettings
import com.azkar.ui.notifications.PrayerPlace
import com.azkar.ui.preview.rememberPreviewNotificationSettingsState
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.stringResource

// `ui/shell/SettingsPrayerSections.kt` — the two blocks under Prayer: the place, and the convention that
// turns it into times. They were one block headed "Prayer times", whose subtitle said where the times
// came from, and which then held the calculation method as the last row of a card about typing a city and
// a time zone. The subtitle described the card; the card contained one thing the subtitle had not
// mentioned, and that one thing looked like a field of an address.
//
// Both of these are typed answers, which is why the rows put the label above the box instead of beside
// it: a time zone is twenty characters and a coordinate is a signed decimal, and either one in a 210dp
// box beside a sentence is a box too small to type into.

/**
 * Where the prayer times come from.
 *
 * The five per-prayer switches are *not* here. They live on the home screen, on the row of the prayer
 * they gate, which is the only place the time itself is shown — a switch on a settings page turns a
 * prayer off against a number the reader cannot see, and had to be duplicated on the reminders screen
 * before this moved. [LocationDetector] still fills the city from the network, but the user is not asked
 * to confirm it there.
 *
 * The note saying so sits at the foot of the card rather than in the block's subtitle, because it is
 * about the other screen rather than about this block: as a subtitle it read as though the switches were
 * about to be listed here and had simply not been yet.
 */
@Composable
internal fun SettingsPrayerLocationSection(
    location: PrayerLocationSettings,
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    var manualExpanded by remember { mutableStateOf(false) }

    SettingsSectionBlock(
        title = stringResource(Res.string.settingsPrayerLocationTitle),
        subtitle = stringResource(Res.string.settingsPrayerLocationHint),
        modifier = modifier,
    ) {
        SettingsGroupCard {
            CurrentPrayerLocation(location)
            SettingsRowDivider()
            SettingsFieldRow(label = stringResource(Res.string.settingsPrayerSearchLabel)) {
                AzkarOutlinedField(
                    value = settings.prayer.searchQuery,
                    onValueChange = settings.prayer::updateSearchQuery,
                    placeholder = stringResource(Res.string.settingsPrayerSearchPlaceholder),
                    leadingIcon = Icons.Default.Search,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (settings.prayer.isSearching) {
                LocationSearchingText()
            }
            if (settings.prayer.suggestions.isNotEmpty()) {
                SettingsRowDivider()
                LocationSuggestions(
                    places = settings.prayer.suggestions,
                    onSelect = settings.prayer::selectPlace,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AzkarSpacing.sm),
            ) {
                AutoDetectButton(settings = settings)
                ManualLocationDisclosure(
                    expanded = manualExpanded,
                    onToggle = { manualExpanded = !manualExpanded },
                )
            }
            if (manualExpanded) {
                SettingsRowDivider()
                ManualLocationFields(settings = settings)
            }
            LocationNoticeText(settings.prayer.notice)
        }
        Box(modifier = Modifier.padding(top = AzkarSpacing.md, start = 4.dp)) {
            HintText(stringResource(Res.string.settingsPrayerPerPrayerHint))
        }
    }
}

@Composable
private fun CurrentPrayerLocation(location: PrayerLocationSettings) {
    val colors = AzkarTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(colors.highlight, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AzkarIcons.Compass,
                contentDescription = null,
                tint = colors.primaryStrong,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(Res.string.settingsPrayerCurrentLocation),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                location.city.ifBlank {
                    location.zoneId
                },
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (location.city.isNotBlank()) {
                Text(location.zoneId, color = colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

/** Which convention turns the stored coordinates into times. */
@Composable
internal fun SettingsPrayerCalculationSection(
    location: PrayerLocationSettings,
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    SettingsSectionBlock(
        title = stringResource(Res.string.settingsPrayerCalculationTitle),
        subtitle = stringResource(Res.string.settingsPrayerCalculationHint),
        modifier = modifier,
        headingAction = {
            AzkarDropdown(
                selected = location.method,
                options = PrayerCalculationMethod.entries,
                label = { method ->
                    stringResource(
                        when (method) {
                            PrayerCalculationMethod.MUSLIM_WORLD_LEAGUE ->
                                Res.string.settingsPrayerMethodMuslimWorldLeague
                            PrayerCalculationMethod.EGYPTIAN -> Res.string.settingsPrayerMethodEgyptian
                            PrayerCalculationMethod.KARACHI -> Res.string.settingsPrayerMethodKarachi
                            PrayerCalculationMethod.UMM_AL_QURA -> Res.string.settingsPrayerMethodUmmAlQura
                            PrayerCalculationMethod.NORTH_AMERICA -> Res.string.settingsPrayerMethodNorthAmerica
                        },
                    )
                },
                onSelect = settings.prayer::setMethod,
                fieldWidth = CALCULATION_METHOD_FIELD_WIDTH,
            )
        },
    )
}

private val CALCULATION_METHOD_FIELD_WIDTH = 280.dp

/**
 * The manual location form behind the disclosure: a city, a coordinate pair, a time zone, and save.
 *
 * Pulled out of [SettingsPrayerLocationSection] as one unit because it is one thing — every field here
 * reads or writes the same draft, and the reader only reaches it by asking for it. It is also the only
 * part of that block that is conditionally shown, so keeping it separate is what keeps the block's own
 * body about the paths that are always on screen.
 */
@Composable
private fun ManualLocationFields(settings: NotificationSettingsState) {
    SettingsFieldRow(label = stringResource(Res.string.settingsPrayerCityLabel)) {
        AzkarOutlinedField(
            value = settings.locationDraft.city,
            onValueChange = { value -> settings.prayer.editDraft { draft -> draft.copy(city = value) } },
            placeholder = "",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SettingsRowDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md)) {
        PrayerCoordinateField(
            label = stringResource(Res.string.settingsPrayerLatitudeLabel),
            value = settings.locationDraft.latitude,
            onValueChange = { value ->
                settings.prayer.editDraft { draft -> draft.copy(latitude = value) }
            },
            modifier = Modifier.weight(1f),
        )
        PrayerCoordinateField(
            label = stringResource(Res.string.settingsPrayerLongitudeLabel),
            value = settings.locationDraft.longitude,
            onValueChange = { value ->
                settings.prayer.editDraft { draft -> draft.copy(longitude = value) }
            },
            modifier = Modifier.weight(1f),
        )
    }
    SettingsRowDivider()
    SettingsFieldRow(
        label = stringResource(Res.string.settingsPrayerZoneLabel),
        hint = stringResource(Res.string.settingsPrayerZoneHint),
    ) {
        AzkarOutlinedField(
            value = settings.locationDraft.zoneId,
            onValueChange = { value -> settings.prayer.editDraft { draft -> draft.copy(zoneId = value) } },
            placeholder = "",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SettingsRowDivider()
    SaveLocationButton(settings = settings)
}

/**
 * The places the last search matched, as a list of picks.
 *
 * A row per result rather than a dropdown, because the results are the point: a suggestion carries
 * the city, the region and the country at once, and a reader choosing between two Springfields needs
 * to see which is which before tapping. Picking one is the whole interaction — the row fills the
 * fields and writes them, so there is no separate confirm step to miss.
 */
@Composable
private fun LocationSuggestions(
    places: List<PrayerPlace>,
    onSelect: (PrayerPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        places.forEachIndexed { index, place ->
            if (index > 0) SettingsRowDivider()
            Text(
                text = place.label,
                color = AzkarTheme.colors.textPrimary,
                fontSize = 13.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(place) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** A quiet line under the search box while a request is in flight. */
@Composable
private fun LocationSearchingText(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.settingsPrayerSearching),
        color = AzkarTheme.colors.textSecondary,
        fontSize = 12.sp,
        modifier = modifier.padding(start = 16.dp, bottom = 12.dp),
    )
}

/**
 * The disclosure that hides the four typed fields.
 *
 * Collapsed by default, because the search box and the map are now the intended way in and the typed
 * fields are the fallback for a place the geocoder does not know or a machine with no network. Hidden
 * rather than removed: the four fields are the only way to set a location when every lookup fails, so
 * they have to stay reachable — but a settings page that leads with four boxes to type a time zone
 * into is the problem this picker exists to solve.
 */
@Composable
private fun ManualLocationDisclosure(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onToggle, modifier = modifier) {
        Icon(
            ActionIcons.edit,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = AzkarTheme.colors.primaryStrong,
        )
        Spacer(Modifier.width(AzkarSpacing.sm))
        Text(
            text = stringResource(Res.string.settingsPrayerManualToggle),
            color = AzkarTheme.colors.primaryStrong,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(AzkarSpacing.sm))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = AzkarTheme.colors.primaryStrong,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * A coordinate, as free text.
 *
 * Free text rather than a pair of steppers because the value is a decimal degree the user may well be
 * copying from somewhere else, and a stepper that moves in 0.001 increments is a slower way to type a
 * number than typing it. `NotificationSettingsState.PrayerEditor.saveLocation` is the only thing that
 * parses it, so a half-typed "-3" is a step towards a coordinate rather than a rejected value.
 */
@Composable
private fun PrayerCoordinateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsFieldRow(label = label, modifier = modifier) {
        AzkarOutlinedField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Writes the typed fields. Lives inside the manual disclosure, beside the fields it saves. */
@Composable
private fun SaveLocationButton(
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PrimaryButton(
            text = stringResource(Res.string.settingsPrayerSave),
            onClick = { settings.prayer.saveLocation() },
        )
    }
}

/** Available beside manual entry so either way to change location is visible before expanding fields. */
@Composable
private fun AutoDetectButton(
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    SecondaryButton(
        text = stringResource(Res.string.settingsPrayerDetect),
        onClick = { settings.prayer.detectLocation() },
        leadingIcon = AzkarIcons.Compass,
        modifier = modifier,
    )
}

/**
 * The outcome of saving, searching or detecting a location.
 *
 * Typed as [LocationNotice], which is why the location rows cannot render a startup failure and the
 * startup row cannot render a bad coordinate. The notice sits below the buttons rather than under the
 * field it is about, because it reports on the last *action*: "Location saved" says nothing about the
 * city field specifically, and putting it there would imply it did.
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
            is LocationNotice.SearchFailed ->
                stringResource(Res.string.settingsPrayerSearchFailed) +
                    notice.reason?.let { ": $it" }.orEmpty()
            LocationNotice.NoResults -> stringResource(Res.string.settingsPrayerNoResults)
            is LocationNotice.PlaceSelected -> stringResource(Res.string.settingsPrayerPlaceSelected, notice.city)
        }
    if (text != null) {
        Text(
            text = text,
            color =
                when (notice) {
                    LocationNotice.Saved, is LocationNotice.Detected, is LocationNotice.PlaceSelected ->
                        AzkarTheme.colors.primaryStrong
                    else -> AzkarTheme.colors.dialogValidation
                },
            fontSize = 12.sp,
            modifier = modifier.padding(start = 16.dp, bottom = 12.dp),
        )
    }
}

@Preview
@Composable
private fun SettingsPrayerSectionsPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(820.dp).padding(AzkarSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
        ) {
            SettingsPrayerLocationSection(
                location = PrayerLocationSettings(),
                settings = rememberPreviewNotificationSettingsState(),
            )
            SettingsPrayerCalculationSection(
                location = PrayerLocationSettings(),
                settings = rememberPreviewNotificationSettingsState(),
            )
        }
    }
}
