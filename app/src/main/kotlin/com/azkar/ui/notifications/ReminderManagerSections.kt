package com.azkar.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.Remembrance
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.reminderManagerDropChance
import com.azkar.generated.resources.reminderManagerManualOrderEmpty
import com.azkar.generated.resources.reminderManagerManualOrderHint
import com.azkar.generated.resources.reminderManagerManualOrderTitle
import com.azkar.generated.resources.reminderManagerMoveDown
import com.azkar.generated.resources.reminderManagerMoveUp
import com.azkar.generated.resources.reminderManagerPassageCount
import com.azkar.generated.resources.reminderManagerPassageUnnamed
import com.azkar.generated.resources.reminderManagerPassagesEmpty
import com.azkar.generated.resources.reminderManagerPassagesTitle
import com.azkar.generated.resources.reminderManagerPassagesTruncated
import com.azkar.generated.resources.reminderManagerSearchPlaceholder
import com.azkar.generated.resources.reminderManagerSourcesEmpty
import com.azkar.generated.resources.reminderManagerSourcesHint
import com.azkar.generated.resources.reminderManagerSourcesTitle
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.AzkarToggleSwitch
import com.azkar.ui.components.CircleIconButton
import com.azkar.ui.components.HintText
import com.azkar.ui.components.STEPPER_WIDTH
import com.azkar.ui.components.SectionCard
import com.azkar.ui.components.StepperControl
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// The queue
// ---------------------------------------------------------------------------------------------

/**
 * What the queue draws from — the whole screen below the summary.
 *
 * Two cards rather than the old six, and neither owns its own scroll: the page scrolls once and the
 * passage list is part of it. The old list had a 360dp `verticalScroll` inside the page's own
 * `verticalScroll`, so on a trackpad the inner list ate the gesture until it hit its end and the page
 * below it could not be reached by dragging where the eye said it should.
 *
 * The schedule used to be the first card here and is now the settings page's, which is why this
 * function no longer takes a name that promises *when*. What is left is the half that cannot be
 * answered without a catalogue: which collections and which passages, and the order they are read in.
 * Those are the answers to "what will I see", and the summary above is a preview of them — so they
 * stay beside it rather than travelling to a page that has nothing to preview.
 */
@Composable
internal fun ReminderQueueContent(
    config: ReminderQueueConfig,
    state: ReminderManagerState,
    modifier: Modifier = Modifier,
) {
    val locale = AzkarTheme.locale
    val availableIds = state.availableIds(config)
    val manualItems = state.manualItems(config, availableIds)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
    ) {
        SourcesCard(
            config = config,
            state = state,
            availableIds = availableIds,
            locale = locale,
        )
        if (config.mode == QueueMode.MANUAL) {
            ManualOrderCard(
                items = manualItems,
                onMove = state::moveInManualOrder,
            )
        }
    }
}

/**
 * The source half: which collections and which individual passages the queue draws from.
 *
 * Two lists stacked in one card rather than the old two cards, because they are one decision — what
 * the queue reads from — and splitting them put the passage search three screens below the switches
 * that made it matter. [AzkarToggleSwitch] replaces the old `Checkbox`: a checkbox asks the eye to
 * pair a tick with a label, and these labels are Arabic sentences that wrap to two lines in the list,
 * while the switch is the control the rest of the app uses for "this is in the set".
 */
@Composable
private fun SourcesCard(
    config: ReminderQueueConfig,
    state: ReminderManagerState,
    availableIds: List<Long>,
    locale: Locale,
) {
    val weighted = config.mode == QueueMode.WEIGHTED
    val selectedCount = config.collectionIds.size + config.individualRemembranceIds.size
    SectionCard(
        title = stringResource(Res.string.reminderManagerSourcesTitle),
        body = stringResource(Res.string.reminderManagerSourcesHint),
    ) {
        CountLine(selectedCount)
        if (weighted) DropChanceHeader()
        CollectionRows(config = config, state = state, locale = locale, weighted = weighted)
        Spacer(Modifier.height(AzkarSpacing.xs))
        AzkarOutlinedField(
            value = state.searchQuery,
            onValueChange = state::updateSearchQuery,
            placeholder = stringResource(Res.string.reminderManagerSearchPlaceholder),
            leadingIcon = Icons.Filled.Search,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(Res.string.reminderManagerPassagesTitle),
            color = AzkarTheme.colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = AzkarSpacing.sm),
        )
        PassageRows(config = config, state = state, locale = locale, weighted = weighted, availableIds = availableIds)
    }
}

/**
 * The collection half of [SourcesCard].
 *
 * The drop-chance stepper only appears once a row is both weighted and selected. A stepper on an
 * unselected row would imply a per-collection weighting that survives turning the collection off,
 * and the value it would show is not the value that comes back when the collection is turned on
 * again — [ReminderManagerState.sourceDropChance] is what resolves that, and it resolves it only for
 * selected sources.
 */
@Composable
private fun CollectionRows(
    config: ReminderQueueConfig,
    state: ReminderManagerState,
    locale: Locale,
    weighted: Boolean,
) {
    val collections = state.collectionItems
    if (collections.isEmpty()) {
        HintText(stringResource(Res.string.reminderManagerSourcesEmpty))
        return
    }
    collections.forEach { collection ->
        val id = requireNotNull(collection.id)
        val selected = id in config.collectionIds
        SourceRow(
            label = collection.displayName(locale),
            selected = selected,
            onSelectedChange = { state.schedule.setCollectionEnabled(id, it) },
        ) {
            if (weighted && selected) {
                StepperControl(
                    value = config.collectionDropChances[id.toString()] ?: config.defaultDropChance,
                    onValueChange = { state.schedule.setCollectionDropChance(id, it) },
                    range = 0..100,
                )
            }
        }
    }
}

/**
 * The individual-passage half of [SourcesCard], filtered by the search box above it.
 *
 * A passage that is not selected still shows its stepper when it is part of a selected collection,
 * because [ReminderManagerState.sourceDropChance] then resolves to that collection's chance rather
 * than to the queue default — hiding it would hide the only place a user can see that inheritance,
 * and there is no other screen that shows it.
 */
@Composable
private fun PassageRows(
    config: ReminderQueueConfig,
    state: ReminderManagerState,
    locale: Locale,
    weighted: Boolean,
    availableIds: List<Long>,
) {
    val results = state.filteredRemembrances(locale)
    if (results.isEmpty()) {
        HintText(stringResource(Res.string.reminderManagerPassagesEmpty))
    }
    results.forEach { passage ->
        val id = requireNotNull(passage.id)
        val selected = id in config.individualRemembranceIds
        SourceRow(
            label = remembranceLabel(passage),
            selected = selected,
            onSelectedChange = { state.schedule.setRemembranceSelected(id, it) },
        ) {
            if (weighted && (selected || id in availableIds)) {
                StepperControl(
                    value = config.remembranceDropOverrides[id.toString()] ?: state.sourceDropChance(id, config),
                    onValueChange = { state.schedule.setRemembranceDropChance(id, it) },
                    range = 0..100,
                )
            }
        }
    }
    if (results.size >= REMINDER_SEARCH_RESULT_LIMIT) {
        HintText(stringResource(Res.string.reminderManagerPassagesTruncated, REMINDER_SEARCH_RESULT_LIMIT))
    }
}

/**
 * How many sources the queue is drawing from, as a count rather than as a tally of ticks.
 *
 * The old screen gave no way to see this without scrolling the whole list and counting, which is
 * the one question a user asks of a picker — "did that switch actually take?" — and the answer it
 * gave was a switch state, which is ambiguous between "queued" and "queued and visible at delivery".
 */
@Composable
private fun CountLine(count: Int) {
    Text(
        text = pluralStringResource(Res.plurals.reminderManagerPassageCount, count, count),
        color = AzkarTheme.colors.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * A heading over the stepper column of the source list.
 *
 * Present only in weighted mode, and present rather than a tooltip because the steppers are inline
 * on rows whose label is an Arabic sentence — there is nowhere on such a row to hang an explanation
 * that names the control at its trailing edge.
 */
@Composable
private fun DropChanceHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = stringResource(Res.string.reminderManagerDropChance),
            color = AzkarTheme.colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(STEPPER_WIDTH),
        )
    }
}

/**
 * The manual sequence, when the order is manual.
 *
 * Rendered even with nothing in it, unlike the old card which simply vanished. A setting that
 * appears only once it has something to show teaches nothing about what it is for, and this one is
 * reached *after* the user has just chosen "Manual" and is looking for the control that explains
 * what they chose.
 */
@Composable
private fun ManualOrderCard(
    items: List<Remembrance>,
    onMove: (Long, Int) -> Unit,
) {
    SectionCard(
        title = stringResource(Res.string.reminderManagerManualOrderTitle),
        body = stringResource(Res.string.reminderManagerManualOrderHint),
    ) {
        if (items.isEmpty()) {
            HintText(stringResource(Res.string.reminderManagerManualOrderEmpty))
        }
        items.forEachIndexed { index, passage ->
            val id = requireNotNull(passage.id)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.sm),
            ) {
                Text(
                    text = "${index + 1}",
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(22.dp),
                )
                Text(
                    text = remembranceLabel(passage),
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(
                    icon = Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.reminderManagerMoveUp),
                    onClick = { onMove(id, -1) },
                    enabled = index > 0,
                )
                CircleIconButton(
                    icon = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.reminderManagerMoveDown),
                    onClick = { onMove(id, 1) },
                    enabled = index < items.lastIndex,
                )
            }
        }
    }
}

/**
 * A passage's name, with the "no translation" case named by a resource.
 *
 * The old inline helper ended with `"Remembrance ${remembrance.id}"` — English built by string
 * concatenation, the same class of mistake the prayer names were. The blank case is the one that
 * mattered: it is the only label a passage with no text in the active language can ever have, so
 * getting it right is the difference between the list reading as translated and not.
 */
@Composable
internal fun remembranceLabel(remembrance: Remembrance): String =
    notificationLabel(remembrance, AzkarTheme.locale).ifBlank {
        stringResource(Res.string.reminderManagerPassageUnnamed, remembrance.id ?: 0L)
    }
