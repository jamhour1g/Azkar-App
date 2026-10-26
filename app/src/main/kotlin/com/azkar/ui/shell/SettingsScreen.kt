package com.azkar.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsSubtitle
import com.azkar.generated.resources.settingsTitle
import com.azkar.generated.resources.settingsUpdateAvailable
import com.azkar.generated.resources.settingsUpdateChecking
import com.azkar.generated.resources.settingsUpdateCopyCommands
import com.azkar.generated.resources.settingsUpdateDev
import com.azkar.generated.resources.settingsUpdateDowngradeWarning
import com.azkar.generated.resources.settingsUpdateDownload
import com.azkar.generated.resources.settingsUpdateDownloading
import com.azkar.generated.resources.settingsUpdateIdle
import com.azkar.generated.resources.settingsUpdateInstall
import com.azkar.generated.resources.settingsUpdateInstalling
import com.azkar.generated.resources.settingsUpdateManaged
import com.azkar.generated.resources.settingsUpdateNoReleaseNotes
import com.azkar.generated.resources.settingsUpdateReleaseNotes
import com.azkar.generated.resources.settingsUpdateStable
import com.azkar.generated.resources.settingsUpdateStatusAvailable
import com.azkar.generated.resources.settingsUpdateStatusChecking
import com.azkar.generated.resources.settingsUpdateStatusFailed
import com.azkar.generated.resources.settingsUpdateStatusReady
import com.azkar.generated.resources.settingsUpdateStatusUpToDate
import com.azkar.generated.resources.settingsUpdateTrackHint
import com.azkar.generated.resources.settingsUpdateTrackTitle
import com.azkar.generated.resources.settingsUpdateVersionHint
import com.azkar.generated.resources.settingsUpdateVersionTitle
import com.azkar.generated.resources.settingsUpdatesBody
import com.azkar.generated.resources.settingsUpdatesTitle
import com.azkar.ui.components.SelectRow
import com.azkar.ui.library.setPlainText
import com.azkar.ui.notifications.GeocodingClient
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
import com.azkar.updates.UpdateChannel
import com.azkar.updates.UpdateManager
import com.azkar.updates.UpdateStatus
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

// `ui/shell/SettingsScreen.kt` — the screen the gear opens: a rail that names every block on this page
// under the family it belongs to, and one long scroll holding them all in the same order.

/** How much of the page the rail's edge takes, as a hairline rather than a shadow. */
private val RAIL_RULE_WIDTH = 1.dp

/** The scroll container's own inset. Wider than the dashboard's, because the pane is narrower. */
private val PAGE_PADDING =
    PaddingValues(
        start = 40.dp,
        end = 40.dp,
        top = AzkarSpacing.screenPaddingTop,
        bottom = AzkarSpacing.screenPaddingBottom,
    )

/**
 * The app-level preferences.
 *
 * It is one scroll rather than a tab strip because a strip hides the shape of the page. The reminders
 * blocks together run several times the height of the language block, so "Notifications" read as a peer of
 * "Language" and gave no hint that choosing it was a longer scroll than the others — and a reader who
 * wanted only the prayer city had to open the last tab to find out. Stacked, the length *is* the
 * information: the reader sees that the reminder settings are the big ones before choosing anything.
 *
 * The rail is what replaces the strip's job. It names every block under the family it is filed in — the
 * four families partition the page, so a row's parent is always the answer to "what kind of setting is
 * this" — jumps to it, and marks the one the reader is inside, so the page still has a table of contents
 * without spending the top of the window on a few buttons that hide everything.
 *
 * State arrives rather than being held here. The theme and locale are already on [AzkarAppState] — they
 * have to be, since they outlive the window — and the notification and prayer values come from the same
 * [NotificationPreferencesRepository] the runtime reads, so this composable holds no field of its own
 * beyond the scroll position.
 */
@Composable
fun SettingsScreen(
    state: AzkarAppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialGroup: SettingsGroup = SettingsGroup.GENERAL,
) {
    val notificationState = rememberNotificationSettingsState()
    val notificationValues = rememberNotificationSettingsValues()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableStateOf(initialGroup.sections.first()) }
    val selectedGroup = selectedSection.group
    val activeSection = activeSettingsSection(listState, selectedSection)
    val colors = AzkarTheme.colors
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(notificationState, windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
            if (focused) notificationState.notifications.refreshStartOnLogin()
        }
    }

    Row(
        modifier = modifier.fillMaxSize().background(colors.background),
        verticalAlignment = Alignment.Top,
    ) {
        SettingsNavRail(
            active = activeSection,
            selectedGroup = selectedGroup,
            onSelect = { section ->
                val groupChanged = section.group != selectedGroup
                selectedSection = section
                if (groupChanged) {
                    scope.launch {
                        listState.scrollToItem(0)
                        listState.animateScrollToItem(sectionScrollIndexInSection(section))
                    }
                } else {
                    scope.launch { listState.animateScrollToItem(sectionScrollIndexInSection(section)) }
                }
            },
            onSelectGroup = { group ->
                if (group != selectedGroup) {
                    selectedSection = group.sections.first()
                    scope.launch { listState.scrollToItem(0) }
                }
            },
        )
        // The rail's trailing edge, drawn by the page rather than by the rail: the rail is the thing
        // being scrolled past, and a rule that belonged to it would scroll with it.
        Box(modifier = Modifier.width(RAIL_RULE_WIDTH).fillMaxHeight().background(colors.border))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PAGE_PADDING,
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        ) {
            item(key = "header") {
                SettingsPageHeader(onClose = onClose, modifier = Modifier.fillMaxWidth())
            }
            settingsSections(state, notificationState, notificationValues, selectedGroup)
        }
    }
}

/** Keeps the scroll declaration's body about the shell, with each settings block in its own item. */
private fun LazyListScope.settingsSections(
    state: AzkarAppState,
    notificationState: NotificationSettingsState,
    notificationValues: NotificationSettingsValues,
    selectedGroup: SettingsGroup,
) {
    selectedGroup.sections.forEach { section ->
        item(key = section.name) {
            SettingsColumn {
                when (section) {
                    SettingsSection.LANGUAGE -> SettingsLanguageSection(state = state)
                    SettingsSection.STARTUP ->
                        SettingsStartupSection(
                            startOnLogin = notificationState.notifications.startOnLogin,
                            settings = notificationState,
                        )
                    SettingsSection.UPDATES -> UpdateSettingsSection()
                    SettingsSection.THEME -> SettingsThemeSection(state = state)
                    SettingsSection.TEXT -> SettingsTypographySection(state = state)
                    SettingsSection.REMINDER_CARD ->
                        SettingsReminderCardSection(
                            presentation = notificationValues.presentation,
                            settings = notificationState,
                            enabled = notificationValues.queue.enabled,
                        )
                    SettingsSection.REMINDER_SCHEDULE ->
                        SettingsReminderScheduleSection(
                            queue = notificationValues.queue,
                            settings = notificationState,
                        )
                    SettingsSection.PRAYER_LOCATION ->
                        SettingsPrayerLocationSection(
                            location = notificationValues.prayerLocation,
                            settings = notificationState,
                        )
                    SettingsSection.PRAYER_CALCULATION ->
                        SettingsPrayerCalculationSection(
                            location = notificationValues.prayerLocation,
                            settings = notificationState,
                        )
                }
            }
        }
    }
}

@Composable
private fun UpdateSettingsSection() {
    val manager = koinInject<UpdateManager>()
    val channel by manager.channel.collectAsState()
    val status by manager.status.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    SettingsSectionBlock(
        title = stringResource(Res.string.settingsUpdatesTitle),
        subtitle = stringResource(Res.string.settingsUpdatesBody),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UpdateSettingsCard(
                    manager = manager,
                    channel = channel,
                    status = status,
                    onSelectChannel = { scope.launch { manager.selectChannel(it) } },
                    onCheck = { scope.launch { manager.checkManually() } },
                )
                if (manager.isRepositoryManaged) RepositoryUpdateActions(manager, channel, clipboard, scope)
                if (status !is UpdateStatus.Idle && status !is UpdateStatus.Current) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UpdateStatusContent(manager, channel, status, clipboard, scope)
                    }
                }
            }
        },
    )
}

@Composable
private fun UpdateSettingsCard(
    manager: UpdateManager,
    channel: UpdateChannel,
    status: UpdateStatus,
    onSelectChannel: (UpdateChannel) -> Unit,
    onCheck: () -> Unit,
) {
    SettingsGroupCard(contentPadding = 0.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
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
                        Text(manager.currentVersion, color = AzkarTheme.colors.textSecondary, fontSize = 14.sp)
                    }
                    Text(
                        stringResource(Res.string.settingsUpdateVersionHint),
                        color = AzkarTheme.colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                UpdateStatusBadge(status, onCheck)
            }
            SettingsRowDivider()
            SelectRow(
                label = stringResource(Res.string.settingsUpdateTrackTitle),
                selected = channel,
                options = listOf(UpdateChannel.STABLE, UpdateChannel.DEV),
                optionLabel = {
                    stringResource(
                        if (it ==
                            UpdateChannel.STABLE
                        ) {
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
    }
}

@Composable
private fun RepositoryUpdateActions(
    manager: UpdateManager,
    channel: UpdateChannel,
    clipboard: Clipboard,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.settingsUpdateManaged),
            color = AzkarTheme.colors.textSecondary,
            fontSize = 12.sp,
        )
        Button(onClick = {
            scope.launch { clipboard.setPlainText(manager.packageManagerCommands(channel, null, false)) }
        }) {
            Text(stringResource(Res.string.settingsUpdateCopyCommands))
        }
    }
}

@Composable
private fun UpdateStatusBadge(
    status: UpdateStatus,
    onClick: () -> Unit,
) {
    val colors = AzkarTheme.colors
    val label = updateStatusLabel(status)
    val inProgress =
        status is UpdateStatus.Checking || status is UpdateStatus.Downloading || status is UpdateStatus.Installing
    val shape = RoundedCornerShape(12.dp)
    val badgeBackground =
        animateColorAsState(
            if (inProgress) colors.highlight else colors.cardBackground,
            label = "update-status-background",
        )
    val badgeBorder =
        animateColorAsState(if (inProgress) colors.primary else colors.border, label = "update-status-border")
    Row(
        modifier =
            Modifier
                .clip(
                    shape,
                ).background(badgeBackground.value, shape)
                .border(1.dp, badgeBorder.value, shape)
                .clickable(
                    enabled =
                        status == UpdateStatus.Idle || status == UpdateStatus.Current || status is UpdateStatus.Failed,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = inProgress,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = colors.primary,
                strokeWidth = 2.dp,
            )
        }
        if (status is UpdateStatus.Available || status is UpdateStatus.ReadyToInstall) {
            Icon(
                imageVector = Icons.Filled.SystemUpdateAlt,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun updateStatusLabel(status: UpdateStatus): String =
    when (status) {
        UpdateStatus.Idle, UpdateStatus.Current -> stringResource(Res.string.settingsUpdateStatusUpToDate)
        UpdateStatus.Checking -> stringResource(Res.string.settingsUpdateStatusChecking)
        is UpdateStatus.Available -> stringResource(Res.string.settingsUpdateStatusAvailable)
        is UpdateStatus.Downloading ->
            stringResource(
                Res.string.settingsUpdateDownloading,
                status.version,
                status.percent.toInt(),
            )
        is UpdateStatus.ReadyToInstall -> stringResource(Res.string.settingsUpdateStatusReady)
        UpdateStatus.Installing -> stringResource(Res.string.settingsUpdateInstalling)
        is UpdateStatus.Failed -> stringResource(Res.string.settingsUpdateStatusFailed)
    }

@Composable
private fun UpdateStatusContent(
    manager: UpdateManager,
    channel: UpdateChannel,
    status: UpdateStatus,
    clipboard: Clipboard,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val colors = AzkarTheme.colors
    when (status) {
        UpdateStatus.Idle -> Text(stringResource(Res.string.settingsUpdateIdle), color = colors.textSecondary)
        UpdateStatus.Checking -> Text(stringResource(Res.string.settingsUpdateChecking), color = colors.textPrimary)
        UpdateStatus.Current -> Unit
        is UpdateStatus.Failed -> Unit
        is UpdateStatus.Available -> AvailableUpdateContent(manager, channel, status, clipboard, scope)
        is UpdateStatus.Downloading ->
            Text(
                stringResource(Res.string.settingsUpdateDownloading, status.version, status.percent.toInt()),
                color = colors.textPrimary,
            )
        is UpdateStatus.ReadyToInstall -> ReadyToInstallContent(manager, status, scope)
        UpdateStatus.Installing -> Text(stringResource(Res.string.settingsUpdateInstalling), color = colors.textPrimary)
    }
}

@Composable
private fun AvailableUpdateContent(
    manager: UpdateManager,
    channel: UpdateChannel,
    update: UpdateStatus.Available,
    clipboard: Clipboard,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val colors = AzkarTheme.colors
    Text(
        stringResource(Res.string.settingsUpdateAvailable, update.info.version, update.info.releaseDate),
        color = colors.textPrimary,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        stringResource(Res.string.settingsUpdateReleaseNotes),
        color = colors.textPrimary,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        update.releaseNotes.ifBlank { stringResource(Res.string.settingsUpdateNoReleaseNotes) },
        color = colors.textSecondary,
    )
    if (update.isDowngrade) DowngradeWarning()
    if (manager.isRepositoryManaged) {
        Button(onClick = {
            scope.launch {
                clipboard.setPlainText(manager.packageManagerCommands(channel, update.info.version, update.isDowngrade))
            }
        }) {
            Text(stringResource(Res.string.settingsUpdateCopyCommands))
        }
    } else {
        Button(onClick = { scope.launch { manager.download(update.info) } }) {
            Text(stringResource(Res.string.settingsUpdateDownload))
        }
    }
}

@Composable
private fun ReadyToInstallContent(
    manager: UpdateManager,
    update: UpdateStatus.ReadyToInstall,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val colors = AzkarTheme.colors
    Text(
        stringResource(Res.string.settingsUpdateAvailable, update.info.version, update.info.releaseDate),
        color = colors.textPrimary,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        update.releaseNotes.ifBlank { stringResource(Res.string.settingsUpdateNoReleaseNotes) },
        color = colors.textSecondary,
    )
    if (update.isDowngrade) DowngradeWarning()
    Button(onClick = { scope.launch { manager.installAndRestart(update.file) } }) {
        Text(stringResource(Res.string.settingsUpdateInstall))
    }
}

@Composable
private fun DowngradeWarning() {
    Text(
        stringResource(Res.string.settingsUpdateDowngradeWarning),
        color = AzkarTheme.colors.dialogValidation,
    )
}

/**
 * Every block's [SettingsSectionBlock], taking the whole pane beside the rail.
 *
 * These used to be capped at 900dp and held to the left of it, which was defensible for a settings row —
 * one short sentence beside one control does not want a 1200dp pane. But the cap was doing its work in
 * the wrong place: it kept the block itself narrow while leaving the pane's extra width as an empty
 * margin beside it, so the page read as a column of cards with the window's half missing rather than as
 * a page. Letting the block fill the pane it was given costs nothing at this size, because the text in
 * it is set beside its control and not run out to a measure, and the cards then line up with the page
 * header and the rail's rule instead of floating inside them.
 */
@Composable
private fun SettingsColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) { content() }
}

/**
 * Which block the reader is inside, for the rail to mark.
 *
 * Derived from the scroll position rather than remembered: the scroll position is already state, and
 * keeping a second copy of "where am I" in sync with it is the sort of thing that survives a change to
 * the list order and then quietly highlights the wrong row.
 *
 * The last block is the awkward one. [LazyListState.firstVisibleItemIndex] names the block at the top of
 * the viewport, and the final block is short enough — with the page's bottom padding under it — that the
 * list runs out of scroll before it ever reaches the top. So reading the topmost item gave the same
 * answer for the whole of the last screenful, and "Prayer times" could not be highlighted however far
 * down the reader went: the rail said they were still in the block above. `canScrollForward` is what
 * distinguishes that case, because it is false exactly when there is nothing below left to reach.
 *
 * [OFFSET_FOR_HEADER] is why this is not just `entries[index]`. The first item is the page header, not a
 * block, so the arithmetic would be off by one for the whole list — and off-by-one here does not crash,
 * it just marks the wrong row, which is the failure that would have shipped.
 */
@Composable
private fun activeSettingsSection(
    listState: LazyListState,
    selectedSection: SettingsSection,
): SettingsSection {
    // When the whole group fits, scrolling cannot express which section the user selected.
    if (!listState.canScrollForward && !listState.canScrollBackward) return selectedSection
    val visible = listState.layoutInfo.visibleItemsInfo
    val index =
        when {
            visible.isEmpty() -> 0
            !listState.canScrollBackward -> visible.firstOrNull { it.index >= OFFSET_FOR_HEADER }?.index ?: 0
            listState.canScrollForward -> visible.first().index
            else -> visible.last().index
        }
    val sections = selectedSection.group.sections
    return sections.getOrElse(index - OFFSET_FOR_HEADER) { sections.first() }
}

/** The number of items that are not blocks: the page header. */
private const val OFFSET_FOR_HEADER = 1

/** Where a block sits in the list, header included. */
private fun sectionScrollIndexInSection(section: SettingsSection): Int =
    section.group.sections.indexOf(section) + OFFSET_FOR_HEADER

/**
 * The page's identity and its way out.
 *
 * The close is a round dismiss with a key caption rather than a labelled button, because the rail has
 * already taken the job a "Back" button used to do here: it is the first thing on the page and it names
 * where you are. What is left is a way out, and a way out that reads as one is not owed a sentence.
 */
@Composable
private fun SettingsPageHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.xl),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
            Text(
                text = stringResource(Res.string.settingsTitle),
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.settingsSubtitle),
                color = colors.textSecondary,
                fontSize = 13.sp,
            )
        }
        SettingsCloseButton(onClick = onClose)
    }
}

/**
 * The holder behind the notification and prayer blocks.
 *
 * Both of the platform capabilities are injected here rather than taken from a parameter: the settings
 * page is the only screen that needs them, and a screen that is handed a detector it does not own would
 * let a caller pass one that is not the one the runtime uses. They are still nullable on the holder so a
 * preview host with no network and no login manager still draws every row.
 */
@Composable
private fun rememberNotificationSettingsState(): NotificationSettingsState {
    val preferences = koinInject<NotificationPreferencesRepository>()
    val locationDetector = koinInject<LocationDetector>()
    val startupRegistration = koinInject<StartupRegistration>()
    val geocodingClient = koinInject<GeocodingClient>()
    val scope = rememberCoroutineScope()
    return remember(preferences, locationDetector, startupRegistration, geocodingClient) {
        NotificationSettingsState(
            scope = scope,
            preferences = preferences,
            locationDetector = locationDetector,
            startupRegistration = startupRegistration,
            geocodingClient = geocodingClient,
        )
    }
}

/**
 * Everything persisted on the moved blocks, snapshotted into one value.
 *
 * Three `collectAsState` calls would have left three locals to thread by hand into the blocks, and a single
 * immutable holder means a block only recomposes when the settings it actually reads change — the prayer
 * block does not recompose when the start-on-login flag is toggled far above it.
 *
 * [queue] joined them when the reminder schedule card moved here from the reminders screen. It is the one
 * value on this page the reminders screen also draws, which is the point of reading it from the repository
 * rather than from a local: the summary at the top of that screen and the card at the top of this one are
 * two readings of one record, and neither holds a copy.
 */
@Immutable
private data class NotificationSettingsValues(
    val queue: ReminderQueueConfig,
    val prayerLocation: PrayerLocationSettings,
    val presentation: NotificationPresentationSettings,
)

@Composable
private fun rememberNotificationSettingsValues(): NotificationSettingsValues {
    val preferences = koinInject<NotificationPreferencesRepository>()
    val queue by preferences.queue.collectAsState()
    val prayerLocation by preferences.prayerLocation.collectAsState()
    val presentation by preferences.presentation.collectAsState()
    return NotificationSettingsValues(queue, prayerLocation, presentation)
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    AzkarThemePreview {
        // Assembled from the parts rather than by calling [SettingsScreen], because that composable
        // reaches Koin for the notification holder and a preview has no container to resolve it against.
        Column(
            modifier = Modifier.fillMaxWidth().padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        ) {
            SettingsPageHeader(onClose = {})
            SettingsLanguageSection(state = previewAzkarAppState())
            SettingsThemeSection(state = previewAzkarAppState())
            SettingsTypographySection(state = previewAzkarAppState())
        }
    }
}
