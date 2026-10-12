package com.azkar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import com.azkar.generated.resources.*
import com.azkar.i18n.displayNameIn
import com.azkar.i18n.isSameLanguageAs
import com.azkar.i18n.supportedLocales
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.pressableClick
import com.azkar.ui.preview.previewAzkarAppState
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

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

/**
 * The screen the gear opens, holding the two preferences that used to be scattered.
 *
 * The theme lived on a sun/moon in the header and the language in a modal that could not show both, so
 * neither read as a setting and there was nowhere to put a third. Both live here now, which is why
 * this is a [AzkarDestination] rather than the dialog it replaced: a modal has nowhere to grow, and
 * it is dismissed by the very click that changes its contents.
 *
 * State arrives rather than being held here. Both values are already on [AzkarAppState] — they have to
 * be, since they outlive the window — so handing this composable the state plus the one callback per
 * value keeps it with no fields of its own and makes the screen's whole contract its signature.
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
            ThemeSection(
                selected = state.themePreference,
                onSelect = state::selectTheme,
            )
            LanguageSection(
                selected = state.locale,
                onSelect = state::applyLocale,
            )
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    AzkarThemePreview {
        SettingsScreen(state = previewAzkarAppState(), onClose = {})
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
        OutlinedButton(
            text = stringResource(Res.string.settingsBack),
            onClick = onClose,
        )
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
    SettingsCard(
        title = stringResource(Res.string.settingsThemeHeader),
        body = stringResource(Res.string.settingsThemeBody),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    SettingsCard(
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
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) colors.highlight else Color.Transparent)
                .border(
                    1.dp,
                    if (selected) colors.primary.copy(alpha = 0.45f) else colors.border,
                    RoundedCornerShape(10.dp),
                ).pressableClick(scale = 1f, onClick = onClick)
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

/** A titled group: what it is called, one line saying what it does, then the control itself. */
@Composable
private fun SettingsCard(
    title: String,
    body: String,
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
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/**
 * The header's way out, outlined rather than filled.
 *
 * Leaving a screen is the default outcome, not the thing the user came to do, so it does not get the
 * primary button's fill — the same reasoning as the library's "Back to dashboard" sitting in its empty
 * state rather than in the toolbar.
 */
@Composable
private fun OutlinedButton(
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
