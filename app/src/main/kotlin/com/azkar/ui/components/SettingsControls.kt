package com.azkar.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.notificationPlacementBottomEnd
import com.azkar.generated.resources.notificationPlacementBottomStart
import com.azkar.generated.resources.notificationPlacementTopEnd
import com.azkar.generated.resources.notificationPlacementTopStart
import com.azkar.generated.resources.settingsStepperDecrease
import com.azkar.generated.resources.settingsStepperIncrease
import com.azkar.ui.library.AzkarDropdown
import com.azkar.ui.notifications.presentation.OverlayPlacement
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The row vocabulary every settings page in the app is built from.
 *
 * Not in [Controls] because that file's kit is app-wide while these are sized and worded for a
 * settings page: a hint line under the label, a fixed-width stepper, a field tall enough to type a
 * coordinate into. They live here rather than beside either screen that uses them because there are
 * now two — the reminders screen and the settings screen — and a row kit only one of them can reach
 * is a row kit that will be copied and then drift.
 *
 * The one rule every row here follows is that the leading edge stays a single column of text however
 * long the sentences get, because that is what makes a long list of settings scannable. Anything a
 * row needs to say therefore goes *under* its label and never beside it — which is how the reminders
 * screen ended up with three-line rows on exactly the entries where the explanation mattered most.
 */
@Composable
fun SectionCard(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = AzkarTheme.colors
    AzkarCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = AzkarSpacing.xl,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (body != null) {
                Text(text = body, color = colors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(AzkarSpacing.md)) {
                content()
            }
        }
    }
}

/**
 * One labelled control, with room for a switch, a dropdown or a stepper on the trailing edge.
 *
 * The hint sits under the label rather than beside the control, so the row's leading edge stays a
 * single column of text however long the sentences get — which is what makes a list of these
 * scannable. The reminders screen put its explanation in a trailing `Text` instead, which grew the
 * row to three lines on exactly the rows where the explanation mattered.
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) colors.textPrimary else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (hint != null) {
                Text(text = hint, color = colors.textSecondary, fontSize = 11.sp)
            }
        }
        trailing()
    }
}

/** A [SettingRow] whose trailing control is a dropdown, built on the app's [AzkarDropdown]. */
@Composable
fun <T> SelectRow(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    fieldWidth: Dp = 210.dp,
    enabled: Boolean = true,
) {
    SettingRow(label = label, modifier = modifier, hint = hint, enabled = enabled) {
        AzkarDropdown(
            selected = selected,
            options = options,
            label = optionLabel,
            onSelect = onSelect,
            fieldWidth = fieldWidth,
            enabled = enabled,
        )
    }
}

/**
 * A bounded integer, as minus and plus rather than as a text field.
 *
 * Every one of these values has a hard range the settings already validate — a skip chance cannot
 * leave 0..100, the stack cannot leave 1..5 — so a field accepting free text can only ever show
 * something the settings will reject. Two buttons and a number cannot hold an invalid value at all,
 * and the reminders screen used to put one of these fields on every row of a list up to 150 long.
 */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
) {
    SettingRow(label = label, modifier = modifier, hint = hint, enabled = enabled) {
        StepperControl(value = value, onValueChange = onValueChange, range = range, enabled = enabled)
    }
}

/**
 * The minus/number/plus trio, without the label, for the rows that carry one inline.
 *
 * [STEPPER_WIDTH] is fixed rather than measured so that the steppers in a list line up with each
 * other and with the column heading above them. Rows measured independently would each centre a
 * three-digit and a one-digit value differently, and a column that wobbles is a column the eye reads
 * as broken.
 */
@Composable
fun StepperControl(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    enabled: Boolean = true,
) {
    val colors = AzkarTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.xs),
    ) {
        CircleIconButton(
            icon = Icons.Filled.Remove,
            contentDescription = stringResource(Res.string.settingsStepperDecrease),
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            enabled = enabled && value > range.first,
        )
        Text(
            text = value.toString(),
            color = if (enabled) colors.textPrimary else colors.textSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(34.dp),
            maxLines = 1,
        )
        CircleIconButton(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(Res.string.settingsStepperIncrease),
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            enabled = enabled && value < range.last,
        )
    }
}

/** The width the stepper column occupies, shared by [StepperControl] and the heading above it. */
val STEPPER_WIDTH = 76.dp

/** A labelled text field. The label is its own line because [AzkarOutlinedField] has no label slot. */
@Composable
fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = AzkarTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        AzkarOutlinedField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
            supportingText = supportingText,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

/** One line of quiet explanation under a control. */
@Composable
fun HintText(text: String) {
    Text(text = text, color = AzkarTheme.colors.textSecondary, fontSize = 11.sp)
}

/**
 * A header's way out, outlined rather than filled.
 *
 * Leaving a screen is the default outcome, not the thing the user came to do, so it does not get the
 * primary button's fill — the same reasoning as the library's "Back to dashboard" sitting in its empty
 * state rather than in the toolbar.
 */
@Composable
fun AzkarOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                // The default shrink rather than the tint-only a large surface gets: this is a
                // button-sized outline, and its border is the whole reason it reads as one.
                .pressableClick(onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * The overlay corner's name.
 *
 * Here rather than at the call site because the four names exist only to label a dropdown and every
 * other label on this page is a resource, and the day one screen inlines "Top end" in a `when` is the
 * day the Arabic translation is half a screen out of date.
 */
@Composable
fun placementLabel(placement: OverlayPlacement): String =
    stringResource(
        when (placement) {
            OverlayPlacement.TOP_START -> Res.string.notificationPlacementTopStart
            OverlayPlacement.TOP_END -> Res.string.notificationPlacementTopEnd
            OverlayPlacement.BOTTOM_START -> Res.string.notificationPlacementBottomStart
            OverlayPlacement.BOTTOM_END -> Res.string.notificationPlacementBottomEnd
        },
    )
