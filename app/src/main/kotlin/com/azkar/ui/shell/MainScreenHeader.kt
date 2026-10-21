package com.azkar.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.*
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.NavButton
import com.azkar.ui.components.TooltipIconActionButton
import com.azkar.ui.components.pressableClick
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

/**
 * `.header` — the top bar from `main_screen_header.fxml` and `MainScreenHeader`.
 *
 * The JavaFX version observed `scene.getPreferences().colorSchemeProperty()`; the theme it wrote now
 * lives on the settings screen, and the bar keeps one job less than it used to.
 *
 * The bar is the app's only persistent chrome, so it carries three jobs at once: the brand lockup, the
 * two top-level destinations, and the way into settings. Each is a pill or a circle rather than a
 * square, because the bar sits directly above cards with the same 16dp radii and any squared-off
 * element here would read as a different kind of surface.
 *
 * The destinations sit in the middle rather than beside the brand. As a [Box] with the three groups
 * aligned to its edges, they stay centred on the window's own midpoint: weighting the gaps would
 * have centred them between the brand and the actions, which drifts sideways whenever either side
 * changes width — and the wordmark already changes with the locale.
 *
 * [activeTab] is a [NavKey] rather than an enum so the highlight cannot disagree with what
 * [NavDisplay] drew: both read the same entry at the top of [AzkarNavigator]'s back stack. The
 * settings screen is in that stack too and lights up neither, which is correct: it is not a peer of the
 * other two, and offering it a tab beside them would claim it was.
 *
 * The brand is clickable and goes home. A logo in the corner of every screen is read as "start here"
 * whether or not it looks like a link, and with Navigation 3 that is now a real destination rather than
 * a reset — it pops everything above the root, so it also works as "get me out of whatever I have
 * open".
 *
 * The window is left decorated, so this bar sits *below* the window manager's title bar rather than
 * replacing it. Drawing the title bar ourselves was tried and reverted: undecorating the window lost the
 * desktop's edge snapping and its button layout, which cost more than the colour mismatch did.
 */
@Composable
fun MainScreenHeader(
    activeTab: AzkarDestination,
    callbacks: HeaderCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.cardBackground)
                .padding(start = 32.dp, end = 32.dp, top = 14.dp, bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BrandLockup(onClick = callbacks.onHome, modifier = Modifier.align(Alignment.CenterStart))

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavButton(
                text = stringResource(Res.string.home),
                selected = activeTab == AzkarDestination.Home,
                onClick = callbacks.onHome,
                leadingIcon = AzkarIcons.Home,
            )
            NavButton(
                text = stringResource(Res.string.azkarLibrary),
                selected = activeTab.rootTab == AzkarDestination.Library,
                onClick = callbacks.onLibrary,
                leadingIcon = AzkarIcons.BookOpen,
            )
        }

        TooltipIconActionButton(
            icon = AzkarIcons.Settings,
            tooltip = stringResource(Res.string.settingsTitle),
            onClick = callbacks.onSettings,
            tint = colors.textSecondary,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/**
 * The mark and wordmark as one unit, and the app's way home.
 *
 * Drawn with [Image] rather than `Icon` on purpose: [AzkarIcons.Brand] carries its own blue
 * gradient, and handing it an `Icon` tint would flatten that to a single flat colour.
 *
 * The hover tint is what advertises the click. The mark already reads as "logo" and the wordmark as
 * a label, so unlike a button there is nothing about the resting appearance that says otherwise; a
 * `hoverNeutralSoft` fill appears under the pointer for the same reason [NavButton] fills `highlight`
 * under its selected state.
 */
@Composable
private fun BrandLockup(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier =
            modifier
                // Ahead of `clip` so the hover fill travels with the wordmark on a press, matching the
                // reading the fill itself sets up the moment the pointer arrives.
                .pressableClick(interactionSource, onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .background(if (hovered) colors.hoverNeutralSoft else Color.Transparent)
                // Padding rather than margin: the fill has to cover the mark too, or the hover
                // highlight stops short of the icon and reads as a separate element.
                .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = rememberVectorPainter(AzkarIcons.Brand),
            // Named rather than null: the row is now a clickable, and a control with no label is
            // announced as just "button" by a screen reader.
            contentDescription = stringResource(Res.string.azkar),
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(Res.string.azkar),
                color = colors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.yourCompanion),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

/** `.header` bottom border, drawn without a Material `HorizontalDivider`. */
@Composable
fun HeaderBottomBorder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AzkarTheme.colors.border),
    )
}

/**
 * The name to greet the signed-in user by, taken from the operating system's account name.
 *
 * The app has no sign-in step — on a desktop the OS *is* the identity — so the account name is the
 * one piece of personalisation available without inventing a login screen. Only the first token is
 * kept: `user.name` is frequently the account's full name, and a greeting wants one word.
 *
 * Returns an empty string when the property is missing, mangled, or carries no letters at all (some
 * Linux sessions publish a numeric uid); callers treat that as "omit the name" rather than inventing
 * a placeholder.
 */
internal fun userDisplayName(): String =
    System
        .getProperty("user.name")
        .orEmpty()
        .split(' ', '\t', '_', '-', '.', '@')
        .firstOrNull { token -> token.any(Char::isLetter) }
        .orEmpty()
        .filter(Char::isLetterOrDigit)
        .replaceFirstChar { first -> first.titlecase() }
