package com.azkar.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.notificationCardSizeComfortable
import com.azkar.generated.resources.notificationCardSizeCompact
import com.azkar.generated.resources.notificationCardSizeFit
import com.azkar.generated.resources.notificationCardSizeFixed
import com.azkar.generated.resources.notificationCardSizeHeight
import com.azkar.generated.resources.notificationCardSizeHeightHint
import com.azkar.generated.resources.notificationCardSizeHint
import com.azkar.generated.resources.notificationCardSizePresetHint
import com.azkar.generated.resources.notificationCardSizePreview
import com.azkar.generated.resources.notificationCardSizeRoomy
import com.azkar.generated.resources.notificationCardSizeSample
import com.azkar.generated.resources.notificationCardSizeSizingHint
import com.azkar.generated.resources.notificationCardSizeTitle
import com.azkar.generated.resources.notificationCardSizeWidth
import com.azkar.generated.resources.notificationCardSizeWidthHint
import com.azkar.generated.resources.notificationDuration
import com.azkar.generated.resources.notificationDurationFixed
import com.azkar.generated.resources.notificationDurationHint
import com.azkar.generated.resources.notificationDurationReading
import com.azkar.generated.resources.notificationDurationTitle
import com.azkar.generated.resources.notificationLanguageHint
import com.azkar.generated.resources.notificationLanguageSameAsApp
import com.azkar.generated.resources.notificationLanguageTitle
import com.azkar.generated.resources.notificationPlacement
import com.azkar.generated.resources.notificationPlacementHint
import com.azkar.generated.resources.notificationStackSizeHint
import com.azkar.generated.resources.notificationStackSizeTitle
import com.azkar.generated.resources.settingsReminderCardHint
import com.azkar.generated.resources.settingsReminderCardTitle
import com.azkar.i18n.displayNameIn
import com.azkar.i18n.supportedLocales
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.components.StepperControl
import com.azkar.ui.components.placementLabel
import com.azkar.ui.library.AzkarDropdown
import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationContent
import com.azkar.ui.notifications.NotificationSettingsState
import com.azkar.ui.notifications.presentation.NotificationCard
import com.azkar.ui.notifications.presentation.NotificationPresentationSettings
import com.azkar.ui.notifications.presentation.OverlayCardSize
import com.azkar.ui.notifications.presentation.OverlayCardSizing
import com.azkar.ui.notifications.presentation.OverlayDurationMode
import com.azkar.ui.notifications.presentation.OverlayPlacement
import com.azkar.ui.notifications.presentation.PresentedNotice
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/**
 * The reminder card's own dropdown width, wider than the shared default because its options are
 * sentences — "Bottom left", "Same as the app" — and the shared 210dp truncated all of them.
 */
private val REMINDER_FIELD_WIDTH = 230.dp

// The bounds the reminder-card settings are limited by, mirroring the `require`s in
// `NotificationPresentationSettings`. They are written out here rather than read off the settings
// object so the steppers' and sliders' limits are visible in the same file as the controls; the config
// still validates on write, so a stale number here would clamp rather than crash.
private const val MIN_DURATION_SECONDS = 3

private const val MAX_DURATION_SECONDS = 120

private const val MAX_VISIBLE_NOTIFICATIONS = 5

private const val MIN_CARD_WIDTH_DP = 300

private const val MAX_CARD_WIDTH_DP = 700
private const val MIN_CARD_HEIGHT_DP = 120
private const val MAX_CARD_HEIGHT_DP = 400

/*
 * The preview's stand-in for a fired reminder. The id is never published — it exists only so the card has
 * an identity to key its own state on — and the total is three so the cycle counter in the preview's
 * header is the same counter a real card of a three-passage cycle shows, rather than the arithmetic
 * "1 of 1" that the card deliberately hides.
 */
private const val PREVIEW_NOTICE_ID = 1L
private const val PREVIEW_NOTICE_TOTAL = 3

/** The stored tag meaning "remind me in whatever language the app is in"; the field's own default. */
private const val FOLLOW_APP_LANGUAGE_TAG = ""

/*
 * The reminder languages on offer: "same as the app" first, then every language the app is translated
 * into. Derived from `supportedLocales` rather than restated, so a language added to the app is offered
 * for reminders by the same change and the two lists cannot drift apart.
 */
private val reminderLanguageOptions: List<String> =
    listOf(FOLLOW_APP_LANGUAGE_TAG) + supportedLocales.map { it.toLanguageTag() }

/**
 * How a fired reminder is drawn: how long it stays, how many stack, where it lands, what language it
 * is written in, and how large one card is.
 *
 * This is the half of the reminder settings that is an appearance — the card a reader is looking at —
 * while *whether* one fires and *when* stay in the reminders group, and start-on-login, which decides
 * whether the app is running to show one at all, stays with them. Splitting it this way is what lets
 * "Appearance" mean one thing: every block under it changes what the reader sees and nothing about
 * what the app does.
 *
 * The block is one card of rows rather than several, because several cards said several answers of
 * equal weight and they are not: a card drawn in the wrong corner still appears, while a size that
 * overflows the screen shows half a passage. One card lets the preview at the bottom be read as the
 * sum of the rows above it, which is how a reader checks whether what they set is what they wanted.
 */
@Composable
internal fun SettingsReminderCardSection(
    presentation: NotificationPresentationSettings,
    settings: NotificationSettingsState,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    SettingsSectionBlock(
        title = stringResource(Res.string.settingsReminderCardTitle),
        subtitle = stringResource(Res.string.settingsReminderCardHint),
        modifier = modifier,
    ) {
        SettingsGroupCard {
            PresentationRows(presentation = presentation, settings = settings, enabled = enabled)
        }
    }
}

/**
 * The rows that describe the card itself: how long it stays up, how many stack, where it lands, what
 * language it is written in, and its size.
 *
 * Split out because these are the only rows in the block whose control writes a number rather than
 * flipping a flag, and inlining them pushed the block past the length where the shape of it was still
 * readable — which mattered here more than usual, because this is the section a reader is most likely to
 * be scanning to find one control among several. Where that number comes from is itself a choice, made
 * in [DurationRows]; everything else here writes a number the reader supplied directly.
 */
@Composable
private fun PresentationRows(
    presentation: NotificationPresentationSettings,
    settings: NotificationSettingsState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        DurationRows(presentation = presentation, settings = settings, enabled = enabled)
        SettingsValueRow(
            value = stringResource(Res.string.notificationStackSizeTitle),
            description = stringResource(Res.string.notificationStackSizeHint),
            enabled = enabled,
            control = {
                StepperControl(
                    value = presentation.maxVisible,
                    onValueChange = settings.notifications::setOverlayStackSize,
                    range = 1..MAX_VISIBLE_NOTIFICATIONS,
                    enabled = enabled,
                )
            },
        )
        SettingsRowDivider()
        SettingsValueRow(
            value = stringResource(Res.string.notificationPlacement),
            description = stringResource(Res.string.notificationPlacementHint),
            enabled = enabled,
            control = {
                AzkarDropdown(
                    selected = presentation.placement,
                    options = OverlayPlacement.entries,
                    label = ::placementLabel,
                    onSelect = settings.notifications::setPlacement,
                    fieldWidth = REMINDER_FIELD_WIDTH,
                    enabled = enabled,
                )
            },
        )
        SettingsRowDivider()
        NotificationLanguageRow(presentation = presentation, settings = settings, enabled = enabled)
        SettingsRowDivider()
        CardSizeSection(presentation = presentation, settings = settings, enabled = enabled)
    }
}

/**
 * The duration decision as a mode and, only under [OverlayDurationMode.FIXED], its number.
 *
 * Structured like [CardSizeSection] because the two choices are the same shape: a question of where a
 * value comes from — the reader, or the content itself — with the reader's number still there when they
 * want to be the one to supply it. The stepper is hidden rather than disabled under
 * [OverlayDurationMode.READING] because a number the card no longer uses is not a candidate value, it is
 * a dead control; the reading hint stands in its place so the mode always explains itself. The number
 * itself is preserved untouched, exactly as [CardSizeSection] preserves the height under fit sizing, so
 * switching back restores what the reader last set.
 */
@Composable
private fun DurationRows(
    presentation: NotificationPresentationSettings,
    settings: NotificationSettingsState,
    enabled: Boolean,
) {
    SettingsValueRow(
        value = stringResource(Res.string.notificationDurationTitle),
        description = stringResource(Res.string.notificationDurationHint),
        enabled = enabled,
        control = {
            AzkarDropdown(
                selected = presentation.durationMode,
                options = OverlayDurationMode.entries,
                label = ::durationModeLabel,
                onSelect = settings.notifications::setDurationMode,
                fieldWidth = REMINDER_FIELD_WIDTH,
                enabled = enabled,
            )
        },
    )
    SettingsRowDivider()
    if (!presentation.usesReadingTime) {
        SettingsValueRow(
            value = stringResource(Res.string.notificationDuration, presentation.durationSeconds),
            description = stringResource(Res.string.notificationDurationHint),
            enabled = enabled,
            control = {
                StepperControl(
                    value = presentation.durationSeconds,
                    onValueChange = settings.notifications::setOverlayDuration,
                    range = MIN_DURATION_SECONDS..MAX_DURATION_SECONDS,
                    enabled = enabled,
                )
            },
        )
        SettingsRowDivider()
    }
}

/** The pill's name for each way a card's stay is decided. */
@Composable
private fun durationModeLabel(mode: OverlayDurationMode): String =
    when (mode) {
        OverlayDurationMode.READING -> stringResource(Res.string.notificationDurationReading)
        OverlayDurationMode.FIXED -> stringResource(Res.string.notificationDurationFixed)
    }

/**
 * The language the reminder text is written in, as a choice of "same as the app" or a specific one.
 *
 * A row of its own rather than a control on the heading, because unlike the interface's own language it
 * changes nothing about the page it sits on: the whole page stays in the reader's language while the
 * reminder they are configuring is written in another, and a control that appeared to also retranslate
 * the page would be claiming an effect it does not have.
 *
 * The options are [supportedLocales] rather than a list restated here, so a language added to the app is
 * offered for reminders in the same change. "Same as app" leads and is the stored default, so a reader
 * who has never opened this row keeps the behaviour they already had.
 */
@Composable
private fun NotificationLanguageRow(
    presentation: NotificationPresentationSettings,
    settings: NotificationSettingsState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val current = AzkarTheme.locale
    SettingsValueRow(
        value = stringResource(Res.string.notificationLanguageTitle),
        description = stringResource(Res.string.notificationLanguageHint),
        enabled = enabled,
        control = {
            AzkarDropdown(
                selected = presentation.notificationLanguageTag,
                options = reminderLanguageOptions,
                label = { tag -> notificationLanguageLabel(tag = tag, current = current) },
                onSelect = settings.notifications::setNotificationLanguage,
                fieldWidth = REMINDER_FIELD_WIDTH,
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}

/** The name a stored tag is offered under: its endonym, or the "same as app" answer for the blank tag. */
@Composable
private fun notificationLanguageLabel(
    tag: String,
    current: Locale,
): String =
    if (tag.isEmpty()) {
        stringResource(Res.string.notificationLanguageSameAsApp)
    } else {
        Locale.forLanguageTag(tag).displayNameIn(current)
    }

/**
 * How large one card is — whether it is any fixed size at all, and what that size is.
 *
 * The sizing row is first because it decides what the controls under it mean. A card that fits its
 * passage has no height of its own to choose — the passage chooses it — so the height slider is not
 * offered in that mode rather than offered and ignored, and the two widths that remain are the pair
 * that matters when height is not the reader's to set. A card that keeps a fixed slot gets both
 * sliders, because both are then genuinely its size.
 *
 * The mode is offered as two named answers rather than a switch, for the same reason the presets are:
 * the choice is not "on or off" but "the passage's height or mine", and a switch labelled with one of
 * those would describe only half of what turning it off means.
 *
 * The presets come next because a size is one of the few settings here where the reader arrives
 * already knowing roughly what they want — "small", "normal", "big" — and a bare pair of sliders asks
 * them to translate that into numbers before they have seen the thing at any of them. A preset is a
 * *pair* of answers, so tapping one under a fitted height still does something visible: it sets the
 * width, which is the measure the passage wraps to, and quietly leaves the height it also wrote as the
 * fallback a fitted card falls back to. The sliders are below the presets rather than instead of them
 * because the presets cannot offer the one thing that matters to somebody whose screen is short: a
 * wide, short card, or a narrow, tall one, neither of which is on the diagonal the presets describe.
 *
 * The preview is the real [NotificationCard] rather than a diagram of one. That is the only version of
 * this setting that cannot lie: a rectangle with the right numbers on it says nothing about whether the
 * passage still fits at the size being offered, and fitting is the entire reason the height control
 * exists. It is drawn at the chosen dp so what is on screen is measured in the same units the overlay
 * window will be sized in — and under a fitted height it is drawn at the chosen *width* and measures
 * its own height, which is what the card in the corner will do.
 */
@Composable
private fun CardSizeSection(
    presentation: NotificationPresentationSettings,
    settings: NotificationSettingsState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val fitsPassage = presentation.usesFittedHeight
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.notificationCardSizeTitle).uppercase(AzkarTheme.locale),
                color = AzkarTheme.colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.9.sp,
            )
            Text(
                text = stringResource(Res.string.notificationCardSizeHint),
                color = AzkarTheme.colors.textSecondary,
                fontSize = 12.sp,
            )
        }

        CardSizeControls(presentation = presentation, settings = settings, fitsPassage = fitsPassage, enabled = enabled)

        var showPreview by remember { mutableStateOf(false) }
        SettingsRowDivider()
        SettingsValueRow(
            value = stringResource(Res.string.notificationCardSizePreview),
            control = {
                SecondaryButton(
                    onClick = { showPreview = !showPreview },
                    text = stringResource(Res.string.notificationCardSizePreview),
                    enabled = enabled,
                )
            },
            enabled = enabled,
        )
        if (showPreview) {
            CardSizePreview(presentation = presentation)
        }
    }
}

/** The name a sizing mode is offered under, from a closed set rather than from a property on the enum. */
@Composable
private fun cardSizingLabel(sizing: OverlayCardSizing): String =
    when (sizing) {
        OverlayCardSizing.FIT -> stringResource(Res.string.notificationCardSizeFit)
        OverlayCardSizing.FIXED -> stringResource(Res.string.notificationCardSizeFixed)
    }

/** The name a preset is offered under, from a closed set rather than from a property on the enum. */
@Composable
private fun cardSizeLabel(preset: OverlayCardSize): String =
    when (preset) {
        OverlayCardSize.COMPACT -> stringResource(Res.string.notificationCardSizeCompact)
        OverlayCardSize.COMFORTABLE -> stringResource(Res.string.notificationCardSizeComfortable)
        OverlayCardSize.ROOMY -> stringResource(Res.string.notificationCardSizeRoomy)
    }

@Composable
private fun CardSizeControls(
    presentation: NotificationPresentationSettings,
    settings: NotificationSettingsState,
    fitsPassage: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    SettingsValueRow(
        value = stringResource(Res.string.notificationCardSizeTitle),
        description = stringResource(Res.string.notificationCardSizeSizingHint),
        enabled = enabled,
        control = {
            AzkarDropdown(
                selected = presentation.cardSizing,
                options = OverlayCardSizing.entries,
                label = ::cardSizingLabel,
                onSelect = settings.notifications::setCardSizing,
                fieldWidth = REMINDER_FIELD_WIDTH,
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
    SettingsRowDivider()
    SettingsValueRow(
        value = stringResource(Res.string.notificationCardSizeTitle),
        description = stringResource(Res.string.notificationCardSizePresetHint),
        enabled = enabled,
        control = {
            AzkarDropdown(
                selected = presentation.cardSizePreset ?: OverlayCardSize.COMFORTABLE,
                options = OverlayCardSize.entries,
                label = ::cardSizeLabel,
                onSelect = settings.notifications::setCardSize,
                fieldWidth = REMINDER_FIELD_WIDTH,
                enabled = enabled,
            )
        },
    )
    SettingsRowDivider()
    CardDimensionField(
        label = stringResource(Res.string.notificationCardSizeWidth),
        value = presentation.cardWidthDp,
        description = stringResource(Res.string.notificationCardSizeWidthHint),
        range = MIN_CARD_WIDTH_DP..MAX_CARD_WIDTH_DP,
        onValueChange = settings.notifications::setCardWidthDp,
        enabled = enabled,
    )
    if (!fitsPassage) {
        SettingsRowDivider()
        CardDimensionField(
            label = stringResource(Res.string.notificationCardSizeHeight),
            value = presentation.cardHeightDp,
            description = stringResource(Res.string.notificationCardSizeHeightHint),
            range = MIN_CARD_HEIGHT_DP..MAX_CARD_HEIGHT_DP,
            onValueChange = settings.notifications::setCardHeightDp,
            enabled = enabled,
        )
    }
}

@Composable
private fun CardDimensionField(
    label: String,
    value: Int,
    description: String,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    SettingsValueRow(
        value = label,
        description = description,
        enabled = enabled,
        control = {
            AzkarOutlinedField(
                value = text,
                onValueChange = { input ->
                    if (!enabled) return@AzkarOutlinedField
                    val filtered = input.filter { it.isDigit() }
                    text = filtered
                    filtered.toIntOrNull()?.let { parsed ->
                        if (parsed in range) onValueChange(parsed)
                    }
                },
                placeholder = value.toString(),
                modifier = Modifier.width(96.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = "${range.first}–${range.last}",
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}

/**
 * The card at the size being offered, with a real passage in it.
 *
 * Sample text rather than a drawn rectangle because the question the preview answers is "does my
 * passage still fit", and an empty box answers it in a way that is always yes. The sample is drawn
 * from resources so it comes back in the reader's own language and the box then takes the paragraph
 * direction off it exactly as it will off a live reminder — a preview pinned to one direction would
 * mis-render every other user's first glance at the setting.
 *
 * Centred rather than pinned to the leading edge because the widest preset is narrower than the card
 * that holds it, and a card left against one wall of a wide settings panel reads as a mistake.
 */
@Composable
private fun CardSizePreview(
    presentation: NotificationPresentationSettings,
    modifier: Modifier = Modifier,
) {
    val sample = stringResource(Res.string.notificationCardSizeSample)
    val preview =
        remember(sample) {
            PresentedNotice(
                id = PREVIEW_NOTICE_ID,
                title = "",
                message = "",
                action = NotificationAction.Remembrance(PREVIEW_NOTICE_ID),
                content =
                    NotificationContent.Remembrance(
                        text = sample,
                        position = 1,
                        total = PREVIEW_NOTICE_TOTAL,
                    ),
            )
        }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AzkarSpacing.xs)) {
        Text(
            text = stringResource(Res.string.notificationCardSizePreview).uppercase(AzkarTheme.locale),
            color = AzkarTheme.colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            NotificationCard(
                item = preview,
                onOpen = {},
                onDismiss = {},
                onPauseChanged = {},
                // Under a fitted height the card is handed only the width and measures its own height,
                // exactly as the one in the corner does; a fixed card is handed both, so the preview
                // shows the slot the reader is actually choosing rather than a slice of one.
                modifier =
                    if (presentation.usesFittedHeight) {
                        Modifier.width(presentation.cardWidth)
                    } else {
                        Modifier.size(presentation.cardWidth, presentation.cardHeight)
                    },
                sizing = presentation.cardSizing,
                // The star, drawn as the overlay draws it. The preview is a real card rather than a
                // drawing of one, so it has to carry the control too — a preview that quietly left
                // the star out would be showing a smaller card than the reader is being offered.
                onToggleFavorite = { _, _ -> true },
            )
        }
    }
}
