package com.azkar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsBack
import com.azkar.generated.resources.settingsSubtitle
import com.azkar.generated.resources.settingsTabGeneral
import com.azkar.generated.resources.settingsTabNotifications
import com.azkar.generated.resources.settingsTabPrayer
import com.azkar.generated.resources.settingsTitle
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarOutlinedButton
import com.azkar.ui.components.AzkarTabStrip
import com.azkar.ui.notifications.LocationDetector
import com.azkar.ui.notifications.NotificationPreferencesRepository
import com.azkar.ui.notifications.NotificationSettingsState
import com.azkar.ui.notifications.PrayerLocationSettings
import com.azkar.ui.notifications.ReminderQueueConfig
import com.azkar.ui.notifications.StartupRegistration
import com.azkar.ui.notifications.presentation.NotificationPresentationSettings
import com.azkar.ui.preview.previewAzkarAppState
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * How wide the settings column is allowed to get.
 *
 * The dashboard's cards fill the window because they hold a hero image, two columns of text and a
 * prayer table — there is always enough inside them to justify the width. A settings list is one short
 * line per row, so stretched to 1440dp it reads as a page of content that ran out, and the eye has
 * 600dp of empty card to cross to get from a heading to the control under it. Capped near the measure
 * the dialogs use (`widthIn(max = 700.dp)`), so the screen they grew out of and the screen they became
 * have the same comfortable width and the window's extra space is left as margin.
 */
private val SETTINGS_MAX_WIDTH = 760.dp

private const val TAB_GENERAL = 0

private const val TAB_NOTIFICATIONS = 1

private const val TAB_PRAYER = 2

/**
 * The screen the gear opens, holding the app-level preferences.
 *
 * The theme lived on a sun/moon in the header and the language in a modal that could not show both, so
 * neither read as a setting and there was nowhere to put a third. Both live here now, which is why
 * this is a [AzkarDestination] rather than the dialog it replaced: a modal has nowhere to grow, and
 * it is dismissed by the very click that changes its contents.
 *
 * It is a tabbed page now because it outgrew one column. Appearance and language were the whole screen
 * at 760dp; the notification presentation settings and the prayer location that the reminders screen
 * used to carry make roughly double that, and a single scroll that long buries the language picker
 * under two cards of unrelated switches. Three tabs is the point at which one column is no longer the
 * honest shape of the page — two would still scroll comfortably.
 *
 * State arrives rather than being held here. The theme and locale are already on [AzkarAppState] — they
 * have to be, since they outlive the window — and the notification and prayer values come from the same
 * [NotificationPreferencesRepository] the runtime reads, so this composable holds no field of its own
 * beyond which tab is showing.
 *
 * The layout is the dashboard's: a scrolling box over one full-width column, padded by the same screen
 * tokens, so settings sits at the same measure as home and library instead of reading as a dialog that
 * got tall.
 */
@Composable
fun SettingsScreen(
    state: AzkarAppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notificationState = rememberNotificationSettingsState()
    val notificationValues = rememberNotificationSettingsValues()
    var selectedTab by rememberSaveable { mutableStateOf(TAB_GENERAL) }
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = SETTINGS_MAX_WIDTH)
                    .padding(
                        start = AzkarSpacing.screenPaddingHorizontal,
                        end = AzkarSpacing.screenPaddingHorizontal,
                        top = AzkarSpacing.screenPaddingTop,
                        bottom = AzkarSpacing.screenPaddingBottom,
                    ),
            verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
        ) {
            SettingsHeader(onClose = onClose)
            AzkarTabStrip(tabs = settingsTabs(), selectedIndex = selectedTab, onSelect = { selectedTab = it })
            SettingsTabBody(selectedTab, state, notificationValues, notificationState)
        }
    }
}

/**
 * The three sections, named by index.
 *
 * Order is General, Notifications, Prayer: the first two are preferences about the app and its
 * reminders, the third is a location the app reads from and belongs to no other setting, so it reads
 * as the odd one out at the end rather than interrupting the two that are related.
 */
@Composable
private fun settingsTabs(): List<String> =
    listOf(
        stringResource(Res.string.settingsTabGeneral),
        stringResource(Res.string.settingsTabNotifications),
        stringResource(Res.string.settingsTabPrayer),
    )

/**
 * Which tab is showing, and the state that tab needs.
 *
 * Split out purely for length: the routing itself is three lines, but inlining it pushed
 * [SettingsScreen] past the point where it was readable as a layout. Nothing here holds state — the
 * tab index stays in the caller because it is the thing the tab strip writes to.
 */
@Composable
private fun SettingsTabBody(
    selectedTab: Int,
    state: AzkarAppState,
    values: NotificationSettingsValues,
    notificationState: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    when (selectedTab) {
        TAB_NOTIFICATIONS ->
            SettingsNotificationsTab(
                queue = values.queue,
                presentation = values.presentation,
                startOnLogin = values.startOnLogin,
                settings = notificationState,
                modifier = modifier,
            )
        TAB_PRAYER ->
            SettingsPrayerTab(
                location = values.prayerLocation,
                settings = notificationState,
                modifier = modifier,
            )
        else -> SettingsGeneralTab(state = state, modifier = modifier)
    }
}

/**
 * The holder behind the two moved tabs.
 *
 * Both of the platform capabilities are injected here rather than taken from a parameter: the settings
 * page is the only screen that needs them, and a screen that is handed a detector it does not own
 * would let a caller pass one that is not the one the runtime uses. They are still nullable on the
 * holder so a preview host with no network and no login manager still draws every row.
 */
@Composable
private fun rememberNotificationSettingsState(): NotificationSettingsState {
    val preferences = koinInject<NotificationPreferencesRepository>()
    val locationDetector = koinInject<LocationDetector>()
    val startupRegistration = koinInject<StartupRegistration>()
    val scope = rememberCoroutineScope()
    return remember(preferences, locationDetector, startupRegistration) {
        NotificationSettingsState(
            scope = scope,
            preferences = preferences,
            locationDetector = locationDetector,
            startupRegistration = startupRegistration,
        )
    }
}

/**
 * Everything persisted on the moved tabs, snapshotted into one value.
 *
 * Four `collectAsState` calls would have left four locals to thread by hand into the tab bodies, and
 * a single immutable holder means a tab only recomposes when the settings it actually reads change —
 * the prayer tab does not recompose when the start-on-login flag is toggled on the one beside it.
 *
 * [queue] joined them when the reminder schedule card moved here from the reminders screen. It is the
 * one value on this page the reminders screen also draws, which is the point of reading it from the
 * repository rather than from a local: the summary at the top of that screen and the card at the top
 * of this one are two readings of one record, and neither holds a copy.
 */
@Immutable
private data class NotificationSettingsValues(
    val queue: ReminderQueueConfig,
    val prayerLocation: PrayerLocationSettings,
    val startOnLogin: Boolean,
    val presentation: NotificationPresentationSettings,
)

@Composable
private fun rememberNotificationSettingsValues(): NotificationSettingsValues {
    val preferences = koinInject<NotificationPreferencesRepository>()
    val queue by preferences.queue.collectAsState()
    val prayerLocation by preferences.prayerLocation.collectAsState()
    val startOnLogin by preferences.startOnLogin.collectAsState()
    val presentation by preferences.presentation.collectAsState()
    return NotificationSettingsValues(queue, prayerLocation, startOnLogin, presentation)
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    AzkarThemePreview {
        // Assembled from the parts rather than by calling [SettingsScreen], because that composable
        // reaches Koin for the moved tabs' holder and a preview has no container to resolve it
        // against. The two tabs below it each carry their own preview for the same reason.
        Column(
            modifier =
                Modifier
                    .widthIn(max = SETTINGS_MAX_WIDTH)
                    .padding(AzkarSpacing.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
        ) {
            SettingsHeader(onClose = {})
            AzkarTabStrip(
                tabs = listOf("General", "Notifications", "Prayer"),
                selectedIndex = TAB_GENERAL,
                onSelect = {},
            )
            SettingsGeneralTab(state = previewAzkarAppState())
        }
    }
}

/**
 * The screen's identity and its way out.
 *
 * The badge and the title-over-subtitle pairing are `LibraryHeader`'s, deliberately: the two screens
 * are siblings, and a header that is a different shape on one of them makes the app look like it has a
 * third place in it.
 *
 * [onClose] pops rather than navigating home. Settings is pushed onto whichever screen opened it, so
 * going home instead would throw away the library the user had open — a "Back" that changes screen is a
 * "Home" wearing the wrong label.
 */
@Composable
private fun SettingsHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AzkarIcons.Settings,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settingsTitle),
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.settingsSubtitle),
                color = colors.textSecondary,
                fontSize = 13.sp,
            )
        }
        AzkarOutlinedButton(text = stringResource(Res.string.settingsBack), onClick = onClose)
    }
}
