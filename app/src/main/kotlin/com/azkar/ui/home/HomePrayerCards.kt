package com.azkar.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.azkar.generated.resources.nextPrayerIn
import com.azkar.generated.resources.prayerNotification
import com.azkar.generated.resources.prayerNotificationsShort
import com.azkar.generated.resources.prayerTimesLocation
import com.azkar.generated.resources.todayPrayerTimes
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.pressFeedback
import com.azkar.ui.components.pressableClick
import com.azkar.ui.preview.previewCountdown
import com.azkar.ui.preview.previewPrayerRows
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.CardSize
import org.jetbrains.compose.resources.stringResource

/** The disc an individual prayer's glyph sits in. */
private val PRAYER_DISC_SIZE = 38.dp

/**
 * The gap between two prayer rows.
 *
 * Wide enough that the 1dp row borders read as two edges rather than one line — anything under 4dp
 * looks like a rendering artefact at this scale, and the rows collapse back into a slab.
 */
private val ROW_GAP = 6.dp

/**
 * The reserved leading width of every prayer row: the optional accent bar plus the disc and its gap.
 *
 * Fixed rather than intrinsic so a highlighted row's 3dp bar does not push its own name out of
 * alignment with the other four.
 */
private val LEADING_WIDTH = 3.dp + 12.dp + PRAYER_DISC_SIZE

/**
 * The narrowest card content width at which the prayer card's header still fits on one line.
 *
 * The arithmetic is the sum of what the header cannot give up: the 38dp badge, the ~140dp
 * notifications button, the ~135dp countdown pill, three 12dp gaps, and the ~205dp its 20sp English
 * title needs to set on one line. Everything below that goes to the stacked header instead of being
 * taken out of the controls — see [InlinePrayerCardHeader].
 */
private val HEADER_INLINE_MIN_WIDTH = 560.dp

/**
 * A prayer's glyph and its hue.
 *
 * The colours are the one place the dashboard deliberately leaves the theme palette: a sunrise is
 * amber and an Isha moon is indigo in either theme, and letting them invert would break the one
 * cue that lets a row be recognised before it is read. They are therefore fixed here rather than
 * added as tokens — they are illustration, not interface.
 */
private data class PrayerAccent(
    val icon: ImageVector,
    val color: Color,
)

private fun prayerAccent(key: PrayerKey): PrayerAccent =
    when (key) {
        PrayerKey.FAJR -> PrayerAccent(AzkarIcons.Sunrise, Color(0xFFF59E0B))
        PrayerKey.DHUHR -> PrayerAccent(AzkarIcons.Sun, Color(0xFFFBBF24))
        PrayerKey.ASR -> PrayerAccent(AzkarIcons.Sun, Color(0xFFFB923C))
        PrayerKey.MAGHRIB -> PrayerAccent(AzkarIcons.Sunset, Color(0xFFF97316))
        PrayerKey.ISHA -> PrayerAccent(AzkarIcons.Moon, Color(0xFF6366F1))
    }

/**
 * The dashboard's primary card: today's five prayers, the countdown to the next one, and the
 * per-prayer notification switches.
 *
 * Replaces the old three-card stack (`RemainingToPrayerCard` + `PrayerNotificationsCard` +
 * `PrayerTableCard`). Those existed as separate surfaces only because the JavaFX `.settings-card`
 * wrapper could not be merged with `.prayer-table-card`; in one column they read as three unrelated
 * cards rather than as one prayer card with a header. The global notification control moved into the
 * header, where it now scopes the whole table it governs.
 *
 * The header picks one of two layouts from the card's own content width — see
 * [HEADER_INLINE_MIN_WIDTH] and [InlinePrayerCardHeader] for why it cannot simply always be one row.
 */
@Composable
fun PrayerTimesCard(
    rows: List<PrayerRowState>,
    countdown: CountdownState,
    locationLabel: String = "Ramallah, Palestine",
    notificationsEnabled: Boolean,
    onGlobalToggle: (Boolean) -> Unit,
    onNotificationToggled: (PrayerKey, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors

    AzkarCard(modifier = modifier.fillMaxWidth(), size = CardSize.MD, contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Inside the card, not outside it: `maxWidth` here is the *content* width, which is the
            // number the header has to fit its controls and its title into.
            BoxWithConstraints {
                if (maxWidth < HEADER_INLINE_MIN_WIDTH) {
                    StackedPrayerCardHeader(
                        countdown = countdown,
                        locationLabel = locationLabel,
                        notificationsEnabled = notificationsEnabled,
                        onGlobalToggle = onGlobalToggle,
                    )
                } else {
                    InlinePrayerCardHeader(
                        countdown = countdown,
                        locationLabel = locationLabel,
                        notificationsEnabled = notificationsEnabled,
                        onGlobalToggle = onGlobalToggle,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.background)
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                rows.forEach { row ->
                    PrayerTableRow(row = row, onNotificationToggled = onNotificationToggled)
                }
            }
        }
    }
}

@Preview
@Composable
private fun PrayerTimesCardPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(520.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrayerTimesCard(
                rows = previewPrayerRows(),
                countdown = previewCountdown(),
                notificationsEnabled = true,
                onGlobalToggle = {},
                onNotificationToggled = { _, _ -> },
            )
            PrayerTimesCard(
                rows = previewPrayerRows(),
                countdown = previewCountdown(),
                notificationsEnabled = false,
                onGlobalToggle = {},
                onNotificationToggled = { _, _ -> },
            )
        }
    }
}

/**
 * The card's badge, title and location line.
 *
 * Kept apart from the two header layouts because the title is the only elastic part of the header,
 * and both layouts need it to be the part that gives way rather than the controls beside it.
 */
@Composable
private fun PrayerCardTitle(
    locationLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.todayPrayerTimes),
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(Res.string.prayerTimesLocation, locationLabel),
            color = colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Title on the left, notifications button and countdown pill on the right — the reference layout.
 *
 * [HEADER_INLINE_MIN_WIDTH] exists because of how `Row` measures: non-weighted children are measured
 * *before* weighted ones, so the two controls take their full intrinsic width first and only what is
 * left over reaches the `weight(1f)` title column. Below that threshold there is nothing left, and
 * the countdown pill was handed a few dp — narrow enough that its two labels wrapped one character
 * per line and the pill grew into a tall blue strip. A title ellipsized is a far smaller failure than
 * a control that unrecognisably explodes, which is why the threshold is set where the title still
 * fits whole and the layout stacks below that rather than clamping.
 */
@Composable
private fun InlinePrayerCardHeader(
    countdown: CountdownState,
    locationLabel: String,
    notificationsEnabled: Boolean,
    onGlobalToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionBadge(icon = AzkarIcons.Mosque)
        PrayerCardTitle(locationLabel = locationLabel, modifier = Modifier.weight(1f))
        AllNotificationsButton(
            enabled = notificationsEnabled,
            onToggle = { onGlobalToggle(!notificationsEnabled) },
            modifier = Modifier.padding(end = 2.dp),
        )
        CountdownPill(text = countdown.text)
    }
}

/**
 * The same header with the two controls on a line of their own, for cards too narrow for one row.
 *
 * The notifications button takes `weight(1f, fill = false)` so it is measured at its natural width but
 * yields first: the countdown pill is the header's one piece of live information, so it is the last
 * thing that should be squeezed.
 */
@Composable
private fun StackedPrayerCardHeader(
    countdown: CountdownState,
    locationLabel: String,
    notificationsEnabled: Boolean,
    onGlobalToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionBadge(icon = AzkarIcons.Mosque)
            PrayerCardTitle(locationLabel = locationLabel, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            AllNotificationsButton(
                enabled = notificationsEnabled,
                onToggle = { onGlobalToggle(!notificationsEnabled) },
                modifier = Modifier.weight(1f, fill = false),
            )
            CountdownPill(text = countdown.text)
        }
    }
}

/**
 * The header's master control for prayer notifications: one button that says what it governs.
 *
 * This was an [AzkarToggleSwitch]. A switch is the right control for a form field, but this one sits
 * in a card header beside five rows that each already carry their own bell, so the switch read as a
 * settings row that belonged to nothing in particular — there was no label to say it covered the
 * whole table, and "on" was the only thing the blue track ever asserted.
 *
 * A labelled button fixes both halves. [AzkarIcons.Bell] / [AzkarIcons.BellOff] carry the state the
 * same way the per-row glyphs already do, so the header and its rows speak one language, and the
 * label names the scope. The off state is an outlined pill rather than a filled one so "disabled"
 * reads as *this control* being off instead of bleeding into the countdown pill beside it.
 */
@Composable
private fun AllNotificationsButton(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)

    val background by
        animateColorAsState(
            targetValue =
                if (enabled) {
                    colors.highlight
                } else if (hovered) {
                    colors.hoverNeutral
                } else {
                    Color.Transparent
                },
            label = "notificationsButtonBackground",
        )
    // primaryStrong rather than primary: the enabled pill fills `highlight`, and `primary` is a fill
    // colour that does not clear the 4.5:1 this small text owes on top of it.
    val contentColor by
        animateColorAsState(
            targetValue = if (enabled) colors.primaryStrong else colors.textSecondary,
            label = "notificationsButtonContent",
        )

    Row(
        modifier =
            modifier
                // Ahead of `clip` so the border the enabled state paints travels with the label,
                // instead of staying put around a pill that dips.
                .pressFeedback(interaction)
                .clip(shape)
                .background(background)
                .border(1.dp, if (enabled) colors.primary.copy(alpha = 0.35f) else colors.border, shape)
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = if (enabled) AzkarIcons.Bell else AzkarIcons.BellOff,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = stringResource(Res.string.prayerNotificationsShort),
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * The pale-blue "Next prayer in: 1h 32m 04s" pill that anchors the prayer card's header.
 *
 * Both labels are single-line with an ellipsis so that, however little room the header can spare,
 * the pill loses width rather than gaining the height a wrapped label costs.
 */
@Composable
private fun CountdownPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.highlight)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = AzkarIcons.Clock,
            contentDescription = null,
            tint = colors.primaryStrong,
            modifier = Modifier.size(16.dp),
        )
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = stringResource(Res.string.nextPrayerIn),
                color = colors.primaryStrong,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = text,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One prayer inside the table.
 *
 * Every row is its own surface on the table's backdrop, separated by [ROW_GAP]. The rows used to be
 * 2dp apart inside a single pale block, which fused the five of them into one slab: the eye found a
 * rectangle, not five prayers. A surface per row restores the boundary the data already had, and the
 * highlighted row keeps the JavaFX `next-prayer` cue — a leading accent bar — but no longer inverts
 * its text to white. Inverting meant the row had to be a saturated *fill*, which cannot hold the
 * pale tint the reference design uses for it, so the row is tinted and the accent bar carries the
 * emphasis instead.
 */
@Composable
private fun PrayerTableRow(
    row: PrayerRowState,
    onNotificationToggled: (PrayerKey, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val accent = prayerAccent(row.key)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (row.isNext) colors.nextPrayerSurface else colors.cardBackground)
                .border(
                    width = 1.dp,
                    color = if (row.isNext) colors.primary.copy(alpha = 0.45f) else colors.border,
                    shape = RoundedCornerShape(12.dp),
                ).padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrayerRowLeading(
            isNext = row.isNext,
            accent = accent,
            modifier = Modifier.width(LEADING_WIDTH),
        )
        PrayerRowLabel(row = row, modifier = Modifier.weight(1f))
        Text(
            text = row.value,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Icon(
            imageVector = if (row.notificationsEnabled) AzkarIcons.Bell else AzkarIcons.BellOff,
            contentDescription = stringResource(Res.string.prayerNotification),
            tint = if (row.notificationsEnabled) colors.primaryStrong else colors.textSecondary,
            modifier =
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .pressableClick {
                        onNotificationToggled(row.key, !row.notificationsEnabled)
                    },
        )
    }
}

/**
 * A prayer's name, and the one line of context under it.
 *
 * Only the next prayer has a subtitle, because only the next prayer has anything to say that its
 * time does not. The other four used to print their time here as well as in the value beside it — once
 * as `5:12 AM` and once as `17:12` — and a row showing the same instant twice in two conventions is a
 * row whose reader has to guess which of the two to trust. Rendering conditionally rather than as an
 * empty string keeps the four ordinary rows one line tall, so the names stay on a single baseline down
 * the card; an empty `Text` would have kept the line and left them sitting a line below their own.
 */
@Composable
private fun PrayerRowLabel(
    row: PrayerRowState,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(modifier = modifier) {
        Text(
            text = row.name,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.timeLabel.isNotBlank()) {
            Text(
                text = row.timeLabel,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The leading edge of a prayer row: the "next" accent bar and the prayer's own disc.
 *
 * Both halves occupy the same fixed [LEADING_WIDTH] whether or not the bar is drawn, so the names
 * of all five rows stay on one vertical line instead of the current prayer indenting by 3dp.
 */
@Composable
private fun PrayerRowLeading(
    isNext: Boolean,
    accent: PrayerAccent,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isNext) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.primary),
            )
        } else {
            Spacer(Modifier.width(3.dp))
        }
        Box(
            modifier =
                Modifier
                    .size(PRAYER_DISC_SIZE)
                    .clip(CircleShape)
                    .background(accent.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = accent.icon,
                contentDescription = null,
                tint = accent.color,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * The round tinted disc that leads every card header in the rail.
 *
 * A 44dp disc is too heavy to sit inside a card that already has a title, so this is 38dp — still
 * large enough to read as an icon button at a glance, small enough not to compete with the title.
 */
@Composable
private fun SectionBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.highlight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primaryStrong,
            modifier = Modifier.size(19.dp),
        )
    }
}
