package com.azkar.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.reminderManagerBack
import com.azkar.generated.resources.reminderManagerSubtitle
import com.azkar.generated.resources.reminderManagerTitle
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarOutlinedButton
import com.azkar.ui.preview.PREVIEW_COLLECTION_ID
import com.azkar.ui.preview.rememberPreviewReminderManagerState
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * How wide the reminders column is allowed to get.
 *
 * Wider than [com.azkar.ui.shell.SettingsScreen]'s 760dp because the source rows put a stepper on the
 * trailing edge of a passage name that is itself an Arabic sentence, and at 760dp those rows wrap
 * instead of holding one line. Past roughly 900dp nothing is gained: the controls are all
 * fixed-width and the extra travel would only push the label away from the thing it names.
 */
private val REMINDERS_MAX_WIDTH = 900.dp

/** How often the summary card redraws its countdown, in milliseconds. */
private const val SUMMARY_TICK_MILLIS = 20_000L

/** The page inset, shared by the scroll container and by the preview so they cannot drift apart. */
private val REMINDERS_PADDING =
    PaddingValues(
        start = AzkarSpacing.screenPaddingHorizontal,
        end = AzkarSpacing.screenPaddingHorizontal,
        top = AzkarSpacing.screenPaddingTop,
        bottom = AzkarSpacing.screenPaddingBottom,
    )

/**
 * The reminders screen: a live "up next" summary over the queue's own settings.
 *
 * It is a pushed [com.azkar.ui.shell.AzkarDestination] rather than a modal, and has a way back —
 * [onClose] pops. The old screen was reachable only from the header's bell and had no visible way
 * out of it, so on a screen with this many controls the only escape was the keyboard shortcut nobody
 * knows about.
 *
 * This used to be three tabs: queue, prayer, appearance. Prayer and appearance are settings — a
 * prayer switch belongs beside the prayer time it gates on the home screen, and how long an overlay
 * stays up or which corner it sits in describes the app rather than any one queue — so both moved to
 * the settings page. What is left is one subject, and a tab strip around one subject was a click that
 * bought nothing: the user arrived here, saw a tab already selected, and had to click to start.
 *
 * The state lives in [ReminderManagerState]; this composable owns only the clock tick behind the
 * summary. Persisted settings are read from the repository's flows rather than copied here, so there
 * is one source of truth for what is stored — see that class for the full reasoning.
 */
@Composable
fun ReminderManagerScreen(
    initialCollectionId: Long?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberReminderManagerState()
    val settings = rememberReminderSettings()

    LaunchedEffect(state) { state.bind() }

    // The collection passed in from a collection page may not have arrived with the catalogue yet,
    // so this is keyed on both: see [ReminderManagerState.autoSelectCollection].
    LaunchedEffect(initialCollectionId, state.collections, settings.queue.collectionIds) {
        state.autoSelectCollection(initialCollectionId)
    }

    val now by summaryTicker()
    val zone = remember { ZoneId.systemDefault() }
    val preview =
        remember(now, settings) {
            state.preview(now, settings.queue, settings.queueProgress)
        }

    val colors = AzkarTheme.colors
    Box(
        modifier = modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = REMINDERS_MAX_WIDTH).padding(REMINDERS_PADDING),
            verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
        ) {
            RemindersHeader(onClose = onClose)
            RemindersSummaryCard(
                preview = preview,
                config = settings.queue,
                queueNow = LocalDateTime.ofInstant(now, zone),
            )
            ReminderQueueContent(config = settings.queue, state = state)
        }
    }
}

/**
 * The one holder [ReminderManagerScreen] composes from.
 *
 * The `koinInject` calls and the `remember` that owns the state holder live here rather than inline
 * so the screen reads as wiring: it is a header, a summary, and the queue. The `remember` keys are the
 * injected singletons rather than the screen's parameters so that a rotation of the preview window,
 * or anything else that re-runs this composable, cannot orphan the holder and its in-flight `bind()` —
 * the repository singletons are what the holder's lifetime should follow.
 */
@Composable
private fun rememberReminderManagerState(): ReminderManagerState {
    val preferences = koinInject<NotificationPreferencesRepository>()
    val catalogue = koinInject<ReminderCatalogueSource>()
    val scope = rememberCoroutineScope()
    return remember(preferences, catalogue) {
        ReminderManagerState(
            scope = scope,
            preferences = preferences,
            catalogueSource = catalogue,
        )
    }
}

/**
 * Everything persisted, snapshotted into one value.
 *
 * Two `collectAsState` calls would have left two locals and the summary's `remember` key would have
 * been a pair rather than a single data class, which is the difference between "recompose when the
 * queue moved" and "recompose when the clock ticked, because `queueProgress` was rebuilt upstream".
 * Nothing about the queue needs the prayer location, the prayer switches, the overlay settings or
 * the start-on-login flag; those live on the settings page now.
 */
@Immutable
private data class ReminderSettings(
    val queue: ReminderQueueConfig,
    val queueProgress: ReminderQueueProgress,
)

@Composable
private fun rememberReminderSettings(): ReminderSettings {
    val preferences = koinInject<NotificationPreferencesRepository>()
    val queue by preferences.queue.collectAsState()
    val queueProgress by preferences.queueProgress.collectAsState()
    return ReminderSettings(queue, queueProgress)
}

/**
 * A clock that only ever produces "now", every [SUMMARY_TICK_MILLIS].
 *
 * A ticker rather than a `LaunchedEffect` reading the clock on every frame because the countdown is
 * the only thing on the page that moves, and it moves in minutes: waking the composition twenty times
 * a second to re-read a clock and re-render the same text is the sort of thing that shows up in a
 * profile of a screen that is otherwise entirely static. Twenty seconds is also below the resolution
 * of the text — the card truncates to whole minutes, so a tick shorter than that would not change
 * what is on screen.
 */
@Composable
private fun summaryTicker(): State<Instant> {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SUMMARY_TICK_MILLIS)
            now = Instant.now()
        }
    }
    return remember(now) { mutableStateOf(now) }
}

/**
 * The screen's identity and its way out.
 *
 * A bell badge over the title, matching [com.azkar.ui.shell.SettingsScreen]'s shape, because the two
 * are siblings and a header that is a different shape on one of them makes the app look like it has
 * a third place in it.
 *
 * [onClose] pops rather than navigating home, for the reason given on the settings header: reminders
 * is pushed onto whichever screen opened it — including a collection page, which is how
 * [initialCollectionId] gets here — and "Back" that changes screen is "Home" wearing the wrong label.
 */
@Composable
private fun RemindersHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                imageVector = AzkarIcons.Bell,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.reminderManagerTitle),
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.reminderManagerSubtitle),
                color = colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AzkarOutlinedButton(text = stringResource(Res.string.reminderManagerBack), onClick = onClose)
    }
}

@Preview
@Composable
private fun ReminderManagerScreenPreview() {
    AzkarThemePreview {
        val config =
            ReminderQueueConfig(enabled = true, collectionIds = listOf(PREVIEW_COLLECTION_ID), intervalMinutes = 60)
        val now = LocalDateTime.of(2026, 3, 14, 9, 5)
        Column(
            modifier = Modifier.widthIn(max = REMINDERS_MAX_WIDTH).padding(REMINDERS_PADDING),
            verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
        ) {
            RemindersHeader(onClose = {})
            RemindersSummaryCard(
                preview =
                    QueuePreview(
                        enabled = true,
                        hasSources = true,
                        slot = now.plusMinutes(95),
                        passage = null,
                    ),
                config = config,
                queueNow = now,
            )
            ReminderQueueContent(config = config, state = rememberPreviewReminderManagerState())
        }
    }
}
